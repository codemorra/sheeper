package de.codemorra.sheeper.entity;

import java.util.TreeSet;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Searches nearby terrain before an elemental Sheeper opens a new arena. */
final class ArenaSpawnPlacement {
    private static final int SEARCH_DISTANCE = 2;
    private static final double EPSILON = 1.0E-7D;

    private ArenaSpawnPlacement() {
    }

    /**
     * Tries the exact request first, then nearby surfaces ordered by horizontal
     * distance and elevation. Existing arenas do not use this search on reload.
     *
     * @param fits checks the candidate against the boss's body and arena rules
     * @return a valid position, or null when none is nearby
     */
    @Nullable
    static Vec3 find(ServerLevel level, ElementalSheeperEntity boss, Predicate<Vec3> fits) {
        Vec3 origin = boss.position();
        if (level.isOutsideBuildHeight(new BlockPos(origin))) {
            return null;
        }
        if (fits.test(origin)) {
            return origin;
        }
        int maxDistanceSquared = SEARCH_DISTANCE * SEARCH_DISTANCE;
        for (int distanceSquared = 0; distanceSquared <= maxDistanceSquared; distanceSquared++) {
            for (int x = -SEARCH_DISTANCE; x <= SEARCH_DISTANCE; x++) {
                for (int z = -SEARCH_DISTANCE; z <= SEARCH_DISTANCE; z++) {
                    if (x * x + z * z != distanceSquared) {
                        continue;
                    }
                    Vec3 point = onSurface(level, boss, fits, origin.add(x, 0, z));
                    if (point != null) {
                        return point;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static Vec3 onSurface(ServerLevel level, ElementalSheeperEntity boss, Predicate<Vec3> fits,
            Vec3 column) {
        AABB footprint = boss.getBoundingBox().move(column.subtract(boss.position())).deflate(EPSILON);
        double top = Math.min(column.y + SEARCH_DISTANCE, level.getMaxBuildHeight());
        var search = new AABB(footprint.minX, column.y - EPSILON, footprint.minZ,
                footprint.maxX, top + EPSILON, footprint.maxZ);
        if (!areChunksLoaded(level, search)) {
            return null;
        }
        // Read the actual shape tops so paths, slabs and nearby steps supply their own height.
        var surfaces = new TreeSet<Double>();
        for (var shape : level.getBlockCollisions(boss, search)) {
            for (AABB part : shape.toAabbs()) {
                if (part.maxY >= column.y && part.maxY <= top && part.intersects(search)) {
                    surfaces.add(part.maxY);
                }
            }
        }
        for (double surface : surfaces) {
            Vec3 candidate = new Vec3(column.x, surface, column.z);
            if (fits.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Checks the scaled body without loading missing terrain. */
    static boolean fitsTerrain(ServerLevel level, ElementalSheeperEntity boss, AABB body) {
        return areChunksLoaded(level, body) && level.getWorldBorder().isWithinBounds(body)
                && !level.getBlockCollisions(boss, body.deflate(EPSILON)).iterator().hasNext();
    }

    /** Rejects candidates whose scaled body or supporting terrain would need unloaded chunks. */
    private static boolean areChunksLoaded(ServerLevel level, AABB bounds) {
        for (int x = Mth.floor(bounds.minX) >> 4; x <= Mth.floor(bounds.maxX) >> 4; x++) {
            for (int z = Mth.floor(bounds.minZ) >> 4; z <= Mth.floor(bounds.maxZ) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }
}
