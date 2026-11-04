package de.codemorra.sheeper.block;

import javax.annotation.Nullable;

import de.codemorra.sheeper.util.SheeperCarpetExplosion;
import de.codemorra.sheeper.util.SheeperWoolMining;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolActions;

/**
 * A single-use step trap retaining vanilla carpet shape, support rules, and
 * color.
 * Registered variants copy their matching vanilla carpet properties.
 */
public class SheeperCarpetBlock extends WoolCarpetBlock {
    public SheeperCarpetBlock(BlockBehaviour.Properties properties, DyeColor color) {
        super(color, properties);
    }

    /**
     * Checks foot contact rather than stepOn: vanilla's step callback samples below
     * this thin carpet and reaches its supporting block. Only grounded living
     * entities trigger; careful stepping and crouching leave the carpet intact.
     * Remove it before applying damage so repeated contacts cannot detonate twice.
     */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity living) || !living.isAlive()
                || entity.isSpectator() || entity instanceof Player player && player.isCreative()
                || entity.isSteppingCarefully() || entity.isCrouching() || !entity.isOnGround()) {
            return;
        }

        double feet = entity.getY() - pos.getY();
        if (feet < 0.0D || feet > 1.0D / 16.0D + 1.0E-4D) {
            return;
        }

        if (level.getBlockState(pos).is(this) && level.removeBlock(pos, false)) {
            SheeperCarpetExplosion.explode(level, pos);
        }
    }

    /**
     * Shears harvest safely; other survival tools cause the same non-destructive
     * mining blast as Sheeper wool, without a carpet drop. The supplied tool is the
     * pre-durability snapshot, so the last use of shears is also safe. Creative
     * harvesting skips this callback.
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (level.isClientSide) {
            return;
        }

        if (tool.canPerformAction(ToolActions.SHEARS_DIG)) {
            super.playerDestroy(level, player, pos, state, blockEntity, tool);
        } else {
            SheeperWoolMining.unsafeHarvest(level, player, pos, this);
        }
    }

    /**
     * Matches vanilla carpet flammability without inheriting wool's TNT behavior.
     */
    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 20;
    }

    /** Matches vanilla carpet fire spread. */
    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 60;
    }
}
