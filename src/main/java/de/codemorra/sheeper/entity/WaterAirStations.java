package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.registry.ModParticles;
import de.codemorra.sheeper.world.ArenaWater;
import de.codemorra.sheeper.world.WaterArenaData;
import de.codemorra.sheeper.world.WaterArenaData.AirColumn;
import de.codemorra.sheeper.world.WaterArenaData.AirState;
import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Discovers, distributes and displays fixed air fountains for one arena. */
final class WaterAirStations {
    private static final int MAX_STATIONS = 48;
    private static final int MAX_SCAN_COLUMNS_PER_TICK = 256;
    private static final int EMITTER_TICKS = 20;
    private static final double VIEW_DISTANCE = 80;

    private final List<Vec3> stations = new ArrayList<>();
    private final List<Vec3> candidates = new ArrayList<>();
    private List<Vec3> loadedStations = List.of();
    private final Map<UUID, Access> access = new HashMap<>();
    @Nullable
    private Iterator<BlockPos> scan;
    private boolean scanComplete;
    private double density;
    private long nextEmitterTick;

    private record Access(Vec3 eye, long tick, boolean reachable) {
    }

    /** Scans during expansion and emits only once the captured arena is ready. */
    void tick(ServerLevel level, VolumeBounds volume, boolean ready) {
        long now = level.getGameTime();
        double configuredDensity = WaterSheeperConfig.WATER_AIR_STATIONS_PER_1000_BLOCKS.get();
        if (density != configuredDensity) {
            density = configuredDensity;
            scan = null;
            scanComplete = false;
            candidates.clear();
        }
        if (!scanComplete) {
            advanceScan(volume);
        }
        if (!ready || now < nextEmitterTick) {
            return;
        }
        nextEmitterTick = now + EMITTER_TICKS;
        List<Vec3> loaded = new ArrayList<>(stations.size());
        double height = volume.maximumY() + 1 - volume.minimumY();
        for (Vec3 position : stations) {
            Vec3 base = position.add(0, volume.minimumY(), 0);
            if (volume.contains(base) && WaterArenaData.isLoaded(level, new BlockPos(base))) {
                loaded.add(position);
                sendEmitter(level, base, height);
            }
        }
        if (!loaded.equals(loadedStations)) {
            loadedStations = List.copyOf(loaded);
            access.clear();
        }
        access.values().removeIf(entry -> now - entry.tick() > EMITTER_TICKS);
    }

    /** Finds a refill column or checks for an open swim route before imposing air loss. */
    AirState airState(ServerPlayer player, VolumeBounds volume, Vec3 eye) {
        for (Vec3 station : loadedStations) {
            if (Math.abs(eye.x - station.x) < 0.5D && Math.abs(eye.z - station.z) < 0.5D
                    && ArenaWater.isClearWaterPoint(player.level, eye)) {
                return AirState.REFILLING;
            }
        }
        if (loadedStations.isEmpty()) {
            return AirState.SHELTERED;
        }
        long now = player.level.getGameTime();
        Access cached = access.get(player.getUUID());
        if (cached == null || now - cached.tick() >= EMITTER_TICKS || cached.eye().distanceToSqr(eye) >= 16) {
            cached = new Access(eye, now, hasSwimRoute(player, volume, eye));
            access.put(player.getUUID(), cached);
        }
        return cached.reachable() ? AirState.CONSUMING : AirState.SHELTERED;
    }

    /** Uses the same loaded positions as air recovery. */
    List<AirColumn> airColumns(VolumeBounds volume) {
        return loadedStations.stream()
                .map(position -> new AirColumn(new BlockPos(position.x, volume.minimumY(), position.z),
                        volume.maximumY() + 1))
                .toList();
    }

    /** Checks a direct swim and a route over obstacles through the upper water layer. */
    private boolean hasSwimRoute(ServerPlayer player, VolumeBounds volume, Vec3 eye) {
        double upperY = volume.maximumY() + 0.65D;
        Vec3 upper = new Vec3(eye.x, upperY, eye.z);
        boolean canRise = isSwimSegmentOpen(player, eye, upper);
        for (Vec3 station : loadedStations) {
            double directY = Mth.clamp(eye.y, volume.minimumY() + 0.25D, upperY);
            if (isSwimSegmentOpen(player, eye, new Vec3(station.x, directY, station.z))
                    || canRise && isSwimSegmentOpen(player, upper, new Vec3(station.x, upperY, station.z))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSwimSegmentOpen(ServerPlayer player, Vec3 from, Vec3 to) {
        ServerLevel level = player.getLevel();
        BlockPos start = new BlockPos(from);
        BlockPos end = new BlockPos(to);
        for (int x = Math.min(start.getX(), end.getX()) >> 4; x <= Math.max(start.getX(), end.getX()) >> 4; x++) {
            for (int z = Math.min(start.getZ(), end.getZ()) >> 4; z <= Math.max(start.getZ(), end.getZ()) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return ArenaWater.isClearWaterPoint(level, to)
                && level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                        .getType() == HitResult.Type.MISS;
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

    private void advanceScan(VolumeBounds volume) {
        if (scan == null) {
            int extent = Mth.ceil(volume.radius());
            BlockPos center = volume.center().atY(0);
            scan = BlockPos.betweenClosed(center.offset(-extent, 0, -extent),
                    center.offset(extent, 0, extent)).iterator();
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
            candidates.add(Vec3.atBottomCenterOf(column));
        }
        if (!scan.hasNext()) {
            placeStations(volume);
            candidates.clear();
            scan = null;
            scanComplete = true;
        }
    }

    /** Spreads stations across distinct columns while favoring room to swim around each one. */
    private void placeStations(VolumeBounds volume) {
        int target = Mth.clamp((int) Math.round(candidates.size() * density / 1_000), 1, MAX_STATIONS);
        stations.removeIf(station -> !candidates.contains(station));
        if (stations.size() > target) {
            stations.subList(target, stations.size()).clear();
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
            entry.putDouble("Z", station.z);
            positions.add(entry);
        }
        tag.put("Positions", positions);
        tag.putBoolean("ScanComplete", scanComplete);
        tag.putBoolean("FullHeightColumns", true);
        tag.putDouble("Density", density);
        return tag;
    }

    /** Restores the fixed positions and restarts any unfinished terrain discovery. */
    void load(CompoundTag tag) {
        stations.clear();
        candidates.clear();
        scan = null;
        loadedStations = List.of();
        access.clear();
        // Older saves counted only terrain surfaces. Recount the full footprint once.
        scanComplete = tag.getBoolean("ScanComplete") && tag.getBoolean("FullHeightColumns");
        density = tag.getDouble("Density");
        nextEmitterTick = 0;
        for (Tag value : tag.getList("Positions", Tag.TAG_COMPOUND)) {
            if (stations.size() >= MAX_STATIONS) {
                break;
            }
            var entry = (CompoundTag) value;
            double x = entry.getDouble("X");
            double z = entry.getDouble("Z");
            if (Double.isFinite(x) && Double.isFinite(z)) {
                var station = Vec3.atBottomCenterOf(new BlockPos(x, 0, z));
                if (!stations.contains(station)) {
                    stations.add(station);
                }
            }
        }
    }
}
