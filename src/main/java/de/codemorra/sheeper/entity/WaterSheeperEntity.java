package de.codemorra.sheeper.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
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

/**
 * Water boss foundation with incremental discovery and tracked initial water
 * placement.
 */
public class WaterSheeperEntity extends ElementalSheeperEntity {
    @Nullable
    private BlockPos arenaCenter;
    @Nullable
    private WaterArenaSearch arenaSearch;
    @Nullable
    private Queue<BlockPos> waterPlacement;
    private int placementWave = -1;
    private long nextFloodWaveTick;
    private boolean spawnPushApplied;
    private int projectileCooldown = SheeperConfig.WATER_PROJECTILE_INTERVAL.get();
    private final Queue<UUID> attackOrder = new ArrayDeque<>();

    public WaterSheeperEntity(EntityType<? extends Sheep> type, Level level) {
        super(type, level, Element.WATER);
        getNavigation().setCanFloat(true);
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
        if (type == MoverType.SELF && !level.isClientSide && !isNoAi() && isAlive()
                && isWithinMovementArea(getX(), getZ())) {
            double dx = getX() - getArenaCenter().getX() - 0.5D;
            double dz = getZ() - getArenaCenter().getZ() - 0.5D;
            // Leave a numerical margin when converting back to world coordinates.
            double radius = Math.max(0.0D, getMovementRadius() - 1.0E-7D);
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
        return player.level == level && player.isAlive() && !player.isRemoved()
                && !player.isCreative() && !player.isSpectator()
                && horizontalDistanceSquared(player.getX(), player.getZ()) <= radius * radius
                && Math.abs(player.getY() - getArenaCenter().getY()) <= heightRange;
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

    private void updateCombatTarget() {
        List<ServerPlayer> participants = getCombatParticipants();
        attackOrder.removeIf(id -> participants.stream().noneMatch(player -> player.getUUID().equals(id)));
        if (getTarget() == null || !participants.contains(getTarget())) {
            selectNextAttackTarget();
        }
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
        selectNextAttackTarget();
        projectileCooldown = SheeperConfig.WATER_PROJECTILE_INTERVAL.get();
    }

    /**
     * Fires at the target's current eye position with no prediction or homing.
     * Shared by regular shots and future volleys; callers own timing and selection.
     *
     * @param target living target whose current eye position determines the shot
     * @return whether a projectile was spawned on the server with a clear sight
     *         line
     */
    public boolean fireWaterProjectile(LivingEntity target) {
        if (level.isClientSide || !isAlive() || !target.isAlive() || target.level != level
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

    private void applySpawnPush() {
        if (spawnPushApplied) {
            return;
        }
        spawnPushApplied = true;
        double horizontal = SheeperConfig.WATER_SPAWN_PUSH_STRENGTH.get();
        double upward = SheeperConfig.WATER_SPAWN_PUSH_UPWARD_STRENGTH.get();
        if (horizontal == 0.0D && upward == 0.0D) {
            return;
        }
        for (ServerPlayer player : ((ServerLevel) level).players()) {
            if (!isEligiblePlayer(player, SheeperConfig.WATER_SPAWN_PUSH_RADIUS.get(),
                    SheeperConfig.WATER_COMBAT_HEIGHT_RANGE.get())) {
                continue;
            }
            Vec3 direction = new Vec3(player.getX() - getArenaCenter().getX() - 0.5D, 0.0D,
                    player.getZ() - getArenaCenter().getZ() - 0.5D);
            if (direction.lengthSqr() < 1.0E-8D) {
                direction = new Vec3(1.0D, 0.0D, 0.0D);
            }
            direction = direction.normalize().scale(horizontal);
            player.push(direction.x, upward, direction.z);
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.hurtMarked = false;
        }
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
        if (!level.isClientSide) {
            // Capture placement before gravity or future AI can move the boss.
            getArenaCenter();
            if (isAlive()) {
                applySpawnPush();
                updateCombatTarget();
                if (arenaSearch == null) {
                    arenaSearch = new WaterArenaSearch(level, getArenaCenter(), getCombatRadius(),
                            SheeperConfig.WATER_ARENA_HEIGHT_RANGE.get(), SheeperConfig.WATER_ARENA_STEP_HEIGHT.get());
                }
                arenaSearch.advance(SheeperConfig.WATER_ARENA_SEARCH_BUDGET.get());
                if (arenaSearch.isComplete()) {
                    placeArenaWater((ServerLevel) level);
                }
            }
        }
        super.tick();
        if (!level.isClientSide && isAlive()) {
            if (!isNoAi() && getTarget() != null) {
                // Apply after vanilla navigation and look control, which otherwise
                // turn the head back toward the movement direction between shots.
                lookAtAttackTarget(getTarget());
            }
            tickRegularShots();
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
            WaterArenaData.get(server).release(getUUID());
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("WaterProjectileCooldown", projectileCooldown);
        tag.putBoolean("WaterSpawnPushApplied", spawnPushApplied);
        tag.putLong("WaterArenaCenter", getArenaCenter().asLong());
        tag.putString("WaterArenaDimension", level.dimension().location().toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Legacy saved bosses must not push again; fresh summon data has no saved
        // origin.
        spawnPushApplied = tag.contains("WaterSpawnPushApplied", Tag.TAG_BYTE)
                ? tag.getBoolean("WaterSpawnPushApplied")
                : tag.contains("WaterArenaCenter", Tag.TAG_LONG);
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
