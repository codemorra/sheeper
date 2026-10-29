package de.codemorra.sheeper.entity;

import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.DyeColor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import javax.annotation.Nullable;

/**
 * Primed wool with vanilla TNT physics and explosions, a synchronized color,
 * and color persistence alongside the inherited fuse data.
 */
public class PrimedSheeperWool extends PrimedTnt {
    private static final EntityDataAccessor<Integer> DATA_WOOL_COLOR = SynchedEntityData.defineId(
            PrimedSheeperWool.class, EntityDataSerializers.INT);

    @Nullable
    private LivingEntity igniter;

    /**
     * Creates the entity for registry factories, client spawning, and world
     * loading.
     */
    public PrimedSheeperWool(
            EntityType<? extends PrimedTnt> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * Creates primed wool at a block position with vanilla TNT launch motion.
     */
    public PrimedSheeperWool(
            Level level, BlockPos pos, DyeColor color,
            @Nullable LivingEntity igniter) {
        this(ModEntities.PRIMED_SHEEPER_WOOL.get(), level);

        setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        double angle = random.nextDouble() * Math.PI * 2.0;
        setDeltaMovement(
                -Math.sin(angle) * 0.02,
                0.2,
                -Math.cos(angle) * 0.02);

        setFuse(80);
        setColor(color);
        this.igniter = igniter;

        // Start interpolation at the spawn position rather than the world origin.
        xo = getX();
        yo = getY();
        zo = getZ();
    }

    /**
     * Exposes the igniter to vanilla explosion attribution and chain reactions.
     * PrimedTnt stores its own owner privately, so this subclass supplies its
     * owner.
     */
    @Override
    @Nullable
    public LivingEntity getOwner() {
        return igniter;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_WOOL_COLOR, DyeColor.WHITE.getId());
    }

    public DyeColor getColor() {
        return DyeColor.byId(entityData.get(DATA_WOOL_COLOR));
    }

    public void setColor(DyeColor color) {
        entityData.set(DATA_WOOL_COLOR, color.getId());
    }

    /**
     * Saves the color in addition to the remaining fuse saved by PrimedTnt.
     */
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("WoolColor", (byte) getColor().getId());
    }

    /**
     * Restores the inherited fuse and the color used by tracking clients.
     */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setColor(DyeColor.byId(tag.getByte("WoolColor")));
    }
}
