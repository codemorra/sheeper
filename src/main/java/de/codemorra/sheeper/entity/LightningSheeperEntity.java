package de.codemorra.sheeper.entity;

import java.util.List;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Lightning encounter with a persistent arena origin and protected startup. */
public class LightningSheeperEntity extends ElementalSheeperEntity {
    private static final EntityDataAccessor<Boolean> DATA_COMBAT_READY = SynchedEntityData
            .defineId(LightningSheeperEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private BlockPos arenaCenter;
    private int preparationDuration = -1;
    private int preparationRemaining;
    private final LightningTeleportMovement teleports = new LightningTeleportMovement(this);
    private final LightningSheeperCombat combat = new LightningSheeperCombat(this, teleports);

    public LightningSheeperEntity(EntityType<? extends Sheep> type, Level level) {
        super(type, level, Element.LIGHTNING);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_COMBAT_READY, false);
    }

    /**
     * Idle looking keeps the stationary boss animated without choosing a player
     * target.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return isCombatReady() && combat.canMove() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return isCombatReady() && combat.canMove() && super.canContinueToUse();
            }
        });
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
        return SheeperConfig.LIGHTNING_COMBAT_RADIUS.get();
    }

    /**
     * Teleport destinations must stay inside both the movement and combat bounds.
     */
    public double getMovementRadius() {
        return Math.min(SheeperConfig.LIGHTNING_MOVEMENT_RADIUS.get(), getCombatRadius());
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
                        && Math.abs(player.getY()
                                - getArenaCenter().getY()) <= SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE.get())
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
        if (!level.isClientSide && type == MoverType.SELF
                && (!isCombatReady() || teleports.isPreparing() || !combat.canMove())) {
            movement = new Vec3(0.0D, movement.y, 0.0D);
        }
        super.move(type, movement);
    }

    private void initializePreparation() {
        getArenaCenter();
        if (preparationDuration < 0) {
            preparationDuration = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_PREPARATION_DURATION.get());
            preparationRemaining = preparationDuration;
            entityData.set(DATA_COMBAT_READY, preparationRemaining == 0);
        }
    }

    @Override
    public void tick() {
        if (!level.isClientSide && isAlive()) {
            initializePreparation();
            if (!isCombatReady() || teleports.isPreparing() || !combat.canMove()) {
                getNavigation().stop();
                Vec3 velocity = getDeltaMovement();
                setDeltaMovement(0.0D, velocity.y, 0.0D);
                setXxa(0.0F);
                setZza(0.0F);
            }
        }
        super.tick();
        if (level instanceof ServerLevel server && isAlive() && !isRemoved()) {
            if (!isCombatReady()) {
                preparationRemaining--;
                // A bounded number of particles represents a growing wave without editing
                // terrain.
                if (tickCount % 2 == 0 || preparationRemaining == 0) {
                    showArenaRing(server, getCombatRadius()
                            * (1.0D - (double) preparationRemaining / preparationDuration));
                }
                if (preparationRemaining == 0) {
                    entityData.set(DATA_COMBAT_READY, true);
                }
            } else {
                if (tickCount % 10 == 0) {
                    showArenaRing(server, getCombatRadius());
                }
                combat.tick(teleports.isPreparing());
                teleports.tick(!isNoAi() && combat.canMove());
            }
        }
    }

    /**
     * Terrain sampling never loads chunks or places blocks, fluids, fire, or real
     * lightning.
     */
    private void showArenaRing(ServerLevel server, double radius) {
        BlockPos center = getArenaCenter();
        for (int index = 0; index < 64; index++) {
            double angle = Math.PI * 2.0D * index / 64;
            double x = center.getX() + 0.5D + Math.cos(angle) * radius;
            double z = center.getZ() + 0.5D + Math.sin(angle) * radius;
            int blockX = Mth.floor(x);
            int blockZ = Mth.floor(z);
            if (!server.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
                continue;
            }
            Double surface = findArenaSurface(server, blockX, blockZ);
            if (surface != null) {
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, surface + 0.1D, z, 1,
                        0.05D, 0.05D, 0.05D, 0.0D);
            }
        }
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
        int originY = getArenaCenter().getY();
        int range = Mth.floor(SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE.get());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int offset = 0; offset <= range * 2; offset++) {
            for (int side = 0; side < (offset == 0 ? 1 : 2); side++) {
                int y = referenceY + (side == 0 ? -offset : offset);
                if (Math.abs(y - originY) > range || y <= server.getMinBuildHeight()
                        || y >= server.getMaxBuildHeight()) {
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
        initializePreparation();
        tag.putLong("LightningArenaCenter", getArenaCenter().asLong());
        tag.putString("LightningArenaDimension", level.dimension().location().toString());
        tag.putInt("LightningPreparationDuration", preparationDuration);
        tag.putInt("LightningPreparationRemaining", preparationRemaining);
        tag.put("LightningTeleport", teleports.save());
        tag.put("LightningCombat", combat.save());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        boolean sameDimension = !tag.contains("LightningArenaDimension", Tag.TAG_STRING)
                || tag.getString("LightningArenaDimension").equals(level.dimension().location().toString());
        arenaCenter = sameDimension && tag.contains("LightningArenaCenter", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("LightningArenaCenter"))
                : null;
        preparationDuration = sameDimension && tag.contains("LightningPreparationDuration", Tag.TAG_INT)
                ? Mth.clamp(tag.getInt("LightningPreparationDuration"), 0, 12000)
                : -1;
        preparationRemaining = preparationDuration < 0 ? 0
                : Mth.clamp(tag.getInt("LightningPreparationRemaining"), 0, preparationDuration);
        entityData.set(DATA_COMBAT_READY, preparationDuration >= 0 && preparationRemaining == 0);
        teleports.load(sameDimension ? tag.getCompound("LightningTeleport") : new CompoundTag());
        combat.load(sameDimension ? tag.getCompound("LightningCombat") : new CompoundTag());
    }
}
