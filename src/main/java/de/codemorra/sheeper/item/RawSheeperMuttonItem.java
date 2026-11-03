package de.codemorra.sheeper.item;

import de.codemorra.sheeper.util.SheeperMuttonExplosion;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * Raw mutton whose dropped stack detonates on direct contact with fire or lava.
 * Items stored in inventories and food placed for cooking use separate rules.
 */
public class RawSheeperMuttonItem extends Item {
    public RawSheeperMuttonItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!(entity.getLevel() instanceof ServerLevel level)
                || entity.isRemoved() || stack.isEmpty() || !SheeperConfig.MUTTON_FIRE_ENABLED.get()) {
            return false;
        }

        if (entity.isOnFire() || entity.isInLava() || touchesFire(level, entity.getBoundingBox())) {
            detonate(entity, level);
            return true;
        }

        return false;
    }

    /**
     * Handles lethal fire damage during vanilla movement before the next item tick.
     */
    @Override
    public void onDestroyed(ItemEntity entity, DamageSource source) {
        if (SheeperConfig.MUTTON_FIRE_ENABLED.get() && source.isFire() && !entity.isRemoved()
                && entity.getLevel() instanceof ServerLevel level) {
            detonate(entity, level);
        }
    }

    private static boolean touchesFire(ServerLevel level, AABB bounds) {
        AABB contact = bounds.deflate(1.0E-7D);

        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(contact.minX, contact.minY, contact.minZ),
                new BlockPos(contact.maxX, contact.maxY, contact.maxZ))) {
            // Contact checks must not load neighboring chunks.
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
            if (chunk == null) {
                continue;
            }

            BlockState state = chunk.getBlockState(pos);
            if (state.is(BlockTags.FIRE) || CampfireBlock.isLitCampfire(state)) {
                return true;
            }

            FluidState fluid = state.getFluidState();
            if (fluid.is(FluidTags.LAVA)
                    && contact.minY < pos.getY() + fluid.getHeight(level, pos)) {
                return true;
            }
        }

        return false;
    }

    private static void detonate(ItemEntity entity, ServerLevel level) {
        // Remove the entire stack before the blast so it cannot trigger twice.
        entity.discard();
        // Keep the blast center above the collision surface the item rests on.
        SheeperMuttonExplosion.explode(level, entity.getX(), entity.getY(0.5D), entity.getZ(),
                SheeperConfig.MUTTON_FIRE_STRENGTH.get().floatValue());
    }
}
