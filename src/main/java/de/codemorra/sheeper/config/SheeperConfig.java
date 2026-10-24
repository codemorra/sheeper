package de.codemorra.sheeper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Installation-wide configuration for Sheeper behavior.
 * Distances are measured in blocks and angles in degrees.
 */
public class SheeperConfig {
    /**
     * Builder for the Sheeper configuration specification.
     */
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /**
     * Detection radius for the Sheeper entity.
     */
    public static final ForgeConfigSpec.DoubleValue DETECTION_RADIUS = BUILDER
            .comment("Maximum distance in blocks for detecting and pursuing players.")
            .defineInRange("detectionRadius", 16.0, 1.0, 128.0);

    /**
     * Observation angle for the Sheeper entity.
     */
    public static final ForgeConfigSpec.DoubleValue OBSERVATION_ANGLE = BUILDER
            .comment("Total observation angle in degrees, centered on the player's look direction.")
            .defineInRange("observationAngle", 120.0, 1.0, 360.0);

    /**
     * Maximum distance in blocks at which the fuse can remain active.
     */
    public static final ForgeConfigSpec.DoubleValue FUSE_DISTANCE = BUILDER
            .comment("Maximum distance in blocks for starting and maintaining the fuse.")
            .defineInRange("fuseDistance", 3.0, 0.0, 128.0);

    /**
     * Fuse duration in seconds.
     */
    public static final ForgeConfigSpec.DoubleValue FUSE_DURATION = BUILDER
            .comment("Duration in seconds for which the fuse remains active.")
            .defineInRange("fuseDuration", 1.5, 0.05, 60.0);

    /**
     * Natural spawn chance for Sheeper.
     */
    public static final ForgeConfigSpec.DoubleValue NATURAL_SPAWN_CHANCE = BUILDER
            .comment("Percentage chance to replace each naturally spawning sheep with a Sheeper.",
                    "Applies during world generation and later natural spawning.",
                    "0 disables replacement; 100 replaces every naturally spawning sheep.")
            .defineInRange("naturalSpawnChance", 5.0, 0.0, 100.0);

    /**
     * Additional movement speed for baby Sheeper, in percent.
     */
    public static final ForgeConfigSpec.DoubleValue BABY_SPEED_BOOST_PERCENT = BUILDER
            .comment("Additional movement speed for baby Sheeper, in percent.",
                    "0 gives babies the same movement speed as adults.")
            .defineInRange("babySpeedBoostPercent", 25.0, 0.0, 100.0);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /**
     * Returns the fuse distance capped at the detection radius, in blocks.
     */
    public static double getFuseDistance() {
        return Math.min(FUSE_DISTANCE.get(), DETECTION_RADIUS.get());
    }

    public static double getFuseDuration() {
        return FUSE_DURATION.get();
    }

    private SheeperConfig() {
    }
}
