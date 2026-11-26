package de.codemorra.sheeper.entity;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.network.LightningWeatherPacket;
import de.codemorra.sheeper.world.LightningArenaBoundaryDrag;
import de.codemorra.sheeper.world.LightningArenaWind;
import de.codemorra.sheeper.world.LightningStormData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lightning encounter with a persistent arena origin and protected startup. */
public class LightningSheeperEntity extends ElementalSheeperEntity {
    static final int ARC_TRAVEL_TICKS = 6;
    private static final EntityDataAccessor<Boolean> DATA_COMBAT_READY = SynchedEntityData
            .defineId(LightningSheeperEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> DATA_TRAVEL_ARC = SynchedEntityData
            .defineId(LightningSheeperEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> DATA_LINE_CHARGE = SynchedEntityData
            .defineId(LightningSheeperEntity.class, EntityDataSerializers.FLOAT);

    /** World-space endpoints keep the visible lightning and charging form anchored to the attack position. */
    public record TravelArc(Vec3 start, Vec3 end, long startTick, int durationTicks, float charge) {
        public float progress(double time) {
            return (float) Mth.clamp((time - startTick) / durationTicks, 0, 1);
        }

        /** @return whether the travelling lightning has condensed at the destination */
        public boolean isCharging() {
            return charge >= 0;
        }
    }

    @Nullable
    private BlockPos arenaCenter;
    @Nullable
    private LightningWeatherPacket.Arena arena;
    private final Set<UUID> admittedPlayers = new HashSet<>();
    private final LightningTeleportMovement teleports = new LightningTeleportMovement(this);
    private final LightningAmbientStrikes ambientStrikes = new LightningAmbientStrikes(this);
    private final LightningSpecialAttacks specialAttacks = new LightningSpecialAttacks(this, teleports);
    private final ElementalArenaCombat arenaCombat = new ElementalArenaCombat(this, this::canPlayerDamage,
            this::resetArenaEncounter, LightningSheeperConfig.LIGHTNING_MAX_HEALTH::get,
            LightningSheeperConfig.LIGHTNING_HEALTH_PER_ADDITIONAL_PLAYER::get,
            LightningSheeperConfig.LIGHTNING_EMPTY_ARENA_GRACE::get,
            LightningSheeperConfig.LIGHTNING_EMPTY_ARENA_REGENERATION::get);
    private int encounterEntryTicks = -1;
    private float previousLineCharge;

    public LightningSheeperEntity(EntityType<? extends Sheep> type, Level level) {
        super(type, level, Element.LIGHTNING);
    }

    @Override
    protected double getConfiguredMaxHealth() {
        return arenaCombat == null ? super.getConfiguredMaxHealth() : arenaCombat.getScaledMaxHealth();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_COMBAT_READY, false);
        entityData.define(DATA_LINE_CHARGE, 0.0F);
        entityData.define(DATA_TRAVEL_ARC, new CompoundTag());
    }

    /**
     * @param partialTick interpolation fraction for the charge effect
     * @return intensity of the lightning clinging to the Sheeper during the row attack
     */
    public float getLineCharge(float partialTick) {
        return Mth.lerp(partialTick, previousLineCharge, entityData.get(DATA_LINE_CHARGE));
    }

    void setLineCharge(float charge) {
        entityData.set(DATA_LINE_CHARGE, charge);
    }

    /**
     * Captures the placed spawn block before movement, including saves before the
     * first tick.
     */
    public BlockPos getArenaCenter() {
        if (arenaCenter == null) {
            arenaCenter = blockPosition().immutable();
        }
        return arenaCenter;
    }

    public double getCombatRadius() {
        return arena == null ? LightningSheeperConfig.LIGHTNING_COMBAT_RADIUS.get() : arena.radius();
    }

    /** @return captured storm floor, with the configured depth as a fallback before the first tick */
    public int getArenaMinimumY() {
        return arena == null ? Math.max(level.getMinBuildHeight(),
                getArenaCenter().getY() - LightningSheeperConfig.LIGHTNING_ARENA_DEPTH.get()) : arena.minimumY();
    }

    /** @return captured exclusive storm ceiling, clipped to the world's build height */
    public int getArenaMaximumY() {
        return arena == null ? Math.min(level.getMaxBuildHeight(),
                getArenaCenter().getY() + LightningSheeperConfig.LIGHTNING_ARENA_HEIGHT.get()) : arena.maximumY();
    }

    /** @return absolute height of the local cloud bank used by arena strikes */
    double getStormCloudY(Vec3 position) {
        return arena == null ? LightningWeatherPacket.Arena.cloudCenterY(getArenaCenter().getY(), getArenaMaximumY())
                : arena.cloudStrikeY(position.x, position.z, level.getGameTime());
    }

    /** @return whether the position falls between the arena floor and ceiling */
    public boolean isWithinArenaHeight(double y) {
        return y >= getArenaMinimumY() && y < getArenaMaximumY();
    }

    /**
     * Uses the same body and edge clearance as the water boss, so changing arena
     * radius changes the available movement area.
     */
    public double getMovementRadius() {
        double bodyMargin = getBbWidth() * Math.sqrt(0.5) + 0.25;
        return Math.max(0, getCombatRadius() - bodyMargin - LightningSheeperConfig.LIGHTNING_MOVEMENT_EDGE_DISTANCE.get());
    }

    /** Starts the shared visible lightning form for the caller's travel duration. */
    void startTravelArc(Vec3 destination, int durationTicks) {
        Vec3 lift = new Vec3(0, getBbHeight() * 0.5, 0);
        Vec3 start = position().add(lift);
        Vec3 end = destination.add(lift);
        var arc = new CompoundTag();
        arc.putLong("StartTick", level.getGameTime());
        arc.putInt("DurationTicks", durationTicks);
        arc.putDouble("StartX", start.x);
        arc.putDouble("StartY", start.y);
        arc.putDouble("StartZ", start.z);
        arc.putDouble("EndX", end.x);
        arc.putDouble("EndY", end.y);
        arc.putDouble("EndZ", end.z);
        entityData.set(DATA_TRAVEL_ARC, arc);
    }

    void clearTravelArc() {
        entityData.set(DATA_TRAVEL_ARC, new CompoundTag());
    }

    /** Turns the existing travel arc into a suspended charge at its endpoint. */
    void setTravelArcCharge(float progress) {
        CompoundTag arc = entityData.get(DATA_TRAVEL_ARC).copy();
        arc.putFloat("Charge", progress);
        entityData.set(DATA_TRAVEL_ARC, arc);
    }

    /** @return the active lightning form for client presentation, or null */
    @Nullable
    public TravelArc getTravelArc() {
        CompoundTag arc = entityData.get(DATA_TRAVEL_ARC);
        return arc.isEmpty() || !isAlive() ? null
                : new TravelArc(new Vec3(arc.getDouble("StartX"), arc.getDouble("StartY"), arc.getDouble("StartZ")),
                        new Vec3(arc.getDouble("EndX"), arc.getDouble("EndY"), arc.getDouble("EndZ")),
                        arc.getLong("StartTick"), Math.max(1, arc.getInt("DurationTicks")),
                        arc.contains("Charge", Tag.TAG_FLOAT) ? arc.getFloat("Charge") : -1);
    }

    private double horizontalDistanceSquared(double x, double z) {
        BlockPos center = getArenaCenter();
        double dx = x - center.getX() - 0.5D;
        double dz = z - center.getZ() - 0.5D;
        return dx * dx + dz * dz;
    }

    public boolean isWithinMovementArea(double x, double z) {
        return horizontalDistanceSquared(x, z) <= getMovementRadius() * getMovementRadius();
    }

    /**
     * Admits players past the inner side of the storm and retains them until
     * they cross the actual boundary, including the open top and bottom.
     */
    public List<ServerPlayer> getCombatParticipants() {
        if (!(level instanceof ServerLevel server) || !isAlive() || isRemoved() || !isCombatReady()) {
            admittedPlayers.clear();
            return List.of();
        }
        // Retain creative damage permission through the side band without counting it as participation.
        List<ServerPlayer> admitted = server.players().stream().filter(this::canPlayerDamage).toList();
        admittedPlayers.clear();
        admitted.forEach(player -> admittedPlayers.add(player.getUUID()));
        return admitted.stream().filter(player -> !player.isCreative())
                .toList();
    }

    /** Resolves arena permission at the moment damage is applied, including indirect attacks. */
    private boolean canPlayerDamage(ServerPlayer player) {
        double radius = getCombatRadius();
        UUID id = player.getUUID();
        if (!isCombatReady() || !isAlive() || isRemoved() || player.level != level
                || !player.isAlive() || player.isRemoved() || player.isSpectator()
                || horizontalDistanceSquared(player.getX(), player.getZ()) > radius * radius
                || !isWithinArenaHeight(player.getBoundingBox().getCenter().y)) {
            admittedPlayers.remove(id);
            return false;
        }
        double innerRadius = radius - LightningArenaBoundaryDrag.entryInset(radius);
        return admittedPlayers.contains(id) || fitsBehindStorm(player, innerRadius) && admittedPlayers.add(id);
    }

    /** Requires the player's horizontal footprint to clear the side band on entry. */
    private boolean fitsBehindStorm(ServerPlayer player, double innerRadius) {
        AABB body = player.getBoundingBox();
        double centerX = getArenaCenter().getX() + 0.5;
        double centerZ = getArenaCenter().getZ() + 0.5;
        double x = Math.max(Math.abs(body.minX - centerX), Math.abs(body.maxX - centerX));
        double z = Math.max(Math.abs(body.minZ - centerZ), Math.abs(body.maxZ - centerZ));
        return x * x + z * z <= innerRadius * innerRadius;
    }

    @Override
    public boolean isCombatReady() {
        return entityData.get(DATA_COMBAT_READY);
    }

    /** @return whether lightning replaces the boss's physical form */
    private boolean isInLightningForm() {
        return !entityData.get(DATA_TRAVEL_ARC).isEmpty();
    }

    /** Removes the old hit target while the body is displayed in lightning form. */
    @Override
    public boolean isPickable() {
        return !isInLightningForm() && super.isPickable();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source)
                || source != DamageSource.OUT_OF_WORLD
                        && (isInLightningForm() || !arenaCombat.allowsDamage(source));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (!level.isClientSide && specialAttacks != null && specialAttacks.isRodHovering()) {
            return;
        }
        // Teleport pauses are stationary, but the short approach still uses vanilla collisions.
        if (!level.isClientSide && type == MoverType.SELF && !teleports.isWalking()) {
            movement = new Vec3(0, movement.y, 0);
        }
        super.move(type, movement);
    }

