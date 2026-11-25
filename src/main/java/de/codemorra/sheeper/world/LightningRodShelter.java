package de.codemorra.sheeper.world;

import net.minecraft.world.phys.Vec3;

/** World-space sphere used for both the visible shell and its shelter boundary. */
public record LightningRodShelter(Vec3 center, double radius) {
    public static final int MAX_SHELTERS = 64;

    /**
     * @param point center of the player's hitbox
     * @return whether the player is inside the shelter
     */
    public boolean contains(Vec3 point) {
        return point.distanceToSqr(center) <= radius * radius;
    }
}
