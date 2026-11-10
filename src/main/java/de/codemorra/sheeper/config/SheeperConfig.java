package de.codemorra.sheeper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Installation-wide configuration for Sheeper behavior.
 * Distances are measured in blocks and angles in degrees.
 */
public class SheeperConfig {
    // Builder for the Sheeper configuration specification.
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** Independent health settings for the three elemental bosses. */
    public static final ForgeConfigSpec.DoubleValue LAVA_MAX_HEALTH = elementalHealth("lava");
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_MAX_HEALTH = elementalHealth("lightning");
    public static final ForgeConfigSpec.DoubleValue WATER_MAX_HEALTH = elementalHealth("water");

    /** Server-controlled sizes used for both model rendering and collision. */
    public static final ForgeConfigSpec.DoubleValue LAVA_SIZE = elementalSize("lava");
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SIZE = elementalSize("lightning");
    public static final ForgeConfigSpec.DoubleValue WATER_SIZE = elementalSize("water");

    /** Provisional horizontal radii for the water encounter. */
    public static final ForgeConfigSpec.DoubleValue WATER_COMBAT_RADIUS = BUILDER
            .comment("Horizontal water Sheeper arena and combat radius around its saved spawn position.",
                    "Surface discovery captures this radius when it starts; combat uses the current value.")
            .defineInRange("elemental.water.combatRadius", 50.0, 1.0, 128.0);

    // Provisional horizontal movement radius for the water encounter.
    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_RADIUS = BUILDER
            .comment("Horizontal movement radius around the water Sheeper's saved spawn position.",
                    "Capped at combatRadius; measured at the boss's feet.")
            .defineInRange("elemental.water.movementRadius", 10.0, 0.0, 128.0);

    // Maximum vertical height difference for the water combat arena.
    public static final ForgeConfigSpec.DoubleValue WATER_COMBAT_HEIGHT_RANGE = BUILDER
            .comment("Maximum player feet height difference above or below the saved arena origin.",
                    "Only living survival/adventure players participate in combat.")
            .defineInRange("elemental.water.combatHeightRange", 15.0, 0.0, 128.0);

    // Minimum interval between random arena movements.
    public static final ForgeConfigSpec.IntValue WATER_MOVEMENT_INTERVAL_MIN = BUILDER
            .comment("Minimum ticks between random arena movement targets (20 ticks = one second).")
            .defineInRange("elemental.water.movement.intervalMinTicks", 100, 1, 12000);

    // Maximum interval between random arena movements.
    public static final ForgeConfigSpec.IntValue WATER_MOVEMENT_INTERVAL_MAX = BUILDER
            .comment("Maximum ticks between random arena movement targets; capped below by the minimum.")
            .defineInRange("elemental.water.movement.intervalMaxTicks", 200, 1, 12000);

    // Navigation speed for random arena movement.
    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_SPEED = BUILDER
            .comment("Navigation speed multiplier for random arena movement. 0 disables movement.")
            .defineInRange("elemental.water.movement.speedMultiplier", 1.0, 0.0, 3.0);

    // Horizontal radius for pushing players when the water boss spawns.
    public static final ForgeConfigSpec.DoubleValue WATER_SPAWN_PUSH_RADIUS = BUILDER
            .comment("Players within this horizontal radius are pushed once when the boss spawns.",
                    "Uses combatHeightRange; only living survival/adventure players are affected.")
            .defineInRange("elemental.water.spawnPush.radius", 8.0, 0.0, 128.0);

    // Horizontal knockback strength for pushing players when the water boss spawns.
    public static final ForgeConfigSpec.DoubleValue WATER_SPAWN_PUSH_STRENGTH = BUILDER
            .comment(
                    "Horizontal knockback strength. Higher values push players farther; 0 disables horizontal knockback.")
            .defineInRange("elemental.water.spawnPush.strength", 3.9, 0.0, 3.9);

    // Upward knockback strength for pushing players when the water boss spawns.
    public static final ForgeConfigSpec.DoubleValue WATER_SPAWN_PUSH_UPWARD_STRENGTH = BUILDER
            .comment("Upward knockback strength. 0 keeps the push horizontal.")
            .defineInRange("elemental.water.spawnPush.upwardStrength", 1.0, 0.0, 1.0);

    // Maximum vertical height difference for the water arena.
    public static final ForgeConfigSpec.IntValue WATER_ARENA_HEIGHT_RANGE = BUILDER
            .comment("Maximum surface-cell height difference above or below the saved spawn position.",
                    "Captured when surface discovery starts.")
            .defineInRange("elemental.water.arena.heightRange", 10, 0, 64);