    @Override
    public void tick() {
        if (level.isClientSide) {
            previousLineCharge = entityData.get(DATA_LINE_CHARGE);
        }
        if (level instanceof ServerLevel server && isAlive()) {
            LightningStormData storm = LightningStormData.get(server);
            arena = storm.startOrResume(server, getUUID(), getArenaCenter(),
                    getCombatRadius(), getArenaMinimumY(), getArenaMaximumY());
            entityData.set(DATA_COMBAT_READY,
                    arena != null && !arena.isDraining() && !arena.isBuilding(server.getGameTime()));
            List<ServerPlayer> participants = getCombatParticipants();
            boolean canMove = isCombatReady() && !isNoAi();
            if (canMove) {
                arenaCombat.tick(participants);
            }
            boolean fighting = canMove && !participants.isEmpty() && tickCombatEntry();
            specialAttacks.tick(server, participants, fighting, teleports.isPreparing());
            storm.updateTornados(server, getUUID(), specialAttacks.tornadoSnapshots());
            specialAttacks.applyTornadoEffects(server, participants);
            tickMovement(participants, canMove);
            igniteTravelLeaves(server, storm);
            LightningArenaWind.Mode windMode = specialAttacks.windMode();
            storm.tickWind(server, getUUID(), windMode, fighting ? participants : List.of());
            boolean randomStrikes = fighting && windMode != LightningArenaWind.Mode.CALM;
            ambientStrikes.tick(server, participants, randomStrikes, specialAttacks.intensifiedStormTicksRemaining());
        }
        super.tick();
    }

