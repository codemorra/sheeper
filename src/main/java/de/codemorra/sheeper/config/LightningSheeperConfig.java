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
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_MAX_HEALTH = ElementalConfigValues
            .arenaBossHealth(BUILDER, 350.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_HEALTH_PER_ADDITIONAL_PLAYER = ElementalConfigValues
            .additionalPlayerHealth(BUILDER, 50.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SIZE = ElementalConfigValues.elementalSize(BUILDER,
            "lightning", 2.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_COMBAT_RADIUS = ElementalConfigValues
            .arenaRadius(BUILDER, 50.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_BOSS_BAR_DISTANCE = ElementalConfigValues
            .bossBarDistance(BUILDER, 10.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_MOVEMENT_EDGE_DISTANCE = ElementalConfigValues
            .movementEdgeDistance(BUILDER, 2.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_ARENA_HEIGHT = BUILDER
            .comment("Blocks from the boss's spawn point up to the top of the storm arena.",
                    "Applies to new arenas.")
            .defineInRange("arena.height", 24, 4, 64);

    public static final ForgeConfigSpec.IntValue LIGHTNING_ARENA_DEPTH = BUILDER
            .comment("Blocks from the boss's spawn point down to the arena floor.",
                    "Applies to new arenas.")
            .defineInRange("arena.depth", 24, 0, 32);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_BOUNDARY_SLOWDOWN_WIDTH = ElementalConfigValues
            .boundaryWidth(BUILDER, 1.5);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_BOUNDARY_SLOWDOWN_STRENGTH = ElementalConfigValues
            .boundaryStrength(BUILDER, 0.70);

    /** Lightning arena buildup (arenaBuild). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_PREPARATION_DURATION = BUILDER
            .comment("Seconds for the storm front to spread from the center to the arena edge. 0 skips the buildup.")
            .defineInRange("arenaBuild.durationSeconds", 5.0, 0.0, 30.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ARENA_DRAIN_DURATION = ElementalConfigValues
            .arenaDrainDuration(BUILDER, 8.0,
                    "Seconds for the storm front to retreat and fade after the boss is defeated or removed.");

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_COMBAT_ENTRY_DELAY = BUILDER
            .comment("Seconds after the first player enters the ready arena before combat hazards begin.")
            .defineInRange("arena.entryDelaySeconds", 2.0, 0.0, 10.0);

    public static final ForgeConfigSpec.BooleanValue LIGHTNING_EMPTY_ARENA_RECOVERY_ENABLED = BUILDER
            .comment("Allows the boss to heal in an empty arena and reset the fight after full recovery.")
            .define("arenaRecovery.enabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_EMPTY_ARENA_GRACE = ElementalConfigValues
            .recoveryGrace(BUILDER, 10.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_EMPTY_ARENA_REGENERATION = ElementalConfigValues
            .recoveryRate(BUILDER, 5.0);

    /** Local storm weather (storm). */
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_STORM_ENABLED = BUILDER
            .comment("Local arena rain and thunder, including normal rain effects on exposed entities and blocks.")
            .define("storm.enabled", true);

    public static final ForgeConfigSpec.BooleanValue LIGHTNING_LEAF_FIRE_ENABLED = BUILDER
            .comment("Allows lightning to ignite leaves. Turning this off also puts out existing leaf fires.")
            .define("leafFire.enabled", true);

    /** Outward transport during arena startup (spawnPush). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SPAWN_PUSH_SPEED = ElementalConfigValues
            .spawnPushSpeed(BUILDER, 20.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SPAWN_PUSH_EDGE_DISTANCE = ElementalConfigValues
            .spawnPushEdgeDistance(BUILDER, 8.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_SPAWN_PUSH_LIFT_HEIGHT = ElementalConfigValues
            .spawnPushLiftHeight(BUILDER, 2.0);

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
            .comment("Minimum distance in blocks from a normal teleport destination to a living, non-spectator player.",
                    "A recovery jump can land closer if no safe destination meets this distance.")
            .defineInRange("teleport.playerDistance", 4.0, 0.0, 32.0);

    /**
     * Defensive reactions while the boss is using its ordinary combat movement
     * (defense).
     */
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_CRACKLE_ENABLED = BUILDER
            .comment("Allows recurring defensive crackles during ordinary combat movement.")
            .define("defense.crackleEnabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_CRACKLE_INTERVAL_MIN = BUILDER
            .comment("Minimum seconds between the starts of defensive crackles.")
            .defineInRange("defense.crackleIntervalMinSeconds", 2.0, 0.5, 15.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_CRACKLE_INTERVAL_MAX = BUILDER
            .comment("Maximum seconds between the starts of defensive crackles.",
                    "If set below the minimum, the minimum is used.")
            .defineInRange("defense.crackleIntervalMaxSeconds", 3.0, 0.5, 15.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_CRACKLE_DURATION = BUILDER
            .comment("Seconds the defensive crackle lasts.")
            .defineInRange("defense.crackleSeconds", 1.0, 0.4, 3.0);

    public static final ForgeConfigSpec.BooleanValue LIGHTNING_CHARGE_RETALIATION_ENABLED = BUILDER
            .comment("Allows melee retaliation while the boss charges an attack.")
            .define("defense.chargeRetaliationEnabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_CRACKLE_DAMAGE = BUILDER
            .comment("Damage dealt on a melee hit during a defensive crackle or while the boss is charging an attack.",
                    "0 makes the shock harmless.")
            .defineInRange("defense.crackleDamage", 2.0, 0.0, 8.0);

    public static final ForgeConfigSpec.BooleanValue LIGHTNING_DODGE_ENABLED = BUILDER
            .comment("Allows the boss to dodge incoming player projectiles by teleporting.")
            .define("defense.dodgeEnabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_DODGE_COOLDOWN = BUILDER
            .comment("Minimum seconds between defensive teleports triggered by incoming player projectiles.")
            .defineInRange("defense.dodgeCooldownSeconds", 3.0, 1.0, 20.0);

    /** Random arena strikes during combat (ambientLightning). */
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_AMBIENT_STRIKES_ENABLED = BUILDER
            .comment("Allows random lightning during combat, arena buildup and idle storms.")
            .define("ambientLightning.enabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_AMBIENT_INTERVAL = BUILDER
            .comment("Seconds between groups of random arena strikes.")
            .defineInRange("ambientLightning.intervalSeconds", 0.5, 0.25, 5.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_AMBIENT_STRIKE_COUNT = BUILDER
            .comment("Random strike locations per interval.",
                    "Strikes are cosmetic when no players are participating.")
            .defineInRange("ambientLightning.strikesPerInterval", 2, 1, 4);

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
            .comment("Seconds of Speed I at the edge of a random strike. Other boss lightning hits grant half.",
                    "Entering lingering ground sparks also grants half. 0 disables the speed bonus.")
            .defineInRange("ambientLightning.speedBonusSeconds", 6.0, 0.0, 16.0);

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

    public static final ForgeConfigSpec.BooleanValue LIGHTNING_WIND_MEDIUM_GUSTS_ENABLED = BUILDER
            .comment("Allows medium gusts during combat.")
            .define("wind.mediumGustsEnabled", true);

    public static final ForgeConfigSpec.BooleanValue LIGHTNING_WIND_LARGE_GUSTS_ENABLED = BUILDER
            .comment("Allows large gusts during combat.")
            .define("wind.largeGustsEnabled", true);

    public static final ForgeConfigSpec.IntValue LIGHTNING_WIND_LARGE_CHANCE = BUILDER
            .comment(
                    "Chance in percent of selecting a large gust. If the selected size is disabled, the gust is skipped.")
            .defineInRange("wind.largeGustChancePercent", 12, 0, 30);

    /** Recovery between special attacks (attack). */
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ATTACK_PAUSE_MIN = BUILDER
            .comment("Minimum rest seconds between attacks. Normal teleports are allowed during rest.",
                    "An announced teleport finishes before the next attack. NoAI and an empty arena pause the timer.")
            .defineInRange("attack.pauseMinSeconds", 4.0, 0.05, 30.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ATTACK_PAUSE_MAX = BUILDER
            .comment("Maximum rest seconds between special attacks. If set below the minimum, the minimum is used.")
            .defineInRange("attack.pauseMaxSeconds", 7.0, 0.05, 30.0);

    /** Lightning row salvos (lineAttack). */
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_LINE_ENABLED = BUILDER
            .comment("Enables the attack that fires multiple lightning rows after each jump.")
            .define("lineAttack.enabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_LINE_COOLDOWN = BUILDER
            .comment("Combat seconds after the last row is launched before this special attack is ready again.")
            .defineInRange("lineAttack.cooldownSeconds", 20.0, 1.0, 120.0);

    public static final ForgeConfigSpec.IntValue LIGHTNING_LINE_POSITION_COUNT_MIN = BUILDER
            .comment("Minimum number of firing positions per attack. The boss fires one salvo at each position.")
            .defineInRange("lineAttack.positionCountMin", 16, 1, 24);

    public static final ForgeConfigSpec.IntValue LIGHTNING_LINE_POSITION_COUNT_MAX = BUILDER
            .comment("Maximum number of firing positions in one attack. If set below the minimum, the minimum is used.")
            .defineInRange("lineAttack.positionCountMax", 16, 1, 24);

    public static final ForgeConfigSpec.IntValue LIGHTNING_LINE_ROW_COUNT_MIN = BUILDER
            .comment("Minimum number of simultaneous lightning rows in a salvo.")
            .defineInRange("lineAttack.rowCountMin", 3, 1, 12);

    public static final ForgeConfigSpec.IntValue LIGHTNING_LINE_ROW_COUNT_MAX = BUILDER
            .comment(
                    "Maximum number of simultaneous lightning rows in a salvo. If set below the minimum, the minimum is used.")
            .defineInRange("lineAttack.rowCountMax", 5, 1, 12);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_LINE_CHARGE_DURATION = BUILDER
            .comment("Seconds the Sheeper charges before teleporting to the first firing position.")
            .defineInRange("lineAttack.chargeSeconds", 1.5, 0.25, 3.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_LINE_TELEPORT_DISTANCE = BUILDER
            .comment("Preferred minimum horizontal distance to the closest player at a firing position, in blocks.",
                    "Distance from the previous position takes priority. The boss can land closer if needed.")
            .defineInRange("lineAttack.teleportDistance", 20.0, 1.0, 64.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_LINE_PREVIOUS_POSITION_DISTANCE = BUILDER
            .comment("Preferred minimum horizontal distance from the previous firing position, in blocks.",
                    "Shorter jumps are allowed if the arena has no suitable landing farther away.")
            .defineInRange("lineAttack.previousPositionDistance", 20.0, 1.0, 64.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_LINE_SPEED = BUILDER
            .comment("Speed at which each row travels toward the arena edge, in blocks per second.")
            .defineInRange("lineAttack.speedBlocksPerSecond", 18.0, 8.0, 36.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_LINE_DAMAGE = BUILDER
            .comment("Health points at the center of a lightning impact, before armor and difficulty scaling.",
                    "The outer block deals half damage. Each row can damage a player once. 0 disables damage.")
            .defineInRange("lineAttack.damage", 5.0, 0.0, 24.0);

    /** Direct teleports followed by individual expanding rings (ring). */
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_RING_ENABLED = BUILDER
            .comment("Enables the special attack with rapid teleports and expanding ground rings.")
            .define("ring.enabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_RING_COOLDOWN = BUILDER
            .comment("Combat seconds after the last ring finishes before the ring attack is ready again.")
            .defineInRange("ring.cooldownSeconds", 20.0, 1.0, 120.0);

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

    /** Wandering tornado special attack (tornado). */
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_TORNADO_ENABLED = BUILDER
            .comment("Enables the tornado special attack.")
            .define("tornado.enabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADO_COOLDOWN = BUILDER
            .comment("Combat seconds after the tornado dissipates before this attack is ready again.")
            .defineInRange("tornado.cooldownSeconds", 25.0, 1.0, 120.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADO_TELEPORT_DISTANCE = BUILDER
            .comment("Preferred horizontal distance in blocks to the nearest player when choosing the launch position.",
                    "The Sheeper can teleport closer if terrain or other players limit the available positions.")
            .defineInRange("tornado.teleportDistance", 24.0, 1.0, 64.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADO_TELEPORT_CENTER_RADIUS = BUILDER
            .comment(
                    "Radius in blocks around the arena center where the Sheeper first looks for a tornado launch position.",
                    "It checks the rest of the arena if it cannot find a safe destination there.")
            .defineInRange("tornado.teleportCenterRadius", 15.0, 1.0, 64.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADO_LAUNCH_INTERVAL = BUILDER
            .comment("Seconds between consecutive tornado launches from the same position.")
            .defineInRange("tornado.launchIntervalSeconds", 0.5, 0.1, 2.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADO_DURATION = BUILDER
            .comment("Seconds each tornado remains active after launch before it starts dissolving.")
            .defineInRange("tornado.durationSeconds", 20.0, 4.0, 22.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADO_DISSOLVE = BUILDER
            .comment("Seconds the funnels take to dissolve while players still caught inside descend gradually.")
            .defineInRange("tornado.dissolveSeconds", 1.25, 0.5, 2.5);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADOS_PER_1000_BLOCKS = BUILDER
            .comment("Tornadoes per 1,000 blocks of arena floor, rounded to a total between 1 and 12 per attack.",
                    "At an arena radius of 50 blocks, 1.25 gives 10 tornadoes and values from 1.47 reach the cap.")
            .defineInRange("tornado.per1000Blocks", 1.25, 0.1, 2.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADO_RADIUS = BUILDER
            .comment("Funnel radius in blocks, before tapering at the base. Small arenas reduce it as needed.")
            .defineInRange("tornado.radius", 4.5, 2.0, 9.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADO_HEIGHT = BUILDER
            .comment("Maximum funnel height in blocks above the ground, limited by the arena ceiling.")
            .defineInRange("tornado.height", 10.0, 4.0, 16.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADO_SPEED = BUILDER
            .comment("Travel speed of the moving funnel in blocks per second.")
            .defineInRange("tornado.speedBlocksPerSecond", 6.5, 2.0, 9.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADO_LAUNCH_DISTANCE = BUILDER
            .comment(
                    "Horizontal distance in blocks from the launch point at which the tornado settles to its usual speed.",
                    "0 disables the initial speed boost.")
            .defineInRange("tornado.launchDistance", 10.0, 0.0, 24.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADO_STRENGTH = BUILDER
            .comment("Multiplier for the tornado's pull, sideways drift and lift. 0 disables forced movement.")
            .defineInRange("tornado.windStrength", 1.0, 0.0, 1.8);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADO_DAMAGE = BUILDER
            .comment("Health points dealt during one damage interval before armor and difficulty scaling.",
                    "0 disables direct tornado damage.")
            .defineInRange("tornado.damage", 4.0, 0.0, 16.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_TORNADO_DAMAGE_INTERVAL = BUILDER
            .comment("Minimum seconds between tornado hits on the same player.")
            .defineInRange("tornado.damageIntervalSeconds", 3.0, 0.5, 6.0);

    /** Targeted ground lightning fan (fan). */
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_FAN_ENABLED = BUILDER
            .comment("Enables the ground lightning fan attack.")
            .define("fan.enabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FAN_COOLDOWN = BUILDER
            .comment("Combat seconds after the attack ends before it can start again.")
            .defineInRange("fan.cooldownSeconds", 20.0, 1.0, 120.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FAN_PREPARATION = BUILDER
            .comment("Seconds for the opening charge and ascent. Capped at half the duration before landing.")
            .defineInRange("fan.preparationSeconds", 2.0, 0.1, 5.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FAN_DURATION_MIN = BUILDER
            .comment("Minimum seconds from opening charge through the last shot, before landing.")
            .defineInRange("fan.durationMinSeconds", 8.0, 0.5, 15.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FAN_DURATION_MAX = BUILDER
            .comment(
                    "Maximum seconds from opening charge through the last shot, before landing. At least durationMinSeconds.")
            .defineInRange("fan.durationMaxSeconds", 12.0, 0.5, 15.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FAN_LEAD = BUILDER
            .comment("Maximum starting gap in blocks between the central ground impact and the targeted player.")
            .defineInRange("fan.leadDistance", 4.0, 1.0, 10.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FAN_SPEED = BUILDER
            .comment("Maximum speed in blocks per second at which the ground impacts follow the target.")
            .defineInRange("fan.speedBlocksPerSecond", 5.2, 2.0, 12.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FAN_TRACKING_RESPONSE = BUILDER
            .comment("Aim response time in seconds. Larger values make the ground impacts react more slowly.")
            .defineInRange("fan.trackingResponseSeconds", 0.3, 0.05, 2.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_FAN_DAMAGE = BUILDER
            .comment("Damage in health points on contact with a bolt or its ground impact, at most once per second.")
            .defineInRange("fan.damagePerSecond", 6.0, 0.0, 24.0);

    /** Lightning rod special attack (lightningRod). */
    public static final ForgeConfigSpec.BooleanValue LIGHTNING_ROD_ENABLED = BUILDER
            .comment("Enables the lightning rod special attack.")
            .define("lightningRod.enabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ROD_COOLDOWN = BUILDER
            .comment("Combat seconds before the lightning rod is ready, both at fight start and after each use.")
            .defineInRange("lightningRod.cooldownSeconds", 60.0, 1.0, 120.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ROD_CHARGE_DURATION = BUILDER
            .comment("Seconds the Sheeper builds its charge after arriving at the center, before the calm phase.")
            .defineInRange("lightningRod.chargeSeconds", 12.0, 2.0, 30.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ROD_SHELTER_RADIUS = BUILDER
            .comment("Radius of the shelter spheres in blocks, limited by the arena sides and ceiling.")
            .defineInRange("lightningRod.shelterRadius", 7.0, 2.0, 12.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ROD_SHELTER_DENSITY = BUILDER
            .comment("Controls how many shelter spheres the arena can produce. A larger radius allows more shelters.",
                    "At a radius of 50 blocks, a value of 0.38 gives three shelters where terrain permits.")
            .defineInRange("lightningRod.sheltersPer1000Blocks", 0.38, 0.01, 1.0);

    public static final ForgeConfigSpec.BooleanValue LIGHTNING_ROD_WIND_BOOST_ENABLED = BUILDER
            .comment("Strengthens the wind and makes it rotate continuously during Lightning Rod.")
            .define("lightningRod.windBoostEnabled", true);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ROD_WIND_MULTIPLIER = BUILDER
            .comment("Multiplier for the arena-wide wind while the Lightning Rod is active.")
            .defineInRange("lightningRod.windMultiplier", 3.0, 1.0, 6.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ROD_CALM_DURATION = BUILDER
            .comment(
                    "Seconds after the charge ends before the final barrage. Storm sounds are quieter during this time.")
            .defineInRange("lightningRod.calmBeforeStrikeSeconds", 2.0, 0.15, 4.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ROD_FINALE_DURATION = BUILDER
            .comment("Seconds the final lightning barrage lasts.")
            .defineInRange("lightningRod.finaleSeconds", 4.0, 0.5, 6.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ROD_FINALE_DAMAGE = BUILDER
            .comment("Damage in health points every quarter second on electrified ground outside shelter spheres.",
                    "The barrage hits airborne players half as often. Armor and difficulty apply. 0 disables damage.")
            .defineInRange("lightningRod.finaleDamage", 6.0, 0.0, 16.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ROD_GROUND_FADE_DURATION = BUILDER
            .comment("Seconds the ground stays dangerous while its lightning fades after the last strike.",
                    "0 ends the ground charge immediately.")
            .defineInRange("lightningRod.groundFadeSeconds", 1.5, 0.0, 3.0);

    public static final ForgeConfigSpec.DoubleValue LIGHTNING_ROD_SHELTER_DISSOLVE_DURATION = BUILDER
            .comment("Seconds the shelter spheres take to fade after the final bolt.",
                    "The spheres protect players until the ground charge ends.")
            .defineInRange("lightningRod.shelterDissolveSeconds", 2.0, 0.5, 3.0);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private LightningSheeperConfig() {
    }
}
