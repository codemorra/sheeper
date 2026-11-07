package de.codemorra.sheeper.fluid;

import de.codemorra.sheeper.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** Hooks for finite boss water inside existing waterloggable blocks. */
public final class BossWaterlogging {
    // Zero is dry; 1-7 are flowing amounts. Waterlogging never creates a source.
    public static final IntegerProperty LEVEL = IntegerProperty.create("sheeper_water_level", 0, 7);

    private static boolean ready;

    public static void enable() {
        ready = true;
    }

    private BossWaterlogging() {
    }

    public static void addProperty(Block block, StateDefinition.Builder<Block, BlockState> builder) {
        if (block instanceof SimpleWaterloggedBlock) {
            builder.add(LEVEL);
        }
    }

    public static BlockState dryDefault(BlockState state) {
        return state.hasProperty(LEVEL) ? state.setValue(LEVEL, 0) : state;
    }

    public static boolean isWet(BlockState state) {
        return state.hasProperty(LEVEL) && state.getValue(LEVEL) > 0;
    }

    /** Null leaves vanilla's fluid lookup unchanged. */
    public static FluidState fluidOverride(BlockState state) {
        if (!isWet(state) || state.getValue(BlockStateProperties.WATERLOGGED)) {
            return null;
        }
        // State caches may be built before mod fluid registration during bootstrap.
        return ready
                ? ModFluids.FLOWING_BOSS_WATER.get().getFlowing(state.getValue(LEVEL), false)
                : Fluids.EMPTY.defaultFluidState();
    }

    /** -1 delegates to vanilla; 0/1 are definitive answers. */
    public static int canPlace(BlockState state, Fluid fluid) {
        if (fluid instanceof BossWaterFluid) {
            return state.hasProperty(LEVEL) && !state.getValue(BlockStateProperties.WATERLOGGED) ? 1 : 0;
        }
        return isWet(state) ? 0 : -1;
    }

    public static int place(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        int decision = canPlace(state, fluid.getType());
        if (decision != 1) {
            return decision;
        }
        if (!level.isClientSide()) {
            if (state.getBlock() instanceof CampfireBlock) {
                if (state.getValue(BlockStateProperties.LIT)) {
                    level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                CampfireBlock.dowse(null, level, pos, state);
                state = state.setValue(BlockStateProperties.LIT, false);
            } else if (state.getBlock() instanceof CandleBlock && state.getValue(BlockStateProperties.LIT)) {
                CandleBlock.extinguish(null, state, level, pos);
                state = level.getBlockState(pos);
            }
            int amount = Math.min(7, fluid.getAmount());
            level.setBlock(pos, state.setValue(LEVEL, amount), 3);
            level.scheduleTick(pos, ModFluids.FLOWING_BOSS_WATER.get(), 5);
        }
        return 1;
    }
}
