package de.codemorra.sheeper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Installation-wide water Sheeper settings in their own common configuration
 * file.
 */
public final class WaterSheeperConfig {
    public static final String FILE_NAME = "sheeper/water.toml";
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** Water boss settings and combat bounds. */
    public static final ForgeConfigSpec.DoubleValue WATER_MAX_HEALTH = ElementalConfigValues
            .arenaBossHealth(BUILDER, 300.0);

    public static final ForgeConfigSpec.DoubleValue WATER_HEALTH_PER_ADDITIONAL_PLAYER = ElementalConfigValues
            .additionalPlayerHealth(BUILDER, 50.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SIZE = ElementalConfigValues.elementalSize(BUILDER, "water",
            2.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BOSS_BAR_DISTANCE = ElementalConfigValues
            .bossBarDistance(BUILDER, 10.0);

    /** Virtual water arena dimensions and entry timing (arena). */
    public static final ForgeConfigSpec.DoubleValue WATER_COMBAT_RADIUS = ElementalConfigValues
            .arenaRadius(BUILDER, 50.0);

    public static final ForgeConfigSpec.IntValue WATER_ARENA_HEIGHT = BUILDER
            .comment("Blocks from the boss's spawn point up to the arena's virtual water surface.",
                    "Applies to new arenas.")
            .defineInRange("arena.height", 12, 4, 24);

    public static final ForgeConfigSpec.IntValue WATER_ARENA_DEPTH = BUILDER
            .comment("Blocks from the boss's spawn point down to the arena floor.",
                    "Applies to new arenas.")
            .defineInRange("arena.depth", 10, 0, 32);

    public static final ForgeConfigSpec.DoubleValue WATER_COMBAT_ENTRY_DELAY = BUILDER
            .comment("Seconds after the first player enters the ready arena before the projectile phase begins.")
            .defineInRange("arena.entryDelaySeconds", 2.0, 0.0, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_ARENA_BUILD_DURATION = BUILDER
            .comment("Seconds for the arena water and bubble front to expand from the center before combat begins.")
            .defineInRange("arenaBuild.durationSeconds", 5.0, 0.5, 30.0);

    public static final ForgeConfigSpec.DoubleValue WATER_ARENA_DRAIN_DURATION = ElementalConfigValues
            .arenaDrainDuration(BUILDER, 8.0,
                    "Seconds for the virtual water level to sink after the boss disappears.",
                    "Affected players descend gently and remain protected from fall damage until landing.");

    /** Soft lateral arena boundary (boundary). */
    public static final ForgeConfigSpec.DoubleValue WATER_BOUNDARY_SLOWDOWN_WIDTH = ElementalConfigValues
            .boundaryWidth(BUILDER, 1.5);

    public static final ForgeConfigSpec.DoubleValue WATER_BOUNDARY_SLOWDOWN_STRENGTH = ElementalConfigValues
            .boundaryStrength(BUILDER, 0.70);

    /** Empty-arena grace and recovery (arenaRecovery). */
    public static final ForgeConfigSpec.BooleanValue WATER_EMPTY_ARENA_RECOVERY_ENABLED = BUILDER
            .comment("Allows the boss to heal in an empty arena and reset the fight after full recovery.")
            .define("arenaRecovery.enabled", true);

    public static final ForgeConfigSpec.DoubleValue WATER_EMPTY_ARENA_GRACE = ElementalConfigValues
            .recoveryGrace(BUILDER, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_EMPTY_ARENA_REGENERATION = ElementalConfigValues
            .recoveryRate(BUILDER, 5.0);

    /** Water boss movement (movement). */
    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_EDGE_DISTANCE = ElementalConfigValues
            .movementEdgeDistance(BUILDER, 2.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_INTERVAL_MIN = BUILDER
            .comment("Minimum seconds between local circling waypoints.")
            .defineInRange("movement.intervalMinSeconds", 1.0, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_INTERVAL_MAX = BUILDER
            .comment("Maximum seconds between circling waypoints. If set below the minimum, the minimum is used.")
            .defineInRange("movement.intervalMaxSeconds", 2.0, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_SPEED = BUILDER
            .comment("Navigation speed multiplier for approaching and circling players. 0 disables movement.")
            .defineInRange("movement.speedMultiplier", 1.0, 0.0, 3.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_ORBIT_DISTANCE = BUILDER
            .comment("Preferred distance for circling the selected player in blocks.",
                    "Approaching players shrink the circle instead of making the boss retreat.")
            .defineInRange("movement.orbitDistance", 10.0, 1.0, 64.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_HEIGHT_OFFSET = BUILDER
            .comment("Preferred boss center height above the selected player's center while circling underwater.",
                    "Clamped to the arena volume; melee approaches use closeRange.stopDistance instead.")
            .defineInRange("movement.heightOffset", 1.0, -2.0, 3.0);

    public static final ForgeConfigSpec.BooleanValue WATER_MOVEMENT_SPRINT_ENABLED = BUILDER
            .comment("Allows short swimming sprints while the boss circles a target.")
            .define("movement.sprintEnabled", true);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_SPRINT_CHANCE = BUILDER
            .comment("Chance of a short sprint when choosing a waypoint and the sprint cooldown has elapsed.")
            .defineInRange("movement.sprintChance", 0.25, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_SPRINT_MULTIPLIER = BUILDER
            .comment("Additional navigation speed multiplier during short water sprints.")
            .defineInRange("movement.sprintSpeedMultiplier", 2.0, 1.0, 4.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_SPRINT_DURATION = BUILDER
            .comment("Water sprint duration in seconds; 0 disables sprints.")
            .defineInRange("movement.sprintSeconds", 0.75, 0.0, 5.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_SPRINT_COOLDOWN = BUILDER
            .comment("Minimum time in seconds after a sprint before another sprint can begin.")
            .defineInRange("movement.sprintCooldownSeconds", 6.0, 0.0, 60.0);

    /**
     * Quiet movement without combat participants (movement.idle).
     */
    public static final ForgeConfigSpec.DoubleValue WATER_IDLE_SPEED = BUILDER
            .comment("Idle speed relative to normal navigation speed; no sprint boosts. 0 disables idle swimming.")
            .defineInRange("movement.idle.speedMultiplier", 0.5, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue WATER_IDLE_INTERVAL_MIN = BUILDER
            .comment("Minimum seconds between idle swim plans. Active routes finish before choosing another.")
            .defineInRange("movement.idle.intervalMinSeconds", 2.0, 0.1, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_IDLE_INTERVAL_MAX = BUILDER
            .comment("Maximum seconds between idle swim plans. If set below the minimum, the minimum is used.")
            .defineInRange("movement.idle.intervalMaxSeconds", 5.0, 0.1, 15.0);

    /** Damage-free transport during arena expansion (spawnPush). */
    public static final ForgeConfigSpec.DoubleValue WATER_SPAWN_PUSH_SPEED = ElementalConfigValues
            .spawnPushSpeed(BUILDER, 20.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SPAWN_PUSH_EDGE_DISTANCE = ElementalConfigValues
            .spawnPushEdgeDistance(BUILDER, 8.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SPAWN_PUSH_LIFT_HEIGHT = ElementalConfigValues
            .spawnPushLiftHeight(BUILDER, 2.0);

    /** Water boss close-range approach (closeRange). */
    public static final ForgeConfigSpec.DoubleValue WATER_CLOSE_RANGE_DISTANCE = BUILDER
            .comment("Distance between body centers at which the boss enters melee mode and approaches the target.",
                    "Projectiles and new beams are suppressed inside; exceeding this same distance resumes ranged combat.")
            .defineInRange("closeRange.distance", 8.0, 0.1, 20.0);

    public static final ForgeConfigSpec.DoubleValue WATER_CLOSE_RANGE_STOP_DISTANCE = BUILDER
            .comment("Fixed distance between body centers where the boss stops approaching in melee mode.",
                    "Limited below melee entry distance and within enabled proximity push reach; approaching players cause no retreat.")
            .defineInRange("closeRange.stopDistance", 2.0, 0.1, 10.0);

    /** Water boss proximity push (proximityPush). */
    public static final ForgeConfigSpec.BooleanValue WATER_PROXIMITY_PUSH_ENABLED = BUILDER
            .comment("Allows the boss to damage and push players who stay too close.")
            .define("proximityPush.enabled", true);

    public static final ForgeConfigSpec.DoubleValue WATER_PROXIMITY_PUSH_RADIUS = BUILDER
            .comment("Distance within which players can trigger and are hit by the shared proximity push.",
                    "Leaving resets the timer. NoAI pauses timers; 0 disables pushes.")
            .defineInRange("proximityPush.radius", 5.5, 0.0, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROXIMITY_PUSH_DELAY = BUILDER
            .comment(
                    "Continuous proximity seconds for any player to trigger a push against everyone in range.",
                    "A push resets all nearby players' timers.")
            .defineInRange("proximityPush.delaySeconds", 2.0, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROXIMITY_PUSH_DAMAGE = BUILDER
            .comment("Damage per proximity push in health points; 2 = one heart. 0 disables damage, keeping knockback.")
            .defineInRange("proximityPush.damage", 3.66, 0.0, 20.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROXIMITY_PUSH_STRENGTH = BUILDER
            .comment("Horizontal launch velocity in blocks per tick away from the boss or an exploding mine.")
            .defineInRange("proximityPush.strength", 2.0, 0.0, 3.9);

    public static final ForgeConfigSpec.DoubleValue WATER_PROXIMITY_PUSH_UPWARD_STRENGTH = BUILDER
            .comment("Upward launch velocity in blocks per tick from proximity pushes and mine blasts.")
            .defineInRange("proximityPush.upwardStrength", 1.0, 0.0, 3.9);

    /** Defensive bubble dodge (defense). */
    public static final ForgeConfigSpec.BooleanValue WATER_DODGE_ENABLED = BUILDER
            .comment("Allows the boss to evade incoming player projectiles as a bubble cloud.")
            .define("defense.dodgeEnabled", true);

    public static final ForgeConfigSpec.DoubleValue WATER_DODGE_COOLDOWN = BUILDER
            .comment("Minimum seconds between defensive bubble dodges.")
            .defineInRange("defense.dodgeCooldownSeconds", 3.0, 1.0, 20.0);

    /** Water projectiles (projectile). */
    public static final ForgeConfigSpec.BooleanValue WATER_PROJECTILE_ENABLED = BUILDER
            .comment("Allows the boss to fire water projectiles.",
                    "Movement, target changes and the timing of special attacks continue when disabled.")
            .define("projectile.enabled", true);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_DAMAGE = BUILDER
            .comment("Base damage per water projectile in health points (2 = one heart); difficulty and armor apply.")
            .defineInRange("projectile.damage", 4.88, 0.0, 20.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_SPEED_PER_SECOND = BUILDER
            .comment("Constant water projectile speed in blocks per second, including underwater.")
            .defineInRange("projectile.speedBlocksPerSecond", 16.0, 1.0, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_KNOCKBACK = BUILDER
            .comment("Additional velocity in the projectile's flight direction on an accepted hit.")
            .defineInRange("projectile.knockback", 0.5, 0.0, 3.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_INTERVAL = BUILDER
            .comment("Seconds between default projectile shots while moving around the selected player.")
            .defineInRange("projectile.intervalSeconds", 1.25, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_DURATION_MIN = BUILDER
            .comment("Minimum seconds for each of the two projectile intervals.",
                    "The boss can switch targets between intervals without pausing.")
            .defineInRange("projectile.phaseIntervalMinSeconds", 8.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_DURATION_MAX = BUILDER
            .comment("Maximum seconds for each projectile interval; lower values use phaseIntervalMinSeconds.")
            .defineInRange("projectile.phaseIntervalMaxSeconds", 12.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_LIFETIME = BUILDER
            .comment("Maximum flight duration in seconds; range is speedBlocksPerSecond multiplied by lifetimeSeconds.")
            .defineInRange("projectile.lifetimeSeconds", 5.0, 0.05, 10.0);

    /** Continuous water beam (beam). */
    public static final ForgeConfigSpec.BooleanValue WATER_BEAM_ENABLED = BUILDER
            .comment("Enables the water beam special attack.")
            .define("beam.enabled", true);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_COOLDOWN = BUILDER
            .comment("Seconds after a beam ends before another beam may start.")
            .defineInRange("beam.cooldownSeconds", 20.0, 1.0, 120.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_SIGHT_SEARCH = BUILDER
            .comment("Maximum time in seconds to look for a clear shot before choosing another attack.",
                    "Set to 0 to skip the search when the target is behind cover.")
            .defineInRange("beam.sightSearchSeconds", 3.0, 0.0, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_PREPARATION = BUILDER
            .comment("Stationary warning seconds before firing; eyes gradually turn red.")
            .defineInRange("beam.preparationSeconds", 2.0, 0.1, 5.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_DURATION_MIN = BUILDER
            .comment("Minimum firing duration, excluding preparation.")
            .defineInRange("beam.durationMinSeconds", 5.0, 0.5, 15.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_DURATION_MAX = BUILDER
            .comment("Maximum firing duration in seconds. If set below the minimum, the minimum is used.")
            .defineInRange("beam.durationMaxSeconds", 8.0, 0.5, 15.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_RANGE = BUILDER
            .comment("Maximum beam reach in blocks. Solid blocks and the arena boundary can shorten it.")
            .defineInRange("beam.range", 50.0, 1.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_EXTENSION = BUILDER
            .comment("Seconds for the beam to extend from the mouth to its full range.")
            .defineInRange("beam.extensionSeconds", 0.5, 0.05, 2.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_TRACKING_RESPONSE = BUILDER
            .comment("Aim response time in seconds; larger values make player tracking slower.")
            .defineInRange("beam.trackingResponseSeconds", 0.5, 0.05, 2.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_DAMAGE_PER_SECOND = BUILDER
            .comment(
                    "Beam damage in health points on first contact, then at least one second after the last accepted beam hit.")
            .defineInRange("beam.damagePerSecond", 4.88, 0.0, 20.0);

    /** Maelstrom preparation (maelstrom). */
    public static final ForgeConfigSpec.BooleanValue WATER_MAELSTROM_ENABLED = BUILDER
            .comment("Enables the Maelstrom special attack.")
            .define("maelstrom.enabled", true);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_COOLDOWN = BUILDER
            .comment("Seconds before Maelstrom becomes ready, both at encounter start and after each use.",
                    "The initial countdown starts with the first combat participant; full recovery restarts it.")
            .defineInRange("maelstrom.cooldownSeconds", 60.0, 1.0, 300.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_MELEE_DELAY = BUILDER
            .comment("Continuous melee-mode seconds required before starting Maelstrom while in close range.")
            .defineInRange("maelstrom.meleeDelaySeconds", 5.0, 0.0, 30.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_CENTER_SPEED = BUILDER
            .comment("Navigation speed multiplier while moving to the arena center for Maelstrom.")
            .defineInRange("maelstrom.centerSpeedMultiplier", 3.0, 0.1, 4.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_CENTER_TIMEOUT = BUILDER
            .comment("Maximum center-approach seconds before aborting safely and releasing damage immunity.")
            .defineInRange("maelstrom.centerTimeoutSeconds", 15.0, 1.0, 30.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_ROTATION_DURATION = BUILDER
            .comment("Initial stationary rotation seconds before ascent and suction begin.")
            .defineInRange("maelstrom.rotationSeconds", 2.0, 0.1, 5.0);

    /** Maelstrom ascent and staged horizontal pull. */
    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_ASCENT_DURATION = BUILDER
            .comment("Seconds of slow ascent with weak suction after the initial rotation.")
            .defineInRange("maelstrom.ascentSeconds", 3.0, 0.1, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_ASCENT_HEIGHT = BUILDER
            .comment("Rise above the preparation position in blocks; water surface and solid ceilings limit ascent.")
            .defineInRange("maelstrom.ascentHeight", 3.0, 0.0, 8.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_MEDIUM_DURATION = BUILDER
            .comment("Seconds of medium suction and faster rotation after ascent.")
            .defineInRange("maelstrom.mediumPullSeconds", 2.0, 0.1, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_STRONG_DURATION = BUILDER
            .comment("Seconds of strongest suction and fastest rotation before impact.")
            .defineInRange("maelstrom.strongPullSeconds", 2.0, 0.1, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_WEAK_PULL = BUILDER
            .comment("Weak inward acceleration in blocks per tick squared during ascent; players can swim away.")
            .defineInRange("maelstrom.weakPullStrength", 0.01, 0.0, 0.1);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_MEDIUM_PULL = BUILDER
            .comment("Medium inward acceleration, intended to balance swimming against the current.",
                    "If set below weakPullStrength, weakPullStrength is used.")
            .defineInRange("maelstrom.mediumPullStrength", 0.02, 0.0, 0.1);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_STRONG_PULL = BUILDER
            .comment("Strong inward acceleration, intended to pull swimmers inward despite opposing input.",
                    "If set below the effective medium pull strength, that strength is used.")
            .defineInRange("maelstrom.strongPullStrength", 0.04, 0.0, 0.1);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_PULL_SPEED_LIMIT = BUILDER
            .comment("Maximum inward speed reached through suction, in blocks per second.",
                    "Faster existing motion is not slowed; vertical and tangential motion remain unchanged.")
            .defineInRange("maelstrom.pullSpeedLimitBlocksPerSecond", 5.0, 0.1, 10.0);

    /** Maelstrom impact, outgoing wave and protected outer ring. */
    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_SLAM_SPEED = BUILDER
            .comment("Downward slam speed in blocks per second; solid terrain stops the descent.")
            .defineInRange("maelstrom.slamSpeedBlocksPerSecond", 24.0, 4.0, 64.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_WAVE_SPEED = BUILDER
            .comment("Outward pressure-wave speed in blocks per second across the arena.")
            .defineInRange("maelstrom.waveSpeedBlocksPerSecond", 20.0, 5.0, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_TRANSPORT_SPEED = BUILDER
            .comment("Player transport speed toward the arena edge in blocks per second, preserving swimming height.")
            .defineInRange("maelstrom.transportSpeedBlocksPerSecond", 20.0, 2.0, 40.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_DAMAGE = BUILDER
            .comment("Normal mob damage per second from wave contact until the wave ends; 2 = one heart.",
                    "Damage is applied every 0.5 seconds; armor and shields apply normally.")
            .defineInRange("maelstrom.damagePerSecond", 7.32, 0.0, 20.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_SAFE_OUTER_WIDTH = BUILDER
            .comment("Width of the outer arena ring protected from wave damage and forced transport, in blocks.",
                    "Limited to half the arena radius. Reaching the ring releases affected players; 0 disables protection.")
            .defineInRange("maelstrom.safeOuterWidth", 8.0, 0.0, 32.0);

    /**
     * Shared recovery between a special and the next projectile phase
     * (special).
     */
    public static final ForgeConfigSpec.DoubleValue WATER_SPECIAL_RECOVERY = BUILDER
            .comment("Seconds without new attacks after a special; the boss is vulnerable during recovery.",
                    "Maelstrom recovery begins at impact and holds the boss still.")
            .defineInRange("special.recoverySeconds", 2.0, 0.1, 5.0);

    /**
     * Water mines (mines). A maximum below its minimum uses the
     * minimum.
     */
    public static final ForgeConfigSpec.BooleanValue WATER_MINES_ENABLED = BUILDER
            .comment("Enables the water mine special attack.")
            .define("mines.enabled", true);

    public static final ForgeConfigSpec.DoubleValue WATER_MINES_DURATION_MIN = BUILDER
            .comment("Minimum seconds during which new water mines appear.")
            .defineInRange("mines.durationMinSeconds", 20.0, 0.5, 60);

    public static final ForgeConfigSpec.DoubleValue WATER_MINES_DURATION_MAX = BUILDER
            .comment("Maximum seconds during which new water mines appear.")
            .defineInRange("mines.durationMaxSeconds", 25.0, 0.5, 60);

    public static final ForgeConfigSpec.DoubleValue WATER_MINES_COUNT_MULTIPLIER = BUILDER
            .comment("Scales how many mines the boss attempts to place without changing attack duration.",
                    "1 uses the standard rate, 0.5 halves it, and 2 doubles it.")
            .defineInRange("mines.countMultiplier", 1.0, 0.25, 2.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MINES_DAMAGE = BUILDER
            .comment("Damage per mine in health points before armor. 2 health points equal one heart.")
            .defineInRange("mines.damage", 2.5, 0.0, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MINES_COOLDOWN = BUILDER
            .comment("Cooldown in seconds after the last mine pressure wave ends.")
            .defineInRange("mines.cooldownSeconds", 20.0, 1.0, 120.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MINES_DIAMETER = BUILDER
            .comment(
                    "Mine bubble diameter in blocks at detonation. Larger mines need more room and have a wider damage area and pressure wave.")
            .defineInRange("mines.diameter", 2.5, 0.75, 6.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MINES_FUSE = BUILDER
            .comment("Seconds from a mine appearing until it explodes.")
            .defineInRange("mines.fuseSeconds", 2.2, 0.2, 5.0);

    /** Air fountains (airStations). */
    public static final ForgeConfigSpec.DoubleValue WATER_AIR_STATIONS_PER_1000_BLOCKS = BUILDER
            .comment("Number of air fountains per 1,000 blocks of arena floor. Increase this value for more fountains.",
                    "For example, a value of 2 gives about 15 fountains at an arena radius of 50 blocks. Maximum: 48.")
            .defineInRange("airStations.per1000Blocks", 2.0, 0.1, 8.0);

    public static final ForgeConfigSpec.DoubleValue WATER_AIR_DURATION = BUILDER
            .comment("Seconds a full air bar lasts in a Water Sheeper arena without breathing bonuses.")
            .defineInRange("airStations.airDurationSeconds", 40.0, 20.0, 120.0);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private WaterSheeperConfig() {
    }
}
