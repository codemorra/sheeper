package de.codemorra.sheeper.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Horizontal geometry shared by the water and storm arenas. */
public final class ArenaGeometry {
    private ArenaGeometry() {
    }

    /** Measures from the middle of the arena's saved spawn block. */
    public static double horizontalDistanceSquared(BlockPos center, double x, double z) {
        double dx = x - (center.getX() + 0.5);
        double dz = z - (center.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    /** Uses the farthest hitbox corner when the whole footprint must be inside the circle. */
    public static double farthestCornerDistanceSquared(BlockPos center, AABB bounds) {
        double x = center.getX() + 0.5;
        double z = center.getZ() + 0.5;
        double dx = Math.max(Math.abs(bounds.minX - x), Math.abs(bounds.maxX - x));
        double dz = Math.max(Math.abs(bounds.minZ - z), Math.abs(bounds.maxZ - z));
        return dx * dx + dz * dz;
    }

    /** Leaves room for the diagonal of the boss's footprint and the configured edge clearance. */
    public static double movementClearance(double bodyWidth, double edgeDistance) {
        return bodyWidth * Math.sqrt(0.5) + 0.25 + edgeDistance;
    }
}
