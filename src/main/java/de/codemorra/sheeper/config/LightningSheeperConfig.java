package de.codemorra.sheeper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Installation-wide lightning Sheeper settings, currently part of the shared
 * common file.
 */
public final class LightningSheeperConfig {
    private static final ForgeConfigSpec.Builder BUILDER = CommonConfigBuilder.BUILDER;

    /** Lightning elemental boss and combat bounds (elemental.lightning). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_MAX_HEALTH = ElementalConfigValues
            .elementalHealth(BUILDER, "lightning", 200.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SIZE = ElementalConfigValues.elementalSize(BUILDER,
            "lightning");

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_COMBAT_RADIUS = BUILDER
            .comment("Horizontal lightning Sheeper arena and combat radius around its saved spawn position.")
            .defineInRange("elemental.lightning.combatRadius", 50.0, 1.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_MOVEMENT_RADIUS = BUILDER
            .comment(
                    "Horizontal teleport movement radius around the saved spawn position; capped at combatRadius. 0 disables movement.")
            .defineInRange("elemental.lightning.movementRadius", 10.0, 0.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_COMBAT_HEIGHT_RANGE = BUILDER
            .comment("Vertical viewer range for arena boundary particles around the saved origin.",
                    "Does not limit combat participation after entering the flooded arena.")
            .defineInRange("elemental.lightning.combatHeightRange", 15.0, 0.0, 128.0);

    /** Lightning arena preparation (elemental.lightning.arena). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PREPARATION_DURATION = BUILDER
            .comment(
                    "Seconds for each preparation wave to reach the arena edge. 0 skips spawn waves and awakening.",
                    "Storm awakening follows the final wave. Wave timing is captured at spawn and preserved when loading.")
            .defineInRange("elemental.lightning.arena.preparationSeconds", 4.0, 0.0, 600.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_PREPARATION_WAVE_COUNT = BUILDER
            .comment("Number of outward preparation waves before storm awakening begins.")
            .defineInRange("elemental.lightning.arena.waveCount", 3, 1, 16);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PREPARATION_WAVE_INTERVAL = BUILDER
            .comment("Seconds between the starts of successive preparation waves; waves may overlap.")
            .defineInRange("elemental.lightning.arena.waveIntervalSeconds", 1.0, 0.0, 60.0);

    /**
     * Local weather, awakening and ambient edge strikes
     * (elemental.lightning.storm).
     */
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_STORM_ENABLED = BUILDER
            .comment("Local arena rain and thunder, including normal rain effects on exposed entities and blocks.")
            .define("elemental.lightning.storm.enabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STORM_AWAKENING = BUILDER
            .comment("Protected storm awakening seconds after the last preparation wave, before the first attack.")
            .defineInRange("elemental.lightning.storm.awakeningSeconds", 3.0, 0.0, 60.0);

    public static final ForgeConfigSpec.BooleanValue LIGHTNING_STORM_EDGE_ENABLED = BUILDER
            .comment("Randomly clustered, announced lightning strikes at the outer arena edge during combat.")
            .define("elemental.lightning.storm.edgeLightning", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STORM_EDGE_DAMAGE = BUILDER
            .comment("Edge strike damage in health points (2 = one heart). Awakening strikes never deal damage.")
            .defineInRange("elemental.lightning.storm.edgeDamage", 2.0, 0.0, 1024.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STORM_EDGE_WARNING = BUILDER
            .comment("Warning seconds before a combat edge strike.")
            .defineInRange("elemental.lightning.storm.edgeWarningSeconds", 1.5, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STORM_RUMBLE_VOLUME = BUILDER
            .comment("Ambient thunder volume for players inside the arena. 0 disables rumbling.")
            .defineInRange("elemental.lightning.storm.rumbleVolume", 0.6, 0.0, 4.0);

    /** One flat knockback per preparation wave (elemental.lightning.spawnPush). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SPAWN_PUSH_RADIUS = BUILDER
            .comment("Maximum player distance from the boss for the spawn wave push. 0 disables it.")
            .defineInRange("elemental.lightning.spawnPush.radius", 15.0, 0.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SPAWN_PUSH_DELAY = BUILDER
            .comment("Seconds after each expanding preparation wave reaches a player before that wave's one-time push.",
                    "Leaving the radius cancels the push. Pending pushes survive the end of preparation and loading.")
            .defineInRange("elemental.lightning.spawnPush.delaySeconds", 0.15, 0.0, 60.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SPAWN_PUSH_STRENGTH = BUILDER
            .comment("Horizontal spawn wave velocity away from the arena center; deals no direct damage.")
            .defineInRange("elemental.lightning.spawnPush.horizontalStrength", 1.5, 0.0, 3.9);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SPAWN_PUSH_UPWARD_STRENGTH = BUILDER
            .comment("Upward spawn wave velocity. Replaces existing player momentum.",
                    "The default rises about two blocks at normal gravity, avoiding fall damage on same-height landings.")
            .defineInRange("elemental.lightning.spawnPush.upwardStrength", 0.55, 0.0, 1.0);

    /** Lightning teleport movement (elemental.lightning.teleport). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TELEPORT_INTERVAL_MIN = BUILDER
            .comment("Minimum seconds from a completed teleport to the next announcement.",
                    "Movement continues without arena participants. NoAI pauses timers. Failed searches wait another interval.")
            .defineInRange("elemental.lightning.teleport.intervalMinSeconds", 5.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TELEPORT_INTERVAL_MAX = BUILDER
            .comment("Maximum seconds before the next announcement; capped below by intervalMinSeconds.")
            .defineInRange("elemental.lightning.teleport.intervalMaxSeconds", 10.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TELEPORT_WARNING = BUILDER
            .comment("Seconds of sparks at the destination before teleporting. 0 teleports immediately.",
                    "The destination is checked again before arrival; invalid destinations cancel the teleport.")
            .defineInRange("elemental.lightning.teleport.warningSeconds", 0.5, 0.0, 60.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TELEPORT_PLAYER_DISTANCE = BUILDER
            .comment("Minimum distance in blocks between the destination and living non-spectator players.")
            .defineInRange("elemental.lightning.teleport.playerDistance", 4.0, 0.0, 128.0);

    /** Lightning attack selection (elemental.lightning.attack). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_WEIGHT = BUILDER
            .comment("Relative weight of lightning storm phases; 0 disables this type.")
            .defineInRange("elemental.lightning.attack.strikeWeight", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_WEIGHT = BUILDER
            .comment("Relative weight of moving storm fronts; 0 disables this type.")
            .defineInRange("elemental.lightning.attack.frontWeight", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_WEIGHT = BUILDER
            .comment("Relative weight of expanding electric rings; 0 disables this type.")
            .defineInRange("elemental.lightning.attack.ringWeight", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ATTACK_PAUSE_MIN = BUILDER
            .comment("Minimum rest seconds between attacks. Normal teleports are allowed during rest.",
                    "An announced teleport finishes before the next attack. NoAI and an empty arena pause the timer.")
            .defineInRange("elemental.lightning.attack.pauseMinSeconds", 4.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ATTACK_PAUSE_MAX = BUILDER
            .comment("Maximum rest seconds between attacks; capped below by pauseMinSeconds.")
            .defineInRange("elemental.lightning.attack.pauseMaxSeconds", 7.0, 0.05, 600.0);

    /** Lightning storm phase (elemental.lightning.strike). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_DURATION_MIN = BUILDER
            .comment("Minimum lightning phase duration in seconds. Sampled at phase start.")
            .defineInRange("elemental.lightning.strike.durationMinSeconds", 20.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_DURATION_MAX = BUILDER
            .comment("Maximum lightning phase duration in seconds; capped below by durationMinSeconds.",
                    "New centers stop early enough for all three warned rounds to finish within the phase.")
            .defineInRange("elemental.lightning.strike.durationMaxSeconds", 30.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_INTERVAL_MIN = BUILDER
            .comment("Minimum independent interval in seconds between central warnings per player and for the arena.")
            .defineInRange("elemental.lightning.strike.intervalMinSeconds", 0.5, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_INTERVAL_MAX = BUILDER
            .comment("Maximum central warning interval in seconds; capped below by intervalMinSeconds.")
            .defineInRange("elemental.lightning.strike.intervalMaxSeconds", 1.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_PLAYER_COOLDOWN = BUILDER
            .comment("Minimum seconds after a player's third round finishes before targeting that player again.",
                    "The complete three-round sequence is reserved first; the usual interval and warning follow this cooldown.")
            .defineInRange("elemental.lightning.strike.playerCooldownSeconds", 5.0, 0.0, 600.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_STRIKE_ARENA_COUNT = BUILDER
            .comment(
                    "Random arena centers announced per interval in addition to player centers. 0 disables random centers.")
            .defineInRange("elemental.lightning.strike.arenaCount", 3, 0, 16);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_WARNING = BUILDER
            .comment("Seconds between marking a fixed strike position and impact.",
                    "Unloaded positions are cancelled. Each center captures the warning duration for all three rounds.")
            .defineInRange("elemental.lightning.strike.warningSeconds", 1.5, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_RADIUS = BUILDER
            .comment("Horizontal damage radius in blocks, shown by the warning ring.")
            .defineInRange("elemental.lightning.strike.radius", 1.5, 0.1, 16.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_HEIGHT = BUILDER
            .comment(
                    "Damage height above the marked surface in blocks. Solid cover between impact and player blocks damage.")
            .defineInRange("elemental.lightning.strike.height", 3.0, 0.1, 32.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_DAMAGE = BUILDER
            .comment("Damage per strike in health points (2 = one heart). 0 disables damage.",
                    "Normal armor and damage immunity apply. Strikes do not ignite or transform anything.")
            .defineInRange("elemental.lightning.strike.damage", 6.0, 0.0, 1024.0);

    /**
     * Two outward lightning rounds around each center
     * (elemental.lightning.strike.followup).
     */
    public static final ForgeConfigSpec.IntValue LIGHTNING_FOLLOWUP_INNER_COUNT_MIN = BUILDER
            .comment("Minimum bolts in the first surrounding round. Announced as the central bolt hits.")
            .defineInRange("elemental.lightning.strike.followup.innerCountMin", 3, 1, 16);

    public static final ForgeConfigSpec.IntValue LIGHTNING_FOLLOWUP_INNER_COUNT_MAX = BUILDER
            .comment("Maximum bolts in the first surrounding round; capped below by innerCountMin.")
            .defineInRange("elemental.lightning.strike.followup.innerCountMax", 4, 1, 16);

    public static final ForgeConfigSpec.IntValue LIGHTNING_FOLLOWUP_OUTER_COUNT_MIN = BUILDER
            .comment("Minimum bolts in the outer round. Announced as the first surrounding round hits.")
            .defineInRange("elemental.lightning.strike.followup.outerCountMin", 5, 1, 16);

    public static final ForgeConfigSpec.IntValue LIGHTNING_FOLLOWUP_OUTER_COUNT_MAX = BUILDER
            .comment("Maximum bolts in the outer round; capped below by outerCountMin.")
            .defineInRange("elemental.lightning.strike.followup.outerCountMax", 7, 1, 16);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FOLLOWUP_INNER_RADIUS_MIN = BUILDER
            .comment("Minimum first-round distance from the center in blocks.")
            .defineInRange("elemental.lightning.strike.followup.innerRadiusMin", 3.0, 0.1, 32.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FOLLOWUP_INNER_RADIUS_MAX = BUILDER
            .comment("Maximum first-round distance; capped below by innerRadiusMin.")
            .defineInRange("elemental.lightning.strike.followup.innerRadiusMax", 4.0, 0.1, 32.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FOLLOWUP_OUTER_RADIUS_MIN = BUILDER
            .comment("Minimum outer-round distance; kept beyond the first round's maximum distance.")
            .defineInRange("elemental.lightning.strike.followup.outerRadiusMin", 6.0, 0.1, 64.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FOLLOWUP_OUTER_RADIUS_MAX = BUILDER
            .comment("Maximum outer-round distance; capped below by the effective outerRadiusMin.")
            .defineInRange("elemental.lightning.strike.followup.outerRadiusMax", 8.0, 0.1, 64.0);

    /**
     * Residual electrical zones at central impacts
     * (elemental.lightning.strike.aftershock).
     */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AFTERSHOCK_AFTER = BUILDER
            .comment(
                    "Seconds electrical zones remain after the storm phase ends. During the phase all central zones stay active.")
            .defineInRange("elemental.lightning.strike.aftershock.afterSeconds", 5.0, 0.0, 120.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AFTERSHOCK_RADIUS = BUILDER
            .comment("Radius of the circular crackling zone in blocks.")
            .defineInRange("elemental.lightning.strike.aftershock.radius", 1.25, 0.1, 8.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AFTERSHOCK_HEIGHT = BUILDER
            .comment("Damage height above the zone in blocks; solid cover blocks damage.")
            .defineInRange("elemental.lightning.strike.aftershock.height", 1.0, 0.1, 8.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AFTERSHOCK_DAMAGE = BUILDER
            .comment("Damage per zone pulse in health points (1 = half a heart), before armor. 0 disables damage.")
            .defineInRange("elemental.lightning.strike.aftershock.damage", 1.0, 0.0, 40.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AFTERSHOCK_INTERVAL = BUILDER
            .comment("Seconds between zone damage pulses per player. Overlapping zones share the cooldown.",
                    "Zones expire during recovery and without participants; NoAI clears them.")
            .defineInRange("elemental.lightning.strike.aftershock.intervalSeconds", 1.0, 0.05, 60.0);

    /** Lightning attack audio (elemental.lightning.strike.sound). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_THUNDER_VOLUME = BUILDER
            .comment("Thunder volume per bolt. 0 disables this sound.")
            .defineInRange("elemental.lightning.strike.sound.thunderVolume", 0.6, 0.0, 4.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_IMPACT_VOLUME = BUILDER
            .comment("Electrical impact volume per bolt. 0 disables this sound.")
            .defineInRange("elemental.lightning.strike.sound.impactVolume", 0.8, 0.0, 4.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AFTERSHOCK_VOLUME = BUILDER
            .comment("Local crackling volume while zones are active. 0 disables this sound.")
            .defineInRange("elemental.lightning.strike.sound.crackleVolume", 0.2, 0.0, 4.0);

    /** Outward lightning lanes (elemental.lightning.front). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_DURATION_MIN = BUILDER
            .comment("Minimum lightning lane phase duration in seconds, sampled at phase start.")
            .defineInRange("elemental.lightning.front.durationMinSeconds", 12.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_DURATION_MAX = BUILDER
            .comment("Maximum phase duration; capped below by durationMinSeconds.",
                    "New lanes start only when their complete path and final warning fit before phase end.")
            .defineInRange("elemental.lightning.front.durationMaxSeconds", 18.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_SPAWN_INTERVAL_MIN = BUILDER
            .comment("Minimum seconds between starting new lanes. The first lane starts immediately.")
            .defineInRange("elemental.lightning.front.spawnIntervalMinSeconds", 1.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_SPAWN_INTERVAL_MAX = BUILDER
            .comment("Maximum seconds between new lanes; capped below by spawnIntervalMinSeconds.")
            .defineInRange("elemental.lightning.front.spawnIntervalMaxSeconds", 1.5, 0.05, 60.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_FRONT_MAX_ACTIVE = BUILDER
            .comment("Maximum simultaneously active lanes, including their pending impacts.")
            .defineInRange("elemental.lightning.front.maxActiveLanes", 4, 1, 16);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_STEP_DISTANCE = BUILDER
            .comment("Forward distance in blocks between successive warnings along a lane's fixed centerline.")
            .defineInRange("elemental.lightning.front.stepDistance", 3.0, 0.5, 16.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_STEP_INTERVAL = BUILDER
            .comment(
                    "Seconds between successive warnings on each lane; speed is stepDistance divided by this interval.")
            .defineInRange("elemental.lightning.front.stepIntervalSeconds", 0.25, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_WARNING = BUILDER
            .comment("Seconds from marking a lane position to impact. Positions never follow players.")
            .defineInRange("elemental.lightning.front.warningSeconds", 0.75, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_SIDE_OFFSET = BUILDER
            .comment("Maximum random sideways offset in blocks from the fixed centerline for each impact.")
            .defineInRange("elemental.lightning.front.sideOffset", 2.5, 0.0, 16.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_DIRECTION_GAP = BUILDER
            .comment("Minimum angle in degrees between active lanes. Directions are otherwise unrestricted.",
                    "If no suitable direction is found, no new lane starts during that interval.")
            .defineInRange("elemental.lightning.front.directionGapDegrees", 45.0, 0.0, 180.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_RADIUS = BUILDER
            .comment("Damage radius in blocks for each lane impact, shown by its warning.")
            .defineInRange("elemental.lightning.front.radius", 1.0, 0.1, 16.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_HEIGHT = BUILDER
            .comment("Damage height above each marked surface in blocks; solid cover blocks damage.")
            .defineInRange("elemental.lightning.front.height", 3.0, 0.1, 32.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_DAMAGE = BUILDER
            .comment(
                    "Damage per lane in health points (2 = one heart). Each player can be damaged once per lane. 0 disables damage.",
                    "Uses lightning strike sounds; lane impacts produce no follow-up rounds or residual zones.")
            .defineInRange("elemental.lightning.front.damage", 2.0, 0.0, 1024.0);

    /**
     * Teleport preparation and terrain-following rings (elemental.lightning.ring).
     */
    public static final ForgeConfigSpec.IntValue LIGHTNING_RING_TELEPORT_COUNT_MIN = BUILDER
            .comment("Minimum rapid teleport attempts before charging. Unsafe destinations are skipped.")
            .defineInRange("elemental.lightning.ring.teleportCountMin", 5, 0, 32);

    public static final ForgeConfigSpec.IntValue LIGHTNING_RING_TELEPORT_COUNT_MAX = BUILDER
            .comment("Maximum rapid teleport attempts; capped below by teleportCountMin.")
            .defineInRange("elemental.lightning.ring.teleportCountMax", 8, 0, 32);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_TELEPORT_INTERVAL = BUILDER
            .comment("Seconds between rapid teleports. Uses the normal teleport area and safety checks.")
            .defineInRange("elemental.lightning.ring.teleportIntervalSeconds", 0.35, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_CHARGE = BUILDER
            .comment("Visible stationary charge duration in seconds after the rapid teleports.")
            .defineInRange("elemental.lightning.ring.chargeSeconds", 2.0, 0.05, 60.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_RING_COUNT_MIN = BUILDER
            .comment("Minimum consecutive ring waves after charging.")
            .defineInRange("elemental.lightning.ring.countMin", 3, 1, 16);

    public static final ForgeConfigSpec.IntValue LIGHTNING_RING_COUNT_MAX = BUILDER
            .comment("Maximum consecutive ring waves; capped below by countMin.")
            .defineInRange("elemental.lightning.ring.countMax", 5, 1, 16);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_INTERVAL = BUILDER
            .comment("Seconds between releasing ring waves.")
            .defineInRange("elemental.lightning.ring.intervalSeconds", 1.25, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_SPEED = BUILDER
            .comment("Outward speed in blocks per second. Rings finish at the arena boundary.")
            .defineInRange("elemental.lightning.ring.speedBlocksPerSecond", 5.0, 0.5, 30.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_WIDTH = BUILDER
            .comment("Radial thickness of the visible ring and its damage band in blocks.")
            .defineInRange("elemental.lightning.ring.width", 0.75, 0.1, 4.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_HIT_HEIGHT = BUILDER
            .comment("Damage height above the local ground in blocks; jumping above it avoids the ring.")
            .defineInRange("elemental.lightning.ring.hitHeight", 0.35, 0.05, 2.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_DAMAGE = BUILDER
            .comment("Damage per ring wave in health points (2 = one heart), at most once per player per wave.")
            .defineInRange("elemental.lightning.ring.damage", 2.0, 0.0, 1024.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_PARTICLE_SPACING = BUILDER
            .comment("Distance between glowing ring segments in blocks. Lower values produce denser effects.")
            .defineInRange("elemental.lightning.ring.particleSpacing", 0.5, 0.1, 1.0);

    /**
     * Shared proximity charge and defensive discharge
     * (elemental.lightning.proximityPush).
     */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_RADIUS = BUILDER
            .comment("Distance from the boss for charging, damage and knockback. 0 disables the defense.",
                    "One shared timer runs while any combat participant stays nearby; an empty radius resets it.",
                    "Runs independently of normal attacks and recovery. NoAI pauses charging.")
            .defineInRange("elemental.lightning.proximityPush.radius", 3.0, 0.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_DELAY_MIN = BUILDER
            .comment("Minimum continuous proximity seconds before discharging. Sampled on entry and after a discharge.")
            .defineInRange("elemental.lightning.proximityPush.delayMinSeconds", 4.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_DELAY_MAX = BUILDER
            .comment("Maximum proximity seconds before discharging; capped below by delayMinSeconds.")
            .defineInRange("elemental.lightning.proximityPush.delayMaxSeconds", 8.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_DAMAGE = BUILDER
            .comment("Discharge damage to every nearby combat participant in health points (2 = one heart).")
            .defineInRange("elemental.lightning.proximityPush.damage", 6.0, 0.0, 1024.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_STRENGTH = BUILDER
            .comment("Horizontal discharge impulse away from the boss, matching the water boss by default.")
            .defineInRange("elemental.lightning.proximityPush.strength", 3.9, 0.0, 3.9);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_UPWARD_STRENGTH = BUILDER
            .comment("Upward discharge impulse, matching the water boss by default.")
            .defineInRange("elemental.lightning.proximityPush.upwardStrength", 1.0, 0.0, 1.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_PROXIMITY_PUSH_PARTICLE_COUNT = BUILDER
            .comment("Peak spark count per charge update and number of outward particle rays when discharging.")
            .defineInRange("elemental.lightning.proximityPush.particleCount", 96, 16, 512);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_THUNDER_VOLUME = BUILDER
            .comment("Thunder volume per discharge. 0 disables this sound.")
            .defineInRange("elemental.lightning.proximityPush.thunderVolume", 0.6, 0.0, 4.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_IMPACT_VOLUME = BUILDER
            .comment("Electrical impact volume per discharge. 0 disables this sound.")
            .defineInRange("elemental.lightning.proximityPush.impactVolume", 0.8, 0.0, 4.0);

    /**
     * Ensures these definitions exist before the shared common specification is
     * built.
     */
    static void initialize() {
    }

    private LightningSheeperConfig() {
    }
}
