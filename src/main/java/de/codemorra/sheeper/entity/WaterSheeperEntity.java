package de.codemorra.sheeper.entity;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.Level;

/**
 * Water boss foundation; arena generation and combat are implemented
 * separately.
 */
public class WaterSheeperEntity extends ElementalSheeperEntity {
    @Nullable
    private BlockPos arenaCenter;

    public WaterSheeperEntity(EntityType<? extends Sheep> type, Level level) {
        super(type, level, Element.WATER);
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
     * Horizontal encounter radius in blocks, reserved for arena and combat logic.
     */
    public double getCombatRadius() {
        return SheeperConfig.WATER_COMBAT_RADIUS.get();
    }

    /** Keeps the configured movement area within the encounter area. */
    public double getMovementRadius() {
        return Math.min(SheeperConfig.WATER_MOVEMENT_RADIUS.get(), getCombatRadius());
    }

    @Override
    public void tick() {
        if (!level.isClientSide) {
            // Capture placement before gravity or future AI can move the boss.
            getArenaCenter();
        }
        super.tick();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("WaterArenaCenter", getArenaCenter().asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Existing bosses without an origin establish one at their loaded position.
        arenaCenter = tag.contains("WaterArenaCenter", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("WaterArenaCenter"))
                : null;
    }
}
