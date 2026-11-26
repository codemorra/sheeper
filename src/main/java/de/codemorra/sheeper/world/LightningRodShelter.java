package de.codemorra.sheeper.world;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** World-space sphere used for both the visible shell and its shelter boundary. */
public record LightningRodShelter(Vec3 center, double radius) {
    public static final int MAX_SHELTERS = 64;
    public static final int BOLT_TRAVEL_TICKS = 8;
    public static final int FORMATION_TICKS = 10;

    /**
     * @param point center of the player's hitbox
     * @return whether the player is inside the shelter
     */
    public boolean contains(Vec3 point) {
        return point.distanceToSqr(center) <= radius * radius;
    }

    /** Keeps the radius synchronized between rendering and lightning interception as the shell forms. */
    public static double formationRadiusScale(double age) {
        if (age < BOLT_TRAVEL_TICKS) {
            return 0;
        }
        double progress = Mth.clamp((age - BOLT_TRAVEL_TICKS) / FORMATION_TICKS, 0, 1);
        double size = 1 - (1 - progress) * (1 - progress);
        return 0.12 + 0.88 * size;
    }

    /** Returns the first point where the segment enters this sphere, or {@code end} on a miss. */
    public Vec3 intercept(Vec3 start, Vec3 end) {
        Vec3 ray = end.subtract(start);
        Vec3 offset = start.subtract(center);
        double a = ray.lengthSqr();
        double c = offset.lengthSqr() - radius * radius;
        if (c <= 0) {
            return start;
        }
        if (a <= 1.0E-10 || radius <= 0) {
            return end;
        }
        double b = offset.dot(ray);
        double discriminant = b * b - a * c;
        if (discriminant < 0) {
            return end;
        }
        double fraction = (-b - Math.sqrt(discriminant)) / a;
        return fraction >= 0 && fraction < 1 ? start.add(ray.scale(fraction)) : end;
    }
}
