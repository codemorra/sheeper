package de.codemorra.sheeper.entity;

import java.util.TreeSet;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.world.ArenaWater;
import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Finds a nearby terrain-safe position before a new Water Sheeper opens its arena. */
final class WaterSpawnPlacement {
    private static final int SEARCH_DISTANCE = 2;
    private static final double EPSILON = 1.0E-7D;

    private WaterSpawnPlacement() {
    }

    /**
     * Tries the exact request first, then nearby surfaces ordered by horizontal
     * distance and elevation. Existing arenas do not use this search on reload.
     *
     * @return a valid position for the scaled body, or null when none is nearby
     */
    @Nullable
    static Vec3 find(ServerLevel level, WaterSheeperEntity boss, WaterCombatSettings settings) {
        Vec3 origin = boss.position();
        if (level.isOutsideBuildHeight(new BlockPos(origin))) {
            return null;
        }
        if (fits(level, boss, settings, origin)) {
            return origin;
        }
        int maxDistanceSquared = SEARCH_DISTANCE * SEARCH_DISTANCE;
        for (int distanceSquared = 0; distanceSquared <= maxDistanceSquared; distanceSquared++) {
            for (int x = -SEARCH_DISTANCE; x <= SEARCH_DISTANCE; x++) {
                for (int z = -SEARCH_DISTANCE; z <= SEARCH_DISTANCE; z++) {
                    if (x * x + z * z != distanceSquared) {
                        continue;
                    }
                    Vec3 point = onSurface(level, boss, settings, origin.add(x, 0, z));
                    if (point != null) {
                        return point;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static Vec3 onSurface(ServerLevel level, WaterSheeperEntity boss, WaterCombatSettings settings,
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
            if (fits(level, boss, settings, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean fits(ServerLevel level, WaterSheeperEntity boss, WaterCombatSettings settings,
            Vec3 position) {
        var bounds = VolumeBounds.capture(level, new BlockPos(position), boss.getCombatRadius(),
                WaterSheeperConfig.WATER_ARENA_DEPTH.get(), WaterSheeperConfig.WATER_ARENA_HEIGHT.get());
        AABB body = boss.getBoundingBox().move(position.subtract(boss.position()));
        return !level.isOutsideBuildHeight(new BlockPos(position))
                && bounds.isValidFor(level) && body.minY >= bounds.minimumY()
                && body.maxY + ArenaWater.BOUNDARY_MARGIN <= bounds.maximumY() + 1
                && areChunksLoaded(level, body) && level.getWorldBorder().isWithinBounds(body)
                && !level.getBlockCollisions(boss, body.deflate(EPSILON)).iterator().hasNext()
                && settings.hasArenaRoom(bounds);
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
