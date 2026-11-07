package de.codemorra.sheeper.world;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.TreeMap;
import de.codemorra.sheeper.config.SheeperConfig;

import de.codemorra.sheeper.registry.ModBlocks;
import de.codemorra.sheeper.registry.ModFluids;
import de.codemorra.sheeper.fluid.BossWaterFluid;
import de.codemorra.sheeper.fluid.BossWaterlogging;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Tracks initial boss sources and every flowing descendant, including finite
 * waterlogging. Cleanup survives unloading, preserves regular water and player
 * blocks, and prevents retiring arenas from spreading again.
 */
public final class WaterArenaData extends SavedData {
    private static final String FILE_NAME = "sheeper_water_arenas";
    private final Map<UUID, Arena> arenas = new LinkedHashMap<>();
    private final Map<BlockPos, Set<UUID>> owners = new HashMap<>();
    private final Queue<UUID> cleanup = new ArrayDeque<>();

    private static final class Arena {
        private final Set<BlockPos> positions = new LinkedHashSet<>();
        private final Map<BlockPos, Integer> distances = new HashMap<>();
        private final NavigableMap<Integer, Set<BlockPos>> drainWaves = new TreeMap<>(Comparator.reverseOrder());
        private long nextDrainWaveTick;
        private boolean legacyVanilla;
        private boolean generated;
        private boolean cleaning;
    }

