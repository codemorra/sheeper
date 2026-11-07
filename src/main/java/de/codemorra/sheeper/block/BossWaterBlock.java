package de.codemorra.sheeper.block;

import de.codemorra.sheeper.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Vanilla fluid levels and scheduling, without bucket extraction. */
public final class BossWaterBlock extends Block {
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;

    public BossWaterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        int level = state.getValue(LEVEL);
        return level == 0 ? ModFluids.BOSS_WATER.get().getSource(false)
                : ModFluids.FLOWING_BOSS_WATER.get().getFlowing(level >= 8 ? 8 : 8 - level, level >= 8);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        level.scheduleTick(pos, state.getFluidState().getType(), 5);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor,
            boolean moving) {
        level.scheduleTick(pos, state.getFluidState().getType(), 5);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        level.scheduleTick(pos, state.getFluidState().getType(), 5);
        return state;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
