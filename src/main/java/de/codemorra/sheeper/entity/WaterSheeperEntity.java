package de.codemorra.sheeper.entity;

import java.util.List;

import javax.annotation.Nullable;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.entity.goal.WaterOrbitGoal;
import de.codemorra.sheeper.entity.goal.WaterIdleSwimGoal;
import de.codemorra.sheeper.entity.navigation.WaterArenaNavigation;
import de.codemorra.sheeper.entity.navigation.WaterSwimMoveControl;
import de.codemorra.sheeper.world.WaterArenaData;
import de.codemorra.sheeper.world.ArenaWater;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
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
 * Owns virtual arena discovery and the shared movement/attack
 * target. Dedicated controllers handle projectile timing and proximity pushes.
 * A separate special scheduler owns beam timing and stationary movement locks.
 */
public class WaterSheeperEntity extends ElementalSheeperEntity {
    // Counteracts vanilla sheep water slowdown; user tuning uses navigation speed.
    private static final double SWIM_SPEED_MULTIPLIER = 4.0D;
    private static final int DEATH_ANIMATION_TICKS = 60;
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
    private static final EntityDataAccessor<Integer> DATA_DEATH_TICKS = SynchedEntityData
            .defineId(WaterSheeperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_MAELSTROM_ROTATION = SynchedEntityData
            .defineId(WaterSheeperEntity.class, EntityDataSerializers.FLOAT);

    @Nullable
    private BlockPos arenaCenter;
    @Nullable
    private final WaterAirStations airStations = new WaterAirStations();
    @Nullable
    private WaterArenaData.VolumeBounds arenaVolume;
    private boolean volumeInitialized;
    private boolean arenaConfigurationValid = true;

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
        entityData.define(DATA_DEATH_TICKS, 0);
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

    /** Captured water radius; live config changes must not move an established encounter's borders. */
    double getArenaRadius() {
        return arenaVolume == null ? getCombatRadius() : arenaVolume.radius();
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
        return ArenaWater.waterHeight(this) > 0 || isInWaterOrBubble();
    }

    /**
     * @return captured water surface Y used by attacks and transport
     */
    double getArenaWaterSurfaceY() {
        return arenaVolume == null ? getArenaCenter().getY() + WaterSheeperConfig.WATER_ARENA_WATER_HEIGHT.get()
                : arenaVolume.maximumY() + 1.0D;
    }

    /**
     * @return lowest captured water cell
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

    /** @return captured bounds for attacks, independent of overlapping arenas */
    @Nullable
    WaterArenaData.VolumeBounds getAttackVolume() {
        return arenaVolume;
    }

    /** Checks exact coordinates without extending attack reach into the combat transition zone. */
    boolean containsAttackPoint(Vec3 point) {
        return arenaVolume != null && arenaVolume.contains(point);
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
     * Ignores currents in ordinary water while preserving other
     * fluids' push behavior. Fluid drag and collision handling remain unchanged.
     *
     * @param type fluid attempting to push this entity
     * @return whether the fluid may apply its current to the boss
     */
    @Override
    public boolean isPushedByFluid(FluidType type) {
        // Arena currents must not push the boss back down flooded steps.
        return type != ForgeMod.WATER_TYPE.get() && super.isPushedByFluid(type);
    }

    /**
     * Provides at least one block of step clearance in arena water, shared by
     * collision movement and pathfinding. Larger attribute bonuses are retained.
     *
     * @return effective step height in blocks
     */
    @Override
    public float getStepHeight() {
        float height = super.getStepHeight();
        return isInArenaWater() ? Math.max(1.0F, height) : height;
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
        if (type == MoverType.SELF && isDeadOrDying()) {
            movement = Vec3.ZERO;
        }
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

    /** Shows the health bar around the captured arena, even when the boss is out of entity tracking range. */
    @Override
    protected void updateBossBarViewers(ServerBossEvent event) {
        if (!(level instanceof ServerLevel server) || arenaVolume == null) {
            return;
        }
        double distance = WaterSheeperConfig.WATER_BOSS_BAR_DISTANCE.get();
        double distanceSquared = distance * distance;
        for (ServerPlayer viewer : List.copyOf(event.getPlayers())) {
            if (viewer.level != server || viewer.isRemoved() || !isWithinBossBarRange(viewer, distanceSquared)) {
                event.removePlayer(viewer);
            }
        }
        for (ServerPlayer player : server.players()) {
            if (!event.getPlayers().contains(player) && isWithinBossBarRange(player, distanceSquared)) {
                event.addPlayer(player);
            }
        }
    }

    /** Measures the shortest distance from the player's position to the arena cylinder, including its caps. */
    private boolean isWithinBossBarRange(ServerPlayer player, double maxDistanceSquared) {
        double radial = Math.max(0, Math.sqrt(horizontalDistanceSquared(player.getX(), player.getZ()))
                - getArenaRadius());
        double vertical = Math.max(0, Math.max(getArenaWaterMinimumY() - player.getY(),
                player.getY() - getArenaWaterSurfaceY()));
        return radial * radial + vertical * vertical <= maxDistanceSquared;
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
        return horizontalDistanceSquared(x, z) <= getArenaRadius() * getArenaRadius();
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
     * Uses the same transition-edge qualification as targeting. Creative players
     * retain damage permission without becoming targets or stopping regeneration;
     * spectators and unqualified players remain excluded.
     *
     * @param player responsible player of a direct or indirect attack
     * @return whether the player is alive and inside this arena's combat bounds
     */
    boolean canPlayerDamage(ServerPlayer player) {
        return arenaPlayers.canDamage(player);
    }

    /**
     * Current server-side participants after submerged entry behind the inner
     * transition edge, retained until they cross the actual arena boundary.
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
                || level instanceof ServerLevel server && WaterArenaData.get(server).isReady(getUUID()));
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
     * Arena origin, air stations and the virtual water volume remain intact.
     */
    void resetArenaEncounter() {
        defaultAttack.reset();
        proximityPush.reset();
        specialAttacks.reset();
        setTarget(null);
        orbitGoal.reset();
        idleGoal.reset();
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
        if (existing == null) {
            Vec3 placement = WaterSpawnPlacement.find(server, this, combatSettings);
            if (placement == null) {
                SheeperMod.LOGGER.warn(
                        "Water Sheeper {}: spawn rejected at {}. No nearby position fits its scaled body and arena bounds.",
                        getUUID(), blockPosition());
                discard();
                return false;
            }
            if (!placement.equals(position())) {
                setPos(placement);
            }
            arenaCenter = blockPosition().immutable();
        } else {
            // A loaded encounter keeps its captured center even if its terrain changed.
            arenaCenter = existing.center();
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

    /** Keeps encounter validity and air-station placement tied to the captured arena. */
    private void advanceArenaPreparation() {
        boolean valid = combatSettings.hasArenaRoom(arenaVolume);
        if (arenaConfigurationValid && !valid) {
            specialAttacks.reset();
            defaultAttack.reset();
            proximityPush.reset();
        }
        arenaConfigurationValid = valid;
        if (arenaConfigurationValid) {
            airStations.tick((ServerLevel) level, arenaVolume, isCombatReady());
        }
    }

    @Override
    public void tick() {
        previousBeamEyeIntensity = entityData.get(DATA_BEAM_EYES);
        previousMaelstromRotation = entityData.get(DATA_MAELSTROM_ROTATION);
        if (!level.isClientSide) {
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

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && !level.isClientSide) {
            setTarget(null);
            specialAttacks.reset();
            stopMovement();
            setNoGravity(true);
        }
    }

    /**
     * Retains the arena while the body dissolves. Removal still uses the ordinary
     * killed reason, allowing the arena lifecycle event to perform the cleanup.
     */
    @Override
    protected void tickDeath() {
        int previousDeathTime = deathTime;
        deathTime = Math.min(DEATH_ANIMATION_TICKS, Math.max(deathTime + 1, entityData.get(DATA_DEATH_TICKS)));
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        if (level.isClientSide) {
            if (!isInvisible() && deathTime > previousDeathTime && deathTime < DEATH_ANIMATION_TICKS) {
                spawnDeathBubbles();
            }
        } else {
            entityData.set(DATA_DEATH_TICKS, deathTime);
            if (deathTime >= DEATH_ANIMATION_TICKS) {
                if (!isInvisible() && level instanceof ServerLevel server) {
                    // Send the final burst before removal, even if a client missed the last animation tick.
                    server.sendParticles(ParticleTypes.BUBBLE, getX(),
                            getY() + getBbHeight() * 0.5D - getDeathSinkOffset(0), getZ(),
                            48, getBbWidth() * 0.2D, getBbHeight() * 0.15D, getBbWidth() * 0.2D, 0.15D);
                }
                remove(RemovalReason.KILLED);
            }
        }
    }

    /** @return synchronized death progress from zero to one, interpolated for rendering */
    public float getDeathAnimationProgress(float partialTick) {
        return isDeadOrDying() ? Mth.clamp((Math.max(deathTime, entityData.get(DATA_DEATH_TICKS))
                + partialTick) / DEATH_ANIMATION_TICKS, 0, 1) : 0;
    }

    /** @return visual downward offset; collision and drops keep their original world positions */
    public double getDeathSinkOffset(float partialTick) {
        return 0.6D * getSizeMultiplier() * getDeathAnimationProgress(partialTick);
    }

    /** @return remaining body scale as the final part of the animation dissolves into bubbles */
    public float getDeathBodyScale(float partialTick) {
        float dissolve = Mth.clamp((getDeathAnimationProgress(partialTick) - 0.65F) / 0.35F, 0, 1);
        return Math.max(0.001F, 1 - dissolve * dissolve * (3 - 2 * dissolve));
    }

    /** Local bubbles grow denser around the sinking body before a final outward burst. */
    private void spawnDeathBubbles() {
        float progress = getDeathAnimationProgress(0);
        int count = 2 + (int) (progress * 10);
        double width = getBbWidth() * Math.max(0.25F, getDeathBodyScale(0));
        double height = getBbHeight() * 0.5D;
        double centerY = getY() + height - getDeathSinkOffset(0);
        for (int index = 0; index < count; index++) {
            double dx = (random.nextDouble() - 0.5D) * width;
            double dz = (random.nextDouble() - 0.5D) * width;
            level.addParticle(ParticleTypes.BUBBLE, getX() + dx,
                    centerY + (random.nextDouble() - 0.5D) * height, getZ() + dz,
                    dx * 0.08D, 0.1D + progress * 0.1D, dz * 0.08D);
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
        tag.put("WaterAirStations", airStations.save());
        tag.putLong("WaterArenaCenter", getArenaCenter().asLong());
        tag.putString("WaterArenaDimension", level.dimension().location().toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_COMBAT_READY, false);
        entityData.set(DATA_DEATH_TICKS, Mth.clamp(deathTime, 0, DEATH_ANIMATION_TICKS));
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
        airStations.load(tag.getCompound("WaterAirStations"));
        if (tag.contains("WaterArenaDimension", Tag.TAG_STRING)
                && !tag.getString("WaterArenaDimension").equals(level.dimension().location().toString())) {
            arenaCenter = null;
            airStations.load(new CompoundTag());
        }
        // Restore captured bounds immediately for the first combat tick.
        arenaVolume = level instanceof ServerLevel server ? WaterArenaData.get(server).getVolume(getUUID()) : null;
        volumeInitialized = false;
    }
}
