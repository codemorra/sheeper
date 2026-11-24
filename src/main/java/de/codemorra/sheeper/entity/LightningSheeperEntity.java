package de.codemorra.sheeper.entity;

import java.util.List;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.network.LightningWeatherPacket;
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
import net.minecraft.world.phys.Vec3;

/** Lightning encounter with a persistent arena origin and protected startup. */
public class LightningSheeperEntity extends ElementalSheeperEntity {
    private static final EntityDataAccessor<Boolean> DATA_COMBAT_READY = SynchedEntityData
            .defineId(LightningSheeperEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private BlockPos arenaCenter;
    @Nullable
    private LightningWeatherPacket.Arena arena;

    public LightningSheeperEntity(EntityType<? extends Sheep> type, Level level) {
        super(type, level, Element.LIGHTNING);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_COMBAT_READY, false);
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

    /** @return whether the position falls between the arena floor and ceiling */
    public boolean isWithinArenaHeight(double y) {
        return y >= getArenaMinimumY() && y < getArenaMaximumY();
    }

    /**
     * Teleport destinations must stay inside both the movement and combat bounds.
     */
    public double getMovementRadius() {
        return Math.min(LightningSheeperConfig.LIGHTNING_MOVEMENT_RADIUS.get(), getCombatRadius());
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
     * Current arena participants, independent of preparation progress and line of
     * sight.
     */
    public List<ServerPlayer> getCombatParticipants() {
        if (!(level instanceof ServerLevel server) || !isAlive() || isRemoved()) {
            return List.of();
        }
        return server.players().stream()
                .filter(player -> player.level == level && player.isAlive() && !player.isRemoved()
                        && !player.isCreative() && !player.isSpectator()
                        && horizontalDistanceSquared(player.getX(), player.getZ()) <= getCombatRadius()
                                * getCombatRadius()
                        && isWithinArenaHeight(player.getY()))
                .toList();
    }

    @Override
    public boolean isCombatReady() {
        return entityData.get(DATA_COMBAT_READY);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // Preserve administrative removal and void damage during startup.
        return !isCombatReady() && source != DamageSource.OUT_OF_WORLD || super.isInvulnerableTo(source);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        // The arena can be tested with gravity and ordinary collisions while AI is being rebuilt.
        if (!level.isClientSide && type == MoverType.SELF) {
            movement = new Vec3(0, movement.y, 0);
        }
        super.move(type, movement);
    }

    @Override
    public void tick() {
        if (level instanceof ServerLevel server && isAlive()) {
            arena = LightningStormData.get(server).startOrResume(server, getUUID(), getArenaCenter(),
                    getCombatRadius(), getArenaMinimumY(), getArenaMaximumY());
            entityData.set(DATA_COMBAT_READY, arena != null && !arena.isBuilding(server.getGameTime()));
        }
        super.tick();
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
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && level instanceof ServerLevel server) {
            LightningStormData.get(server).remove(server, getUUID());
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (level instanceof ServerLevel server
                && (reason.shouldDestroy() || reason == RemovalReason.CHANGED_DIMENSION)) {
            LightningStormData.get(server).remove(server, getUUID());
        }
    }
}
