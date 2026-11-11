package de.codemorra.sheeper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Local presentation settings. These never change server gameplay or effect
 * times.
 * Defaults preserve the appearance and sound of the 0.8.0 feature milestone.
 */
public final class SheeperClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // Tint settings for the Sheeper visual effects.
    public static final ForgeConfigSpec.BooleanValue SKIN_TINT_ENABLED = BUILDER
            .comment("Show the red player-skin effect, including on other players.")
            .define("tint.skinEnabled", true);

    // Screen tint settings for the Sheeper visual effects.
    public static final ForgeConfigSpec.BooleanValue SCREEN_TINT_ENABLED = BUILDER
            .comment("Show the red viewport effect for your own player.")
            .define("tint.screenEnabled", true);

    // Strength settings for the Sheeper visual effects.
    public static final ForgeConfigSpec.DoubleValue RAW_TINT_STRENGTH = BUILDER
            .comment("Raw internal-explosion tint multiplier, shared by skin and viewport. 0 hides it.")
            .defineInRange("tint.rawStrength", 1.0, 0.0, 2.0);

    // Cooked tint strength for the Sheeper visual effects.
    public static final ForgeConfigSpec.DoubleValue COOKED_TINT_STRENGTH = BUILDER
            .comment("Cooked tint multiplier, shared by skin and viewport. 0.25 is one quarter of normal strength.")
            .defineInRange("tint.cookedStrength", 0.25, 0.0, 2.0);

    /** Pulse timing in seconds, measured at 90% response or amplitude decay. */
    public static final ForgeConfigSpec.DoubleValue PULSE_ATTACK_SECONDS = BUILDER
            .comment("Seconds to close 90% of the gap to a rising tint target. 0 responds immediately.")
            .defineInRange("tint.pulseAttackSeconds", pulseSeconds(1.0D - 0.85D), 0.0, 120.0);

    public static final ForgeConfigSpec.DoubleValue PULSE_RELEASE_SECONDS = BUILDER
            .comment("Seconds to close 90% of the gap to a falling tint target. 0 responds immediately.")
            .defineInRange("tint.pulseReleaseSeconds", pulseSeconds(1.0D - 0.12D), 0.0, 120.0);

    public static final ForgeConfigSpec.DoubleValue PULSE_DECAY_SECONDS = BUILDER
            .comment("Seconds for a pulse to lose 90% of its amplitude. 0 removes the pulse immediately.")
            .defineInRange("tint.pulseDecaySeconds", pulseSeconds(0.94D), 0.0, 120.0);

    // Final fade settings for the Sheeper visual effects.
    public static final ForgeConfigSpec.DoubleValue FADE_SECONDS = BUILDER
            .comment(
                    "Final cosmetic fade duration in seconds. 0 ends the tint immediately; gameplay ends independently.")
            .defineInRange("tint.finalFadeSeconds", 2.0, 0.0, 10.0);

    // Particle settings for the Sheeper visual effects.
    public static final ForgeConfigSpec.BooleanValue PARTICLES_ENABLED = BUILDER
            .comment("Show vapor from internal explosions. Minecraft's Minimal particle setting also hides it.")
            .define("particles.enabled", true);

    // Raw particle settings for the Sheeper visual effects.
    public static final ForgeConfigSpec.DoubleValue RAW_PARTICLE_SIZE = BUILDER
            .comment("Raw vapor size multiplier. 0 hides raw vapor.")
            .defineInRange("particles.rawSize", 1.0, 0.0, 5.0);

    // Cooked particle settings for the Sheeper visual effects.
    public static final ForgeConfigSpec.DoubleValue COOKED_PARTICLE_SIZE = BUILDER
            .comment("Cooked vapor size multiplier, for both the green stream and orange-red sequences.")
            .defineInRange("particles.cookedSize", 1.3, 0.0, 5.0);

    // Puff settings for the Sheeper visual effects.
    public static final ForgeConfigSpec.IntValue RAW_PUFF_COUNT = BUILDER
            .comment("Puffs per outlet for each raw internal explosion. 0 hides these sequences.")
            .defineInRange("particles.rawPuffsPerOutlet", 3, 0, 20);

    // Cooked puff settings for the Sheeper visual effects.
    public static final ForgeConfigSpec.IntValue COOKED_PUFF_COUNT = BUILDER
            .comment("Orange-red rear puffs per cooked internal explosion. Does not control the green stream.")
            .defineInRange("particles.cookedPuffsPerExplosion", 3, 0, 20);

    // Puff interval settings for the Sheeper visual effects.
    public static final ForgeConfigSpec.DoubleValue PUFF_INTERVAL_SECONDS = BUILDER
            .comment("Seconds between individual puffs in a sequence.")
            .defineInRange("particles.sequenceIntervalSeconds", 0.1, 0.05, 1.0);

    // Cooked stream interval settings for the Sheeper visual effects.
    public static final ForgeConfigSpec.DoubleValue COOKED_STREAM_INTERVAL_SECONDS = BUILDER
            .comment("Seconds between green rear puffs during the cooked effect.")
            .defineInRange("particles.cookedStreamIntervalSeconds", 0.2, 0.05, 5.0);

    // Warning sound settings for the Sheeper audio effects.
    public static final ForgeConfigSpec.DoubleValue WARNING_VOLUME = BUILDER
            .comment("Local volume multiplier for Sheeper fuse bleats, cooking warnings, and wool priming.",
                    "1 preserves normal volume; 0 mutes only these warnings.")
            .defineInRange("sound.warningVolume", 1.0, 0.0, 4.0);

    // Raw internal explosion sound settings for the Sheeper audio effects.
    public static final ForgeConfigSpec.DoubleValue RAW_INTERNAL_EXPLOSION_VOLUME = BUILDER
            .comment("Local volume multiplier for raw internal explosions (normally quiet). 0 mutes them.")
            .defineInRange("sound.rawInternalExplosionVolume", 1.0, 0.0, 4.0);

    // Cooked internal explosion sound settings for the Sheeper audio effects.
    public static final ForgeConfigSpec.DoubleValue COOKED_INTERNAL_EXPLOSION_VOLUME = BUILDER
            .comment("Local volume multiplier for cooked internal explosions (normally quieter). 0 mutes them.")
            .defineInRange("sound.cookedInternalExplosionVolume", 1.0, 0.0, 4.0);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /** Converts an exponential per-update retention factor to its 90% duration. */
    public static double pulseSeconds(double retainedFraction) {
        return retainedFraction <= 0.0D ? 0.0D : Math.log(0.1D) / (20.0D * Math.log(retainedFraction));
    }

    /** Per-update retention for a pulse configured by its 90% decay duration. */
    public static double pulseRetention(double seconds) {
        return seconds <= 0.0D ? 0.0D : Math.exp(Math.log(0.1D) / (20.0D * seconds));
    }

    private SheeperClientConfig() {
    }
}