    // Maximum height difference between horizontally adjacent arena surface cells.
    public static final ForgeConfigSpec.IntValue WATER_ARENA_STEP_HEIGHT = BUILDER
            .comment("Maximum height difference between horizontally adjacent arena surface cells.",
                    "Captured when surface discovery starts. 0 restricts discovery to one level.")
            .defineInRange("elemental.water.arena.stepHeight", 1, 0, 4);

    // Maximum number of surface candidates examined per server tick for the water
    // arena.
    public static final ForgeConfigSpec.IntValue WATER_ARENA_SEARCH_BUDGET = BUILDER
            .comment("Maximum surface candidates examined per water Sheeper per server tick.",
                    "Unloaded chunks are deferred without being force-loaded.")
            .defineInRange("elemental.water.arena.searchBudget", 512, 1, 16384);

    // Maximum number of block positions processed per server tick for the water
    // arena.
    public static final ForgeConfigSpec.IntValue WATER_ARENA_BLOCK_BUDGET = BUILDER
            .comment(
                    "Limits block positions processed per tick when placing or removing arena water to reduce server load spikes.")
            .defineInRange("elemental.water.arena.blockBudget", 512, 1, 16384);

    // Ticks between outward flood waves for the water arena.
    public static final ForgeConfigSpec.IntValue WATER_ARENA_FLOOD_WAVE_TICKS = BUILDER
            .comment("Ticks between outward source layers along the connected terrain (20 ticks = one second).",
                    "0 disables layer timing and uses only blockBudget. Large layers still respect blockBudget.")
            .defineInRange("elemental.water.arena.floodWaveIntervalTicks", 1, 0, 200);

    // Ticks between cleanup waves for the water arena.
    public static final ForgeConfigSpec.IntValue WATER_ARENA_DRAIN_WAVE_TICKS = BUILDER
            .comment("Ticks between cleanup layers, from the outermost descendants back toward the spawn.",
                    "0 disables layer timing. Unloaded cells are retried without blocking loaded cleanup.")
            .defineInRange("elemental.water.arena.drainWaveIntervalTicks", 2, 0, 200);

    // Water-specific elemental configuration helpers.
    private static ForgeConfigSpec.DoubleValue elementalHealth(String element) {
        return BUILDER.comment("Maximum health of the " + element + " Sheeper in health points (2 = one heart).",
                "100 is a provisional default pending combat balancing.",
                "Changes apply to existing bosses while preserving their percentage of remaining health.")
                .defineInRange("elemental." + element + ".maxHealth", 100.0, 1.0, 1024.0);
    }

    // Size multiplier for the water Sheeper, including its hitbox.
    private static ForgeConfigSpec.DoubleValue elementalSize(String element) {
        return BUILDER.comment("Size multiplier for the " + element + " Sheeper, including its hitbox.",
                "1.0 = normal sheep/Sheeper size; 2.0 = twice that size.",
                "The server synchronizes this value to clients; changes apply to existing bosses.")
                .defineInRange("elemental." + element + ".size", 2.0, 0.5, 4.0);
    }

    // Detection radius for the Sheeper entity.
    public static final ForgeConfigSpec.DoubleValue DETECTION_RADIUS = BUILDER
            .comment("Maximum distance in blocks for detecting and pursuing players.")
            .defineInRange("sheeper.detectionRadius", 16.0, 1.0, 128.0);

    // Observation angle for the Sheeper entity.
    public static final ForgeConfigSpec.DoubleValue OBSERVATION_ANGLE = BUILDER
            .comment("Total observation angle in degrees, centered on the player's look direction.")
            .defineInRange("sheeper.observationAngle", 120.0, 1.0, 360.0);

    // Maximum distance in blocks for starting and maintaining the fuse.
    public static final ForgeConfigSpec.DoubleValue FUSE_DISTANCE = BUILDER
            .comment("Maximum distance in blocks for starting and maintaining the fuse.")
            .defineInRange("sheeper.fuseDistance", 3.0, 0.0, 128.0);

    // Fuse duration in seconds.
    public static final ForgeConfigSpec.DoubleValue FUSE_DURATION = BUILDER
            .comment("Fuse duration in seconds for stealth and fatal-fire self-explosions.",
                    "Fatal-fire duration is captured when the killing hit is accepted.")
            .defineInRange("sheeper.fuseDuration", 1.5, 0.05, 60.0);

