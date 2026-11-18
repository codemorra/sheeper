package de.codemorra.sheeper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Shared health and size definitions; each caller supplies the owning
 * configuration builder.
 */
final class ElementalConfigValues {
    /** Shared definitions for elemental health and size options. */
    static ForgeConfigSpec.DoubleValue elementalHealth(ForgeConfigSpec.Builder builder, String element,
            double defaultHealth) {
        return builder.comment("Maximum health of the " + element + " Sheeper in health points (2 = one heart).",
                "Changes apply to existing bosses while preserving their percentage of remaining health.")
                .defineInRange("elemental." + element + ".maxHealth", defaultHealth, 1.0, 1024.0);
    }

    static ForgeConfigSpec.DoubleValue elementalSize(ForgeConfigSpec.Builder builder, String element) {
        return builder.comment("Size multiplier for the " + element + " Sheeper, including its hitbox.",
                "1.0 = normal sheep/Sheeper size; 2.0 = twice that size.",
                "The server synchronizes this value to clients; changes apply to existing bosses.")
                .defineInRange("elemental." + element + ".size", 2.0, 0.5, 4.0);
    }

    private ElementalConfigValues() {
    }
}
