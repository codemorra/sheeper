package de.codemorra.sheeper.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.entity.WaterSheeperEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Persists virtual arena geometry, startup and drainage without placing water blocks.
 */
public final class WaterArenaData extends SavedData {
    private static final String FILE_NAME = "sheeper_water_arenas";
    private final Map<UUID, Arena> arenas = new LinkedHashMap<>();
    private final List<WaterArenaSpawnWave> releasedSpawnWaves = new ArrayList<>();

    /**
     * Captured bounds shared by virtual swimming, attacks and client rendering.
     */
    public record VolumeBounds(BlockPos center, double radius, int minimumY, double maximumY) {
        /** Captures the requested cylinder, clipping its vertical extent to the world. */
        public static VolumeBounds capture(Level level, BlockPos center, double radius, int depth, int height) {
            return new VolumeBounds(center.immutable(), radius,
                    Math.max(level.getMinBuildHeight(), center.getY() - depth),
                    Math.min(level.getMaxBuildHeight() - 1, center.getY() + height - 1));
        }

        /** Uses the same geometry limits for saved data and network snapshots. */
        public boolean isValid() {
            return Double.isFinite(radius) && radius >= 1 && radius <= 128
                    && Double.isFinite(maximumY) && minimumY <= maximumY && maximumY - minimumY <= 128
                    && minimumY >= -2032 && maximumY <= 2031;
        }

        /** Rejects inverted or out-of-world bounds before arena creation or synchronization. */
        public boolean isValidFor(Level level) {
            return isValid() && minimumY >= level.getMinBuildHeight() && maximumY < level.getMaxBuildHeight();
        }

        /** Signed distance to the nearest cylinder boundary; positive values are inside. */
        public double interiorDistance(Vec3 point) {
            double dx = point.x - center.getX() - 0.5D;
            double dz = point.z - center.getZ() - 0.5D;
            return Math.min(radius - Math.sqrt(dx * dx + dz * dz),
                    Math.min(point.y - minimumY, maximumY + 1.0D - point.y));
        }

        /** Tests continuous world coordinates without rounding the circular boundary to cells. */
        public boolean contains(Vec3 point) {
            return interiorDistance(point) >= 0;
        }

        /**
         * @return whether a water cell belongs to this arena's horizontal circle and
         *         vertical extent
         */
        public boolean contains(BlockPos pos) {
            double dx = (double) pos.getX() - center.getX();
            double dz = (double) pos.getZ() - center.getZ();
            return pos.getY() >= minimumY && pos.getY() <= maximumY && dx * dx + dz * dz <= radius * radius;
        }
    }

    /** Fixed geometry and world-time clocks keep startup and drainage consistent on both sides. */
    public record AnimatedVolume(VolumeBounds bounds, long startTick, int durationTicks,
            long drainStartTick, int drainDurationTicks) {
        /** Returns the current cylinder; a drained volume has zero height and is excluded from active lookups. */
        public VolumeBounds at(double gameTime) {
            double progress = durationTicks == 0 ? 1
                    : Math.max(0, Math.min(1, (gameTime - startTick) / durationTicks));
            double surface = bounds.maximumY() + 1;
            if (isDraining()) {
                double drained = Math.max(0, Math.min(1, (gameTime - drainStartTick) / drainDurationTicks));
                surface -= (surface - bounds.minimumY()) * drained;
            }
            return new VolumeBounds(bounds.center(), Math.max(1, bounds.radius() * progress),
                    bounds.minimumY(), surface - 1);
        }

        /** Whether the one-time startup front is still advancing. */
        public boolean isExpanding(double gameTime) {
            return !isDraining() && durationTicks > 0 && gameTime - startTick < durationTicks;
        }

        public boolean isDraining() {
            return drainDurationTicks > 0;
        }

        /** Also expires stale client geometry between snapshot packets. */
        public boolean hasWater(double gameTime) {
            return !isDraining() || gameTime - drainStartTick < drainDurationTicks;
        }
    }

    private static final class Arena {
        private final WaterArenaSpawnWave spawnWave = new WaterArenaSpawnWave();
        private final WaterArenaDrain drain = new WaterArenaDrain();
        private long drainTick;
        private int drainDuration;
        private boolean ready;
        private long startupTick;
        private int startupDuration;
        @Nullable
        private VolumeBounds volume;
    }

