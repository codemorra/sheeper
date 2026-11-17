package de.codemorra.sheeper.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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
import de.codemorra.sheeper.fluid.BossWaterFluid;
import de.codemorra.sheeper.registry.ModFluids;
import de.codemorra.sheeper.world.WaterArenaData;

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
 */
public class WaterSheeperEntity extends ElementalSheeperEntity {
    private static final EntityDataAccessor<Boolean> DATA_COMBAT_READY = SynchedEntityData
            .defineId(WaterSheeperEntity.class, EntityDataSerializers.BOOLEAN);

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

    private final WaterDefaultAttack defaultAttack = new WaterDefaultAttack(this);
    private final WaterProximityPush proximityPush = new WaterProximityPush(this);

    public WaterSheeperEntity(EntityType<? extends Sheep> type, Level level) {
        super(type, level, Element.WATER);
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
    }

    /**
     * Stops navigation and horizontal drift without interrupting vertical physics.
     */
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
        goalSelector.addGoal(0, new WaterOrbitGoal(this));
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
        if (type == MoverType.SELF && !level.isClientSide && (!isCombatReady() || isNoAi())) {
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
     * Advances default attack target selection before the AI tick so movement and
     * shooting share a valid participant. Head tracking remains separate from the
     * body's navigation direction, including during firing pauses.
     */
    private void updateMovementTarget() {
        if (!isAlive() || !isCombatReady() || isNoAi()) {
            setTarget(null);
            stopHorizontalMovement();
            return;
        }
        ServerPlayer target = defaultAttack.tickTarget(getCombatParticipants());
        setTarget(target);
        if (target == null) {
            stopHorizontalMovement();
        } else {
            // Look control turns the head without overwriting navigation's body yaw.
            getLookControl().setLookAt(target, 90.0F, 45.0F);
        }
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
        if (!level.isClientSide) {
            // Capture placement before gravity or future AI can move the boss.
            getArenaCenter();
            if (isAlive()) {
                boolean preparing = !isCombatReady();
                if (arenaSearch == null) {
                    arenaSearch = new WaterArenaSearch(level, getArenaCenter(), getCombatRadius(),
                            SheeperConfig.WATER_ARENA_HEIGHT_RANGE.get(), SheeperConfig.WATER_ARENA_STEP_HEIGHT.get());
                }
                arenaSearch.advance(SheeperConfig.WATER_ARENA_SEARCH_BUDGET_PER_SECOND.get() / 20);
                if (arenaSearch.isComplete()) {
                    placeArenaWater((ServerLevel) level);
                }
                tickWaterWave(preparing);
                entityData.set(DATA_COMBAT_READY, isCombatReady());
                if (!isCombatReady()) {
                    stopHorizontalMovement();
                }
            }
            getAttribute(ForgeMod.SWIM_SPEED.get()).setBaseValue(SheeperConfig.WATER_MOVEMENT_SWIM_SPEED.get());
            updateMovementTarget();
        }
        super.tick();
        if (!level.isClientSide && isAlive() && isCombatReady() && !isNoAi()) {
            defaultAttack.tickProjectiles();
            proximityPush.tick(getCombatParticipants());
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
        int interval = SheeperConfig.secondsToTicks(SheeperConfig.WATER_ARENA_FLOOD_WAVE_SECONDS.get());
        long now = level.getGameTime();
        if (interval > 0 && !waterPlacement.isEmpty()
                && arenaSearch.getSurfaceDistance(waterPlacement.peek()) != placementWave) {
            if (now < nextFloodWaveTick) {
                return;
            }
            placementWave = arenaSearch.getSurfaceDistance(waterPlacement.peek());
        }
        int candidates = Math.min(waterPlacement.size(), SheeperConfig.WATER_ARENA_BLOCK_BUDGET_PER_SECOND.get() / 20);
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
            setTarget(null);
            stopHorizontalMovement();
            WaterArenaData.get(server).release(getUUID());
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("WaterDefaultAttack", defaultAttack.save());
        tag.put("WaterProximityPush", proximityPush.save());
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