    public static WaterArenaData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WaterArenaData::load, WaterArenaData::new, FILE_NAME);
    }

    /** Does not create new saved data in dimensions without an encounter. */
    public static void tick(ServerLevel level, int budget) {
        WaterArenaData data = level.getDataStorage().get(WaterArenaData::load, FILE_NAME);
        if (data != null) {
            data.advanceCleanup(level, budget);
        }
    }

    public boolean isGenerated(UUID boss) {
        Arena arena = arenas.get(boss);
        return arena != null && arena.generated;
    }

    /**
     * Places initial sources in air or washable objects, or promotes already
     * spread boss flow during generation. Regular water is never claimed.
     *
     * @return whether this arena owns the cell after the call
     */
    public boolean claim(ServerLevel level, UUID boss, BlockPos pos) {
        return claim(level, boss, pos, 0);
    }

    /**
     * Places a source in its discovered terrain layer for timed flooding and
     * drainage.
     */
    public boolean claim(ServerLevel level, UUID boss, BlockPos pos, int distance) {
        if (!isLoaded(level, pos)) {
            return false;
        }
        Arena arena = arenas.get(boss);
        if (arena != null && (arena.cleaning || arena.generated)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.BOSS_WATER.get()) || !owners.containsKey(pos)) {
            if (!ModFluids.BOSS_WATER.get().placeInitialSource(level, pos)) {
                return false;
            }
        } else if (!state.getFluidState().isSource() && !ModFluids.BOSS_WATER.get().placeInitialSource(level, pos)) {
            return false;
        }
        BlockPos cell = pos.immutable();
        arena = arenas.computeIfAbsent(boss, ignored -> new Arena());
        if (arena.positions.add(cell)) {
            owners.computeIfAbsent(cell, ignored -> new HashSet<>()).add(boss);
        }
        arena.distances.put(cell, distance);
        setDirty();
        return true;
    }

    /**
     * Registers a descendant one layer beyond its parent; retiring owners are
     * excluded.
     */
    public boolean inheritFlow(ServerLevel level, BlockPos from, BlockPos to) {
        Map<UUID, Integer> inherited = activeDistances(to);
        mergeDistances(inherited, activeDistances(from), 1);
        return addOwners(to, inherited);
    }

    /**
     * Refreshes overlapping ownership and shortest known flow paths without
     * creating sources.
     */
    public boolean observeFlow(ServerLevel level, BlockPos pos, boolean source) {
        Map<UUID, Integer> inherited = activeDistances(pos);
        if (!source) {
            for (Direction direction : Direction.values()) {
                if (direction == Direction.DOWN) {
                    continue;
                }
                BlockPos neighbor = pos.relative(direction);
                if (isLoaded(level, neighbor)
                        && level.getFluidState(neighbor).getType() instanceof BossWaterFluid) {
                    mergeDistances(inherited, activeDistances(neighbor), 1);
                }
            }
        }
        return addOwners(pos, inherited);
    }

    private static void mergeDistances(Map<UUID, Integer> target, Map<UUID, Integer> incoming, int step) {
        incoming.forEach((owner, distance) -> target.merge(owner, distance + step, Math::min));
    }

    private Map<UUID, Integer> activeDistances(BlockPos pos) {
        Map<UUID, Integer> active = new HashMap<>();
        for (UUID owner : owners.getOrDefault(pos, Set.of())) {
            Arena arena = arenas.get(owner);
            if (arena != null && !arena.cleaning) {
                active.put(owner, arena.distances.getOrDefault(pos, 0));
            }
        }
        return active;
    }

    private boolean addOwners(BlockPos pos, Map<UUID, Integer> inherited) {
        if (inherited.isEmpty()) {
            return false;
        }
        BlockPos cell = pos.immutable();
        Set<UUID> cellOwners = owners.computeIfAbsent(cell, ignored -> new HashSet<>());
        inherited.forEach((owner, distance) -> {
            Arena arena = arenas.get(owner);
            if (arena.positions.add(cell)) {
                cellOwners.add(owner);
            }
            Integer previous = arena.distances.get(cell);
            if (previous == null || distance < previous) {
                arena.distances.put(cell, distance);
                setDirty();
            }
        });
        return true;
    }

    /** Drops derived ownership when native flow dries a cell naturally. */
    public void forget(BlockPos pos) {
        Set<UUID> previous = owners.remove(pos);
        if (previous != null) {
            for (UUID owner : previous) {
                arenas.get(owner).positions.remove(pos);
                arenas.get(owner).distances.remove(pos);
            }
            setDirty();
        }
    }

    /** A loaded boss does not place its initial sources again after generation. */
    public void finishGeneration(UUID boss) {
        Arena arena = arenas.computeIfAbsent(boss, ignored -> new Arena());
        if (!arena.generated) {
            arena.generated = true;
            setDirty();
        }
    }

    /** Schedules bounded cleanup, including cells in currently unloaded chunks. */
    public void release(UUID boss) {
        Arena arena = arenas.get(boss);
        if (arena == null || arena.cleaning) {
            return;
        }
        if (arena.positions.isEmpty()) {
            arenas.remove(boss);
        } else {
            arena.cleaning = true;
            cleanup.add(boss);
        }
        setDirty();
    }

    /**
     * Advances outer-to-inner layers, respecting both the interval and the work
     * budget.
     */
    public void advanceCleanup(ServerLevel level, int budget) {
        int interval = SheeperConfig.WATER_ARENA_DRAIN_WAVE_TICKS.get();
        long now = level.getGameTime();
        int examined = 0;
        int waiting = 0;
        while (examined < budget && !cleanup.isEmpty()) {
            UUID boss = cleanup.remove();
            Arena arena = arenas.get(boss);
            if (arena == null || arena.positions.isEmpty()) {
                arenas.remove(boss);
                continue;
            }
            if (interval > 0 && now < arena.nextDrainWaveTick) {
                cleanup.add(boss);
                if (++waiting >= cleanup.size()) {
                    break;
                }
                continue;
            }
            waiting = 0;
            if (arena.drainWaves.isEmpty()) {
                // Retained unloaded cells form another pass after loaded inner layers finish.
                for (BlockPos pos : arena.positions) {
                    arena.drainWaves.computeIfAbsent(arena.distances.getOrDefault(pos, 0),
                            ignored -> new LinkedHashSet<>()).add(pos);
                }
            }
            var wave = arena.drainWaves.firstEntry();
            BlockPos pos = wave.getValue().iterator().next();
            wave.getValue().remove(pos);
            examined++;
            if (arena.positions.contains(pos) && isLoaded(level, pos)) {
                removeOwnedCell(level, boss, arena, pos);
            }
            if (wave.getValue().isEmpty()) {
                arena.drainWaves.pollFirstEntry();
                arena.nextDrainWaveTick = now + interval;
                setDirty();
            }
            if (arena.positions.isEmpty()) {
                arenas.remove(boss);
            } else {
                cleanup.add(boss);
            }
        }
    }

    private void removeOwnedCell(ServerLevel level, UUID boss, Arena arena, BlockPos pos) {
        arena.positions.remove(pos);
        arena.distances.remove(pos);
        Set<UUID> cellOwners = owners.get(pos);
        cellOwners.remove(boss);
        if (cellOwners.isEmpty()) {
            owners.remove(pos);
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.BOSS_WATER.get()) || arena.legacyVanilla && state.is(Blocks.WATER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else if (BossWaterlogging.isWet(state)) {
                level.setBlock(pos, state.setValue(BossWaterlogging.LEVEL, 0), Block.UPDATE_ALL);
            }
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
            arena.legacyVanilla = !entry.getBoolean("BossWater");
            arena.generated = entry.getBoolean("Generated");
            arena.cleaning = entry.getBoolean("Cleaning");
            arena.nextDrainWaveTick = entry.getLong("NextDrainWaveTick");
            long[] positions = entry.getLongArray("Positions");
            int[] distances = entry.getIntArray("Distances");
            for (int cell = 0; cell < positions.length; cell++) {
                BlockPos pos = BlockPos.of(positions[cell]);
                arena.positions.add(pos);
                arena.distances.put(pos, cell < distances.length ? Math.max(0, distances[cell]) : 0);
                data.owners.computeIfAbsent(pos, ignored -> new HashSet<>()).add(boss);
            }
            if (arena.cleaning && arena.positions.isEmpty()) {
                continue;
            }
            data.arenas.put(boss, arena);
            if (arena.cleaning) {
                data.cleanup.add(boss);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag saved = new ListTag();
        arenas.forEach((boss, arena) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Boss", boss);
            entry.putBoolean("BossWater", !arena.legacyVanilla);
            entry.putBoolean("Generated", arena.generated);
            entry.putBoolean("Cleaning", arena.cleaning);
            entry.putLongArray("Positions", arena.positions.stream().mapToLong(BlockPos::asLong).toArray());
            entry.putIntArray("Distances",
                    arena.positions.stream().mapToInt(pos -> arena.distances.getOrDefault(pos, 0)).toArray());
            entry.putLong("NextDrainWaveTick", arena.nextDrainWaveTick);
            saved.add(entry);
        });
        tag.put("Arenas", saved);
        return tag;
    }
}