    /** Checks only the section of the travelling lightning crossed during this tick. */
    private void igniteTravelLeaves(ServerLevel level, LightningStormData storm) {
        TravelArc arc = getTravelArc();
        if (arc == null || arc.isCharging()) {
            return;
        }
        long now = level.getGameTime();
        Vec3 from = arc.start().lerp(arc.end(), arc.progress(now));
        Vec3 to = arc.start().lerp(arc.end(), arc.progress(now + 1));
        int steps = Math.max(1, Mth.ceil(from.distanceTo(to) * 2));
        for (int step = 0; step <= steps; step++) {
            storm.igniteLeaves(level, getUUID(), new BlockPos(from.lerp(to, (double) step / steps)));
        }
    }

    /** Uses ordinary teleports unless the current special is controlling movement. */
    private void tickMovement(List<ServerPlayer> participants, boolean canMove) {
        if (specialAttacks.blocksNormalMovement()) {
            teleports.tick(false, null);
        } else {
            ServerPlayer focus = canMove ? getMovementFocus(participants) : null;
            if (getTarget() != focus) {
                setTarget(focus);
            }
            if (teleports.tick(canMove, focus)) {
                ServerPlayer nearest = closestParticipant(participants);
                if (nearest != null) {
                    if (getTarget() != nearest) {
                        setTarget(nearest);
                    }
                    teleports.focusAfterLanding(nearest);
                }
            }
        }
        if (specialAttacks.isRodHovering()) {
            setDeltaMovement(Vec3.ZERO);
            fallDistance = 0;
        } else if (!teleports.isWalking()) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
    }

