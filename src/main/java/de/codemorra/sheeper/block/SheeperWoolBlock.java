package de.codemorra.sheeper.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Shared block type for all Sheeper wool colors.
 * Each color receives the properties of its matching vanilla wool block.
 * Block instances are shared by all placed blocks of the same registered type.
 */
public class SheeperWoolBlock extends Block {
    public SheeperWoolBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
