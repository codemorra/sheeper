package de.codemorra.sheeper.entity;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.goal.WaterOrbitGoal;
import de.codemorra.sheeper.entity.navigation.WaterArenaNavigation;
import de.codemorra.sheeper.entity.navigation.WaterSwimMoveControl;
import de.codemorra.sheeper.fluid.BossWaterFluid;
import de.codemorra.sheeper.registry.ModFluids;
import de.codemorra.sheeper.world.WaterArenaData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
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
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;

/**
 * Owns arena discovery, tracked water placement and the shared movement/attack
 * target. Dedicated controllers handle projectile timing and proximity pushes.
 * A separate special scheduler owns beam timing and stationary movement locks.
 */
public class WaterSheeperEntity extends ElementalSheeperEntity {
    // Temporary underwater test stage: default projectiles only; specials and
    // proximity/spawn knockback stay disabled until their separate in-game checks.
    private static final boolean PROJECTILE_TEST_MODE = true;
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

    @Nullable
    private BlockPos arenaCenter;
    @Nullable
    private WaterArenaSearch arenaSearch;
    @Nullable
    private WaterArenaVolumeSearch volumeSearch;
    @Nullable
    private WaterArenaData.VolumeBounds arenaVolume;
    private boolean volumeInitialized;
    @Nullable
    private Queue<BlockPos> waterPlacement;
    private int placementWave = -1;
    private long nextFloodWaveTick;
    private final Set<UUID> waterWaveContacts = new HashSet<>();
    private final Map<UUID, Integer> waterWaveDelays = new HashMap<>();

    private final WaterDefaultAttack defaultAttack = new WaterDefaultAttack(this);
    private final WaterProximityPush proximityPush = new WaterProximityPush(this);
    private final WaterArenaCombat arenaCombat = new WaterArenaCombat(this);
    private final WaterArenaBoundary arenaBoundary = new WaterArenaBoundary(this);
    private final WaterSpecialAttacks specialAttacks = new WaterSpecialAttacks(this);
    private float previousBeamEyeIntensity;
    private WaterOrbitGoal orbitGoal;

    public WaterSheeperEntity(EntityType<? extends Sheep> type, Level level) {
        super(type, level, Element.WATER);
        moveControl = new WaterSwimMoveControl(this);
        getNavigation().setCanFloat(true);
        // This boss lives in water; ordinary sheep water avoidance must not apply.
        setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        setPathfindingMalus(BlockPathTypes.WATER_BORDER, 0.0F);
        getAttribute(ForgeMod.SWIM_SPEED.get()).setBaseValue(SheeperConfig.WATER_MOVEMENT_SWIM_SPEED.get());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_COMBAT_READY, false);
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
     * @return whether beam warning or firing currently locks horizontal movement
     */
    public boolean isPerformingBeam() {
        return specialAttacks.isBeamActive();
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
        return SheeperConfig.WATER_COMBAT_RADIUS.get();
    }

    /**
     * Derives the movement radius from body size and the configured clearance to
     * the arena edge.
     */
    public double getMovementRadius() {
        double waterRadius = arenaVolume == null ? getCombatRadius()
                : Math.min(getCombatRadius(), arenaVolume.radius());
        double bodyMargin = getBbWidth() * Math.sqrt(0.5D) + 0.25D;
        return Math.max(0, waterRadius - bodyMargin - SheeperConfig.WATER_MOVEMENT_EDGE_DISTANCE.get());
    }

