package de.codemorra.sheeper.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.goal.WaterArenaMoveGoal;
import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeMod;
import de.codemorra.sheeper.fluid.BossWaterFluid;

/**
 * Water boss foundation with incremental discovery and tracked initial water
 * placement.
 */
public class WaterSheeperEntity extends ElementalSheeperEntity {
    private static final EntityDataAccessor<Float> DATA_SALVO_EYES = SynchedEntityData
            .defineId(WaterSheeperEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_COMBAT_READY = SynchedEntityData
            .defineId(WaterSheeperEntity.class, EntityDataSerializers.BOOLEAN);
    private float previousSalvoEyeIntensity;

    @Nullable
    private BlockPos arenaCenter;
    @Nullable
    private WaterArenaSearch arenaSearch;
    @Nullable
    private Queue<BlockPos> waterPlacement;
    private int placementWave = -1;
    private long nextFloodWaveTick;
    private final Set<UUID> waterWaveContacts = new HashSet<>();
    private final Map<UUID, Integer> waterWaveDelays = new HashMap<>();

    private enum AttackPhase {
        PROJECTILES,
        FOUNTAINS,
        SALVO,
        REST
    }

    private final WaterFountainAttack fountains = new WaterFountainAttack(this);
    private final WaterProjectileSalvo salvo = new WaterProjectileSalvo(this);
    private AttackPhase attackPhase = AttackPhase.REST;
    private int attackPhaseTicks;
    @Nullable
    private UUID attackTargetId;
    private int projectileCooldown = SheeperConfig.WATER_PROJECTILE_INTERVAL.get();
    private final Queue<UUID> attackOrder = new ArrayDeque<>();
    private final Map<UUID, Integer> proximityPushTicks = new HashMap<>();

    public WaterSheeperEntity(EntityType<? extends Sheep> type, Level level) {
        super(type, level, Element.WATER);
        getNavigation().setCanFloat(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SALVO_EYES, 0.0F);
        entityData.define(DATA_COMBAT_READY, false);
    }

    /** Interpolated, server-controlled warning for the client eye layer. */
    public float getSalvoEyeIntensity(float partialTicks) {
        return Mth.lerp(partialTicks, previousSalvoEyeIntensity, entityData.get(DATA_SALVO_EYES));
    }

    void setSalvoEyeIntensity(float intensity) {
        entityData.set(DATA_SALVO_EYES, Mth.clamp(intensity, 0.0F, 1.0F));
    }

    /** Navigation is suspended for the entire preparation and firing sequence. */
    public boolean isPerformingSalvo() {
        return attackPhase == AttackPhase.SALVO;
    }

    private void stopHorizontalMovement() {
        getNavigation().stop();
        Vec3 velocity = getDeltaMovement();
        setDeltaMovement(0.0D, velocity.y, 0.0D);
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

    /** Keeps the configured movement area within the encounter area. */
    public double getMovementRadius() {
        return Math.min(SheeperConfig.WATER_MOVEMENT_RADIUS.get(), getCombatRadius());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new WaterArenaMoveGoal(this));
    }

    /** Checks a horizontal circle around the center of the saved spawn block. */
    public boolean isWithinMovementArea(double x, double z) {
        return horizontalDistanceSquared(x, z) <= getMovementRadius() * getMovementRadius();
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (type == MoverType.SELF && !level.isClientSide && (!isCombatReady() || isPerformingSalvo())) {
            movement = new Vec3(0.0D, movement.y, 0.0D);
        }
        if (type == MoverType.SELF && !level.isClientSide && !isNoAi() && isAlive()) {
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
        return isActivePlayer(player)
                && horizontalDistanceSquared(player.getX(), player.getZ()) <= radius * radius
                && Math.abs(player.getY() - getArenaCenter().getY()) <= heightRange;
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
     * Advances the shared round-robin order once per attack selection. Invalid
     * players are removed; returning and new participants join the back of the
     * queue.
     * Returns null while combat is paused without participants.
     */
    @Nullable
    public ServerPlayer selectNextAttackTarget() {
        if (!isCombatReady()) {
            setTarget(null);
            return null;
        }
        List<ServerPlayer> participants = getCombatParticipants();
        Set<UUID> eligible = new HashSet<>();
        participants.forEach(player -> eligible.add(player.getUUID()));
        attackOrder.removeIf(id -> !eligible.contains(id));
        for (ServerPlayer player : participants) {
            if (!attackOrder.contains(player.getUUID())) {
                attackOrder.add(player.getUUID());
            }
        }
        UUID next = attackOrder.poll();
        if (next == null) {
            setTarget(null);
            return null;
        }
        attackOrder.add(next);
        ServerPlayer target = participants.stream().filter(player -> player.getUUID().equals(next)).findFirst()
                .orElseThrow();
        setTarget(target);
        return target;
    }

    private void tickAttackPhase() {
        if (isNoAi()) {
            if (isPerformingSalvo()) {
                beginAttackRest();
            }
            return;
        }
        List<ServerPlayer> participants = getCombatParticipants();
        if (isPerformingSalvo() || attackPhase == AttackPhase.PROJECTILES && attackPhaseTicks > 0) {
            // Resolve the saved focus without selecting a replacement mid-attack.
            ServerPlayer target = participants.stream()
                    .filter(player -> player.getUUID().equals(attackTargetId)).findFirst().orElse(null);
            if (target == null) {
                beginAttackRest();
                return;
            }
            setTarget(target);
            if (isPerformingSalvo()) {
                stopHorizontalMovement();
                if (!salvo.tick(target)) {
                    beginAttackRest();
                }
                return;
            }
        }
        if (participants.isEmpty()) {
            return;
        }
        if (attackPhaseTicks == 0) {
            double salvoWeight = SheeperConfig.WATER_SALVO_WEIGHT.get();
            double fountainWeight = SheeperConfig.WATER_FOUNTAIN_WEIGHT.get();
            double totalWeight = salvoWeight + fountainWeight + SheeperConfig.WATER_PROJECTILE_WEIGHT.get();
            if (totalWeight == 0.0D) {
                return;
            }
            double choice = random.nextDouble() * totalWeight;
            if (choice < salvoWeight) {
                ServerPlayer target = selectNextAttackTarget();
                if (target == null) {
                    return;
                }
                attackTargetId = target.getUUID();
                attackPhase = AttackPhase.SALVO;
                salvo.start();
                stopHorizontalMovement();
                // Count this first stationary tick toward the preparation.
                if (!salvo.tick(target)) {
                    beginAttackRest();
                }
                return;
            } else if (choice < salvoWeight + fountainWeight) {
                setTarget(null);
                attackTargetId = null;
                attackPhase = AttackPhase.FOUNTAINS;
                attackPhaseTicks = randomPhaseTicks(SheeperConfig.WATER_FOUNTAIN_DURATION_MIN.get(),
                        SheeperConfig.WATER_FOUNTAIN_DURATION_MAX.get());
                fountains.start();
            } else {
                ServerPlayer target = selectNextAttackTarget();
                if (target == null) {
                    return;
                }
                attackTargetId = target.getUUID();
                attackPhase = AttackPhase.PROJECTILES;
                attackPhaseTicks = randomPhaseTicks(SheeperConfig.WATER_PROJECTILE_DURATION_MIN.get(),
                        SheeperConfig.WATER_PROJECTILE_DURATION_MAX.get());
                projectileCooldown = SheeperConfig.WATER_PROJECTILE_INTERVAL.get();
            }
        }
        if (attackPhase == AttackPhase.REST) {
            attackPhaseTicks--;
            return;
        }
        if (attackPhase == AttackPhase.FOUNTAINS) {
            setTarget(null);
            // Loading rediscovers terrain; retain pending warnings until it is ready.
            if (!isArenaSearchComplete()) {
                return;
            }
            fountains.tick(participants, attackPhaseTicks);
        } else {
            tickRegularShots();
        }
        if (--attackPhaseTicks == 0) {
            beginAttackRest();
        }
    }

    private void beginAttackRest() {
        if (attackPhase == AttackPhase.FOUNTAINS) {
            fountains.finish();
        }
        if (isPerformingSalvo()) {
            stopHorizontalMovement();
        }
        setTarget(null);
        attackTargetId = null;
        attackPhase = AttackPhase.REST;
        attackPhaseTicks = randomPhaseTicks(SheeperConfig.WATER_ATTACK_PAUSE_MIN.get(),
                SheeperConfig.WATER_ATTACK_PAUSE_MAX.get());
    }

    private int randomPhaseTicks(int minimum, int maximum) {
        // Use a long range so inclusive bounds also support 0..Integer.MAX_VALUE.
        long range = (long) Math.max(minimum, maximum) - minimum + 1L;
        return minimum + (int) (random.nextDouble() * range);
    }

    private void tickRegularShots() {
        if (isNoAi() || getTarget() == null) {
            return;
        }
        projectileCooldown = Math.min(projectileCooldown, SheeperConfig.WATER_PROJECTILE_INTERVAL.get());
        if (--projectileCooldown > 0) {
            return;
        }
        fireWaterProjectile(getTarget());
        projectileCooldown = SheeperConfig.WATER_PROJECTILE_INTERVAL.get();
    }

    /**
     * Fires at the target's current eye position with no prediction or homing.
     * Shared by regular shots and salvos; callers own timing and selection.
     *
     * @param target living target whose current eye position determines the shot
     * @return whether a projectile was spawned on the server with a clear sight
     *         line
     */
    public boolean fireWaterProjectile(LivingEntity target) {
        if (level.isClientSide || !isCombatReady() || !isAlive() || !target.isAlive() || target.level != level
                || !hasLineOfSight(target)) {
            return false;
        }
        lookAtAttackTarget(target);
        WaterProjectile projectile = new WaterProjectile(ModEntities.WATER_PROJECTILE.get(), level);
        projectile.setOwner(this);
        projectile.setPos(getEyePosition());
        Vec3 direction = target.getEyePosition().subtract(projectile.position());
        if (direction.lengthSqr() < 1.0E-8D) {
            direction = getLookAngle();
        }
        projectile.setDeltaMovement(direction.normalize().scale(SheeperConfig.WATER_PROJECTILE_SPEED.get()));
        return level.addFreshEntity(projectile);
    }

    private void lookAtAttackTarget(LivingEntity target) {
        lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        setYHeadRot(getYRot());
    }

    @Override
    public boolean isCombatReady() {
        return entityData.get(DATA_COMBAT_READY)
                || level instanceof ServerLevel server && WaterArenaData.get(server).isGenerated(getUUID());
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // Keep /kill and void removal available during preparation.
        return !isCombatReady() && source != DamageSource.OUT_OF_WORLD || super.isInvulnerableTo(source);
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
            int delay = SheeperConfig.WATER_SPAWN_PUSH_DELAY.get();
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

    private void pushPlayer(ServerPlayer player, Vec3 center, double horizontal, double upward) {
        Vec3 direction = new Vec3(player.getX() - center.x, 0.0D, player.getZ() - center.z);
        if (direction.lengthSqr() < 1.0E-8D) {
            direction = new Vec3(1.0D, 0.0D, 0.0D);
        }
        direction = direction.normalize().scale(horizontal);
        player.push(direction.x, upward, direction.z);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.hurtMarked = false;
    }

    private int nextProximityPushDelay() {
        return randomPhaseTicks(SheeperConfig.WATER_PROXIMITY_PUSH_DELAY_MIN.get(),
                SheeperConfig.WATER_PROXIMITY_PUSH_DELAY_MAX.get());
    }

    private void tickProximityPush() {
        double radius = SheeperConfig.WATER_PROXIMITY_PUSH_RADIUS.get();
        double horizontal = SheeperConfig.WATER_SPAWN_PUSH_STRENGTH.get();
        double upward = SheeperConfig.WATER_SPAWN_PUSH_UPWARD_STRENGTH.get();
        if (radius == 0.0D || horizontal == 0.0D && upward == 0.0D) {
            proximityPushTicks.clear();
            return;
        }
        Set<UUID> nearby = new HashSet<>();
        for (ServerPlayer player : ((ServerLevel) level).players()) {
            if (!isActivePlayer(player) || distanceToSqr(player) > radius * radius) {
                continue;
            }
            UUID id = player.getUUID();
            nearby.add(id);
            if (isNoAi()) {
                continue;
            }
            int remaining = proximityPushTicks.computeIfAbsent(id, ignored -> nextProximityPushDelay()) - 1;
            if (remaining <= 0) {
                pushPlayer(player, position(), horizontal, upward);
                remaining = nextProximityPushDelay();
            }
            proximityPushTicks.put(id, remaining);
        }
        proximityPushTicks.keySet().retainAll(nearby);
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
        previousSalvoEyeIntensity = entityData.get(DATA_SALVO_EYES);
        if (!level.isClientSide) {
            // Capture placement before gravity or future AI can move the boss.
            getArenaCenter();
            if (isAlive()) {
                boolean preparing = !isCombatReady();
                if (arenaSearch == null) {
                    arenaSearch = new WaterArenaSearch(level, getArenaCenter(), getCombatRadius(),
                            SheeperConfig.WATER_ARENA_HEIGHT_RANGE.get(), SheeperConfig.WATER_ARENA_STEP_HEIGHT.get());
                }
                arenaSearch.advance(SheeperConfig.WATER_ARENA_SEARCH_BUDGET.get());
                if (arenaSearch.isComplete()) {
                    placeArenaWater((ServerLevel) level);
                }
                tickWaterWave(preparing);
                entityData.set(DATA_COMBAT_READY, isCombatReady());
                if (!isCombatReady()) {
                    stopHorizontalMovement();
                }
            }
        }
        super.tick();
        if (!level.isClientSide && isAlive() && isCombatReady()) {
            tickProximityPush();
            tickAttackPhase();
            fountains.tickRain(attackPhase == AttackPhase.FOUNTAINS);
            if (!isNoAi() && getTarget() != null) {
                // Apply after vanilla navigation and look control, which otherwise
                // turn the head back toward the movement direction between shots.
                lookAtAttackTarget(getTarget());
            }
        }
        if (!level.isClientSide && !isPerformingSalvo()) {
            setSalvoEyeIntensity(Mth.approach(entityData.get(DATA_SALVO_EYES), 0.0F,
                    1.0F / SheeperConfig.WATER_SALVO_EYE_FADE.get()));
        }
    }

    private void placeArenaWater(ServerLevel level) {
        WaterArenaData data = WaterArenaData.get(level);
        if (data.isGenerated(getUUID())) {
            return;
        }
        if (waterPlacement == null) {
            var positions = new ArrayList<>(getArenaSurfacePositions());
            // Deferred chunks may finish discovery late; still place nearer layers first.
            positions.sort(Comparator.comparingInt(arenaSearch::getSurfaceDistance));
            waterPlacement = new ArrayDeque<>(positions);
        }
        int interval = SheeperConfig.WATER_ARENA_FLOOD_WAVE_TICKS.get();
        long now = level.getGameTime();
        if (interval > 0 && !waterPlacement.isEmpty()
                && arenaSearch.getSurfaceDistance(waterPlacement.peek()) != placementWave) {
            if (now < nextFloodWaveTick) {
                return;
            }
            placementWave = arenaSearch.getSurfaceDistance(waterPlacement.peek());
        }
        int candidates = Math.min(waterPlacement.size(), SheeperConfig.WATER_ARENA_BLOCK_BUDGET.get());
        for (int examined = 0; examined < candidates; examined++) {
            BlockPos pos = waterPlacement.peek();
            int distance = arenaSearch.getSurfaceDistance(pos);
            if (interval > 0 && distance != placementWave || !WaterArenaData.isLoaded(level, pos)) {
                break;
            }
            waterPlacement.remove();
            data.claim(level, getUUID(), pos, distance);
        }
        if (interval > 0 && !waterPlacement.isEmpty()
                && arenaSearch.getSurfaceDistance(waterPlacement.peek()) != placementWave) {
            nextFloodWaveTick = now + interval;
        }
        if (waterPlacement.isEmpty()) {
            data.finishGeneration(getUUID());
            waterPlacement = null;
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && level instanceof ServerLevel server) {
            if (isPerformingSalvo()) {
                beginAttackRest();
            }
            WaterArenaData.get(server).release(getUUID());
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (attackTargetId != null) {
            tag.putUUID("WaterAttackTarget", attackTargetId);
        }
        tag.put("WaterFountains", fountains.save());
        tag.put("WaterSalvo", salvo.save());
        tag.putFloat("WaterSalvoEyes", entityData.get(DATA_SALVO_EYES));
        tag.putString("WaterAttackPhase", attackPhase.name());
        tag.putInt("WaterAttackPhaseTicks", attackPhaseTicks);
        tag.putInt("WaterProjectileCooldown", projectileCooldown);
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
        ListTag proximity = new ListTag();
        proximityPushTicks.forEach((id, ticks) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Ticks", ticks);
            proximity.add(entry);
        });
        tag.put("WaterProximityPush", proximity);
        tag.putLong("WaterArenaCenter", getArenaCenter().asLong());
        tag.putString("WaterArenaDimension", level.dimension().location().toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        proximityPushTicks.clear();
        for (Tag value : tag.getList("WaterProximityPush", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                proximityPushTicks.put(entry.getUUID("Player"), Mth.clamp(entry.getInt("Ticks"), 1, 12000));
            }
        }
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
        attackTargetId = tag.hasUUID("WaterAttackTarget") ? tag.getUUID("WaterAttackTarget") : null;
        attackPhaseTicks = Math.max(0, tag.getInt("WaterAttackPhaseTicks"));
        attackPhase = switch (tag.getString("WaterAttackPhase")) {
            case "PROJECTILES" -> AttackPhase.PROJECTILES;
            case "FOUNTAINS" -> AttackPhase.FOUNTAINS;
            case "SALVO" -> AttackPhase.SALVO;
            default -> AttackPhase.REST;
        };
        fountains.load(tag.getCompound("WaterFountains"));
        salvo.load(tag.getCompound("WaterSalvo"));
        float eyes = tag.getFloat("WaterSalvoEyes");
        setSalvoEyeIntensity(Float.isFinite(eyes) ? eyes : 0.0F);
        previousSalvoEyeIntensity = entityData.get(DATA_SALVO_EYES);
        projectileCooldown = tag.contains("WaterProjectileCooldown", Tag.TAG_INT)
                ? Mth.clamp(tag.getInt("WaterProjectileCooldown"), 1, SheeperConfig.WATER_PROJECTILE_INTERVAL.get())
                : SheeperConfig.WATER_PROJECTILE_INTERVAL.get();
        attackOrder.clear();
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
        waterPlacement = null;
        placementWave = -1;
        nextFloodWaveTick = 0;
    }
}