    // Adult retaliation settings. Detection radius also limits angry pursuit.
    public static final ForgeConfigSpec.DoubleValue RETALIATION_SPEED_MULTIPLIER = BUILDER
            .comment("Movement-speed multiplier while pursuing the first attacking player.")
            .defineInRange("sheeper.retaliation.speedMultiplier", 1.5, 0.1, 3.0);

    // Retaliation fuse duration settings.
    public static final ForgeConfigSpec.DoubleValue RETALIATION_FUSE_DURATION = BUILDER
            .comment("Minimum ignition time in seconds before contact can trigger a retaliation burst.",
                    "Ignition starts within melee range and resets when the target leaves that range.")
            .defineInRange("sheeper.retaliation.fuseDuration", 0.5, 0.05, 10.0);

    // Retaliation pause settings.
    public static final ForgeConfigSpec.DoubleValue RETALIATION_PAUSE = BUILDER
            .comment("Pause in seconds after a retaliation burst before pursuing the same player again.")
            .defineInRange("sheeper.retaliation.pauseSeconds", 0.75, 0.0, 1.0);

    // Burst settings.
    public static final ForgeConfigSpec.DoubleValue BURST_RADIUS = BUILDER
            .comment("Radius in blocks of a Sheeper's non-destructive burst.")
            .defineInRange("sheeper.burst.radius", 3.0, 1.0, 8.0);

    // Burst damage settings.
    public static final ForgeConfigSpec.DoubleValue BURST_DAMAGE = BUILDER
            .comment("Base burst damage in health points (1 = half a heart). 0 disables damage.",
                    "Normal armor, damage immunity, and explosion protection still apply.")
            .defineInRange("sheeper.burst.damage", 2.0, 0.0, 40.0);

    // Burst knockback settings.
    public static final ForgeConfigSpec.DoubleValue BURST_KNOCKBACK = BUILDER
            .comment("Burst knockback strength, reduced by distance, cover, and knockback resistance.",
                    "0 disables the additional burst knockback.")
            .defineInRange("sheeper.burst.knockback", 1.2, 0.0, 3.0);

    // Natural spawn chance for Sheeper.
    public static final ForgeConfigSpec.DoubleValue NATURAL_SPAWN_CHANCE = BUILDER
            .comment("Percentage chance to replace each naturally spawning sheep with a Sheeper.",
                    "Applies during world generation and later natural spawning.",
                    "0 disables replacement; 100 replaces every naturally spawning sheep.")
            .defineInRange("spawning.naturalSpawnChance", 5.0, 0.0, 100.0);

    // Additional movement speed for baby Sheeper, in percent.
    public static final ForgeConfigSpec.DoubleValue BABY_SPEED_BOOST_PERCENT = BUILDER
            .comment("Additional movement speed for baby Sheeper, in percent.",
                    "0 gives babies the same movement speed as adults.")
            .defineInRange("baby.speedBoostPercent", 25.0, 0.0, 100.0);

    // Knockback strength when a baby Sheeper bumps a player.
    public static final ForgeConfigSpec.DoubleValue BABY_BUMP_KNOCKBACK = BUILDER
            .comment("Knockback strength when a baby Sheeper bumps a player.",
                    "0 disables the knockback.")
            .defineInRange("baby.bumpKnockback", 0.3, 0.0, 2.0);

    // Minimum pause in seconds after a baby Sheeper retreats.
    public static final ForgeConfigSpec.DoubleValue BABY_ATTACK_PAUSE_MIN = BUILDER
            .comment("Minimum pause in seconds after a baby Sheeper retreats.")
            .defineInRange("baby.attackPauseMin", 3.0, 0.0, 60.0);

    // Maximum pause in seconds after a baby Sheeper retreats.
    public static final ForgeConfigSpec.DoubleValue BABY_ATTACK_PAUSE_MAX = BUILDER
            .comment("Maximum pause in seconds after a baby Sheeper retreats.",
                    "Equal minimum and maximum values produce a fixed pause.",
                    "If the maximum is below the minimum, the pause is fixed at the minimum.")
            .defineInRange("baby.attackPauseMax", 8.0, 0.0, 60.0);

    // Explosion strength of adult Sheeper.
    public static final ForgeConfigSpec.DoubleValue ADULT_EXPLOSION_STRENGTH = BUILDER
            .comment("Explosion strength of adult Sheeper.",
                    "Default: 3.0, matching a normal Creeper.")
            .defineInRange("sheeper.adultExplosionStrength", 3.0, 0.1, 5.0);

