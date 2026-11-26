package de.codemorra.sheeper.world;

/** Bounds shared by arena configuration, persistence and network validation. */
public final class ArenaLimits {
    public static final double MIN_RADIUS = 1.0;
    public static final double MAX_RADIUS = 128.0;
    public static final double MAX_BOSS_HEALTH = 1024.0;
    public static final int MAX_DRAIN_SECONDS = 30;
    public static final int MAX_DRAIN_TICKS = MAX_DRAIN_SECONDS * 20;

    private ArenaLimits() {
    }

    /** Rejects missing or unbounded radii before an arena is used. */
    public static boolean isValidRadius(double radius) {
        return radius >= MIN_RADIUS && radius <= MAX_RADIUS;
    }
}
