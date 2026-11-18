package de.codemorra.sheeper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Installation-wide lava Sheeper settings, currently part of the shared common
 * file.
 */
public final class LavaSheeperConfig {
    private static final ForgeConfigSpec.Builder BUILDER = CommonConfigBuilder.BUILDER;

    /** Lava elemental boss (elemental.lava). */
    public static final ForgeConfigSpec.DoubleValue LAVA_MAX_HEALTH = ElementalConfigValues
            .elementalHealth(BUILDER, "lava", 200.0);

    public static final ForgeConfigSpec.DoubleValue LAVA_SIZE = ElementalConfigValues.elementalSize(BUILDER, "lava");

    /**
     * Ensures these definitions exist before the shared common specification is
     * built.
     */
    static void initialize() {
    }

    private LavaSheeperConfig() {
    }
}
