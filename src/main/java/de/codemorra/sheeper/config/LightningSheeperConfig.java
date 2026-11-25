package de.codemorra.sheeper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Installation-wide lightning Sheeper settings in their own common
 * configuration file.
 */
public final class LightningSheeperConfig {
    public static final String FILE_NAME = "sheeper/lightning.toml";
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** Lightning boss settings and combat bounds. */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_MAX_HEALTH = BUILDER
            .comment("Solo maximum health in health points (2 = one heart).",
                    "Multiplayer scaling adds health up to 1024 and preserves the remaining health percentage.")
            .defineInRange("boss.maxHealth", 300.0, 1.0, 1024.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_HEALTH_PER_ADDITIONAL_PLAYER = BUILDER
            .comment("Extra percentage of solo health per additional player present for at least 3 seconds.",
                    "The highest simultaneous player count applies until the boss fully recovers.")
            .defineInRange("healthScaling.additionalPlayerPercent", 50.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SIZE = ElementalConfigValues.elementalSize(BUILDER,
            "lightning");

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_COMBAT_RADIUS = BUILDER
            .comment("Horizontal lightning Sheeper arena and combat radius around its saved spawn position.")
            .defineInRange("arena.combatRadius", 50.0, 1.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_MOVEMENT_EDGE_DISTANCE = BUILDER
            .comment("Minimum horizontal distance in blocks between the boss's body and the arena edge.",
                    "0 keeps only body clearance. Teleports stop if the remaining area is too small.")
            .defineInRange("movement.edgeDistance", 2.0, 0.0, 16.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_ARENA_HEIGHT = BUILDER
            .comment("Blocks from the boss's spawn point up to the top of the storm arena.",
                    "Applies to new arenas.")
            .defineInRange("arena.height", 24, 4, 64);

    public static final ForgeConfigSpec.IntValue LIGHTNING_ARENA_DEPTH = BUILDER
            .comment("Blocks from the boss's spawn point down to the arena floor.",
                    "Applies to new arenas.")
            .defineInRange("arena.depth", 24, 0, 32);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_BOUNDARY_SLOWDOWN_WIDTH = BUILDER
            .comment("Blocks on each side of the storm boundary where crossing is slowed. 0 disables the slowdown.",
                    "Combat entry uses the inner edge. Width is capped at half the arena radius.")
            .defineInRange("boundary.slowdownWidth", 1.5, 0.0, 4.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_BOUNDARY_SLOWDOWN_STRENGTH = BUILDER
            .comment("Fraction of sideways crossing speed lost at the storm boundary. 0 disables the slowdown.",
                    "Movement along the boundary and through the top or bottom stays free.")
            .defineInRange("boundary.slowdownStrength", 0.35, 0.0, 0.75);

    /** Lightning arena buildup (arenaBuild). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PREPARATION_DURATION = BUILDER
            .comment("Seconds for the storm front to spread from the center to the arena edge. 0 skips the buildup.")
            .defineInRange("arenaBuild.durationSeconds", 5.0, 0.0, 30.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ARENA_DRAIN_DURATION = BUILDER
            .comment("Seconds for the storm front to retreat and fade after the boss is defeated or removed.")
            .defineInRange("arena.drainDurationSeconds", 8.0, 1.0, 30.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_COMBAT_ENTRY_DELAY = BUILDER
            .comment("Seconds after the first player enters the ready arena before combat hazards begin.")
            .defineInRange("arena.entryDelaySeconds", 2.0, 0.0, 10.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_EMPTY_ARENA_GRACE = BUILDER
            .comment("Seconds the arena must stay empty before the boss starts healing.",
                    "Returning players stop recovery immediately.")
            .defineInRange("arenaRecovery.graceSeconds", 10.0, 0.0, 30.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_EMPTY_ARENA_REGENERATION = BUILDER
            .comment("Percentage of maximum health restored per second after the grace period.",
                    "Full recovery resets the encounter.")
            .defineInRange("arenaRecovery.healthPercentPerSecond", 5.0, 0.1, 25.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_PREPARATION_WAVE_COUNT = BUILDER
            .comment("Number of outward preparation waves before storm awakening begins.")
            .defineInRange("arena.waveCount", 3, 1, 16);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PREPARATION_WAVE_INTERVAL = BUILDER
            .comment("Seconds between the starts of successive preparation waves; waves may overlap.")
            .defineInRange("arena.waveIntervalSeconds", 1.0, 0.0, 60.0);

    /**
     * Local weather, awakening and ambient edge strikes
     * (storm).
     */
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_STORM_ENABLED = BUILDER
            .comment("Local arena rain and thunder, including normal rain effects on exposed entities and blocks.")
            .define("storm.enabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STORM_AWAKENING = BUILDER
            .comment("Protected storm awakening seconds after the last preparation wave, before the first attack.")
            .defineInRange("storm.awakeningSeconds", 3.0, 0.0, 60.0);

    public static final ForgeConfigSpec.BooleanValue LIGHTNING_STORM_EDGE_ENABLED = BUILDER
            .comment("Randomly clustered, announced lightning strikes at the outer arena edge during combat.")
            .define("storm.edgeLightning", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STORM_EDGE_DAMAGE = BUILDER
            .comment("Edge strike damage in health points (2 = one heart). Awakening strikes never deal damage.")
            .defineInRange("storm.edgeDamage", 2.0, 0.0, 1024.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STORM_EDGE_WARNING = BUILDER
            .comment("Warning seconds before a combat edge strike.")
            .defineInRange("storm.edgeWarningSeconds", 1.5, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STORM_RUMBLE_VOLUME = BUILDER
            .comment("Ambient thunder volume for players inside the arena. 0 disables rumbling.")
            .defineInRange("storm.rumbleVolume", 0.6, 0.0, 4.0);

    /** One flat knockback per preparation wave (spawnPush). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SPAWN_PUSH_RADIUS = BUILDER
            .comment("Maximum player distance from the boss for the spawn wave push. 0 disables it.")
            .defineInRange("spawnPush.radius", 15.0, 0.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SPAWN_PUSH_DELAY = BUILDER
            .comment("Seconds after each expanding preparation wave reaches a player before that wave's one-time push.",
                    "Leaving the radius cancels the push. Pending pushes survive the end of preparation and loading.")
            .defineInRange("spawnPush.delaySeconds", 0.15, 0.0, 60.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SPAWN_PUSH_STRENGTH = BUILDER
            .comment("Horizontal spawn wave velocity away from the arena center; deals no direct damage.")
            .defineInRange("spawnPush.horizontalStrength", 1.5, 0.0, 3.9);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SPAWN_PUSH_UPWARD_STRENGTH = BUILDER
            .comment("Upward spawn wave velocity. Replaces existing player momentum.",
                    "The default rises about two blocks at normal gravity, avoiding fall damage on same-height landings.")
            .defineInRange("spawnPush.upwardStrength", 0.55, 0.0, 1.0);

    /** Lightning teleport movement (teleport). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TELEPORT_INTERVAL_MIN = BUILDER
            .comment("Minimum seconds the boss waits after a teleport when a player is in the arena.")
            .defineInRange("teleport.intervalMinSeconds", 1.5, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TELEPORT_INTERVAL_MAX = BUILDER
            .comment("Maximum seconds the boss waits after a teleport when a player is in the arena.",
                    "If set below the minimum, the minimum is used.")
            .defineInRange("teleport.intervalMaxSeconds", 3.0, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_IDLE_INTERVAL_MIN = BUILDER
            .comment("Minimum seconds the boss waits after a teleport when the arena is empty.")
            .defineInRange("movement.idle.intervalMinSeconds", 2.0, 0.1, 15.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_IDLE_INTERVAL_MAX = BUILDER
            .comment("Maximum seconds the boss waits after a teleport when the arena is empty.",
                    "If set below the minimum, the minimum is used.")
            .defineInRange("movement.idle.intervalMaxSeconds", 5.0, 0.1, 15.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_MOVEMENT_ORBIT_DISTANCE = BUILDER
            .comment("Preferred horizontal distance from the selected player when choosing a teleport destination.")
            .defineInRange("movement.orbitDistance", 16.0, 1.0, 64.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TELEPORT_WARNING = BUILDER
            .comment("Seconds of warning sparks and time for the short walk before teleporting.",
                    "0 skips the warning and the walk.")
            .defineInRange("teleport.warningSeconds", 0.75, 0.0, 2.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TELEPORT_PLAYER_DISTANCE = BUILDER
            .comment("Minimum distance in blocks from the teleport destination to a living, non-spectator player.")
            .defineInRange("teleport.playerDistance", 4.0, 0.0, 32.0);

    /** Random arena strikes during combat (ambientLightning). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AMBIENT_INTERVAL = BUILDER
            .comment("Seconds between groups of random arena strikes.")
            .defineInRange("ambientLightning.intervalSeconds", 0.5, 0.25, 5.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_AMBIENT_STRIKE_COUNT = BUILDER
            .comment("Random strike locations per interval. 0 disables recurring strikes.",
                    "Strikes are cosmetic when no players are participating.")
            .defineInRange("ambientLightning.strikesPerInterval", 2, 0, 4);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AMBIENT_WARNING = BUILDER
            .comment("Seconds that branching ground lines warn of an incoming strike.")
            .defineInRange("ambientLightning.warningSeconds", 1.75, 0.25, 3.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AMBIENT_CENTER_RADIUS = BUILDER
            .comment("Radius in blocks of the inner ring where a strike deals full damage.")
            .defineInRange("ambientLightning.centerRadius", 1.0, 0.25, 4.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AMBIENT_EDGE_WIDTH = BUILDER
            .comment("Width in blocks beyond the inner ring that grants Speed I and deals reduced damage.")
            .defineInRange("ambientLightning.edgeWidth", 1.5, 0.25, 5.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AMBIENT_HIT_HEIGHT = BUILDER
            .comment("How many blocks above the marked surface a strike can hit. Solid cover blocks the hit.")
            .defineInRange("ambientLightning.hitHeight", 3.0, 0.5, 8.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AMBIENT_CENTER_DAMAGE = BUILDER
            .comment("Damage in the inner ring, in health points before armor and difficulty scaling.")
            .defineInRange("ambientLightning.centerDamage", 6.0, 0.0, 20.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AMBIENT_WATER_DAMAGE = BUILDER
            .comment("Damage in health points to players touching connected water when lightning strikes it.",
                    "Only water within the arena conducts. 0 disables water damage.")
            .defineInRange("ambientLightning.waterDamage", 10.0, 0.0, 24.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AMBIENT_EDGE_DAMAGE_PERCENT = BUILDER
            .comment("Percentage of the inner ring's damage dealt in the outer ring.")
            .defineInRange("ambientLightning.edgeDamagePercent", 25.0, 0.0, 75.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AMBIENT_SPEED_DURATION = BUILDER
            .comment("Seconds of Speed I after an outer-ring hit. Entering the lingering ground sparks grants half.",
                    "0 disables the speed bonus.")
            .defineInRange("ambientLightning.speedBonusSeconds", 4.0, 0.0, 8.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AMBIENT_GROUND_SPARK_DURATION = BUILDER
            .comment(
                    "Seconds the impact area keeps sparking and grants Speed I to new entrants. 0 disables the aftereffect.")
            .defineInRange("ambientLightning.groundSparkSeconds", 2.0, 0.0, 4.0);

    /** Local gusts that share a changing wind direction (wind). */
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_WIND_ENABLED = BUILDER
            .comment("Enables continuous wind in formed arenas and stronger gusts during combat.")
            .define("wind.enabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_WIND_DIRECTION_MIN = BUILDER
            .comment("Minimum seconds between changes in wind direction.")
            .defineInRange("wind.directionMinSeconds", 8.0, 3.0, 30.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_WIND_DIRECTION_MAX = BUILDER
            .comment("Maximum seconds between changes in wind direction.",
                    "If set below the minimum, the minimum is used.")
            .defineInRange("wind.directionMaxSeconds", 14.0, 3.0, 30.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_WIND_LARGE_CHANCE = BUILDER
            .comment("Chance in percent that a combat gust is wide and adds stronger drift and resistance.")
            .defineInRange("wind.largeGustChancePercent", 12, 0, 30);

    /** Lightning attack selection (attack). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_WEIGHT = BUILDER
            .comment("Relative weight of lightning storm phases; 0 disables this type.")
            .defineInRange("attack.strikeWeight", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_WEIGHT = BUILDER
            .comment("Relative weight of moving storm fronts; 0 disables this type.")
            .defineInRange("attack.frontWeight", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_WEIGHT = BUILDER
            .comment("Relative weight of expanding electric rings; 0 disables this type.")
            .defineInRange("attack.ringWeight", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ATTACK_PAUSE_MIN = BUILDER
            .comment("Minimum rest seconds between attacks. Normal teleports are allowed during rest.",
                    "An announced teleport finishes before the next attack. NoAI and an empty arena pause the timer.")
            .defineInRange("attack.pauseMinSeconds", 4.0, 0.05, 30.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ATTACK_PAUSE_MAX = BUILDER
            .comment("Maximum rest seconds between special attacks. If set below the minimum, the minimum is used.")
            .defineInRange("attack.pauseMaxSeconds", 7.0, 0.05, 30.0);

    /** Targeted lightning rows (lineAttack). */
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_LINE_ENABLED = BUILDER
            .comment("Enables the special attack that fires lightning rows toward arena players.")
            .define("lineAttack.enabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_LINE_COOLDOWN = BUILDER
            .comment("Combat seconds after the last row finishes before this special attack is ready again.")
            .defineInRange("lineAttack.cooldownSeconds", 20.0, 1.0, 120.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_LINE_SHOTS = BUILDER
            .comment("Number of lightning rows fired each time the attack is used.")
            .defineInRange("lineAttack.shotCount", 6, 1, 16);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_LINE_SOLO_INTERVAL = BUILDER
            .comment("Seconds between rows when one player is participating.")
            .defineInRange("lineAttack.soloIntervalSeconds", 1.5, 0.25, 5.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_LINE_MULTIPLAYER_INTERVAL = BUILDER
            .comment("Seconds between rows when several players are participating.")
            .defineInRange("lineAttack.multiplayerIntervalSeconds", 0.5, 0.25, 5.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_LINE_SPEED = BUILDER
            .comment("Speed at which each row travels toward the arena edge, in blocks per second.")
            .defineInRange("lineAttack.speedBlocksPerSecond", 18.0, 8.0, 36.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_LINE_DAMAGE = BUILDER
            .comment("Health points dealt to a player hit by one row before armor and difficulty scaling.",
                    "Each row can damage a player once. 0 disables damage.")
            .defineInRange("lineAttack.damage", 5.0, 0.0, 24.0);

    /** Lightning storm phase (strike). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_DURATION_MIN = BUILDER
            .comment("Minimum lightning phase duration in seconds. Sampled at phase start.")
            .defineInRange("strike.durationMinSeconds", 20.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_DURATION_MAX = BUILDER
            .comment("Maximum lightning phase duration in seconds; capped below by durationMinSeconds.",
                    "New centers stop early enough for all three warned rounds to finish within the phase.")
            .defineInRange("strike.durationMaxSeconds", 30.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_INTERVAL_MIN = BUILDER
            .comment("Minimum independent interval in seconds between central warnings per player and for the arena.")
            .defineInRange("strike.intervalMinSeconds", 0.5, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_INTERVAL_MAX = BUILDER
            .comment("Maximum central warning interval in seconds; capped below by intervalMinSeconds.")
            .defineInRange("strike.intervalMaxSeconds", 1.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_PLAYER_COOLDOWN = BUILDER
            .comment("Minimum seconds after a player's third round finishes before targeting that player again.",
                    "The complete three-round sequence is reserved first; the usual interval and warning follow this cooldown.")
            .defineInRange("strike.playerCooldownSeconds", 5.0, 0.0, 600.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_STRIKE_ARENA_COUNT = BUILDER
            .comment(
                    "Random arena centers announced per interval in addition to player centers. 0 disables random centers.")
            .defineInRange("strike.arenaCount", 3, 0, 16);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_WARNING = BUILDER
            .comment("Seconds between marking a fixed strike position and impact.",
                    "Unloaded positions are cancelled. Each center captures the warning duration for all three rounds.")
            .defineInRange("strike.warningSeconds", 1.5, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_RADIUS = BUILDER
            .comment("Horizontal damage radius in blocks, shown by the warning ring.")
            .defineInRange("strike.radius", 1.5, 0.1, 16.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_HEIGHT = BUILDER
            .comment(
                    "Damage height above the marked surface in blocks. Solid cover between impact and player blocks damage.")
            .defineInRange("strike.height", 3.0, 0.1, 32.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_DAMAGE = BUILDER
            .comment("Damage per strike in health points (2 = one heart). 0 disables damage.",
                    "Normal armor and damage immunity apply. Strikes do not ignite or transform anything.")
            .defineInRange("strike.damage", 6.0, 0.0, 1024.0);

    /**
     * Two outward lightning rounds around each center
     * (strike.followup).
     */
    public static final ForgeConfigSpec.IntValue LIGHTNING_FOLLOWUP_INNER_COUNT_MIN = BUILDER
            .comment("Minimum bolts in the first surrounding round. Announced as the central bolt hits.")
            .defineInRange("strike.followup.innerCountMin", 3, 1, 16);

    public static final ForgeConfigSpec.IntValue LIGHTNING_FOLLOWUP_INNER_COUNT_MAX = BUILDER
            .comment("Maximum bolts in the first surrounding round; capped below by innerCountMin.")
            .defineInRange("strike.followup.innerCountMax", 4, 1, 16);

    public static final ForgeConfigSpec.IntValue LIGHTNING_FOLLOWUP_OUTER_COUNT_MIN = BUILDER
            .comment("Minimum bolts in the outer round. Announced as the first surrounding round hits.")
            .defineInRange("strike.followup.outerCountMin", 5, 1, 16);

    public static final ForgeConfigSpec.IntValue LIGHTNING_FOLLOWUP_OUTER_COUNT_MAX = BUILDER
            .comment("Maximum bolts in the outer round; capped below by outerCountMin.")
            .defineInRange("strike.followup.outerCountMax", 7, 1, 16);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FOLLOWUP_INNER_RADIUS_MIN = BUILDER
            .comment("Minimum first-round distance from the center in blocks.")
            .defineInRange("strike.followup.innerRadiusMin", 3.0, 0.1, 32.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FOLLOWUP_INNER_RADIUS_MAX = BUILDER
            .comment("Maximum first-round distance; capped below by innerRadiusMin.")
            .defineInRange("strike.followup.innerRadiusMax", 4.0, 0.1, 32.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FOLLOWUP_OUTER_RADIUS_MIN = BUILDER
            .comment("Minimum outer-round distance; kept beyond the first round's maximum distance.")
            .defineInRange("strike.followup.outerRadiusMin", 6.0, 0.1, 64.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FOLLOWUP_OUTER_RADIUS_MAX = BUILDER
            .comment("Maximum outer-round distance; capped below by the effective outerRadiusMin.")
            .defineInRange("strike.followup.outerRadiusMax", 8.0, 0.1, 64.0);

    /**
     * Residual electrical zones at central impacts
     * (strike.aftershock).
     */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AFTERSHOCK_AFTER = BUILDER
            .comment(
                    "Seconds electrical zones remain after the storm phase ends. During the phase all central zones stay active.")
            .defineInRange("strike.aftershock.afterSeconds", 5.0, 0.0, 120.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AFTERSHOCK_RADIUS = BUILDER
            .comment("Radius of the circular crackling zone in blocks.")
            .defineInRange("strike.aftershock.radius", 1.25, 0.1, 8.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AFTERSHOCK_HEIGHT = BUILDER
            .comment("Damage height above the zone in blocks; solid cover blocks damage.")
            .defineInRange("strike.aftershock.height", 1.0, 0.1, 8.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AFTERSHOCK_DAMAGE = BUILDER
            .comment("Damage per zone pulse in health points (1 = half a heart), before armor. 0 disables damage.")
            .defineInRange("strike.aftershock.damage", 1.0, 0.0, 40.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AFTERSHOCK_INTERVAL = BUILDER
            .comment("Seconds between zone damage pulses per player. Overlapping zones share the cooldown.",
                    "Zones expire during recovery and without participants; NoAI clears them.")
            .defineInRange("strike.aftershock.intervalSeconds", 1.0, 0.05, 60.0);

    /** Lightning attack audio (strike.sound). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_THUNDER_VOLUME = BUILDER
            .comment("Thunder volume per bolt. 0 disables this sound.")
            .defineInRange("strike.sound.thunderVolume", 0.6, 0.0, 4.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_STRIKE_IMPACT_VOLUME = BUILDER
            .comment("Electrical impact volume per bolt. 0 disables this sound.")
            .defineInRange("strike.sound.impactVolume", 0.8, 0.0, 4.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AFTERSHOCK_VOLUME = BUILDER
            .comment("Local crackling volume while zones are active. 0 disables this sound.")
            .defineInRange("strike.sound.crackleVolume", 0.2, 0.0, 4.0);

    /** Outward lightning lanes (front). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_DURATION_MIN = BUILDER
            .comment("Minimum lightning lane phase duration in seconds, sampled at phase start.")
            .defineInRange("front.durationMinSeconds", 12.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_DURATION_MAX = BUILDER
            .comment("Maximum phase duration; capped below by durationMinSeconds.",
                    "New lanes start only when their complete path and final warning fit before phase end.")
            .defineInRange("front.durationMaxSeconds", 18.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_SPAWN_INTERVAL_MIN = BUILDER
            .comment("Minimum seconds between starting new lanes. The first lane starts immediately.")
            .defineInRange("front.spawnIntervalMinSeconds", 1.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_SPAWN_INTERVAL_MAX = BUILDER
            .comment("Maximum seconds between new lanes; capped below by spawnIntervalMinSeconds.")
            .defineInRange("front.spawnIntervalMaxSeconds", 1.5, 0.05, 60.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_FRONT_MAX_ACTIVE = BUILDER
            .comment("Maximum simultaneously active lanes, including their pending impacts.")
            .defineInRange("front.maxActiveLanes", 4, 1, 16);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_STEP_DISTANCE = BUILDER
            .comment("Forward distance in blocks between successive warnings along a lane's fixed centerline.")
            .defineInRange("front.stepDistance", 3.0, 0.5, 16.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_STEP_INTERVAL = BUILDER
            .comment(
                    "Seconds between successive warnings on each lane; speed is stepDistance divided by this interval.")
            .defineInRange("front.stepIntervalSeconds", 0.25, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_WARNING = BUILDER
            .comment("Seconds from marking a lane position to impact. Positions never follow players.")
            .defineInRange("front.warningSeconds", 0.75, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_SIDE_OFFSET = BUILDER
            .comment("Maximum random sideways offset in blocks from the fixed centerline for each impact.")
            .defineInRange("front.sideOffset", 2.5, 0.0, 16.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_DIRECTION_GAP = BUILDER
            .comment("Minimum angle in degrees between active lanes. Directions are otherwise unrestricted.",
                    "If no suitable direction is found, no new lane starts during that interval.")
            .defineInRange("front.directionGapDegrees", 45.0, 0.0, 180.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_RADIUS = BUILDER
            .comment("Damage radius in blocks for each lane impact, shown by its warning.")
            .defineInRange("front.radius", 1.0, 0.1, 16.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_HEIGHT = BUILDER
            .comment("Damage height above each marked surface in blocks; solid cover blocks damage.")
            .defineInRange("front.height", 3.0, 0.1, 32.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FRONT_DAMAGE = BUILDER
            .comment(
                    "Damage per lane in health points (2 = one heart). Each player can be damaged once per lane. 0 disables damage.",
                    "Uses lightning strike sounds; lane impacts produce no follow-up rounds or residual zones.")
            .defineInRange("front.damage", 2.0, 0.0, 1024.0);

    /** Direct teleports followed by individual expanding rings (ring). */
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_RING_ENABLED = BUILDER
            .comment("Enables the special attack with rapid teleports and expanding ground rings.")
            .define("ring.enabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_COOLDOWN = BUILDER
            .comment("Combat seconds after the last ring finishes before the ring attack is ready again.")
            .defineInRange("ring.cooldownSeconds", 20.0, 1.0, 120.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_RING_TELEPORT_COUNT_MIN = BUILDER
            .comment("Minimum rapid teleport attempts before charging. Unsafe destinations are skipped.")
            .defineInRange("ring.teleportCountMin", 5, 0, 32);

    public static final ForgeConfigSpec.IntValue LIGHTNING_RING_TELEPORT_COUNT_MAX = BUILDER
            .comment("Maximum rapid teleport attempts; capped below by teleportCountMin.")
            .defineInRange("ring.teleportCountMax", 8, 0, 32);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_TELEPORT_INTERVAL = BUILDER
            .comment("Seconds between rapid teleports. Uses the normal teleport area and safety checks.")
            .defineInRange("ring.teleportIntervalSeconds", 0.35, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_WARNING = BUILDER
            .comment("Seconds of warning before the first teleport. 0 skips the opening warning.")
            .defineInRange("ring.warningSeconds", 0.8, 0.0, 2.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_CHARGE = BUILDER
            .comment("Seconds the boss charges after each landing before releasing one ring.")
            .defineInRange("ring.chargeSeconds", 1.0, 0.1, 1.5);

    public static final ForgeConfigSpec.IntValue LIGHTNING_RING_COUNT_MIN = BUILDER
            .comment("Minimum ring waves per attack, with a teleport attempted before each wave.")
            .defineInRange("ring.countMin", 4, 1, 16);

    public static final ForgeConfigSpec.IntValue LIGHTNING_RING_COUNT_MAX = BUILDER
            .comment("Maximum ring waves per attack. If set below the minimum, the minimum is used.")
            .defineInRange("ring.countMax", 8, 1, 16);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_INTERVAL = BUILDER
            .comment("Minimum seconds between ring releases, including the next jump and charge.",
                    "The jump and charge are allowed to finish if they take longer than this interval.")
            .defineInRange("ring.intervalSeconds", 3.0, 0.5, 8.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_SPEED = BUILDER
            .comment("Outward speed in blocks per second. Rings finish at the arena boundary.")
            .defineInRange("ring.speedBlocksPerSecond", 18.0, 8.0, 36.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_WIDTH = BUILDER
            .comment("Radial thickness of the visible ring and its damage band in blocks.")
            .defineInRange("ring.width", 0.75, 0.1, 2.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_HIT_HEIGHT = BUILDER
            .comment("Damage height above the local ground in blocks; jumping above it avoids the ring.")
            .defineInRange("ring.hitHeight", 0.35, 0.05, 1.25);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_DAMAGE = BUILDER
            .comment("Damage in health points before armor and difficulty scaling. Each ring can damage a player once.")
            .defineInRange("ring.damage", 6.0, 0.0, 24.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_PARTICLE_SPACING = BUILDER
            .comment("Spacing of the lightning arcs on the ring. Lower values make the ring denser.")
            .defineInRange("ring.particleSpacing", 0.5, 0.2, 1.0);

    /**
     * Shared proximity charge and defensive discharge
     * (proximityPush).
     */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_RADIUS = BUILDER
            .comment("Distance from the boss for charging, damage and knockback. 0 disables the defense.",
                    "One shared timer runs while any combat participant stays nearby; an empty radius resets it.",
                    "Runs independently of normal attacks and recovery. NoAI pauses charging.")
            .defineInRange("proximityPush.radius", 3.0, 0.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_DELAY_MIN = BUILDER
            .comment("Minimum continuous proximity seconds before discharging. Sampled on entry and after a discharge.")
            .defineInRange("proximityPush.delayMinSeconds", 4.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_DELAY_MAX = BUILDER
            .comment("Maximum proximity seconds before discharging; capped below by delayMinSeconds.")
            .defineInRange("proximityPush.delayMaxSeconds", 8.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_DAMAGE = BUILDER
            .comment("Discharge damage to every nearby combat participant in health points (2 = one heart).")
            .defineInRange("proximityPush.damage", 6.0, 0.0, 1024.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_STRENGTH = BUILDER
            .comment("Horizontal discharge impulse away from the boss, matching the water boss by default.")
            .defineInRange("proximityPush.strength", 3.9, 0.0, 3.9);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_UPWARD_STRENGTH = BUILDER
            .comment("Upward discharge impulse, matching the water boss by default.")
            .defineInRange("proximityPush.upwardStrength", 1.0, 0.0, 1.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_PROXIMITY_PUSH_PARTICLE_COUNT = BUILDER
            .comment("Peak spark count per charge update and number of outward particle rays when discharging.")
            .defineInRange("proximityPush.particleCount", 96, 16, 512);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_THUNDER_VOLUME = BUILDER
            .comment("Thunder volume per discharge. 0 disables this sound.")
            .defineInRange("proximityPush.thunderVolume", 0.6, 0.0, 4.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PROXIMITY_PUSH_IMPACT_VOLUME = BUILDER
            .comment("Electrical impact volume per discharge. 0 disables this sound.")
            .defineInRange("proximityPush.impactVolume", 0.8, 0.0, 4.0);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private LightningSheeperConfig() {
    }
}
