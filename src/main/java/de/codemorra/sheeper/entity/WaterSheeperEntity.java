package de.codemorra.sheeper.entity;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nullable;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.goal.WaterOrbitGoal;
import de.codemorra.sheeper.entity.goal.WaterIdleSwimGoal;
import de.codemorra.sheeper.entity.navigation.WaterArenaNavigation;
import de.codemorra.sheeper.entity.navigation.WaterSwimMoveControl;
import de.codemorra.sheeper.registry.ModFluids;
import de.codemorra.sheeper.world.WaterArenaData;
import de.codemorra.sheeper.world.ArenaWater;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;

/**
 * Owns arena discovery, tracked water placement and the shared movement/attack
 * target. Dedicated controllers handle projectile timing and proximity pushes.
 * A separate special scheduler owns beam timing and stationary movement locks.
 */
public class WaterSheeperEntity extends ElementalSheeperEntity {
    // Counteracts vanilla sheep water slowdown; user tuning uses navigation speed.
    private static final double SWIM_SPEED_MULTIPLIER = 4.0D;
    private static final int SURFACE_DISCOVERY_STEP_HEIGHT = 1;
    public static final double BEAM_RADIUS = 0.25D;
    private static final EntityDataAccessor<Float> DATA_BEAM_EYES = SynchedEntityData
            .defineId(WaterSheeperEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_X = SynchedEntityData
            .defineId(WaterSheeperEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_Y = SynchedEntityData
            .defineId(WaterSheeperEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_Z = SynchedEntityData
            .defineId(WaterSheeperEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_COMBAT_READY = SynchedEntityData
            .defineId(WaterSheeperEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_MAELSTROM_ROTATION = SynchedEntityData
            .defineId(WaterSheeperEntity.class, EntityDataSerializers.FLOAT);

    @Nullable
    private BlockPos arenaCenter;
    @Nullable
    private WaterArenaSearch arenaSearch;
    @Nullable
    private WaterArenaVolumeSearch volumeSearch;
    @Nullable
    private WaterArenaData.VolumeBounds arenaVolume;
    private boolean volumeInitialized;
    private boolean arenaConfigurationValid = true;
    @Nullable
    private Queue<BlockPos> waterPlacement;
    private int placementWave = -1;
    private long nextFloodWaveTick;

    private final WaterCombatSettings combatSettings = new WaterCombatSettings(this);
    private final WaterDefaultAttack defaultAttack = new WaterDefaultAttack(this);
    private final WaterProximityPush proximityPush = new WaterProximityPush(this);
    private final WaterArenaCombat arenaCombat = new WaterArenaCombat(this);
    private final WaterArenaPlayers arenaPlayers = new WaterArenaPlayers(this);
    private final WaterSpecialAttacks specialAttacks = new WaterSpecialAttacks(this);
    private float previousBeamEyeIntensity;
    private float previousMaelstromRotation;
    private WaterOrbitGoal orbitGoal;
    private WaterIdleSwimGoal idleGoal;

    public WaterSheeperEntity(EntityType<? extends Sheep> type, Level level) {
        super(type, level, Element.WATER);
        moveControl = new WaterSwimMoveControl(this);
        getNavigation().setCanFloat(true);
        // This boss lives in water; ordinary sheep water avoidance must not apply.
        setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        setPathfindingMalus(BlockPathTypes.WATER_BORDER, 0.0F);
        getAttribute(ForgeMod.SWIM_SPEED.get()).setBaseValue(SWIM_SPEED_MULTIPLIER);
    }

    /**
     * Includes the encounter's player-count bonus, retaining solo health during
     * base construction.
     */
    @Override
    protected double getConfiguredMaxHealth() {
        return arenaCombat == null ? super.getConfiguredMaxHealth() : arenaCombat.getScaledMaxHealth();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_COMBAT_READY, false);
        entityData.define(DATA_MAELSTROM_ROTATION, 0.0F);
        entityData.define(DATA_BEAM_EYES, 0.0F);
        entityData.define(DATA_BEAM_X, 0.0F);
        entityData.define(DATA_BEAM_Y, 0.0F);
        entityData.define(DATA_BEAM_Z, 0.0F);
    }

    /** @return interpolated red-eye warning intensity for client presentation */
    public float getBeamEyeIntensity(float partialTick) {
        return Mth.lerp(partialTick, previousBeamEyeIntensity, entityData.get(DATA_BEAM_EYES));
    }

    /** Updates the warning intensity on the server for all tracking clients. */
    void setBeamEyeIntensity(float intensity) {
        entityData.set(DATA_BEAM_EYES, Mth.clamp(intensity, 0, 1));
    }

    /**
     * @return whether beam warning or firing currently locks swimming movement
     */
    public boolean isPerformingBeam() {
        return specialAttacks.isBeamActive();
    }

    /**
     * @return whether Maelstrom currently owns movement and attack scheduling
     */
    public boolean isPerformingMaelstrom() {
        return specialAttacks.isMaelstromActive();
    }

    /**
     * @return interpolated visual rotation, independent of navigation and head
     *         tracking
     */
    public float getMaelstromRotation(float partialTick) {
        float rotation = entityData.get(DATA_MAELSTROM_ROTATION);
        // Clearing the effect must not interpolate backward through completed turns.
        return rotation == 0 ? 0 : Mth.lerp(partialTick, previousMaelstromRotation, rotation);
    }

    /**
     * Synchronizes unwrapped rotation so crossing a full turn cannot reverse
     * interpolation.
     */
    void setMaelstromRotation(float degrees) {
        entityData.set(DATA_MAELSTROM_ROTATION, degrees);
    }

    /** @return mouth-height beam origin, scaled with the boss's body */
    public Vec3 getBeamOrigin() {
        return getEyePosition().add(0, -0.15D * getSizeMultiplier(), 0);
    }

    /** @return server-authoritative collision segment relative to the mouth */
    public Vec3 getBeamOffset() {
        return new Vec3(entityData.get(DATA_BEAM_X), entityData.get(DATA_BEAM_Y), entityData.get(DATA_BEAM_Z));
    }

    /** Synchronizes the collision segment; a zero vector hides the beam. */
    void setBeamOffset(Vec3 offset) {
        entityData.set(DATA_BEAM_X, (float) offset.x);
        entityData.set(DATA_BEAM_Y, (float) offset.y);
        entityData.set(DATA_BEAM_Z, (float) offset.z);
    }

    /**
     * Stops swimming at the current depth while retaining ordinary falling physics
     * outside water.
     */
    private void stopMovement() {
        getNavigation().stop();
        Vec3 velocity = getDeltaMovement();
        setDeltaMovement(0.0D, isInArenaWater() ? 0 : velocity.y, 0.0D);
        setYya(0);
        setXxa(0.0F);
        setZza(0.0F);
    }

    /**
     * Returns the fixed arena origin on the logical server. A new boss captures
     * its placed position on the first tick, or earlier if queried or saved.
     */
    public BlockPos getArenaCenter() {
        if (arenaCenter == null) {
            arenaCenter = blockPosition().immutable();
        }
        return arenaCenter;
    }

    /**
     * Horizontal arena and combat radius in blocks around the saved origin.
     */
    public double getCombatRadius() {
        return WaterSheeperConfig.WATER_COMBAT_RADIUS.get();
    }

    /**
     * @return a stopping distance compatible with melee entry and enabled proximity
     *         pushes
     */
    public double getMeleeStopDistance() {
        return combatSettings.meleeStopDistance();
    }

    /**
     * @return the safe outer width while retaining at least half the radius as
     *         dangerous
     */
    double getMaelstromSafeOuterWidth() {
        return combatSettings.safeOuterWidth();
    }

    /**
     * Derives the movement radius from body size and the configured clearance to
     * the arena edge.
     */
    public double getMovementRadius() {
        double waterRadius = arenaVolume == null ? getCombatRadius()
                : Math.min(getCombatRadius(), arenaVolume.radius());
        double bodyMargin = getBbWidth() * Math.sqrt(0.5D) + 0.25D;
        return Math.max(0, waterRadius - bodyMargin - WaterSheeperConfig.WATER_MOVEMENT_EDGE_DISTANCE.get());
    }

    /**
     * @return whether the boss currently uses ordinary or custom arena-water
     *         physics
     */
    public boolean isInArenaWater() {
        return ArenaWater.waterHeight(this) > 0 || isInWaterOrBubble()
                || isInFluidType(ModFluids.BOSS_WATER_TYPE.get());
    }

    /**
     * @return captured water surface Y used by full-height fountain columns and
     *         transport
     */
    double getArenaWaterSurfaceY() {
        return arenaVolume == null ? getArenaCenter().getY() + WaterSheeperConfig.WATER_ARENA_WATER_HEIGHT.get()
                : arenaVolume.maximumY() + 1.0D;
    }

    /**
     * @return lowest captured water cell at which a fountain may search for
     *         supporting ground
     */
    int getArenaWaterMinimumY() {
        return arenaVolume == null ? getArenaCenter().getY() - WaterSheeperConfig.WATER_ARENA_HEIGHT_RANGE.get()
                : arenaVolume.minimumY();
    }

    /**
     * Checks captured volume geometry independently of the boss's smaller swimming
     * area.
     */
    boolean isArenaWaterCell(BlockPos pos) {
        return arenaVolume != null && arenaVolume.contains(pos);
    }

    /**
     * Maintains neutral buoyancy in water without changing the persistent gravity
     * flag. On land, gravity and fall behavior remain vanilla.
     */
    @Override
    public Vec3 getFluidFallingAdjustedMovement(double gravity, boolean falling, Vec3 movement) {
        return isInArenaWater() ? movement : super.getFluidFallingAdjustedMovement(gravity, falling, movement);
    }

    /**
     * Projects a requested feet position into the swim volume, accounting for the
     * boss's height and the inset needed at the top water surface.
     *
     * @param point desired world-space feet position
     * @return position inside the captured horizontal and vertical swim limits
     */
    public Vec3 clampSwimmingPosition(Vec3 point) {
        double centerX = getArenaCenter().getX() + 0.5D;
        double centerZ = getArenaCenter().getZ() + 0.5D;
        Vec3 horizontal = new Vec3(point.x - centerX, 0, point.z - centerZ);
        double radius = getMovementRadius();
        if (horizontal.lengthSqr() > radius * radius) {
            horizontal = horizontal.normalize().scale(radius);
        }
        double minimum = arenaVolume == null ? getArenaCenter().getY() : arenaVolume.minimumY();
        double maximum = arenaVolume == null ? minimum
                : Math.max(minimum, arenaVolume.maximumY() + 1 - getBbHeight() - 0.15D);
        return new Vec3(centerX + horizontal.x, Mth.clamp(point.y, minimum, maximum), centerZ + horizontal.z);
    }

    /**
     * Checks a submerged waypoint against loaded water cells and block collision
     * for the whole scaled body. Used by both waypoint selection and swim nodes.
     *
     * @param point proposed world-space feet position
     * @return whether the boss fits completely inside water and the arena at this
     *         position
     */
    public boolean canSwimAt(Vec3 point) {
        if (arenaVolume == null || !(level instanceof ServerLevel server)) {
            return false;
        }
        // Increasing edge clearance must not strand a boss already near the old
        // boundary. Nodes outside the new movement area may lead it back inward.
        double allowedRadiusSquared = Math.max(getMovementRadius() * getMovementRadius(),
                horizontalDistanceSquared(getX(), getZ()));
        if (Math.abs(point.y - clampSwimmingPosition(point).y) > 1.0E-4D
                || horizontalDistanceSquared(point.x, point.z) > allowedRadiusSquared + 1.0E-6D) {
            return false;
        }
        AABB body = getBoundingBox().move(point.subtract(position())).deflate(0.01D);
        for (BlockPos cell : BlockPos.betweenClosed(new BlockPos(body.minX, body.minY, body.minZ),
                new BlockPos(body.maxX, body.maxY, body.maxZ))) {
            if (!WaterArenaData.isLoaded(server, cell)) {
                return false;
            }
            if (!arenaVolume.contains(cell)) {
                return false;
            }
        }
        return !level.getBlockCollisions(this, body).iterator().hasNext();
    }

    /**
     * Ignores currents in ordinary water and boss water while preserving other
     * fluids' push behavior. Fluid drag and collision handling remain unchanged.
     *
     * @param type fluid attempting to push this entity
     * @return whether the fluid may apply its current to the boss
     */
    @Override
    public boolean isPushedByFluid(FluidType type) {
        // Arena currents must not push the boss back down flooded steps.
        return type != ModFluids.BOSS_WATER_TYPE.get() && type != ForgeMod.WATER_TYPE.get()
                && super.isPushedByFluid(type);
    }

    /**
     * Provides at least one block of step clearance in boss water, shared by
     * collision movement and pathfinding. Larger attribute bonuses are retained.
     *
     * @return effective step height in blocks
     */
    @Override
    public float getStepHeight() {
        float height = super.getStepHeight();
        return isInFluidType(ModFluids.BOSS_WATER_TYPE.get()) ? Math.max(1.0F, height) : height;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterArenaNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        orbitGoal = new WaterOrbitGoal(this);
        goalSelector.addGoal(0, orbitGoal);
        idleGoal = new WaterIdleSwimGoal(this);
        goalSelector.addGoal(1, idleGoal);
    }

    /**
     * Exposes the default attack's close-range mode to navigation without giving
     * the movement goal control over projectile timing or target selection.
     *
     * @return whether the boss should approach rather than circle its target
     */
    public boolean isClosingOnTarget() {
        return defaultAttack.isClosingOnTarget();
    }

    /** Checks a horizontal circle around the center of the saved spawn block. */
    public boolean isWithinMovementArea(double x, double z) {
        return horizontalDistanceSquared(x, z) <= getMovementRadius() * getMovementRadius();
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (type == MoverType.SELF && !level.isClientSide
                && (!isCombatReady() || isNoAi() || specialAttacks.locksMovement())) {
            movement = isInArenaWater() ? Vec3.ZERO : new Vec3(0.0D, movement.y, 0.0D);
        }
        if (!level.isClientSide && isAlive() && (type == MoverType.SELF || isCombatReady())) {
            double dx = getX() - getArenaCenter().getX() - 0.5D;
            double dz = getZ() - getArenaCenter().getZ() - 0.5D;
            // Leave a numerical margin when converting back to world coordinates.
            // Outside the configured area, allow recovery without moving farther out.
            double radius = Math.max(Math.max(0.0D, getMovementRadius() - 1.0E-7D), Math.sqrt(dx * dx + dz * dz));
            double radiusSquared = radius * radius;
            double limitX = Math.sqrt(Math.max(0.0D, radiusSquared - dz * dz));
            double nextX = Mth.clamp(dx + movement.x, -limitX, limitX);
            // Keep the whole movement rectangle inside the circle, so sliding along
            // a collision cannot carry the boss outside when one axis is blocked.
            double limitZ = Math.sqrt(Math.max(0.0D, radiusSquared - Math.max(dx * dx, nextX * nextX)));
            double nextZ = Mth.clamp(dz + movement.z, -limitZ, limitZ);
            movement = new Vec3(nextX - dx, movement.y, nextZ - dz);
            if (arenaVolume != null && isCombatReady()) {
                double minimumY = Math.min(getY(), arenaVolume.minimumY());
                double top = Math.max(arenaVolume.minimumY(), arenaVolume.maximumY() + 1 - getBbHeight() - 0.15D);
                double maximumY = Math.max(getY(), top);
                movement = new Vec3(movement.x, Mth.clamp(getY() + movement.y, minimumY, maximumY) - getY(),
                        movement.z);
            }
        }
        super.move(type, movement);
    }

    private double horizontalDistanceSquared(double x, double z) {
        BlockPos center = getArenaCenter();
        double dx = x - (center.getX() + 0.5D);
        double dz = z - (center.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    /** Checks horizontal membership independently of height or game mode. */
    boolean isInsideArenaColumn(ServerPlayer player) {
        return player.level == level && isInsideArenaColumn(player.getX(), player.getZ());
    }

    /**
     * Checks a correction destination against the same circle used for
     * participation.
     */
    boolean isInsideArenaColumn(double x, double z) {
        return horizontalDistanceSquared(x, z) <= getCombatRadius() * getCombatRadius();
    }

    /**
     * Enforces vertical water bounds after all player movement in the server tick.
     */
    public void enforceArenaPlayerBounds() {
        if (!level.isClientSide && isAlive() && isCombatReady()) {
            arenaPlayers.enforceVerticalBounds();
        }
    }

    /**
     * Grants damage permission independently of combat participation. Creative
     * players may attack inside the arena without becoming targets or stopping
     * regeneration; spectators and outside players remain excluded.
     *
     * @param player responsible player of a direct or indirect attack
     * @return whether the player is alive and inside this arena's combat bounds
     */
    boolean canPlayerDamage(ServerPlayer player) {
        return isAlive() && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && (player.isCreative() ? isInsideArenaColumn(player)
                        && player.getEyeY() < getArenaWaterSurfaceY() : arenaPlayers.contains(player));
    }

    private boolean isActivePlayer(ServerPlayer player) {
        return player.level == level && player.isAlive() && !player.isRemoved()
                && !player.isCreative() && !player.isSpectator();
    }

    /**
     * Current server-side participants after submerged entry, independent of
     * subsequent height changes or sight.
     */
    public List<ServerPlayer> getCombatParticipants() {
        if (!(level instanceof ServerLevel server) || !isAlive()) {
            return List.of();
        }
        return server.players().stream()
                .filter(arenaPlayers::contains)
                .toList();
    }

    /**
     * Advances default attack target selection before the AI tick so movement and
     * shooting share a valid participant. Head tracking remains separate from the
     * body's navigation direction, including during firing pauses.
     */
    private void updateMovementTarget() {
        if (!isAlive() || !isCombatReady() || isNoAi()) {
            setTarget(null);
            stopMovement();
            return;
        }
        List<ServerPlayer> participants = getCombatParticipants();
        specialAttacks.tickTimers(participants, defaultAttack);
        ServerPlayer target = defaultAttack.tickTarget(participants, !specialAttacks.suppressesDefault());
        setTarget(target);
        // An empty arena leaves navigation to the idle goal instead of cancelling
        // its current route every tick. Goal priorities handle combat transitions.
        if (target != null) {
            specialAttacks.tryStart(target, defaultAttack);
            if (isPerformingMaelstrom()) {
                return;
            }
            if (isPerformingBeam()) {
                stopMovement();
                Vec3 aim = specialAttacks.getAimPosition();
                getLookControl().setLookAt(aim.x, aim.y, aim.z, 90.0F, 45.0F);
                return;
            }
            // Look control turns the head without overwriting navigation's body yaw.
            getLookControl().setLookAt(target, 90.0F, 45.0F);
        }
    }

    @Override
    public boolean isCombatReady() {
        return arenaConfigurationValid && (entityData.get(DATA_COMBAT_READY)
                || level instanceof ServerLevel server && WaterArenaData.get(server).isGenerated(getUUID()));
    }

    /**
     * Rejects combat damage unless its responsible player has arena damage
     * permission. Administrative removal and the void retain vanilla handling.
     *
     * @param source incoming damage source, including indirect attack ownership
     * @return whether this source is blocked by vanilla or arena rules
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source)
                || source != DamageSource.OUT_OF_WORLD
                        && (specialAttacks.isMaelstromImmune() || !arenaCombat.allowsDamage(source));
    }

    /**
     * Restores attack and movement controllers after full empty-arena recovery.
     * Arena origin, discovered terrain and existing water remain intact.
     */
    void resetArenaEncounter() {
        defaultAttack.reset();
        proximityPush.reset();
        specialAttacks.reset();
        setTarget(null);
        orbitGoal.reset();
        idleGoal.reset();
    }

    public boolean isArenaSearchComplete() {
        return arenaSearch != null && arenaSearch.isComplete();
    }

    /**
     * Server-side, read-only surface cells, potentially incomplete during
     * discovery.
     */
    public Set<BlockPos> getArenaSurfacePositions() {
        return arenaSearch == null ? Set.of() : arenaSearch.getSurfacePositions();
    }

    /**
     * Captures and validates encounter geometry independently of water placement.
     * Existing captured bounds survive unloading and retain their original origin.
     *
     * @return whether this boss has a usable captured volume
     */
    private boolean initializeArenaVolume() {
        if (volumeInitialized) {
            return true;
        }
        ServerLevel server = (ServerLevel) level;
        WaterArenaData data = WaterArenaData.get(server);
        WaterArenaData.VolumeBounds existing = data.getVolume(getUUID());
        WaterArenaData.VolumeBounds proposed = existing == null ? WaterArenaData.VolumeBounds.capture(
                server, getArenaCenter(), getCombatRadius(),
                WaterSheeperConfig.WATER_ARENA_HEIGHT_RANGE.get(),
                WaterSheeperConfig.WATER_ARENA_WATER_HEIGHT.get()) : existing;
        // Validate before creating saved arena state or placing any water.
        // A loaded encounter may occupy terrain changed after its initial spawn.
        if (!proposed.isValidFor(server)) {
            SheeperMod.LOGGER.warn(
                    "Water Sheeper {}: spawn rejected at {} because its arena bounds are outside the world.",
                    getUUID(), getArenaCenter());
            discard();
            return false;
        }
        if (existing == null && !combatSettings.canSpawn(proposed)) {
            discard();
            return false;
        }
        WaterArenaData.VolumeBounds bounds = data.prepareVolume(server, getUUID(), getArenaCenter(),
                getCombatRadius(), WaterSheeperConfig.WATER_ARENA_HEIGHT_RANGE.get(),
                WaterSheeperConfig.WATER_ARENA_WATER_HEIGHT.get());
        if (bounds == null) {
            // prepareVolume also protects callers that do not perform spawn checks.
            discard();
            return false;
        }
        arenaVolume = bounds;
        // Combat stays paused until the saved startup clock has filled the virtual arena.
        volumeInitialized = true;
        entityData.set(DATA_COMBAT_READY, false);
        return true;
    }

    /** Advances optional floor discovery without filling or changing terrain. */
    private void advanceArenaPreparation() {
        boolean valid = combatSettings.hasArenaRoom(arenaVolume);
        if (arenaConfigurationValid && !valid) {
            specialAttacks.reset();
            defaultAttack.reset();
            proximityPush.reset();
        }
        arenaConfigurationValid = valid;
        if (arenaConfigurationValid) {
            if (arenaSearch == null) {
                arenaSearch = new WaterArenaSearch(level, getArenaCenter(), getCombatRadius(),
                        WaterSheeperConfig.WATER_ARENA_HEIGHT_RANGE.get(),
                        SURFACE_DISCOVERY_STEP_HEIGHT, getArenaWaterMinimumY());
            }
            arenaSearch.advance(WaterSheeperConfig.WATER_ARENA_SEARCH_BUDGET_PER_SECOND.get() / 20);
        }
    }

    @Override
    public void tick() {
        previousBeamEyeIntensity = entityData.get(DATA_BEAM_EYES);
        previousMaelstromRotation = entityData.get(DATA_MAELSTROM_ROTATION);
        if (!level.isClientSide) {
            // Capture placement before gravity or future AI can move the boss.
            getArenaCenter();
            if (isAlive()) {
                if (!initializeArenaVolume()) {
                    return;
                }
                advanceArenaPreparation();
                entityData.set(DATA_COMBAT_READY, isCombatReady());
                if (!isCombatReady()) {
                    stopMovement();
                }
            }
            getAttribute(ForgeMod.SWIM_SPEED.get()).setBaseValue(SWIM_SPEED_MULTIPLIER);
            updateMovementTarget();
        }
        super.tick();
        if (!level.isClientSide && isAlive() && isCombatReady()) {
            if (!isNoAi()) {
                arenaCombat.tick(getCombatParticipants());
                specialAttacks.tickAttack();
                if (!specialAttacks.suppressesDefault()) {
                    defaultAttack.tickProjectiles();
                    proximityPush.tick(getCombatParticipants());
                }
            }
        }
        if (level.isClientSide && isAlive() && !isInvisible() && tickCount % 2 == 0) {
            spawnBeamParticles();
        }
    }

    /** Adds bounded, forward-moving bubbles along the synchronized water tube. */
    private void spawnBeamParticles() {
        Vec3 offset = getBeamOffset();
        if (offset.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 motion = offset.normalize().scale(1.5D);
        int count = Math.min(24, (int) Math.ceil(offset.length() / 2));
        for (int index = 0; index < count; index++) {
            Vec3 point = getBeamOrigin().add(offset.scale((index + random.nextDouble()) / count));
            level.addParticle(ParticleTypes.BUBBLE,
                    point.x + random.nextGaussian() * BEAM_RADIUS * 0.6D,
                    point.y + random.nextGaussian() * BEAM_RADIUS * 0.6D,
                    point.z + random.nextGaussian() * BEAM_RADIUS * 0.6D, motion.x, motion.y, motion.z);
        }
        if (tickCount % 4 == 0) {
            spawnBeamImpactBubbles(offset);
        }
    }

    /**
     * Shows an impact only when the synchronized tip actually touches solid
     * terrain.
     */
    private void spawnBeamImpactBubbles(Vec3 offset) {
        Vec3 tip = getBeamOrigin().add(offset);
        Vec3 step = offset.normalize().scale(0.2D);
        var hit = level.clip(new ClipContext(tip.subtract(step), tip.add(step),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Vec3 outward = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 point = hit.getLocation().add(outward.scale(0.12D));
        for (int index = 0; index < 6; index++) {
            level.addParticle(ParticleTypes.BUBBLE,
                    point.x + random.nextGaussian() * 0.05D,
                    point.y + random.nextGaussian() * 0.05D,
                    point.z + random.nextGaussian() * 0.05D,
                    outward.x * 0.6D, outward.y * 0.6D, outward.z * 0.6D);
        }
    }

    private void placeArenaWater(ServerLevel level) {
        WaterArenaData data = WaterArenaData.get(level);
        if (data.isGenerated(getUUID())) {
            return;
        }
        if (waterPlacement == null) {
            waterPlacement = new ArrayDeque<>();
        }
        BlockPos discovered;
        while ((discovered = volumeSearch.pollDiscoveredCell()) != null) {
            waterPlacement.add(discovered);
        }
        int interval = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_ARENA_FLOOD_WAVE_SECONDS.get());
        long now = level.getGameTime();
        if (interval > 0 && !waterPlacement.isEmpty()
                && volumeSearch.getDistance(waterPlacement.peek()) != placementWave) {
            if (now < nextFloodWaveTick) {
                return;
            }
            placementWave = volumeSearch.getDistance(waterPlacement.peek());
        }
        int candidates = Math.min(waterPlacement.size(),
                WaterSheeperConfig.WATER_ARENA_BLOCK_BUDGET_PER_SECOND.get() / 20);
        for (int examined = 0; examined < candidates; examined++) {
            BlockPos pos = waterPlacement.peek();
            int distance = volumeSearch.getDistance(pos);
            if (interval > 0 && distance != placementWave || !WaterArenaData.isLoaded(level, pos)) {
                break;
            }
            waterPlacement.remove();
            data.claim(level, getUUID(), pos, distance);
        }
        if (interval > 0 && !waterPlacement.isEmpty()
                && volumeSearch.getDistance(waterPlacement.peek()) != placementWave) {
            nextFloodWaveTick = now + interval;
        }
        if (waterPlacement.isEmpty() && volumeSearch.isComplete()) {
            data.finishGeneration(getUUID());
            waterPlacement = null;
            // The large volume search is only needed during generation, not throughout
            // combat.
            volumeSearch = null;
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && level instanceof ServerLevel server) {
            setTarget(null);
            specialAttacks.reset();
            stopMovement();
            WaterArenaData.get(server).release(getUUID());
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("WaterDefaultAttack", defaultAttack.save());
        tag.put("WaterProximityPush", proximityPush.save());
        tag.put("WaterArenaCombat", arenaCombat.save());
        tag.put("WaterArenaPlayers", arenaPlayers.save());
        tag.put("WaterSpecialAttacks", specialAttacks.save());
        tag.putLong("WaterArenaCenter", getArenaCenter().asLong());
        tag.putString("WaterArenaDimension", level.dimension().location().toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_COMBAT_READY, false);
        defaultAttack.load(tag.getCompound("WaterDefaultAttack"));
        proximityPush.load(tag.getList("WaterProximityPush", Tag.TAG_COMPOUND));
        arenaCombat.load(tag.getCompound("WaterArenaCombat"));
        arenaPlayers.load(tag.getList("WaterArenaPlayers", Tag.TAG_COMPOUND));
        specialAttacks.load(tag.getCompound("WaterSpecialAttacks"));
        setTarget(null);
        // Existing bosses without an origin establish one at their loaded position.
        arenaCenter = tag.contains("WaterArenaCenter", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("WaterArenaCenter"))
                : null;
        if (tag.contains("WaterArenaDimension", Tag.TAG_STRING)
                && !tag.getString("WaterArenaDimension").equals(level.dimension().location().toString())) {
            arenaCenter = null;
        }
        // Derived surface data is rediscovered from the saved origin after loading.
        arenaSearch = null;
        volumeSearch = null;
        arenaVolume = null;
        volumeInitialized = false;
        waterPlacement = null;
        placementWave = -1;
        nextFloodWaveTick = 0;
    }
}
