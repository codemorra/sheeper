package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.registry.ModParticles;
import de.codemorra.sheeper.world.WaterArenaData;
import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Discovers, distributes and displays fixed air fountains for one arena. */
final class WaterAirStations {
    private static final int MAX_STATIONS = 48;
    private static final int MAX_SCAN_COLUMNS_PER_TICK = 256;
    private static final int EMITTER_TICKS = 20;
    private static final double VIEW_DISTANCE = 80;
    private static final int RETRY_TICKS = 200;
    private static final double MIN_CLEAR_HEIGHT = 1.0D;

    private final List<Vec3> stations = new ArrayList<>();
    private final List<Vec3> candidates = new ArrayList<>();
    @Nullable
    private Iterator<BlockPos> scan;
    private boolean missedChunks;
    private boolean scanComplete;
    private double density;
    private long nextScanTick;
    private long nextEmitterTick;

    /** Scans during expansion and emits only once the captured arena is ready. */
    void tick(ServerLevel level, VolumeBounds volume, boolean ready) {
        long now = level.getGameTime();
        double configuredDensity = WaterSheeperConfig.WATER_AIR_STATIONS_PER_1000_BLOCKS.get();
        if (density != configuredDensity) {
            density = configuredDensity;
            scan = null;
            scanComplete = false;
            candidates.clear();
            nextScanTick = now;
        }
        if (!scanComplete && now >= nextScanTick) {
            advanceScan(level, volume, now);
        }
        if (!ready || now < nextEmitterTick || stations.isEmpty()) {
            return;
        }
        nextEmitterTick = now + EMITTER_TICKS;
        for (Vec3 station : stations) {
            BlockPos column = new BlockPos(station);
            double height = volume.maximumY() + 1 - station.y;
            if (height >= MIN_CLEAR_HEIGHT && volume.contains(station)
                    && WaterArenaData.isLoaded(level, column) && station.equals(findSurface(level, volume, column))) {
                sendEmitter(level, station, height);
            }
        }
    }

    /** Measures from the visible column instead of Vanilla's short radius around the emitter base. */
    private static void sendEmitter(ServerLevel level, Vec3 station, double height) {
        for (ServerPlayer viewer : level.players()) {
            Vec3 eye = viewer.getEyePosition();
            double vertical = eye.y - Mth.clamp(eye.y, station.y, station.y + height);
            if (horizontalDistanceSquared(station, eye) + vertical * vertical <= VIEW_DISTANCE * VIEW_DISTANCE) {
                level.sendParticles(viewer, ModParticles.WATER_FOUNTAIN.get(), true,
                        station.x, station.y + 0.05D, station.z, 0, height - 0.1D, 0, EMITTER_TICKS, 1);
            }
        }
    }

    private void advanceScan(ServerLevel level, VolumeBounds volume, long now) {
        if (scan == null) {
            int extent = Mth.ceil(volume.radius());
            BlockPos center = volume.center().atY(volume.minimumY());
            scan = BlockPos.betweenClosed(center.offset(-extent, 0, -extent),
                    center.offset(extent, 0, extent)).iterator();
            missedChunks = false;
        }
        int budget = Math.min(MAX_SCAN_COLUMNS_PER_TICK,
                WaterSheeperConfig.WATER_ARENA_SEARCH_BUDGET_PER_SECOND.get() / 20);
        while (budget-- > 0 && scan.hasNext()) {
            BlockPos column = scan.next();
            double dx = Math.abs(column.getX() - volume.center().getX()) + 0.5D;
            double dz = Math.abs(column.getZ() - volume.center().getZ()) + 0.5D;
            if (dx * dx + dz * dz > volume.radius() * volume.radius()) {
                continue;
            }
            if (!WaterArenaData.isLoaded(level, column)) {
                missedChunks = true;
                continue;
            }
            Vec3 surface = findSurface(level, volume, column);
            if (surface != null) {
                candidates.add(surface);
            }
        }
        if (!scan.hasNext()) {
            placeStations(volume);
            candidates.clear();
            scan = null;
            scanComplete = !missedChunks;
            nextScanTick = now + RETRY_TICKS;
        }
    }

