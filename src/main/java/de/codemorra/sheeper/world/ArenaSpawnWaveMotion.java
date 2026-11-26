package de.codemorra.sheeper.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Horizontal geometry shared by the water and storm startup waves. */
record ArenaSpawnWaveMotion(Vec3 radial, double distance, double transportFront, double targetRadius) {
    /**
     * Keeps the whole player behind the transport front and inside the stopping radius.
     *
     * @param front the wave front after the arena-specific setback
     * @param boundaryMargin the clearance between the player's body and the arena edge
     */
    static ArenaSpawnWaveMotion at(BlockPos center, Vec3 position, double front, double radius, double playerWidth,
            double edgeDistance, double boundaryMargin) {
        Vec3 radial = new Vec3(position.x - (center.getX() + 0.5), 0, position.z - (center.getZ() + 0.5));
        double halfWidth = playerWidth * 0.5;
        double targetRadius = Math.max(0, radius - Math.max(edgeDistance, halfWidth + boundaryMargin));
        return new ArenaSpawnWaveMotion(radial, radial.length(), front - halfWidth, targetRadius);
    }

    /** Caps the outward step at both the moving front and the final stopping radius. */
    Vec3 velocity(double speed, double upward) {
        double step = Math.min(speed, Math.max(0, Math.min(transportFront, targetRadius) - distance));
        Vec3 direction = distance > 1.0E-6 ? radial.scale(1 / distance) : new Vec3(1, 0, 0);
        return new Vec3(direction.x * step, upward, direction.z * step);
    }
}