    /**
     * @return whether the boss currently uses ordinary or custom arena-water
     *         physics
     */
    public boolean isInArenaWater() {
        return isInWaterOrBubble() || isInFluidType(ModFluids.BOSS_WATER_TYPE.get());
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
            var fluid = level.getFluidState(cell);
            // Only the head cell needs a surface-height check. Stacked ordinary
            // and boss water can report different heights at their shared seam.
            if (!fluid.is(FluidTags.WATER) || body.maxY <= cell.getY() + 1
                    && cell.getY() + fluid.getHeight(level, cell) < body.maxY - 0.01D) {
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
        if (type == MoverType.SELF && !level.isClientSide && (!isCombatReady() || isNoAi() || isPerformingBeam())) {
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

    private boolean isEligiblePlayer(ServerPlayer player, double radius, double heightRange) {
        return isActivePlayer(player) && isWithinArenaBounds(player, radius, heightRange);
    }

    /** Checks spatial bounds independently of game mode or combat participation. */
    private boolean isWithinArenaBounds(ServerPlayer player, double radius, double heightRange) {
        return player.level == level
                && horizontalDistanceSquared(player.getX(), player.getZ()) <= radius * radius
                && Math.abs(player.getY() - getArenaCenter().getY()) <= heightRange;
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
                && isWithinArenaBounds(player, getCombatRadius(), SheeperConfig.WATER_COMBAT_HEIGHT_RANGE.get());
    }

    private boolean isActivePlayer(ServerPlayer player) {
        return player.level == level && player.isAlive() && !player.isRemoved()
                && !player.isCreative() && !player.isSpectator();
    }

    /**
     * Current server-side participants, independent of flooding progress or sight.
     */
    public List<ServerPlayer> getCombatParticipants() {
        if (!(level instanceof ServerLevel server) || !isAlive()) {
            return List.of();
        }
        return server.players().stream()
                .filter(player -> isEligiblePlayer(player, getCombatRadius(),
                        SheeperConfig.WATER_COMBAT_HEIGHT_RANGE.get()))
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
        if (target == null) {
            stopMovement();
        } else {
            if (!PROJECTILE_TEST_MODE) {
                specialAttacks.tryStart(target, defaultAttack);
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
        return entityData.get(DATA_COMBAT_READY)
                || level instanceof ServerLevel server && WaterArenaData.get(server).isGenerated(getUUID());
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
                || source != DamageSource.OUT_OF_WORLD && !arenaCombat.allowsDamage(source);
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
    }

    private boolean touchesArenaWater(ServerPlayer player, WaterArenaData data) {
        AABB bounds = player.getBoundingBox().deflate(1.0E-7D);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(bounds.minX, bounds.minY, bounds.minZ),
                new BlockPos(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (WaterArenaData.isLoaded((ServerLevel) level, pos) && data.isOwnedBy(getUUID(), pos)
                    && level.getFluidState(pos).getType() instanceof BossWaterFluid
                    && pos.getY() + level.getFluidState(pos).getHeight(level, pos) > bounds.minY) {
                return true;
            }
        }
        return false;
    }

    private void tickWaterWave(boolean preparing) {
        if (PROJECTILE_TEST_MODE) {
            waterWaveDelays.clear();
            return;
        }
        double radius = SheeperConfig.WATER_SPAWN_WAVE_RADIUS.get();
        if (radius == 0.0D || SheeperConfig.WATER_SPAWN_WAVE_HEIGHT.get() == 0.0D
                && SheeperConfig.WATER_SPAWN_WAVE_HORIZONTAL_STRENGTH.get() == 0.0D) {
            waterWaveDelays.clear();
            return;
        }
        ServerLevel server = (ServerLevel) level;
        var pending = waterWaveDelays.entrySet().iterator();
        while (pending.hasNext()) {
            var entry = pending.next();
            ServerPlayer player = server.players().stream()
                    .filter(candidate -> candidate.getUUID().equals(entry.getKey())).findFirst().orElse(null);
            if (player == null || !isActivePlayer(player) || distanceToSqr(player) > radius * radius) {
                pending.remove();
            } else if (entry.getValue() <= 1) {
                launchWaterWave(player);
                pending.remove();
            } else {
                entry.setValue(entry.getValue() - 1);
            }
        }
        if (!preparing) {
            return;
        }
        WaterArenaData data = WaterArenaData.get(server);
        for (ServerPlayer player : server.players()) {
            if (!isActivePlayer(player) || distanceToSqr(player) > radius * radius
                    || waterWaveContacts.contains(player.getUUID())
                    || !touchesArenaWater(player, data)) {
                continue;
            }
            waterWaveContacts.add(player.getUUID());
            int delay = SheeperConfig.secondsToTicks(SheeperConfig.WATER_SPAWN_PUSH_DELAY.get());
            if (delay == 0) {
                launchWaterWave(player);
            } else {
                waterWaveDelays.put(player.getUUID(), delay);
            }
        }
    }

    private void launchWaterWave(ServerPlayer player) {
        Vec3 center = Vec3.atBottomCenterOf(getArenaCenter());
        Vec3 direction = new Vec3(player.getX() - center.x, 0.0D, player.getZ() - center.z);
        if (direction.lengthSqr() < 1.0E-8D) {
            direction = new Vec3(1.0D, 0.0D, 0.0D);
        }
        direction = direction.normalize().scale(SheeperConfig.WATER_SPAWN_WAVE_HORIZONTAL_STRENGTH.get());
        double upward = WaterFountainAttack.launchSpeed(SheeperConfig.WATER_SPAWN_WAVE_HEIGHT.get(),
                player.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get()));
        // A defined launch velocity keeps existing momentum from extending the flight.
        player.setDeltaMovement(direction.x, upward, direction.z);
        player.fallDistance = 0;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.hurtMarked = false;
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

    @Override
    public void tick() {
        previousBeamEyeIntensity = entityData.get(DATA_BEAM_EYES);
        if (!level.isClientSide) {
            // Capture placement before gravity or future AI can move the boss.
            getArenaCenter();
            if (isAlive()) {
                boolean preparing = !isCombatReady();
                if (!volumeInitialized) {
                    WaterArenaData data = WaterArenaData.get((ServerLevel) level);
                    WaterArenaData.VolumeBounds bounds = data.prepareVolume(
                            (ServerLevel) level, getUUID(), getArenaCenter(), getCombatRadius(),
                            SheeperConfig.WATER_ARENA_HEIGHT_RANGE.get(), SheeperConfig.WATER_ARENA_WATER_HEIGHT.get());
                    arenaVolume = bounds;
                    if (!data.isGenerated(getUUID())) {
                        volumeSearch = new WaterArenaVolumeSearch((ServerLevel) level, bounds);
                    }
                    volumeInitialized = true;
                    entityData.set(DATA_COMBAT_READY, false);
                }
                if (arenaSearch == null) {
                    arenaSearch = new WaterArenaSearch(level, getArenaCenter(), getCombatRadius(),
                            SheeperConfig.WATER_ARENA_HEIGHT_RANGE.get(), SheeperConfig.WATER_ARENA_STEP_HEIGHT.get());
                }
                int searchBudget = SheeperConfig.WATER_ARENA_SEARCH_BUDGET_PER_SECOND.get() / 20;
                int surfaceBudget = arenaSearch.isComplete() ? 0
                        : volumeSearch == null ? searchBudget : Math.max(1, searchBudget / 4);
                arenaSearch.advance(surfaceBudget);
                if (volumeSearch != null) {
                    volumeSearch.advance(searchBudget - surfaceBudget);
                    placeArenaWater((ServerLevel) level);
                }
                tickWaterWave(preparing);
                entityData.set(DATA_COMBAT_READY, isCombatReady());
                if (!isCombatReady()) {
                    stopMovement();
                }
            }
            getAttribute(ForgeMod.SWIM_SPEED.get()).setBaseValue(SheeperConfig.WATER_MOVEMENT_SWIM_SPEED.get());
            if (PROJECTILE_TEST_MODE) {
                // Clear saved specials so this test stage cannot lock movement or
                // trigger an attack that has not yet been enabled for underwater testing.
                specialAttacks.reset();
            }
            updateMovementTarget();
        }
        super.tick();
        if (!level.isClientSide && isAlive() && isCombatReady()) {
            arenaBoundary.tick();
            if (!isNoAi()) {
                arenaCombat.tick(getCombatParticipants());
                if (!PROJECTILE_TEST_MODE) {
                    specialAttacks.tickAttack();
                }
                if (!specialAttacks.suppressesDefault()) {
                    defaultAttack.tickProjectiles();
                    if (!PROJECTILE_TEST_MODE) {
                        proximityPush.tick(getCombatParticipants());
                    }
                }
            }
            specialAttacks.tickEffects();
        }
        if (level.isClientSide && isAlive() && !isInvisible() && tickCount % 2 == 0) {
            spawnBeamParticles();
        }
    }

    /** Adds sparse client-side spray around the synchronized water tube. */
    private void spawnBeamParticles() {
        Vec3 offset = getBeamOffset();
        int count = Math.min(24, (int) Math.ceil(offset.length() / 2));
        for (int index = 0; index < count; index++) {
            Vec3 point = getBeamOrigin().add(offset.scale((index + random.nextDouble()) / count));
            level.addParticle(ParticleTypes.SPLASH,
                    point.x + random.nextGaussian() * BEAM_RADIUS,
                    point.y + random.nextGaussian() * BEAM_RADIUS,
                    point.z + random.nextGaussian() * BEAM_RADIUS, 0, 0.03D, 0);
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
        int interval = SheeperConfig.secondsToTicks(SheeperConfig.WATER_ARENA_FLOOD_WAVE_SECONDS.get());
        long now = level.getGameTime();
        if (interval > 0 && !waterPlacement.isEmpty()
                && volumeSearch.getDistance(waterPlacement.peek()) != placementWave) {
            if (now < nextFloodWaveTick) {
                return;
            }
            placementWave = volumeSearch.getDistance(waterPlacement.peek());
        }
        int candidates = Math.min(waterPlacement.size(), SheeperConfig.WATER_ARENA_BLOCK_BUDGET_PER_SECOND.get() / 20);
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
        tag.put("WaterSpecialAttacks", specialAttacks.save());
        ListTag contacts = new ListTag();
        waterWaveContacts.forEach(id -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            contacts.add(entry);
        });
        tag.put("WaterWaveContacts", contacts);
        ListTag wave = new ListTag();
        waterWaveDelays.forEach((id, ticks) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Ticks", ticks);
            wave.add(entry);
        });
        tag.put("WaterWaveDelays", wave);
        tag.putLong("WaterArenaCenter", getArenaCenter().asLong());
        tag.putString("WaterArenaDimension", level.dimension().location().toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_COMBAT_READY, false);
        waterWaveContacts.clear();
        for (Tag value : tag.getList("WaterWaveContacts", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                waterWaveContacts.add(entry.getUUID("Player"));
            }
        }
        waterWaveDelays.clear();
        for (Tag value : tag.getList("WaterWaveDelays", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                UUID player = entry.getUUID("Player");
                waterWaveContacts.add(player);
                waterWaveDelays.put(player, Mth.clamp(entry.getInt("Ticks"), 1, 1200));
            }
        }
        defaultAttack.load(tag.getCompound("WaterDefaultAttack"));
        proximityPush.load(tag.getList("WaterProximityPush", Tag.TAG_COMPOUND));
        arenaCombat.load(tag.getCompound("WaterArenaCombat"));
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
