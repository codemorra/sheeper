package de.codemorra.sheeper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Installation-wide configuration for Sheeper behavior.
 * Distances are measured in blocks and angles in degrees.
 */
public class SheeperConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** Lava elemental boss (elemental.lava). */
    public static final ForgeConfigSpec.DoubleValue LAVA_MAX_HEALTH = elementalHealth("lava", 200.0);

    public static final ForgeConfigSpec.DoubleValue LAVA_SIZE = elementalSize("lava");

    /** Lightning elemental boss and combat bounds (elemental.lightning). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_MAX_HEALTH = elementalHealth("lightning", 200.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SIZE = elementalSize("lightning");

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_COMBAT_RADIUS = BUILDER
            .comment("Horizontal lightning Sheeper arena and combat radius around its saved spawn position.")
            .defineInRange("elemental.lightning.combatRadius", 50.0, 1.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_MOVEMENT_RADIUS = BUILDER
            .comment(
                    "Horizontal teleport movement radius around the saved spawn position; capped at combatRadius. 0 disables movement.")
            .defineInRange("elemental.lightning.movementRadius", 10.0, 0.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_COMBAT_HEIGHT_RANGE = BUILDER
            .comment("Maximum player feet height difference above or below the saved arena origin.",
                    "Only living survival/adventure players participate in combat.")
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

    /** Water elemental boss and combat bounds (elemental.water). */
    public static final ForgeConfigSpec.DoubleValue WATER_MAX_HEALTH = elementalHealth("water", 200.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SIZE = elementalSize("water");

    public static final ForgeConfigSpec.DoubleValue WATER_COMBAT_RADIUS = BUILDER
            .comment("Horizontal water Sheeper arena and combat radius around its saved spawn position.",
                    "Surface discovery captures this radius when it starts; combat uses the current value.")
            .defineInRange("elemental.water.combatRadius", 50.0, 1.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_RADIUS = BUILDER
            .comment("Horizontal movement radius around the water Sheeper's saved spawn position.",
                    "Capped at combatRadius; measured at the boss's feet.")
            .defineInRange("elemental.water.movementRadius", 10.0, 0.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue WATER_COMBAT_HEIGHT_RANGE = BUILDER
            .comment("Maximum player feet height difference above or below the saved arena origin.",
                    "Only living survival/adventure players participate in combat.")
            .defineInRange("elemental.water.combatHeightRange", 15.0, 0.0, 128.0);

    /** Water boss movement (elemental.water.movement). */
    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_INTERVAL_MIN = BUILDER
            .comment("Minimum seconds between random arena movement targets.")
            .defineInRange("elemental.water.movement.intervalMinSeconds", 5.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_INTERVAL_MAX = BUILDER
            .comment("Maximum seconds between random arena movement targets; capped below by the minimum.")
            .defineInRange("elemental.water.movement.intervalMaxSeconds", 10.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_SPEED = BUILDER
            .comment("Navigation speed multiplier for random arena movement. 0 disables movement.")
            .defineInRange("elemental.water.movement.speedMultiplier", 1.0, 0.0, 3.0);

    /**
     * Water-contact spawn wave and legacy proximity strengths
     * (elemental.water.spawnPush).
     */
    public static final ForgeConfigSpec.DoubleValue WATER_SPAWN_WAVE_RADIUS = BUILDER
            .comment("Maximum distance from the boss for contact and launch of the spawn water wave; 0 disables it.")
            .defineInRange("elemental.water.spawnPush.radius", 15.0, 0.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SPAWN_PUSH_DELAY = BUILDER
            .comment("Seconds after first contact with this boss's water during flooding before the one-time push.")
            .defineInRange("elemental.water.spawnPush.delaySeconds", 0.15, 0.0, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SPAWN_WAVE_HEIGHT = BUILDER
            .comment("Maximum rise of the spawn water wave in blocks; water drag and ceilings may reduce it.")
            .defineInRange("elemental.water.spawnPush.height", 10.0, 0.0, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SPAWN_WAVE_HORIZONTAL_STRENGTH = BUILDER
            .comment("Horizontal launch velocity away from the arena center for the spawn water wave.")
            .defineInRange("elemental.water.spawnPush.horizontalStrength", 1.5, 0.0, 3.9);

    public static final ForgeConfigSpec.DoubleValue WATER_SPAWN_PUSH_STRENGTH = BUILDER
            .comment(
                    "Horizontal proximity knockback strength. The spawn water wave uses horizontalStrength instead.")
            .defineInRange("elemental.water.spawnPush.strength", 3.9, 0.0, 3.9);

    public static final ForgeConfigSpec.DoubleValue WATER_SPAWN_PUSH_UPWARD_STRENGTH = BUILDER
            .comment("Upward proximity knockback strength. The spawn water wave uses height instead.")
            .defineInRange("elemental.water.spawnPush.upwardStrength", 1.0, 0.0, 1.0);

    /** Water boss proximity push (elemental.water.proximityPush). */
    public static final ForgeConfigSpec.DoubleValue WATER_PROXIMITY_PUSH_RADIUS = BUILDER
            .comment("Distance from the boss within which players accumulate time toward a proximity push.",
                    "Leaving resets the timer. Uses spawnPush strengths; 0 disables proximity pushes.")
            .defineInRange("elemental.water.proximityPush.radius", 3.0, 0.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROXIMITY_PUSH_DELAY_MIN = BUILDER
            .comment("Minimum continuous proximity seconds before a push.",
                    "Sampled independently per player on entry and after each push. NoAI pauses timers.")
            .defineInRange("elemental.water.proximityPush.delayMinSeconds", 8.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROXIMITY_PUSH_DELAY_MAX = BUILDER
            .comment("Maximum continuous proximity seconds before a push; capped below by delayMinSeconds.")
            .defineInRange("elemental.water.proximityPush.delayMaxSeconds", 12.0, 0.05, 600.0);

    /** Water projectiles (elemental.water.projectile). */
    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_DAMAGE = BUILDER
            .comment("Damage per water projectile in health points (2 = one heart).")
            .defineInRange("elemental.water.projectile.damage", 2.0, 0.0, 1024.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_SPEED_PER_SECOND = BUILDER
            .comment("Constant water projectile speed in blocks per second, including underwater.")
            .defineInRange("elemental.water.projectile.speedBlocksPerSecond", 16.0, 1.0, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_KNOCKBACK = BUILDER
            .comment("Additional velocity in the projectile's flight direction on an accepted hit.")
            .defineInRange("elemental.water.projectile.knockback", 0.5, 0.0, 3.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_INTERVAL = BUILDER
            .comment("Seconds between shots within a regular projectile phase.")
            .defineInRange("elemental.water.projectile.intervalSeconds", 2.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_DURATION_MIN = BUILDER
            .comment("Minimum seconds per regular projectile phase.",
                    "The duration is sampled once at phase start; the first shot waits intervalSeconds.")
            .defineInRange("elemental.water.projectile.durationMinSeconds", 4.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_DURATION_MAX = BUILDER
            .comment("Maximum seconds per regular projectile phase; capped below by durationMinSeconds.")
            .defineInRange("elemental.water.projectile.durationMaxSeconds", 8.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_LIFETIME = BUILDER
            .comment("Maximum flight duration in seconds; range is speedBlocksPerSecond multiplied by lifetimeSeconds.")
            .defineInRange("elemental.water.projectile.lifetimeSeconds", 5.0, 0.05, 600.0);

    /** Water projectile salvos (elemental.water.salvo). */
    public static final ForgeConfigSpec.DoubleValue WATER_SALVO_PREPARATION = BUILDER
            .comment("Preparation seconds before the first salvo shot; eyes gradually turn red.")
            .defineInRange("elemental.water.salvo.preparationSeconds", 2.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SALVO_DURATION_MIN = BUILDER
            .comment("Minimum salvo firing duration in seconds, excluding preparation.",
                    "Sampled once at phase start; duration continues during sight loss.")
            .defineInRange("elemental.water.salvo.durationMinSeconds", 5.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SALVO_DURATION_MAX = BUILDER
            .comment("Maximum salvo firing duration; capped below by durationMinSeconds.")
            .defineInRange("elemental.water.salvo.durationMaxSeconds", 10.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SALVO_INTERVAL = BUILDER
            .comment("Seconds between salvo shots.")
            .defineInRange("elemental.water.salvo.intervalSeconds", 0.3, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SALVO_SIGHT_TIMEOUT = BUILDER
            .comment("Consecutive seconds without sight before aborting a salvo. Shots pause during sight loss.")
            .defineInRange("elemental.water.salvo.sightTimeoutSeconds", 1.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SALVO_EYE_FADE = BUILDER
            .comment("Seconds for red eyes to fade out after completing or aborting a salvo.")
            .defineInRange("elemental.water.salvo.eyeFadeSeconds", 0.5, 0.05, 60.0);

    /** Water attack selection and rest periods (elemental.water.attack). */
    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_WEIGHT = BUILDER
            .comment("Relative weight of regular projectile phases. Chance = weight / sum of all three attack weights.",
                    "Equal weights give equal chances. 0 disables this type; all weights 0 disable new attacks.")
            .defineInRange("elemental.water.attack.projectileWeight", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_WEIGHT = BUILDER
            .comment("Relative weight of fountain phases; 0 disables this type.")
            .defineInRange("elemental.water.attack.fountainWeight", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SALVO_WEIGHT = BUILDER
            .comment("Relative weight of salvo phases; 0 disables this type.")
            .defineInRange("elemental.water.attack.salvoWeight", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue WATER_ATTACK_PAUSE_MIN = BUILDER
            .comment("Minimum rest seconds after an attack. No new attacks start during rest.",
                    "Rest pauses without eligible players. Projectile attacks end if their chosen target becomes ineligible.",
                    "NoAI pauses both attack and rest timers.")
            .defineInRange("elemental.water.attack.pauseMinSeconds", 2.0, 0.0, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_ATTACK_PAUSE_MAX = BUILDER
            .comment("Maximum rest seconds; capped below by pauseMinSeconds. Both may be 0 to disable rest.")
            .defineInRange("elemental.water.attack.pauseMaxSeconds", 4.0, 0.0, 60.0);

    /** Water fountains (elemental.water.fountain). */
    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_DURATION_MIN = BUILDER
            .comment("Minimum fountain phase duration. Sampled once at phase start.")
            .defineInRange("elemental.water.fountain.durationMinSeconds", 20.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_DURATION_MAX = BUILDER
            .comment("Maximum fountain phase duration; capped below by durationMinSeconds.")
            .defineInRange("elemental.water.fountain.durationMaxSeconds", 30.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_INTERVAL_MIN = BUILDER
            .comment("Minimum independent delay in seconds between fountains for each player and the arena.")
            .defineInRange("elemental.water.fountain.intervalMinSeconds", 0.5, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_INTERVAL_MAX = BUILDER
            .comment("Maximum fountain delay in seconds; capped below by intervalMinSeconds.")
            .defineInRange("elemental.water.fountain.intervalMaxSeconds", 1.0, 0.05, 600.0);

    public static final ForgeConfigSpec.IntValue WATER_FOUNTAIN_ARENA_COUNT = BUILDER
            .comment("Random arena fountains announced per interval, in addition to each player's fountains.")
            .defineInRange("elemental.water.fountain.arenaCount", 3, 1, 16);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_WARNING = BUILDER
            .comment("Fishing-particle warning duration in seconds before a fixed fountain erupts.")
            .defineInRange("elemental.water.fountain.warningSeconds", 1.5, 0.05, 60.0);

    public static final ForgeConfigSpec.IntValue WATER_FOUNTAIN_SIZE = BUILDER
            .comment("Width and depth of a fountain in blocks.")
            .defineInRange("elemental.water.fountain.size", 3, 1, 9);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_HEIGHT = BUILDER
            .comment("Maximum launch height above the water cell, capped at 20 blocks.")
            .defineInRange("elemental.water.fountain.height", 10.0, 0.0, 20.0);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_HIT_HEIGHT = BUILDER
            .comment("Height of the hit area above the water surface.")
            .defineInRange("elemental.water.fountain.hitHeight", 2.0, 0.1, 10.0);

    /** Cosmetic arena rain (elemental.water.fountain.rain). */
    public static final ForgeConfigSpec.BooleanValue WATER_FOUNTAIN_RAIN_ENABLED = BUILDER
            .comment("Enable cosmetic arena rain without changing world weather.")
            .define("elemental.water.fountain.rain.enabled", true);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_RAIN_DELAY = BUILDER
            .comment("Delay in seconds after the first eruption before arena rain starts.")
            .defineInRange("elemental.water.fountain.rain.delaySeconds", 0.5, 0.0, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_RAIN_AFTER = BUILDER
            .comment("Additional rain seconds after the fountain phase ends.")
            .defineInRange("elemental.water.fountain.rain.afterSeconds", 5.0, 0.0, 60.0);

    public static final ForgeConfigSpec.IntValue WATER_FOUNTAIN_RAIN_PARTICLES_PER_SECOND = BUILDER
            .comment("Maximum local raindrops per second and arena.")
            .defineInRange("elemental.water.fountain.rain.particlesPerSecond", 480, 0, 5120);

    /** Water arena discovery, flooding and cleanup (elemental.water.arena). */
    public static final ForgeConfigSpec.IntValue WATER_ARENA_HEIGHT_RANGE = BUILDER
            .comment("Maximum surface-cell height difference above or below the saved spawn position.",
                    "Captured when surface discovery starts.")
            .defineInRange("elemental.water.arena.heightRange", 10, 0, 64);

    public static final ForgeConfigSpec.IntValue WATER_ARENA_STEP_HEIGHT = BUILDER
            .comment("Maximum height difference between horizontally adjacent arena surface cells.",
                    "Captured when surface discovery starts. 0 restricts discovery to one level.")
            .defineInRange("elemental.water.arena.stepHeight", 1, 0, 4);

    public static final ForgeConfigSpec.IntValue WATER_ARENA_SEARCH_BUDGET_PER_SECOND = BUILDER
            .comment("Maximum surface candidates examined per water Sheeper per second.",
                    "Unloaded chunks are deferred without being force-loaded.")
            .defineInRange("elemental.water.arena.searchCandidatesPerSecond", 10240, 20, 327680);

    public static final ForgeConfigSpec.IntValue WATER_ARENA_BLOCK_BUDGET_PER_SECOND = BUILDER
            .comment(
                    "Limits block positions processed per second when placing or removing arena water to reduce server load spikes.")
            .defineInRange("elemental.water.arena.blockUpdatesPerSecond", 10240, 20, 327680);

    public static final ForgeConfigSpec.DoubleValue WATER_ARENA_FLOOD_WAVE_SECONDS = BUILDER
            .comment("Seconds between outward source layers along the connected terrain.",
                    "0 disables layer timing and uses only blockUpdatesPerSecond. Large layers still respect blockUpdatesPerSecond.")
            .defineInRange("elemental.water.arena.floodWaveIntervalSeconds", 0.05, 0.0, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_ARENA_DRAIN_WAVE_SECONDS = BUILDER
            .comment("Seconds between cleanup layers, from the outermost descendants back toward the spawn.",
                    "0 disables layer timing. Unloaded cells are retried without blocking loaded cleanup.")
            .defineInRange("elemental.water.arena.drainWaveIntervalSeconds", 0.1, 0.0, 10.0);

    /** Adult Sheeper detection, observation and self-explosions (sheeper). */
    public static final ForgeConfigSpec.DoubleValue DETECTION_RADIUS = BUILDER
            .comment("Maximum distance in blocks for detecting and pursuing players.")
            .defineInRange("sheeper.detectionRadius", 16.0, 1.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue OBSERVATION_ANGLE = BUILDER
            .comment("Total observation angle in degrees, centered on the player's look direction.")
            .defineInRange("sheeper.observationAngle", 120.0, 1.0, 360.0);

    public static final ForgeConfigSpec.DoubleValue FUSE_DISTANCE = BUILDER
            .comment("Maximum distance in blocks for starting and maintaining the fuse.")
            .defineInRange("sheeper.fuseDistance", 3.0, 0.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue FUSE_DURATION = BUILDER
            .comment("Fuse duration in seconds for stealth and fatal-fire self-explosions.",
                    "Fatal-fire duration is captured when the killing hit is accepted.")
            .defineInRange("sheeper.fuseDuration", 1.5, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue ADULT_EXPLOSION_STRENGTH = BUILDER
            .comment("Explosion strength of adult Sheeper.",
                    "Default: 3.0, matching a normal Creeper.")
            .defineInRange("sheeper.adultExplosionStrength", 3.0, 0.1, 5.0);

    /** Adult retaliation (sheeper.retaliation). */
    public static final ForgeConfigSpec.DoubleValue RETALIATION_SPEED_MULTIPLIER = BUILDER
            .comment("Movement-speed multiplier while pursuing the first attacking player.")
            .defineInRange("sheeper.retaliation.speedMultiplier", 1.5, 0.1, 3.0);

    public static final ForgeConfigSpec.DoubleValue RETALIATION_FUSE_DURATION = BUILDER
            .comment("Minimum ignition time in seconds before contact can trigger a retaliation burst.",
                    "Ignition starts within melee range and resets when the target leaves that range.")
            .defineInRange("sheeper.retaliation.fuseDuration", 0.5, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue RETALIATION_PAUSE = BUILDER
            .comment("Pause in seconds after a retaliation burst before pursuing the same player again.")
            .defineInRange("sheeper.retaliation.pauseSeconds", 0.75, 0.0, 1.0);

    /** Retaliation bursts (sheeper.burst). */
    public static final ForgeConfigSpec.DoubleValue BURST_RADIUS = BUILDER
            .comment("Radius in blocks of a Sheeper's non-destructive burst.")
            .defineInRange("sheeper.burst.radius", 3.0, 1.0, 8.0);

    public static final ForgeConfigSpec.DoubleValue BURST_DAMAGE = BUILDER
            .comment("Base burst damage in health points (1 = half a heart). 0 disables damage.",
                    "Normal armor, damage immunity, and explosion protection still apply.")
            .defineInRange("sheeper.burst.damage", 2.0, 0.0, 40.0);

    public static final ForgeConfigSpec.DoubleValue BURST_KNOCKBACK = BUILDER
            .comment("Burst knockback strength, reduced by distance, cover, and knockback resistance.",
                    "0 disables the additional burst knockback.")
            .defineInRange("sheeper.burst.knockback", 1.2, 0.0, 3.0);

    /** Natural spawning (spawning). */
    public static final ForgeConfigSpec.DoubleValue NATURAL_SPAWN_CHANCE = BUILDER
            .comment("Percentage chance to replace each naturally spawning sheep with a Sheeper.",
                    "Applies during world generation and later natural spawning.",
                    "0 disables replacement; 100 replaces every naturally spawning sheep.")
            .defineInRange("spawning.naturalSpawnChance", 5.0, 0.0, 100.0);

    /** Baby Sheeper movement, attacks and explosions (baby). */
    public static final ForgeConfigSpec.DoubleValue BABY_SPEED_BOOST_PERCENT = BUILDER
            .comment("Additional movement speed for baby Sheeper, in percent.",
                    "0 gives babies the same movement speed as adults.")
            .defineInRange("baby.speedBoostPercent", 25.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue BABY_BUMP_KNOCKBACK = BUILDER
            .comment("Knockback strength when a baby Sheeper bumps a player.",
                    "0 disables the knockback.")
            .defineInRange("baby.bumpKnockback", 0.3, 0.0, 2.0);

    public static final ForgeConfigSpec.DoubleValue BABY_ATTACK_PAUSE_MIN = BUILDER
            .comment("Minimum pause in seconds after a baby Sheeper retreats.")
            .defineInRange("baby.attackPauseMin", 3.0, 0.0, 60.0);

    public static final ForgeConfigSpec.DoubleValue BABY_ATTACK_PAUSE_MAX = BUILDER
            .comment("Maximum pause in seconds after a baby Sheeper retreats.",
                    "Equal minimum and maximum values produce a fixed pause.",
                    "If the maximum is below the minimum, the pause is fixed at the minimum.")
            .defineInRange("baby.attackPauseMax", 8.0, 0.0, 60.0);

    public static final ForgeConfigSpec.DoubleValue BABY_EXPLOSION_STRENGTH = BUILDER
            .comment("Explosion strength when a baby Sheeper takes damage.",
                    "Default: 0.4; Adult explosion strength is configured separately.",
                    "Values below 0.4 may cause no noticeable player damage or block destruction.",
                    "The actual effect depends on distance and surrounding blocks.")
            .defineInRange("baby.explosionStrength", 0.4, 0.1, 5.0);

    /** Breeding (breeding). */
    public static final ForgeConfigSpec.DoubleValue MIXED_BREEDING_CHANCE = BUILDER
            .comment("Percentage chance of Sheeper offspring from a mixed sheep/Sheeper pair.",
                    "Pairs of two Sheeper or two normal sheep are unaffected.")
            .defineInRange("breeding.mixedSheeperChance", 50.0, 0.0, 100.0);

    /** Raw mutton effects (mutton.raw). */
    public static final ForgeConfigSpec.DoubleValue RAW_MUTTON_DURATION = BUILDER
            .comment("Raw mutton effect duration in seconds. 0 disables its eating effect.",
                    "Duration is captured when food is consumed; existing countdowns are retained.")
            .defineInRange("mutton.raw.durationSeconds", 6.0, 0.0, 600.0);

    public static final ForgeConfigSpec.DoubleValue RAW_MUTTON_INTERVAL = BUILDER
            .comment("Seconds between raw mutton internal explosions. The first waits one full interval.",
                    "Minecraft's normal damage immunity still applies at short intervals.")
            .defineInRange("mutton.raw.internalExplosionIntervalSeconds", 1.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue RAW_MUTTON_DAMAGE = BUILDER
            .comment("Base damage per raw mutton internal explosion in health points (1 = half a heart).",
                    "Bypasses armor and may be lethal. 0 disables damage, but keeps other effects.")
            .defineInRange("mutton.raw.internalExplosionDamage", 1.0, 0.0, 40.0);

    public static final ForgeConfigSpec.DoubleValue RAW_MUTTON_PUSH = BUILDER
            .comment("Random horizontal impulse per raw mutton internal explosion. 0 disables the impulse.")
            .defineInRange("mutton.raw.knockback", 0.12, 0.0, 2.0);

    /** Cooked mutton effects (mutton.cooked). */
    public static final ForgeConfigSpec.DoubleValue COOKED_MUTTON_DURATION = BUILDER
            .comment("Cooked mutton effect duration in seconds. 0 disables damage and the speed bonus.",
                    "Duration is captured when food is consumed; existing countdowns are retained.")
            .defineInRange("mutton.cooked.durationSeconds", 10.0, 0.0, 600.0);

    public static final ForgeConfigSpec.DoubleValue COOKED_MUTTON_INTERVAL = BUILDER
            .comment("Seconds between cooked mutton internal explosions. The first waits one full interval.",
                    "Minecraft's normal damage immunity still applies at short intervals.")
            .defineInRange("mutton.cooked.internalExplosionIntervalSeconds", 2.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue COOKED_MUTTON_DAMAGE = BUILDER
            .comment("Base damage per cooked mutton internal explosion in health points (1 = half a heart).",
                    "Bypasses armor and may be lethal. 0 disables damage, but keeps other effects.")
            .defineInRange("mutton.cooked.internalExplosionDamage", 1.0, 0.0, 40.0);

    public static final ForgeConfigSpec.DoubleValue COOKED_MUTTON_SPEED_PERCENT = BUILDER
            .comment("Additional movement speed in percent during the cooked mutton effect. 0 disables the bonus.")
            .defineInRange("mutton.cooked.speedBoostPercent", 50.0, 0.0, 200.0);

    /** Unsafe mutton cooking (mutton.cooking). */
    public static final ForgeConfigSpec.BooleanValue UNSAFE_COOKING_ENABLED = BUILDER
            .comment("Whether heating raw mutton in furnaces/campfires causes an explosion.",
                    "When disabled, their cooking recipes complete normally.")
            .define("mutton.cooking.explosionsEnabled", true);

    public static final ForgeConfigSpec.DoubleValue COOKING_WARNING_SECONDS = BUILDER
            .comment("Warning time in seconds before unsafe cooking explodes.",
                    "Limited to 9.95 seconds so the default 10-second furnace recipe cannot finish first.",
                    "Custom data-pack recipes must also remain longer than this warning.")
            .defineInRange("mutton.cooking.warningSeconds", 3.0, 0.05, 9.95);

    public static final ForgeConfigSpec.DoubleValue COOKING_EXPLOSION_STRENGTH = BUILDER
            .comment("Unsafe-cooking explosion strength. 0 suppresses the blast, but still removes the appliance",
                    "and consumes one piece. Disable explosionsEnabled to permit safe cooking instead.")
            .defineInRange("mutton.cooking.explosionStrength", 1.0, 0.0, 8.0);

    /** Dropped mutton fire explosions (mutton.fire). */
    public static final ForgeConfigSpec.BooleanValue MUTTON_FIRE_ENABLED = BUILDER
            .comment("Whether dropped raw mutton detonates on fire/lava contact.",
                    "When disabled, it behaves like an ordinary dropped item and can burn normally.")
            .define("mutton.fire.explosionsEnabled", true);

    public static final ForgeConfigSpec.DoubleValue MUTTON_FIRE_STRENGTH = BUILDER
            .comment("Dropped-mutton fire explosion strength. 0 suppresses the blast but still consumes the stack.")
            .defineInRange("mutton.fire.explosionStrength", 1.0, 0.0, 8.0);

    /** Sheeper wool explosions and fuses (wool). */
    public static final ForgeConfigSpec.DoubleValue WOOL_MINING_STRENGTH = BUILDER
            .comment(
                    "Explosion strength for unsafe wool/carpet mining. 0 disables the blast; unsafe mining still drops nothing.")
            .defineInRange("wool.unsafeMiningExplosionStrength", 1.0, 0.0, 8.0);

    public static final ForgeConfigSpec.DoubleValue WOOL_PRIMED_STRENGTH = BUILDER
            .comment("Destructive explosion strength of primed wool. 0 suppresses the blast.")
            .defineInRange("wool.primedExplosionStrength", 4.0, 0.0, 16.0);

    public static final ForgeConfigSpec.DoubleValue WOOL_FUSE_SECONDS = BUILDER
            .comment("Normal wool fuse in seconds, captured when ignited. Explosion chains shorten this fuse.")
            .defineInRange("wool.fuseSeconds", 4.0, 0.05, 120.0);

    /** Sheeper carpet step explosions (carpet). */
    public static final ForgeConfigSpec.DoubleValue CARPET_RADIUS = BUILDER
            .comment("Radius in blocks of a carpet step explosion. Walls shield entities.")
            .defineInRange("carpet.radius", 2.0, 1.0, 8.0);

    public static final ForgeConfigSpec.DoubleValue CARPET_DAMAGE = BUILDER
            .comment("Base step-explosion damage in health points (2 = one heart), reduced by cover.",
                    "Normal armor, damage immunity, and explosion protection apply. 0 disables damage.")
            .defineInRange("carpet.damage", 2.0, 0.0, 40.0);

    public static final ForgeConfigSpec.DoubleValue CARPET_KNOCKBACK = BUILDER
            .comment("Horizontal step-explosion impulse, reduced by distance, cover, and knockback resistance.",
                    "0 disables the additional horizontal impulse.")
            .defineInRange("carpet.knockback", 1.0, 0.0, 3.0);

    public static final ForgeConfigSpec.DoubleValue CARPET_UPWARD_PUSH = BUILDER
            .comment("Upward step-explosion impulse, reduced by distance, cover, and knockback resistance.",
                    "0 disables the additional upward impulse. Ordinary fall damage still applies.")
            .defineInRange("carpet.upwardPush", 0.8, 0.0, 2.0);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /** Shared definitions for elemental health and size options. */
    private static ForgeConfigSpec.DoubleValue elementalHealth(String element, double defaultHealth) {
        return BUILDER.comment("Maximum health of the " + element + " Sheeper in health points (2 = one heart).",
                "Changes apply to existing bosses while preserving their percentage of remaining health.")
                .defineInRange("elemental." + element + ".maxHealth", defaultHealth, 1.0, 1024.0);
    }

    private static ForgeConfigSpec.DoubleValue elementalSize(String element) {
        return BUILDER.comment("Size multiplier for the " + element + " Sheeper, including its hitbox.",
                "1.0 = normal sheep/Sheeper size; 2.0 = twice that size.",
                "The server synchronizes this value to clients; changes apply to existing bosses.")
                .defineInRange("elemental." + element + ".size", 2.0, 0.5, 4.0);
    }

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