    public static WaterArenaData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WaterArenaData::load, WaterArenaData::new, FILE_NAME);
    }

    public enum AirState {
        OUTSIDE, SHELTERED, CONSUMING, REFILLING
    }

    /** A refill column takes priority over consumption when arenas overlap. */
    public static AirState airState(ServerPlayer player) {
        ServerLevel level = player.getLevel();
        WaterArenaData data = level.getDataStorage().get(WaterArenaData::load, FILE_NAME);
        if (data == null) {
            return AirState.OUTSIDE;
        }
        Vec3 eye = player.getEyePosition();
        long now = level.getGameTime();
        AirState result = AirState.OUTSIDE;
        for (var entry : data.arenas.entrySet()) {
            Arena arena = entry.getValue();
            if (arena.volume == null || !arena.volume.contains(eye)) {
                continue;
            }
            var animated = data.animation(arena);
            if (!animated.hasWater(now) || !animated.at(now).contains(eye)) {
                continue;
            }
            AirState state = arena.ready && !animated.isDraining()
                    && level.getEntity(entry.getKey()) instanceof WaterSheeperEntity boss
                            ? boss.getArenaAirState(player, eye) : AirState.SHELTERED;
            if (state == AirState.REFILLING) {
                return state;
            }
            if (state == AirState.CONSUMING || result == AirState.OUTSIDE) {
                result = state;
            }
        }
        return result;
    }

    /** Does not create new saved data in dimensions without an encounter. */
    public static void tick(ServerLevel level) {
        WaterArenaData data = level.getDataStorage().get(WaterArenaData::load, FILE_NAME);
        if (data != null) {
            data.releasedSpawnWaves.forEach(wave -> wave.clear(level));
            data.releasedSpawnWaves.clear();
            var iterator = data.arenas.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                Arena arena = entry.getValue();
                if (arena.volume == null) {
                    continue;
                }
                AnimatedVolume volume = data.animation(arena);
                if (volume.isDraining()) {
                    boolean changed = arena.drain.tick(level, volume,
                            point -> data.hasOtherWater(level, entry.getKey(), point));
                    if (!volume.hasWater(level.getGameTime()) && arena.drain.isFinished()) {
                        iterator.remove();
                        changed = true;
                    }
                    if (changed) {
                        data.setDirty();
                    }
                } else {
                    if (arena.spawnWave.tick(level, volume)) {
                        data.setDirty();
                    }
                    if (!arena.ready && !volume.isExpanding(level.getGameTime())) {
                        arena.ready = true;
                        data.setDirty();
                    }
                }
            }
        }
    }

    /**
     * Captured arenas remain active while unloaded and recede independently after owner removal.
     */
    public static List<VolumeBounds> activeVolumes(ServerLevel level) {
        var animated = animatedVolumes(level);
        return animated.isEmpty() ? List.of()
                : animated.stream().map(volume -> volume.at(level.getGameTime())).toList();
    }

    /** Synchronizes both lifecycle clocks and bounds so clients can animate between packets. */
    public static List<AnimatedVolume> animatedVolumes(ServerLevel level) {
        WaterArenaData data = level.getDataStorage().get(WaterArenaData::load, FILE_NAME);
        return data == null || data.arenas.isEmpty() ? List.of()
                : data.arenas.values().stream()
                        .filter(arena -> arena.volume != null && arena.volume.isValidFor(level))
                        .map(data::animation).filter(volume -> volume.hasWater(level.getGameTime())).toList();
    }

    private AnimatedVolume animation(Arena arena) {
        return new AnimatedVolume(arena.volume, arena.startupTick, arena.ready ? 0 : arena.startupDuration,
                arena.drainTick, arena.drainDuration);
    }

    /** Overlapping arenas keep their own water physics and must not drag swimmers down with this one. */
    private boolean hasOtherWater(ServerLevel level, UUID draining, Vec3 point) {
        return arenas.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(draining)
                && entry.getValue().volume != null && animation(entry.getValue()).hasWater(level.getGameTime())
                && animation(entry.getValue()).at(level.getGameTime()).contains(point));
    }

    public boolean isReady(UUID boss) {
        Arena arena = arenas.get(boss);
        return arena != null && arena.ready && arena.drainDuration == 0;
    }

    /** Returns captured bounds without creating an arena for a rejected spawn. */
    @Nullable
    public VolumeBounds getVolume(UUID boss) {
        Arena arena = arenas.get(boss);
        return arena == null ? null : arena.volume;
    }

    /**
     * Captures the arena bounds and starts expansion once. Existing arenas retain
     * their geometry and startup clock, including after the boss reloads.
     *
     * @param level  world supplying valid build-height limits
     * @param boss   arena owner
     * @param center fixed encounter origin
     * @param radius horizontal combat radius in blocks
     * @param depth  lower search extent below the origin
     * @param height common water surface height above the origin
     * @return persistent volume bounds, or null if the requested geometry is invalid
     */
    @Nullable
    public VolumeBounds prepareVolume(ServerLevel level, UUID boss, BlockPos center, double radius, int depth,
            int height) {
        VolumeBounds captured = getVolume(boss);
        if (captured == null) {
            captured = VolumeBounds.capture(level, center, radius, depth, height);
        }
        if (!captured.isValidFor(level)) {
            return null;
        }
        Arena arena = arenas.computeIfAbsent(boss, ignored -> new Arena());
        if (arena.volume == null) {
            arena.volume = captured;
            arena.ready = false;
            arena.startupTick = level.getGameTime();
            arena.startupDuration = (int) Math.round(WaterSheeperConfig.WATER_ARENA_BUILD_DURATION.get() * 20);
            setDirty();
        }
        return arena.volume;
    }

    /**
     * Clears attack-hit leaves without introducing physical arena water. Existing
     * natural water in waterlogged leaves remains a source; dry leaves leave air.
     *
     * @param attacker boss whose active captured arena must contain the block
     * @return whether leaves were replaced; permission checks belong to the attack
     */
    public boolean replaceAttackLeaves(ServerLevel level, LivingEntity attacker, BlockPos pos) {
        UUID boss = attacker.getUUID();
        Arena arena = arenas.get(boss);
        if (arena == null || arena.volume == null || !arena.volume.contains(pos)
                || !isLoaded(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.LEAVES)) {
            return false;
        }
        boolean naturalWater = state.getFluidState().is(FluidTags.WATER);
        var blockEntity = level.getBlockEntity(pos);
        BlockState replacement = naturalWater ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        if (!level.setBlock(pos, replacement, Block.UPDATE_ALL)) {
            return false;
        }
        Block.dropResources(state, level, pos, blockEntity, attacker, ItemStack.EMPTY);
        level.levelEvent(2001, pos, Block.getId(state));
        return true;
    }

    /** Marks the one-time startup complete without changing captured geometry. */
    public void finishStartup(UUID boss) {
        Arena arena = arenas.computeIfAbsent(boss, ignored -> new Arena());
        if (!arena.ready) {
            arena.ready = true;
            setDirty();
        }
    }

    /** Starts a persisted lowering water level after death or explicit removal; unloading alone does not drain. */
    public void startDraining(ServerLevel level, UUID boss) {
        Arena arena = arenas.remove(boss);
        if (arena == null) {
            return;
        }
        releasedSpawnWaves.add(arena.spawnWave);
        if (arena.volume != null && arena.volume.isValidFor(level)) {
            // Freeze a partially built arena rather than completing its startup after removal.
            arena.volume = animation(arena).at(level.getGameTime());
            arena.ready = true;
            arena.drainTick = level.getGameTime();
            arena.drainDuration = (int) Math.round(WaterSheeperConfig.WATER_ARENA_DRAIN_DURATION.get() * 20);
            // Detach from the owner so a boss returning from another dimension can start a fresh arena.
            arenas.put(UUID.randomUUID(), arena);
        }
        setDirty();
    }

    public static boolean isLoaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ())) != null;
    }

    public static WaterArenaData load(CompoundTag tag) {
        WaterArenaData data = new WaterArenaData();
        ListTag saved = tag.getList("Arenas", Tag.TAG_COMPOUND);
        for (int index = 0; index < saved.size(); index++) {
            CompoundTag entry = saved.getCompound(index);
            UUID boss = entry.getUUID("Boss");
            Arena arena = new Arena();
            arena.ready = entry.getBoolean("Ready");
            arena.drainTick = Math.max(0, entry.getLong("DrainTick"));
            arena.drainDuration = Math.max(0, Math.min(600, entry.getInt("DrainDuration")));
            arena.drain.load(entry.getList("DrainPlayers", Tag.TAG_COMPOUND));
            arena.spawnWave.load(entry.getCompound("SpawnWave"));
            arena.startupTick = Math.max(0, entry.getLong("StartupTick"));
            arena.startupDuration = Math.max(0, Math.min(600, entry.getInt("StartupDuration")));
            CompoundTag volume = entry.getCompound("Volume");
            double radius = volume.getDouble("Radius");
            VolumeBounds bounds = new VolumeBounds(BlockPos.of(volume.getLong("Center")), radius,
                    volume.getInt("MinimumY"), volume.getDouble("MaximumY"));
            if (volume.contains("Center", Tag.TAG_LONG) && bounds.isValid()) {
                arena.volume = bounds;
            }
            data.arenas.put(boss, arena);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag saved = new ListTag();
        arenas.forEach((boss, arena) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Boss", boss);
            entry.putBoolean("Ready", arena.ready);
            entry.putLong("DrainTick", arena.drainTick);
            entry.putInt("DrainDuration", arena.drainDuration);
            entry.put("DrainPlayers", arena.drain.save());
            entry.put("SpawnWave", arena.spawnWave.save());
            entry.putLong("StartupTick", arena.startupTick);
            entry.putInt("StartupDuration", arena.startupDuration);
            if (arena.volume != null) {
                CompoundTag volume = new CompoundTag();
                volume.putLong("Center", arena.volume.center().asLong());
                volume.putDouble("Radius", arena.volume.radius());
                volume.putInt("MinimumY", arena.volume.minimumY());
                volume.putDouble("MaximumY", arena.volume.maximumY());
                entry.put("Volume", volume);
            }
            saved.add(entry);
        });
        tag.put("Arenas", saved);
        return tag;
    }
}
