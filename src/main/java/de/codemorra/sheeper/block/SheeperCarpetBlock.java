package de.codemorra.sheeper.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared type for Sheeper carpets, retaining vanilla carpet shape, support
 * rules,
 * and color. Registered variants copy their matching vanilla carpet properties.
 */
public class SheeperCarpetBlock extends WoolCarpetBlock {
    public SheeperCarpetBlock(BlockBehaviour.Properties properties, DyeColor color) {
        super(color, properties);
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
