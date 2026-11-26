package de.codemorra.sheeper.config;

import de.codemorra.sheeper.world.ArenaLimits;
import net.minecraftforge.common.ForgeConfigSpec;

/** Shared elemental config definitions. Each boss supplies its own default values. */
final class ElementalConfigValues {
    /** Shared definitions for elemental health and size options. */
    static ForgeConfigSpec.DoubleValue elementalHealth(ForgeConfigSpec.Builder builder, String element,
            double defaultHealth) {
        return builder.comment("Maximum health of the " + element + " Sheeper in health points (2 = one heart).",
                "Changes apply to existing bosses while preserving their percentage of remaining health.")
                .defineInRange("boss.maxHealth", defaultHealth, 1.0, ArenaLimits.MAX_BOSS_HEALTH);
    }

    static ForgeConfigSpec.DoubleValue elementalSize(ForgeConfigSpec.Builder builder, String element, double defaultValue) {
        return builder.comment("Size multiplier for the " + element + " Sheeper, including its hitbox.",
                "1.0 = normal sheep/Sheeper size; 2.0 = twice that size.",
                "The server synchronizes this value to clients; changes apply to existing bosses.")
                .defineInRange("boss.size", defaultValue, 0.5, 4.0);
    }

    static ForgeConfigSpec.DoubleValue arenaBossHealth(ForgeConfigSpec.Builder builder, double defaultValue) {
        return builder.comment("Solo maximum health in health points (2 = one heart).",
                "Multiplayer scaling adds health while preserving the remaining health percentage.")
                .defineInRange("boss.maxHealth", defaultValue, 1.0, ArenaLimits.MAX_BOSS_HEALTH);
    }

    static ForgeConfigSpec.DoubleValue additionalPlayerHealth(ForgeConfigSpec.Builder builder, double defaultValue) {
        return builder.comment("Extra percentage of solo health per additional simultaneous arena player. 0 disables scaling.",
                "Players qualify after 3 continuous seconds. The highest count lasts until full recovery.")
                .defineInRange("healthScaling.additionalPlayerPercent", defaultValue, 0.0, 100.0);
    }

    static ForgeConfigSpec.DoubleValue arenaRadius(ForgeConfigSpec.Builder builder, double defaultValue) {
        return builder.comment("Horizontal combat radius around the boss's saved spawn position, in blocks.",
                "New arenas capture this radius when they are created.")
                .defineInRange("arena.combatRadius", defaultValue, ArenaLimits.MIN_RADIUS, ArenaLimits.MAX_RADIUS);
    }

    static ForgeConfigSpec.DoubleValue bossBarDistance(ForgeConfigSpec.Builder builder, double defaultValue) {
        return builder.comment("Maximum distance in blocks beyond the arena where the boss bar is visible.",
                "Set to 0 to show it only inside the arena.")
                .defineInRange("boss.bossBarDistance", defaultValue, 0.0, 64.0);
    }

    static ForgeConfigSpec.DoubleValue arenaDrainDuration(ForgeConfigSpec.Builder builder, double defaultValue,
            String... description) {
        return builder.comment(description).defineInRange("arena.drainDurationSeconds", defaultValue, 1.0,
                ArenaLimits.MAX_DRAIN_SECONDS);
    }

    static ForgeConfigSpec.DoubleValue recoveryGrace(ForgeConfigSpec.Builder builder, double defaultValue) {
        return builder.comment("Seconds the arena must stay empty before the boss starts healing.",
                "Returning players stop recovery immediately.")
                .defineInRange("arenaRecovery.graceSeconds", defaultValue, 0.0, 30.0);
    }

    static ForgeConfigSpec.DoubleValue recoveryRate(ForgeConfigSpec.Builder builder, double defaultValue) {
        return builder.comment("Percentage of maximum health restored per second after the empty-arena grace period.",
                "Full recovery resets the encounter.")
                .defineInRange("arenaRecovery.healthPercentPerSecond", defaultValue, 0.1, 25.0);
    }

    static ForgeConfigSpec.DoubleValue movementEdgeDistance(ForgeConfigSpec.Builder builder, double defaultValue) {
        return builder.comment("Minimum horizontal clearance in blocks between the boss body and the arena boundary.",
                "The movement radius adapts to the arena radius and boss size. 0 keeps only body clearance.")
                .defineInRange("movement.edgeDistance", defaultValue, 0.0, 16.0);
    }

    static ForgeConfigSpec.DoubleValue boundaryWidth(ForgeConfigSpec.Builder builder, double defaultValue) {
        return builder.comment("Width in blocks on each side of the arena boundary where crossing is slowed.",
                "Combat entry uses the inner edge. Width is capped at half the arena radius. 0 disables it.")
                .defineInRange("boundary.slowdownWidth", defaultValue, 0.0, 4.0);
    }

    static ForgeConfigSpec.DoubleValue boundaryStrength(ForgeConfigSpec.Builder builder, double defaultStrength) {
        return builder.comment("Maximum fraction of crossing speed removed at the side boundary. 0 disables it.",
                "The effect fades with distance. Movement along the boundary stays free.")
                .defineInRange("boundary.slowdownStrength", defaultStrength, 0.0, 0.75);
    }

    static ForgeConfigSpec.DoubleValue spawnPushSpeed(ForgeConfigSpec.Builder builder, double defaultValue) {
        return builder.comment("Maximum outward transport speed during arena startup in blocks per second. 0 disables it.")
                .defineInRange("spawnPush.speedBlocksPerSecond", defaultValue, 0.0, 40.0);
    }

    static ForgeConfigSpec.DoubleValue spawnPushEdgeDistance(ForgeConfigSpec.Builder builder, double defaultValue) {
        return builder.comment("Distance from the arena edge where spawn transport stops, in blocks.",
                "Players already in this outer ring are spared.")
                .defineInRange("spawnPush.edgeDistance", defaultValue, 0.0, 32.0);
    }

    static ForgeConfigSpec.DoubleValue spawnPushLiftHeight(ForgeConfigSpec.Builder builder, double defaultValue) {
        return builder.comment("Rise above a player's height at first wave contact, in blocks. Ceilings limit the lift.")
                .defineInRange("spawnPush.liftHeight", defaultValue, 0.0, 6.0);
    }

    private ElementalConfigValues() {
    }
}