    /** Pauses the entry countdown while the arena is empty or the boss AI is disabled. */
    private boolean tickCombatEntry() {
        if (encounterEntryTicks < 0) {
            encounterEntryTicks = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_COMBAT_ENTRY_DELAY.get());
        }
        return encounterEntryTicks == 0 || --encounterEntryTicks == 0;
    }

    private void resetArenaEncounter() {
        encounterEntryTicks = -1;
        ambientStrikes.reset();
        specialAttacks.reset();
        teleports.reset();
        setTarget(null);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        // Short teleports can use a relative movement packet. Snap across large horizontal jumps
        // so the model does not slide between the departure and arrival sparks.
        double dx = x - getX();
        double dz = z - getZ();
        if (level.isClientSide && dx * dx + dz * dz > 4) {
            moveTo(x, y, z, yaw, pitch);
            steps = 0;
        }
        super.lerpTo(x, y, z, yaw, pitch, steps, teleport);
    }

    /** Keeps the focus between landings while that player is still participating. */
    @Nullable
    ServerPlayer getMovementFocus(List<ServerPlayer> participants) {
        if (getTarget() instanceof ServerPlayer player && participants.contains(player)) {
            return player;
        }
        return closestParticipant(participants);
    }

    @Nullable
    private ServerPlayer closestParticipant(List<ServerPlayer> participants) {
        return participants.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    /**
     * Finds the nearest local floor, including arenas below the world's highest
     * surface.
     */
    @Nullable
    Double findArenaSurface(ServerLevel server, int x, int z) {
        return findArenaSurface(server, x, z, getArenaCenter().getY());
    }

    @Nullable
    Double findArenaSurface(ServerLevel server, int x, int z, int referenceY) {
        int minimumY = Math.max(getArenaMinimumY(), server.getMinBuildHeight() + 1);
        int maximumY = getArenaMaximumY() - 1;
        if (minimumY > maximumY) {
            return null;
        }
        referenceY = Mth.clamp(referenceY, minimumY, maximumY);
        int range = Math.max(referenceY - minimumY, maximumY - referenceY);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int offset = 0; offset <= range; offset++) {
            for (int side = 0; side < (offset == 0 ? 1 : 2); side++) {
                int y = referenceY + (side == 0 ? -offset : offset);
                if (y < minimumY || y > maximumY) {
                    continue;
                }
                pos.set(x, y, z);
                if (!server.getBlockState(pos).getCollisionShape(server, pos).isEmpty()) {
                    continue;
                }
                BlockPos below = pos.below();
                var support = server.getBlockState(below).getCollisionShape(server, below);
                if (!support.isEmpty()) {
                    return below.getY() + support.max(Direction.Axis.Y);
                }
            }
        }
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("LightningArenaCenter", getArenaCenter().asLong());
        tag.putString("LightningArenaDimension", level.dimension().location().toString());
        tag.put("LightningMovement", teleports.save());
        CompoundTag combat = arenaCombat.save();
        combat.putInt("EntryTicks", encounterEntryTicks);
        tag.put("LightningArenaCombat", combat);
        tag.put("LightningAmbientStrikes", ambientStrikes.save());
        tag.put("LightningSpecialAttacks", specialAttacks.save());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        boolean sameDimension = tag.getString("LightningArenaDimension")
                .equals(level.dimension().location().toString());
        arenaCenter = sameDimension && tag.contains("LightningArenaCenter", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("LightningArenaCenter"))
                : null;
        arena = null;
        admittedPlayers.clear();
        teleports.load(sameDimension ? tag.getCompound("LightningMovement") : new CompoundTag());
        CompoundTag combat = sameDimension ? tag.getCompound("LightningArenaCombat") : new CompoundTag();
        arenaCombat.load(combat);
        encounterEntryTicks = combat.contains("EntryTicks", Tag.TAG_INT)
                ? Mth.clamp(combat.getInt("EntryTicks"), -1, 200) : -1;
        ambientStrikes.load(sameDimension ? tag.getCompound("LightningAmbientStrikes") : new CompoundTag());
        specialAttacks.load(sameDimension ? tag.getCompound("LightningSpecialAttacks") : new CompoundTag());
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && level instanceof ServerLevel server) {
            LightningStormData.get(server).beginDrain(server, getUUID());
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (level instanceof ServerLevel server
                && (reason.shouldDestroy() || reason == RemovalReason.CHANGED_DIMENSION)) {
            LightningStormData.get(server).beginDrain(server, getUUID());
        }
    }
}