    // Explosion strength when a baby Sheeper takes damage.
    public static final ForgeConfigSpec.DoubleValue BABY_EXPLOSION_STRENGTH = BUILDER
            .comment("Explosion strength when a baby Sheeper takes damage.",
                    "Default: 0.4; Adult explosion strength is configured separately.",
                    "Values below 0.4 may cause no noticeable player damage or block destruction.",
                    "The actual effect depends on distance and surrounding blocks.")
            .defineInRange("baby.explosionStrength", 0.4, 0.1, 5.0);

    // Additional gameplay settings, read by the logical server and grouped by
    // feature.
    public static final ForgeConfigSpec.DoubleValue MIXED_BREEDING_CHANCE = BUILDER
            .comment("Percentage chance of Sheeper offspring from a mixed sheep/Sheeper pair.",
                    "Pairs of two Sheeper or two normal sheep are unaffected.")
            .defineInRange("breeding.mixedSheeperChance", 50.0, 0.0, 100.0);

    // Raw mutton effect settings.
    public static final ForgeConfigSpec.DoubleValue RAW_MUTTON_DURATION = BUILDER
            .comment("Raw mutton effect duration in seconds. 0 disables its eating effect.",
                    "Duration is captured when food is consumed; existing countdowns are retained.")
            .defineInRange("mutton.raw.durationSeconds", 6.0, 0.0, 600.0);

    // Raw mutton internal explosion interval.
    public static final ForgeConfigSpec.DoubleValue RAW_MUTTON_INTERVAL = BUILDER
            .comment("Seconds between raw mutton internal explosions. The first waits one full interval.",
                    "Minecraft's normal damage immunity still applies at short intervals.")
            .defineInRange("mutton.raw.internalExplosionIntervalSeconds", 1.0, 0.05, 60.0);

    // Raw mutton internal explosion damage and knockback.
    public static final ForgeConfigSpec.DoubleValue RAW_MUTTON_DAMAGE = BUILDER
            .comment("Base damage per raw mutton internal explosion in health points (1 = half a heart).",
                    "Bypasses armor and may be lethal. 0 disables damage, but keeps other effects.")
            .defineInRange("mutton.raw.internalExplosionDamage", 1.0, 0.0, 40.0);

    // Raw mutton internal explosion knockback.
    public static final ForgeConfigSpec.DoubleValue RAW_MUTTON_PUSH = BUILDER
            .comment("Random horizontal impulse per raw mutton internal explosion. 0 disables the impulse.")
            .defineInRange("mutton.raw.knockback", 0.12, 0.0, 2.0);

    // Cooked mutton effect settings.
    public static final ForgeConfigSpec.DoubleValue COOKED_MUTTON_DURATION = BUILDER
            .comment("Cooked mutton effect duration in seconds. 0 disables damage and the speed bonus.",
                    "Duration is captured when food is consumed; existing countdowns are retained.")
            .defineInRange("mutton.cooked.durationSeconds", 10.0, 0.0, 600.0);

    // Cooked mutton internal explosion interval.
    public static final ForgeConfigSpec.DoubleValue COOKED_MUTTON_INTERVAL = BUILDER
            .comment("Seconds between cooked mutton internal explosions. The first waits one full interval.",
                    "Minecraft's normal damage immunity still applies at short intervals.")
            .defineInRange("mutton.cooked.internalExplosionIntervalSeconds", 2.0, 0.05, 60.0);

    // Cooked mutton internal explosion damage and speed boost.
    public static final ForgeConfigSpec.DoubleValue COOKED_MUTTON_DAMAGE = BUILDER
            .comment("Base damage per cooked mutton internal explosion in health points (1 = half a heart).",
                    "Bypasses armor and may be lethal. 0 disables damage, but keeps other effects.")
            .defineInRange("mutton.cooked.internalExplosionDamage", 1.0, 0.0, 40.0);

    // Cooked mutton speed boost.
    public static final ForgeConfigSpec.DoubleValue COOKED_MUTTON_SPEED_PERCENT = BUILDER
            .comment("Additional movement speed in percent during the cooked mutton effect. 0 disables the bonus.")
            .defineInRange("mutton.cooked.speedBoostPercent", 50.0, 0.0, 200.0);

    // Unsafe cooking settings.
    public static final ForgeConfigSpec.BooleanValue UNSAFE_COOKING_ENABLED = BUILDER
            .comment("Whether heating raw mutton in furnaces/campfires causes an explosion.",
                    "When disabled, their cooking recipes complete normally.")
            .define("mutton.cooking.explosionsEnabled", true);

