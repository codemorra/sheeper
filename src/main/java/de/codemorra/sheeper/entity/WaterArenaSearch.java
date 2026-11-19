package de.codemorra.sheeper.entity;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

import de.codemorra.sheeper.world.ArenaWater;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Incrementally discovers connected surface cells without modifying terrain or
 * loading chunks. Positions denote candidate water cells above the supporting
 * floor. Existing fluids and terrain remain unchanged.
 */
public final class WaterArenaSearch {
    private final Level level;
    private final BlockPos origin;
    private final double radiusSquared;
    private final int minimumY;
    private final int maximumY;
    private final int stepHeight;
    private final Iterator<BlockPos> floorCandidates;
    private final Queue<BlockPos> deferredFloor = new ArrayDeque<>();
    private final Queue<BlockPos> pending = new ArrayDeque<>();
    private final Set<BlockPos> visited = new HashSet<>();
    // Stable discovery order makes incremental fountain candidates predictable.
    private final Set<BlockPos> surfaces = new LinkedHashSet<>();
    private final Set<BlockPos> surfaceView = Collections.unmodifiableSet(surfaces);
    private int seedY;
    private boolean seedFound;
    private boolean complete;

    /**
     * Captures terrain discovery bounds and the virtual lower boundary, including
     * floor sections disconnected from real ground by cliffs.
     */
    public WaterArenaSearch(Level level, BlockPos origin, double radius, int heightRange, int stepHeight,
            int lowerBoundaryY) {
        this.level = level;
        this.origin = origin.immutable();
        this.radiusSquared = radius * radius;
        minimumY = lowerBoundaryY;
        maximumY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + heightRange);
        seedY = Math.min(origin.getY(), maximumY);
        this.stepHeight = stepHeight;
        int extent = (int) Math.ceil(radius);
        floorCandidates = BlockPos.betweenClosed(
                new BlockPos(origin.getX() - extent, minimumY, origin.getZ() - extent),
                new BlockPos(origin.getX() + extent, minimumY, origin.getZ() + extent)).iterator();
    }

    /**
     * Examines at most the given number of candidate cells. Unloaded candidates
     * are retained for a later tick, so chunk availability cannot cut off an arena.
     *
     * @param budget maximum candidate cells to examine in this call
     */
    public void advance(int budget) {
        if (budget > 0 && (floorCandidates.hasNext() || !deferredFloor.isEmpty())) {
            int floorBudget = Math.max(1, budget / 2);
            advanceFloor(floorBudget);
            budget -= floorBudget;
        }
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
            if (isSurfaceCandidate(candidate)) {
                seedFound = true;
                enqueue(candidate);
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
            if (!isSurfaceCandidate(candidate)) {
                continue;
            }
            surfaces.add(candidate);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int offset = -stepHeight; offset <= stepHeight; offset++) {
                    enqueue(candidate.relative(direction).offset(0, offset, 0));
                }
            }
        }
        pending.addAll(deferred);
        complete = seedFound && pending.isEmpty();
    }

    /** Scans the bounded plane incrementally and retries unloaded cells without loading chunks. */
    private void advanceFloor(int budget) {
        Queue<BlockPos> retry = new ArrayDeque<>();
        while (budget-- > 0 && (floorCandidates.hasNext() || !deferredFloor.isEmpty())) {
            BlockPos pos = floorCandidates.hasNext() ? floorCandidates.next().immutable() : deferredFloor.remove();
            double dx = (double) pos.getX() - origin.getX();
            double dz = (double) pos.getZ() - origin.getZ();
            if (dx * dx + dz * dz > radiusSquared) {
                continue;
            }
            if (!isChunkLoaded(pos)) {
                retry.add(pos);
            } else if (isOpen(pos)) {
                surfaces.add(pos);
            }
        }
        deferredFloor.addAll(retry);
    }

    private boolean isSurfaceCandidate(BlockPos pos) {
        return isSurface(level, pos, minimumY);
    }

    private boolean isChunkLoaded(BlockPos pos) {
        return level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private void enqueue(BlockPos pos) {
        double dx = (double) pos.getX() - origin.getX();
        double dz = (double) pos.getZ() - origin.getZ();
        if (pos.getY() >= minimumY && pos.getY() <= maximumY
                && dx * dx + dz * dz <= radiusSquared && visited.add(pos)) {
            pending.add(pos);
        }
    }

    private boolean isOpen(BlockPos pos) {
        return ArenaWater.isOpenCell(level, pos);
    }

    /** Uses virtual water clearance and accepts the captured lower plane as supporting ground. */
    public static boolean isSurface(Level level, BlockPos pos, int lowerBoundaryY) {
        return ArenaWater.isOpenCell(level, pos)
                && (pos.getY() == lowerBoundaryY || hasSupportingGround(level, pos));
    }

    private static boolean hasSupportingGround(Level level, BlockPos pos) {
        BlockPos floor = pos.below();
        BlockState support = level.getBlockState(floor);
        return !support.is(BlockTags.LEAVES) && support.isFaceSturdy(level, floor, Direction.UP);
    }

    public boolean isComplete() {
        return complete && !floorCandidates.hasNext() && deferredFloor.isEmpty();
    }

    /** Read-only live view in discovery order; final only after completion. */
    public Set<BlockPos> getSurfacePositions() {
        return surfaceView;
    }
}