    /**
     * Finds the first usable surface below open water. Solid shapes and natural
     * water supply their exact surface heights. Open columns reach the virtual
     * floor, so arenas in the air or deeper than the seabed can still host fountains.
     */
    @Nullable
    private static Vec3 findSurface(ServerLevel level, VolumeBounds volume, BlockPos column) {
        double top = volume.maximumY() + 1;
        int bottom = Math.max(level.getMinBuildHeight(), volume.minimumY() - 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = Mth.ceil(top) - 1; y >= bottom; y--) {
            cursor.set(column.getX(), y, column.getZ());
            var state = level.getBlockState(cursor);
            var fluid = state.getFluidState();
            if (!fluid.isEmpty() && !fluid.is(FluidTags.WATER)) {
                return null;
            }
            var shape = state.getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                double base = Math.max(volume.minimumY(), y + shape.max(Direction.Axis.Y));
                return state.is(BlockTags.LEAVES) || top - base < MIN_CLEAR_HEIGHT
                        ? null : new Vec3(column.getX() + 0.5D, base, column.getZ() + 0.5D);
            }
            if (fluid.is(FluidTags.WATER) && !level.getFluidState(cursor.above()).is(FluidTags.WATER)) {
                double base = y + fluid.getHeight(level, cursor);
                if (base >= volume.minimumY() && top - base >= MIN_CLEAR_HEIGHT) {
                    return new Vec3(column.getX() + 0.5D, base, column.getZ() + 0.5D);
                }
            }
        }
        return top - volume.minimumY() >= MIN_CLEAR_HEIGHT
                ? Vec3.atBottomCenterOf(column.atY(volume.minimumY())) : null;
    }

    /** Spreads stations across distinct columns while favoring room to swim around each one. */
    private void placeStations(VolumeBounds volume) {
        int target = Mth.clamp((int) Math.round(candidates.size() * density / 1_000), 1, MAX_STATIONS);
        if (!missedChunks) {
            stations.removeIf(station -> !candidates.contains(station));
            if (stations.size() > target) {
                stations.subList(target, stations.size()).clear();
            }
        }
        if (stations.size() >= target || candidates.isEmpty()) {
            return;
        }
        Vec3 center = Vec3.atBottomCenterOf(volume.center());
        double[] scores = new double[candidates.size()];
        for (int index = 0; index < candidates.size(); index++) {
            Vec3 candidate = candidates.get(index);
            double clearance = volume.radius() - Math.sqrt(horizontalDistanceSquared(candidate, center));
            scores[index] = 4 * clearance * clearance;
        }
        for (Vec3 station : stations) {
            updateSpacingScores(scores, station);
        }
        while (stations.size() < target) {
            double bestScore = 0;
            int bestIndex = -1;
            for (int index = 0; index < scores.length; index++) {
                if (scores[index] > bestScore) {
                    bestScore = scores[index];
                    bestIndex = index;
                }
            }
            if (bestIndex < 0) {
                return;
            }
            Vec3 best = candidates.get(bestIndex);
            stations.add(best);
            updateSpacingScores(scores, best);
        }
    }

    /** Keeps the nearest-station distance without rechecking every existing station on each pick. */
    private void updateSpacingScores(double[] scores, Vec3 station) {
        for (int index = 0; index < scores.length; index++) {
            scores[index] = Math.min(scores[index], horizontalDistanceSquared(candidates.get(index), station));
        }
    }

    private static double horizontalDistanceSquared(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return dx * dx + dz * dz;
    }

    /** Saves positions so leaving or reloading the arena cannot reshuffle them. */
    CompoundTag save() {
        var tag = new CompoundTag();
        var positions = new ListTag();
        for (Vec3 station : stations) {
            var entry = new CompoundTag();
            entry.putDouble("X", station.x);
            entry.putDouble("Y", station.y);
            entry.putDouble("Z", station.z);
            positions.add(entry);
        }
        tag.put("Positions", positions);
        tag.putBoolean("ScanComplete", scanComplete);
        tag.putDouble("Density", density);
        return tag;
    }

    /** Restores the fixed positions and restarts any unfinished terrain discovery. */
    void load(CompoundTag tag) {
        stations.clear();
        candidates.clear();
        scan = null;
        scanComplete = tag.getBoolean("ScanComplete");
        density = tag.getDouble("Density");
        nextScanTick = 0;
        nextEmitterTick = 0;
        for (Tag value : tag.getList("Positions", Tag.TAG_COMPOUND)) {
            if (stations.size() >= MAX_STATIONS) {
                break;
            }
            var entry = (CompoundTag) value;
            Vec3 station = new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z"));
            if (Double.isFinite(station.x) && Double.isFinite(station.y) && Double.isFinite(station.z)) {
                stations.add(station);
            }
        }
    }
}