    // Unsafe cooking warning and explosion settings.
    public static final ForgeConfigSpec.DoubleValue COOKING_WARNING_SECONDS = BUILDER
            .comment("Warning time in seconds before unsafe cooking explodes.",
                    "Limited to 9.95 seconds so the default 10-second furnace recipe cannot finish first.",
                    "Custom data-pack recipes must also remain longer than this warning.")
            .defineInRange("mutton.cooking.warningSeconds", 3.0, 0.05, 9.95);

    // Unsafe cooking explosion strength.
    public static final ForgeConfigSpec.DoubleValue COOKING_EXPLOSION_STRENGTH = BUILDER
            .comment("Unsafe-cooking explosion strength. 0 suppresses the blast, but still removes the appliance",
                    "and consumes one piece. Disable explosionsEnabled to permit safe cooking instead.")
            .defineInRange("mutton.cooking.explosionStrength", 1.0, 0.0, 8.0);

    // Fire-related raw mutton explosion settings.
    public static final ForgeConfigSpec.BooleanValue MUTTON_FIRE_ENABLED = BUILDER
            .comment("Whether dropped raw mutton detonates on fire/lava contact.",
                    "When disabled, it behaves like an ordinary dropped item and can burn normally.")
            .define("mutton.fire.explosionsEnabled", true);

    // Fire-related raw mutton explosion strength.
    public static final ForgeConfigSpec.DoubleValue MUTTON_FIRE_STRENGTH = BUILDER
            .comment("Dropped-mutton fire explosion strength. 0 suppresses the blast but still consumes the stack.")
            .defineInRange("mutton.fire.explosionStrength", 1.0, 0.0, 8.0);

    // Unsafe wool mining and primed wool explosion settings.
    public static final ForgeConfigSpec.DoubleValue WOOL_MINING_STRENGTH = BUILDER
            .comment(
                    "Explosion strength for unsafe wool/carpet mining. 0 disables the blast; unsafe mining still drops nothing.")
            .defineInRange("wool.unsafeMiningExplosionStrength", 1.0, 0.0, 8.0);

    // Primed wool explosion settings.
    public static final ForgeConfigSpec.DoubleValue WOOL_PRIMED_STRENGTH = BUILDER
            .comment("Destructive explosion strength of primed wool. 0 suppresses the blast.")
            .defineInRange("wool.primedExplosionStrength", 4.0, 0.0, 16.0);

    // Wool fuse settings.
    public static final ForgeConfigSpec.DoubleValue WOOL_FUSE_SECONDS = BUILDER
            .comment("Normal wool fuse in seconds, captured when ignited. Explosion chains shorten this fuse.")
            .defineInRange("wool.fuseSeconds", 4.0, 0.05, 120.0);

    // Carpet step explosions keep damage and impulse independent of their radius.
    public static final ForgeConfigSpec.DoubleValue CARPET_RADIUS = BUILDER
            .comment("Radius in blocks of a carpet step explosion. Walls shield entities.")
            .defineInRange("carpet.radius", 2.0, 1.0, 8.0);

    // Carpet step explosion damage settings.
    public static final ForgeConfigSpec.DoubleValue CARPET_DAMAGE = BUILDER
            .comment("Base step-explosion damage in health points (2 = one heart), reduced by cover.",
                    "Normal armor, damage immunity, and explosion protection apply. 0 disables damage.")
            .defineInRange("carpet.damage", 2.0, 0.0, 40.0);

    // Carpet step explosion knockback settings.
    public static final ForgeConfigSpec.DoubleValue CARPET_KNOCKBACK = BUILDER
            .comment("Horizontal step-explosion impulse, reduced by distance, cover, and knockback resistance.",
                    "0 disables the additional horizontal impulse.")
            .defineInRange("carpet.knockback", 1.0, 0.0, 3.0);

    // Carpet step explosion upward push settings.
    public static final ForgeConfigSpec.DoubleValue CARPET_UPWARD_PUSH = BUILDER
            .comment("Upward step-explosion impulse, reduced by distance, cover, and knockback resistance.",
                    "0 disables the additional upward impulse. Ordinary fall damage still applies.")
            .defineInRange("carpet.upwardPush", 0.8, 0.0, 2.0);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /** Converts seconds to whole game ticks, rounding up; zero remains zero. */
    public static int secondsToTicks(double seconds) {
        return (int) Math.ceil(seconds * 20.0);
    }

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
