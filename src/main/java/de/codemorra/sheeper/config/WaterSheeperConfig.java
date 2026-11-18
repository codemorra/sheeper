package de.codemorra.sheeper.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Installation-wide water Sheeper settings in their own common configuration
 * file.
 */
public final class WaterSheeperConfig {
    public static final String FILE_NAME = "sheeper/water.toml";
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** Water elemental boss and combat bounds (elemental.water). */
    public static final ForgeConfigSpec.DoubleValue WATER_MAX_HEALTH = BUILDER
            .comment("Solo maximum health of the water Sheeper in health points (2 = one heart).",
                    "Multiplayer scaling adds health up to 1024; changes preserve the remaining health percentage.")
            .defineInRange("elemental.water.maxHealth", 300.0, 1.0, 1024.0);

    public static final ForgeConfigSpec.DoubleValue WATER_HEALTH_PER_ADDITIONAL_PLAYER = BUILDER
            .comment(
                    "Additional percentage of solo maximum health per extra simultaneous arena player; 0 disables scaling.",
                    "Players qualify after 3 continuous seconds. The highest count lasts until full empty-arena recovery.")
            .defineInRange("elemental.water.healthScaling.additionalPlayerPercent", 50.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SIZE = ElementalConfigValues.elementalSize(BUILDER, "water");

    public static final ForgeConfigSpec.DoubleValue WATER_COMBAT_RADIUS = BUILDER
            .comment("Horizontal water Sheeper arena and combat radius around its saved spawn position.",
                    "Surface discovery captures this radius when it starts; combat uses the current value.")
            .defineInRange("elemental.water.combatRadius", 50.0, 1.0, 128.0);

    /** Empty-arena grace and recovery (elemental.water.arenaRecovery). */
    public static final ForgeConfigSpec.DoubleValue WATER_EMPTY_ARENA_GRACE = BUILDER
            .comment("Seconds without arena participants before regeneration begins.",
                    "The boss rejects damage immediately; returning players cancel recovery.")
            .defineInRange("elemental.water.arenaRecovery.graceSeconds", 10.0, 0.0, 30.0);

    public static final ForgeConfigSpec.DoubleValue WATER_EMPTY_ARENA_REGENERATION = BUILDER
            .comment("Percentage of maximum health restored per second after the empty-arena grace period.",
                    "Full recovery resets the encounter; returning players stop further healing.")
            .defineInRange("elemental.water.arenaRecovery.healthPercentPerSecond", 5.0, 0.1, 25.0);

    /** Water boss movement (elemental.water.movement). */
    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_EDGE_DISTANCE = BUILDER
            .comment("Minimum horizontal clearance between the boss body and the flooded arena boundary.",
                    "The movement radius adapts automatically to arena radius and boss size; 0 keeps body clearance only.")
            .defineInRange("elemental.water.movement.edgeDistance", 2.0, 0.0, 16.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_INTERVAL_MIN = BUILDER
            .comment("Minimum seconds between local circling waypoints.")
            .defineInRange("elemental.water.movement.intervalMinSeconds", 1.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_INTERVAL_MAX = BUILDER
            .comment("Maximum seconds between local circling waypoints; capped below by the minimum.")
            .defineInRange("elemental.water.movement.intervalMaxSeconds", 2.0, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_SPEED = BUILDER
            .comment("Navigation speed multiplier for approaching and circling players. 0 disables movement.")
            .defineInRange("elemental.water.movement.speedMultiplier", 1.0, 0.0, 3.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_ORBIT_DISTANCE = BUILDER
            .comment("Preferred distance for circling the selected player in blocks.",
                    "Approaching players shrink the circle instead of making the boss retreat.")
            .defineInRange("elemental.water.movement.orbitDistance", 10.0, 1.0, 64.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_SWIM_SPEED = BUILDER
            .comment("Water boss swim acceleration multiplier; offsets ordinary sheep water slowdown.")
            .defineInRange("elemental.water.movement.swimSpeedMultiplier", 4.0, 0.1, 16.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_HEIGHT_OFFSET = BUILDER
            .comment("Preferred boss center height above the selected player's center while circling underwater.",
                    "Clamped to the flooded volume; melee approaches use closeRange.stopDistance instead.")
            .defineInRange("elemental.water.movement.heightOffset", 1.0, -2.0, 3.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_SPRINT_CHANCE = BUILDER
            .comment("Chance of a short sprint when choosing a waypoint and the sprint cooldown has elapsed.")
            .defineInRange("elemental.water.movement.sprintChance", 0.25, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_SPRINT_MULTIPLIER = BUILDER
            .comment("Additional navigation speed multiplier during short water sprints.")
            .defineInRange("elemental.water.movement.sprintSpeedMultiplier", 2.0, 1.0, 4.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_SPRINT_DURATION = BUILDER
            .comment("Water sprint duration in seconds; 0 disables sprints.")
            .defineInRange("elemental.water.movement.sprintSeconds", 0.75, 0.0, 5.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MOVEMENT_SPRINT_COOLDOWN = BUILDER
            .comment("Minimum time in seconds after a sprint before another sprint can begin.")
            .defineInRange("elemental.water.movement.sprintCooldownSeconds", 6.0, 0.0, 60.0);

    /**
     * Quiet movement without combat participants (elemental.water.movement.idle).
     */
    public static final ForgeConfigSpec.DoubleValue WATER_IDLE_SPEED = BUILDER
            .comment("Idle speed relative to normal navigation speed; no sprint boosts. 0 disables idle swimming.")
            .defineInRange("elemental.water.movement.idle.speedMultiplier", 0.5, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue WATER_IDLE_INTERVAL_MIN = BUILDER
            .comment("Minimum seconds between idle swim plans. Active routes finish before choosing another.")
            .defineInRange("elemental.water.movement.idle.intervalMinSeconds", 2.0, 0.1, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_IDLE_INTERVAL_MAX = BUILDER
            .comment("Maximum seconds between idle swim plans; capped below by intervalMinSeconds.")
            .defineInRange("elemental.water.movement.idle.intervalMaxSeconds", 5.0, 0.1, 15.0);

    /** Water-contact spawn wave (elemental.water.spawnPush). */
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

    /** Water boss close-range approach (elemental.water.closeRange). */
    public static final ForgeConfigSpec.DoubleValue WATER_CLOSE_RANGE_DISTANCE = BUILDER
            .comment("Distance between body centers at which the boss enters melee mode and approaches the target.",
                    "Projectiles and new beams are suppressed inside; exceeding this same distance resumes ranged combat.")
            .defineInRange("elemental.water.closeRange.distance", 8.0, 0.1, 20.0);

    public static final ForgeConfigSpec.DoubleValue WATER_CLOSE_RANGE_STOP_DISTANCE = BUILDER
            .comment("Fixed distance between body centers where the boss stops approaching in melee mode.",
                    "Independent of boss size, orbitDistance and the proximity push radius; approaching players cause no retreat.")
            .defineInRange("elemental.water.closeRange.stopDistance", 2.0, 0.1, 10.0);

    /** Water boss proximity push (elemental.water.proximityPush). */
    public static final ForgeConfigSpec.DoubleValue WATER_PROXIMITY_PUSH_RADIUS = BUILDER
            .comment("Distance within which players can trigger and are hit by the shared proximity push.",
                    "Leaving resets the timer. NoAI pauses timers; 0 disables pushes.")
            .defineInRange("elemental.water.proximityPush.radius", 5.5, 0.0, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROXIMITY_PUSH_DELAY = BUILDER
            .comment(
                    "Continuous proximity seconds for any player to trigger a push against everyone in range.",
                    "A push resets all nearby players' timers.")
            .defineInRange("elemental.water.proximityPush.delaySeconds", 2.0, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROXIMITY_PUSH_DAMAGE = BUILDER
            .comment("Damage per proximity push in health points; 2 = one heart. 0 disables damage, keeping knockback.")
            .defineInRange("elemental.water.proximityPush.damage", 3.66, 0.0, 20.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROXIMITY_PUSH_STRENGTH = BUILDER
            .comment("Horizontal launch velocity away from the boss for proximity knockback.")
            .defineInRange("elemental.water.proximityPush.strength", 2.0, 0.0, 3.9);

    public static final ForgeConfigSpec.DoubleValue WATER_PROXIMITY_PUSH_UPWARD_STRENGTH = BUILDER
            .comment("Upward launch velocity for proximity knockback.")
            .defineInRange("elemental.water.proximityPush.upwardStrength", 1.0, 0.0, 5.0);

    /** Water projectiles (elemental.water.projectile). */
    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_DAMAGE = BUILDER
            .comment("Base damage per water projectile in health points (2 = one heart); difficulty and armor apply.")
            .defineInRange("elemental.water.projectile.damage", 4.88, 0.0, 1024.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_SPEED_PER_SECOND = BUILDER
            .comment("Constant water projectile speed in blocks per second, including underwater.")
            .defineInRange("elemental.water.projectile.speedBlocksPerSecond", 16.0, 1.0, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_KNOCKBACK = BUILDER
            .comment("Additional velocity in the projectile's flight direction on an accepted hit.")
            .defineInRange("elemental.water.projectile.knockback", 0.5, 0.0, 3.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_INTERVAL = BUILDER
            .comment("Seconds between default projectile shots while moving around the selected player.")
            .defineInRange("elemental.water.projectile.intervalSeconds", 1.25, 0.05, 600.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_DURATION_MIN = BUILDER
            .comment("Minimum default attack phase seconds before a two-second target-selection pause.",
                    "Firing starts immediately after the pause; movement continues throughout.")
            .defineInRange("elemental.water.projectile.durationMinSeconds", 10.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_DURATION_MAX = BUILDER
            .comment(
                    "Maximum default attack phase seconds before target selection; capped below by durationMinSeconds.")
            .defineInRange("elemental.water.projectile.durationMaxSeconds", 15.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_PROJECTILE_LIFETIME = BUILDER
            .comment("Maximum flight duration in seconds; range is speedBlocksPerSecond multiplied by lifetimeSeconds.")
            .defineInRange("elemental.water.projectile.lifetimeSeconds", 5.0, 0.05, 600.0);

    /** Continuous water beam (elemental.water.beam). */
    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_COOLDOWN = BUILDER
            .comment("Seconds after a beam ends before another beam may start.")
            .defineInRange("elemental.water.beam.cooldownSeconds", 20.0, 1.0, 120.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_PREPARATION = BUILDER
            .comment("Stationary warning seconds before firing; eyes gradually turn red.")
            .defineInRange("elemental.water.beam.preparationSeconds", 2.0, 0.1, 5.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_DURATION_MIN = BUILDER
            .comment("Minimum firing duration, excluding preparation.")
            .defineInRange("elemental.water.beam.durationMinSeconds", 5.0, 0.5, 15.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_DURATION_MAX = BUILDER
            .comment("Maximum firing duration; capped below by durationMinSeconds.")
            .defineInRange("elemental.water.beam.durationMaxSeconds", 8.0, 0.5, 15.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_RANGE = BUILDER
            .comment("Fixed beam reach in blocks, independent of target distance. Solid blocks stop the beam.")
            .defineInRange("elemental.water.beam.range", 50.0, 1.0, 128.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_EXTENSION = BUILDER
            .comment("Seconds for the beam to extend from the mouth to its full range.")
            .defineInRange("elemental.water.beam.extensionSeconds", 0.5, 0.05, 2.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_TRACKING_RESPONSE = BUILDER
            .comment("Aim response time in seconds; larger values make player tracking slower.")
            .defineInRange("elemental.water.beam.trackingResponseSeconds", 0.5, 0.05, 2.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_DAMAGE_PER_SECOND = BUILDER
            .comment(
                    "Beam damage in health points on first contact, then at least one second after the last accepted beam hit.")
            .defineInRange("elemental.water.beam.damagePerSecond", 4.88, 0.0, 20.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_SIGHT_TIMEOUT = BUILDER
            .comment("Consecutive seconds without sight before aborting; collision still stops the visible beam.")
            .defineInRange("elemental.water.beam.sightTimeoutSeconds", 1.0, 0.1, 5.0);

    public static final ForgeConfigSpec.DoubleValue WATER_BEAM_EYE_FADE = BUILDER
            .comment("Seconds for warning eyes to fade after the beam ends.")
            .defineInRange("elemental.water.beam.eyeFadeSeconds", 0.5, 0.05, 2.0);

    /** Maelstrom preparation (elemental.water.maelstrom). */
    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_COOLDOWN = BUILDER
            .comment("Seconds before Maelstrom becomes ready, both at encounter start and after each use.",
                    "The initial countdown starts with the first combat participant; full recovery restarts it.")
            .defineInRange("elemental.water.maelstrom.cooldownSeconds", 60.0, 1.0, 300.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_MELEE_DELAY = BUILDER
            .comment("Continuous melee-mode seconds required before starting Maelstrom while in close range.")
            .defineInRange("elemental.water.maelstrom.meleeDelaySeconds", 5.0, 0.0, 30.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_CENTER_SPEED = BUILDER
            .comment("Navigation speed multiplier while moving to the arena center for Maelstrom.")
            .defineInRange("elemental.water.maelstrom.centerSpeedMultiplier", 3.0, 0.1, 3.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_CENTER_TIMEOUT = BUILDER
            .comment("Maximum center-approach seconds before aborting safely and releasing damage immunity.")
            .defineInRange("elemental.water.maelstrom.centerTimeoutSeconds", 15.0, 1.0, 30.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_ROTATION_DURATION = BUILDER
            .comment("Initial stationary rotation seconds before ascent and suction begin.")
            .defineInRange("elemental.water.maelstrom.rotationSeconds", 2.0, 0.1, 5.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_ROTATION_SPEED = BUILDER
            .comment("Initial visual rotation speed in degrees per second.")
            .defineInRange("elemental.water.maelstrom.rotationDegreesPerSecond", 180.0, 30.0, 720.0);

    /** Maelstrom ascent and staged horizontal pull. */
    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_ASCENT_DURATION = BUILDER
            .comment("Seconds of slow ascent with weak suction after the initial rotation.")
            .defineInRange("elemental.water.maelstrom.ascentSeconds", 3.0, 0.1, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_ASCENT_HEIGHT = BUILDER
            .comment("Rise above the preparation position in blocks; water surface and solid ceilings limit ascent.")
            .defineInRange("elemental.water.maelstrom.ascentHeight", 3.0, 0.0, 8.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_MEDIUM_DURATION = BUILDER
            .comment("Seconds of medium suction and faster rotation after ascent.")
            .defineInRange("elemental.water.maelstrom.mediumPullSeconds", 2.0, 0.1, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_STRONG_DURATION = BUILDER
            .comment("Seconds of strongest suction and fastest rotation before impact.")
            .defineInRange("elemental.water.maelstrom.strongPullSeconds", 2.0, 0.1, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_WEAK_PULL = BUILDER
            .comment("Weak inward acceleration in blocks per tick squared during ascent; players can swim away.")
            .defineInRange("elemental.water.maelstrom.weakPullStrength", 0.01, 0.0, 0.1);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_MEDIUM_PULL = BUILDER
            .comment("Medium inward acceleration, intended to balance swimming against the current.",
                    "Capped below by weakPullStrength.")
            .defineInRange("elemental.water.maelstrom.mediumPullStrength", 0.02, 0.0, 0.1);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_STRONG_PULL = BUILDER
            .comment("Strong inward acceleration, intended to pull swimmers inward despite opposing input.",
                    "Capped below by mediumPullStrength.")
            .defineInRange("elemental.water.maelstrom.strongPullStrength", 0.04, 0.0, 0.1);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_PULL_SPEED_LIMIT = BUILDER
            .comment("Maximum inward speed reached through suction, in blocks per second.",
                    "Faster existing motion is not slowed; vertical and tangential motion remain unchanged.")
            .defineInRange("elemental.water.maelstrom.pullSpeedLimitBlocksPerSecond", 5.0, 0.1, 10.0);

    /** Maelstrom impact, outgoing wave and protected outer ring. */
    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_SLAM_SPEED = BUILDER
            .comment("Downward slam speed in blocks per second; solid terrain stops the descent.")
            .defineInRange("elemental.water.maelstrom.slamSpeedBlocksPerSecond", 24.0, 4.0, 64.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_WAVE_SPEED = BUILDER
            .comment("Outward pressure-wave speed in blocks per second across the flooded arena.")
            .defineInRange("elemental.water.maelstrom.waveSpeedBlocksPerSecond", 20.0, 5.0, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_TRANSPORT_SPEED = BUILDER
            .comment("Player transport speed toward the arena edge in blocks per second, preserving swimming height.")
            .defineInRange("elemental.water.maelstrom.transportSpeedBlocksPerSecond", 20.0, 2.0, 40.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_DAMAGE = BUILDER
            .comment("Normal mob damage per second from wave contact until the wave ends; 2 = one heart.",
                    "Each hit scales with damageIntervalSeconds; armor and shields apply normally.")
            .defineInRange("elemental.water.maelstrom.damagePerSecond", 7.32, 0.0, 20.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_DAMAGE_INTERVAL = BUILDER
            .comment("Seconds between wave damage pulses; minimum respects ordinary Minecraft damage immunity.")
            .defineInRange("elemental.water.maelstrom.damageIntervalSeconds", 0.5, 0.5, 2.0);

    public static final ForgeConfigSpec.DoubleValue WATER_MAELSTROM_SAFE_OUTER_WIDTH = BUILDER
            .comment("Width of the outer arena ring protected from wave damage and forced transport, in blocks.",
                    "Checked against current player position; reaching the ring releases affected players. 0 disables it.")
            .defineInRange("elemental.water.maelstrom.safeOuterWidth", 8.0, 0.0, 32.0);

    /** Shared recovery after stationary specials (elemental.water.special). */
    public static final ForgeConfigSpec.DoubleValue WATER_SPECIAL_RECOVERY = BUILDER
            .comment("Seconds without attacks after a beam or Maelstrom; the boss is vulnerable during recovery.",
                    "Movement resumes after the beam; Maelstrom holds the boss still until recovery ends.")
            .defineInRange("elemental.water.special.recoverySeconds", 2.0, 0.1, 5.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SPECIAL_DEFAULT_ONLY_MIN = BUILDER
            .comment("Minimum default-only seconds following recovery before another special may start.")
            .defineInRange("elemental.water.special.defaultOnlyMinSeconds", 5.0, 0.1, 20.0);

    public static final ForgeConfigSpec.DoubleValue WATER_SPECIAL_DEFAULT_ONLY_MAX = BUILDER
            .comment("Maximum default-only seconds following recovery; capped below by defaultOnlyMinSeconds.")
            .defineInRange("elemental.water.special.defaultOnlyMaxSeconds", 10.0, 0.1, 30.0);

    /** Water fountains (elemental.water.fountain). */
    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_COOLDOWN = BUILDER
            .comment("Seconds after the fountain phase ends before it may start again.")
            .defineInRange("elemental.water.fountain.cooldownSeconds", 20.0, 1.0, 120.0);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_DAMAGE = BUILDER
            .comment("One-time damage per player and fountain column in health points; 2 = one heart.")
            .defineInRange("elemental.water.fountain.damage", 6.10, 0.0, 20.0);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_DURATION_MIN = BUILDER
            .comment(
                    "Minimum fountain announcement duration. Active columns and transports finish before the cooldown starts.")
            .defineInRange("elemental.water.fountain.durationMinSeconds", 20.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_DURATION_MAX = BUILDER
            .comment("Maximum fountain announcement duration; capped below by durationMinSeconds.")
            .defineInRange("elemental.water.fountain.durationMaxSeconds", 30.0, 0.05, 60.0);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_INTERVAL_MIN = BUILDER
            .comment("Minimum independent delay between fountain announcements for each player and the arena.",
                    "A player's delay pauses until their previous fixed warning resolves.")
            .defineInRange("elemental.water.fountain.intervalMinSeconds", 2.0, 0.05, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_INTERVAL_MAX = BUILDER
            .comment("Maximum fountain delay in seconds; capped below by intervalMinSeconds.")
            .defineInRange("elemental.water.fountain.intervalMaxSeconds", 4.0, 0.05, 10.0);

    public static final ForgeConfigSpec.IntValue WATER_FOUNTAIN_ARENA_COUNT = BUILDER
            .comment("Random arena fountains announced per interval, in addition to each player's fountains.")
            .defineInRange("elemental.water.fountain.arenaCount", 3, 1, 16);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_WARNING = BUILDER
            .comment("Bubble warning seconds before a fixed floor-to-ceiling fountain erupts.")
            .defineInRange("elemental.water.fountain.warningSeconds", 1.5, 0.05, 5.0);

    public static final ForgeConfigSpec.IntValue WATER_FOUNTAIN_SIZE = BUILDER
            .comment("Width and depth of a fountain in blocks.")
            .defineInRange("elemental.water.fountain.size", 3, 1, 9);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_ACTIVE = BUILDER
            .comment("Seconds a fully erupted bubble column can hit players. Each player is hit once per column.")
            .defineInRange("elemental.water.fountain.activeSeconds", 3.0, 0.5, 8.0);

    public static final ForgeConfigSpec.DoubleValue WATER_FOUNTAIN_LIFT_SPEED = BUILDER
            .comment("Controlled ascent speed in blocks per second toward the captured arena ceiling.")
            .defineInRange("elemental.water.fountain.liftSpeedBlocksPerSecond", 6.0, 2.0, 12.0);

    /** Visibility inside boss water (elemental.water.visuals). */
    public static final ForgeConfigSpec.DoubleValue WATER_FOG_DISTANCE = BUILDER
            .comment("Underwater fog distance in boss water, capped by the client's render distance.")
            .defineInRange("elemental.water.visuals.fogDistance", 32.0, 16.0, 96.0);

    /** Water arena discovery, flooding and cleanup (elemental.water.arena). */
    public static final ForgeConfigSpec.IntValue WATER_ARENA_WATER_HEIGHT = BUILDER
            .comment("Water surface height in blocks above the saved arena origin.",
                    "Captured per arena; connected open cells below this common surface are flooded.")
            .defineInRange("elemental.water.arena.waterHeight", 12, 4, 24);

    public static final ForgeConfigSpec.IntValue WATER_ARENA_HEIGHT_RANGE = BUILDER
            .comment("Maximum flooded depth below the saved arena origin; also bounds ground-surface discovery.",
                    "Captured when arena discovery starts.")
            .defineInRange("elemental.water.arena.heightRange", 10, 0, 64);

    public static final ForgeConfigSpec.IntValue WATER_ARENA_STEP_HEIGHT = BUILDER
            .comment("Maximum height step when discovering adjacent ground positions for arena effects.",
                    "Captured when surface discovery starts. 0 restricts discovery to one level.")
            .defineInRange("elemental.water.arena.stepHeight", 1, 0, 4);

    public static final ForgeConfigSpec.IntValue WATER_ARENA_SEARCH_BUDGET_PER_SECOND = BUILDER
            .comment("Maximum volume and ground surface candidates examined per water Sheeper per second.",
                    "Unloaded chunks are deferred without being force-loaded.")
            .defineInRange("elemental.water.arena.searchCandidatesPerSecond", 40960, 20, 81920);

    public static final ForgeConfigSpec.IntValue WATER_ARENA_BLOCK_BUDGET_PER_SECOND = BUILDER
            .comment(
                    "Limits block positions processed per second when placing or removing arena water to reduce server load spikes.")
            .defineInRange("elemental.water.arena.blockUpdatesPerSecond", 20480, 20, 40960);

    public static final ForgeConfigSpec.DoubleValue WATER_ARENA_FLOOD_WAVE_SECONDS = BUILDER
            .comment("Seconds between outward flood layers through connected open space.",
                    "0 disables layer timing and uses only blockUpdatesPerSecond. Large layers still respect blockUpdatesPerSecond.")
            .defineInRange("elemental.water.arena.floodWaveIntervalSeconds", 0.0, 0.0, 10.0);

    public static final ForgeConfigSpec.DoubleValue WATER_ARENA_DRAIN_WAVE_SECONDS = BUILDER
            .comment("Seconds between cleanup layers, from the outermost descendants back toward the spawn.",
                    "0 disables layer timing. Unloaded cells are retried without blocking loaded cleanup.")
            .defineInRange("elemental.water.arena.drainWaveIntervalSeconds", 0.1, 0.0, 10.0);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private WaterSheeperConfig() {
    }
}
