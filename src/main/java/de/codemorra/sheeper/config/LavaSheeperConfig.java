package de.codemorra.sheeper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Installation-wide lava Sheeper settings in their own common configuration
 * file.
 */
public final class LavaSheeperConfig {
    public static final String FILE_NAME = "sheeper/lava.toml";
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** Lava boss settings (boss). */
    public static final ForgeConfigSpec.DoubleValue LAVA_MAX_HEALTH = ElementalConfigValues
            .elementalHealth(BUILDER, "lava", 200.0);

    public static final ForgeConfigSpec.DoubleValue LAVA_SIZE = ElementalConfigValues.elementalSize(BUILDER, "lava");

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private LavaSheeperConfig() {
    }
}
