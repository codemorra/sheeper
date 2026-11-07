package de.codemorra.sheeper.entity;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Material;

/**
 * Incrementally discovers connected surface cells without modifying terrain or
 * loading chunks. Positions denote candidate water cells above the supporting
 * floor, including cells that vanilla water can flow through.
 */
public final class WaterArenaSearch {
    private final Level level;
    private final BlockPos origin;
    private final double radiusSquared;
    private final int minimumY;
    private final int maximumY;
    private final int stepHeight;
    private final Queue<BlockPos> pending = new ArrayDeque<>();
    private final Map<BlockPos, Integer> visited = new HashMap<>();
    // Preserve breadth-first discovery order for an outward-moving source front.
    private final Set<BlockPos> surfaces = new LinkedHashSet<>();
    private final Set<BlockPos> surfaceView = Collections.unmodifiableSet(surfaces);
    private int seedY;
    private boolean seedFound;
    private boolean complete;

    /**
     * Captures the search bounds; later config changes do not alter this search.
     */
    public WaterArenaSearch(Level level, BlockPos origin, double radius, int heightRange, int stepHeight) {
        this.level = level;
        this.origin = origin.immutable();
        this.radiusSquared = radius * radius;
        minimumY = Math.max(level.getMinBuildHeight() + 1, origin.getY() - heightRange);
        maximumY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + heightRange);
        seedY = Math.min(origin.getY(), maximumY);
        this.stepHeight = stepHeight;
    }

    /**
     * Examines at most the given number of candidate cells. Unloaded candidates
     * are retained for a later tick, so chunk availability cannot cut off an arena.
     *
     * @param budget maximum candidate cells to examine in this call
     */
    public void advance(int budget) {
        if (complete) {
            return;
        }
        while (!seedFound && budget > 0) {
            if (seedY < minimumY) {
                complete = true;
                return;
            }
            BlockPos candidate = new BlockPos(origin.getX(), seedY, origin.getZ());
            if (!isChunkLoaded(candidate)) {
                return;
            }
            budget--;
            if (isSurface(level, candidate)) {
                seedFound = true;
                enqueue(candidate, 0);
            } else if (!isOpen(candidate)) {
                // Do not tunnel through a roof or other obstruction to find a floor.
                complete = true;
                return;
            } else {
                seedY--;
            }
        }

        // Retry unavailable chunks once per call rather than repeatedly polling them.
        Queue<BlockPos> deferred = new ArrayDeque<>();
        while (budget > 0 && !pending.isEmpty()) {
            BlockPos candidate = pending.remove();
            budget--;
            if (!isChunkLoaded(candidate)) {
                deferred.add(candidate);
                continue;
            }
            if (!isSurface(level, candidate)) {
                continue;
            }
            surfaces.add(candidate);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int offset = -stepHeight; offset <= stepHeight; offset++) {
                    enqueue(candidate.relative(direction).offset(0, offset, 0), visited.get(candidate) + 1);
                }
            }
        }
        pending.addAll(deferred);
        complete = seedFound && pending.isEmpty();
    }

    private boolean isChunkLoaded(BlockPos pos) {
        return level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private void enqueue(BlockPos pos, int distance) {
        double dx = (double) pos.getX() - origin.getX();
        double dz = (double) pos.getZ() - origin.getZ();
        if (pos.getY() >= minimumY && pos.getY() <= maximumY
                && dx * dx + dz * dz <= radiusSquared && visited.putIfAbsent(pos, distance) == null) {
            pending.add(pos);
        }
    }

    private boolean isOpen(BlockPos pos) {
        return isOpen(level, pos);
    }

    private static boolean isOpen(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(FluidTags.WATER)) {
            return true;
        }
        if (state.getBlock() instanceof LiquidBlockContainer container) {
            return container.canPlaceLiquid(level, pos, state, Fluids.WATER);
        }
        if (state.getBlock() instanceof DoorBlock || state.is(BlockTags.SIGNS)
                || state.is(Blocks.LADDER) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BUBBLE_COLUMN)) {
            return false;
        }
        Material material = state.getMaterial();
        return !material.blocksMotion() && material != Material.PORTAL && material != Material.STRUCTURAL_AIR
                && material != Material.WATER_PLANT && material != Material.REPLACEABLE_WATER_PLANT;
    }

    /**
     * Source placement may clear washable objects, but must retain fluid
     * containers.
     */
    public static boolean isSourceCandidate(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && !(state.getBlock() instanceof LiquidBlockContainer)
                && isOpen(level, pos);
    }

    /** Shared discovery/placement rule; callers must ensure the chunk is loaded. */
    public static boolean isSurface(Level level, BlockPos pos) {
        BlockPos floor = pos.below();
        BlockState support = level.getBlockState(floor);
        return isOpen(level, pos) && !support.is(BlockTags.LEAVES)
                && support.isFaceSturdy(level, floor, Direction.UP);
    }

    public boolean isComplete() {
        return complete;
    }

    /** Read-only live view in discovery order; final only after completion. */
    public Set<BlockPos> getSurfacePositions() {
        return surfaceView;
    }

    /** Returns the discovered path length from the initial ground cell. */
    public int getSurfaceDistance(BlockPos pos) {
        if (!surfaces.contains(pos)) {
            throw new IllegalArgumentException("Position is not a discovered arena surface: " + pos);
        }
        return visited.get(pos);
    }
}
