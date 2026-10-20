package de.codemorra.sheeper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server-side configuration for the Sheeper mod.
 * Contains configurable parameters for the Sheeper entity's behavior.
 * Distance are measured in blocks.
 * Angles are measured in degrees.
 */
public class SheeperConfig {
    public static final ForgeConfigSpec.Builder BUILDER =
        new ForgeConfigSpec.Builder();

    // Detection radius for the Sheeper entity.
    public static final ForgeConfigSpec.DoubleValue DETECTION_RADIUS = BUILDER
        .comment("Maximum distance in blocks for detecting and pursuing players.")
        .defineInRange("detectionRadius", 16.0, 1.0, 128.0);

    // Observation angle for the Sheeper entity.
    public static final ForgeConfigSpec.DoubleValue OBSERVATION_ANGLE = BUILDER
        .comment("Total observation angle in degrees, centered on the player's look direction.")
        .defineInRange("observationAngle", 120.0, 1.0, 360.0);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private SheeperConfig() {
    }
}
