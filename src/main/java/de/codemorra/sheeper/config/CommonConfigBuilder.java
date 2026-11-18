package de.codemorra.sheeper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Keeps the existing common TOML schema while its settings live in separate
 * classes.
 * Independent builder ownership avoids circular initialization between those
 * classes.
 */
final class CommonConfigBuilder {
    static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /**
     * @return the complete common specification, including every elemental
     *         definition
     */
    static ForgeConfigSpec build() {
        LavaSheeperConfig.initialize();
        LightningSheeperConfig.initialize();
        WaterSheeperConfig.initialize();
        return BUILDER.build();
    }

    private CommonConfigBuilder() {
    }
}
