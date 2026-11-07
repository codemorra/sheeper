package de.codemorra.sheeper.fluid;

import de.codemorra.sheeper.block.BossWaterBlock;
import de.codemorra.sheeper.registry.ModBlocks;
import de.codemorra.sheeper.registry.ModFluids;
import de.codemorra.sheeper.world.WaterArenaData;
import de.codemorra.sheeper.entity.WaterArenaSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.fluids.FluidType;

/**
 * Water flow with finite descendants and sources restricted to arena placement.
 */
public abstract class BossWaterFluid extends WaterFluid {
    /** Uses vanilla water's drop handling when clearing an initial source cell. */
    public boolean placeInitialSource(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!WaterArenaSearch.isSurface(level, pos)
                || !state.is(ModBlocks.BOSS_WATER.get()) && !WaterArenaSearch.isSourceCandidate(level, pos)) {
            return false;
        }
        if (!state.isAir() && !state.is(ModBlocks.BOSS_WATER.get())) {
            beforeDestroyingBlock(level, pos, state);
        }
        return level.setBlock(pos, ModBlocks.BOSS_WATER.get().defaultBlockState(), 3);
    }

    @Override
    public Fluid getSource() {
        return ModFluids.BOSS_WATER.get();
    }

    @Override
    public Fluid getFlowing() {
        return ModFluids.FLOWING_BOSS_WATER.get();
    }

    @Override
    public FluidType getFluidType() {
        return ModFluids.BOSS_WATER_TYPE.get();
    }

    @Override
    public Item getBucket() {
        return Items.AIR;
    }

    @Override
    public boolean isSame(Fluid other) {
        return other instanceof BossWaterFluid;
    }

    @Override
    protected boolean canConvertToSource() {
        return false;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid,
            Direction direction) {
        return fluid instanceof BossWaterFluid && !state.isSource();
    }

    @Override
    public BlockState createLegacyBlock(FluidState state) {
        return ModBlocks.BOSS_WATER.get().defaultBlockState().setValue(BossWaterBlock.LEVEL, getLegacyLevel(state));
    }

    /**
     * Follows vanilla's neighbor/shape rules but never converts neighbors into
     * sources.
     */
    @Override
    protected FluidState getNewLiquid(LevelReader level, BlockPos pos, BlockState state) {
        int amount = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            BlockState other = level.getBlockState(neighbor);
            FluidState fluid = other.getFluidState();
            if (fluid.getType() instanceof BossWaterFluid && canPass(level, pos, state, neighbor, other, direction)) {
                amount = Math.max(amount, fluid.getAmount());
            }
        }
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (aboveState.getFluidState().getType() instanceof BossWaterFluid
                && canPass(level, pos, state, above, aboveState, Direction.UP)) {
            return getFlowing(8, true);
        }
        return amount <= getDropOff(level) ? Fluids.EMPTY.defaultFluidState()
                : getFlowing(amount - getDropOff(level), false);
    }

    private boolean canPass(BlockGetter level, BlockPos from, BlockState first, BlockPos to, BlockState second,
            Direction direction) {
        return !Shapes.mergedFaceOccludes(first.getCollisionShape(level, from), second.getCollisionShape(level, to),
                direction);
    }

    @Override
    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction,
            FluidState fluid) {
        if (level instanceof ServerLevel server
                && WaterArenaData.get(server).inheritFlow(server, pos.relative(direction.getOpposite()), pos)) {
            if (state.hasProperty(BossWaterlogging.LEVEL)) {
                // Some vanilla containers override placeLiquid and only accept Fluids.WATER.
                BossWaterlogging.place(level, pos, state, fluid);
            } else {
                super.spreadTo(level, pos, state, direction, fluid);
            }
        }
    }

    @Override
    public void tick(Level level, BlockPos pos, FluidState state) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        WaterArenaData data = WaterArenaData.get(server);
        if (!data.observeFlow(server, pos, state.isSource())) {
            return;
        }
        BlockState block = level.getBlockState(pos);
        if (BossWaterlogging.isWet(block)) {
            FluidState updated = getNewLiquid(level, pos, block);
            int amount = updated.isEmpty() ? 0 : Math.min(7, updated.getValue(LEVEL));
            level.setBlock(pos, block.setValue(BossWaterlogging.LEVEL, amount), 3);
            if (amount > 0) {
                FluidState current = level.getFluidState(pos);
                spread(level, pos, current);
                level.scheduleTick(pos, current.getType(), getTickDelay(level));
            } else {
                data.forget(pos);
            }
        } else {
            super.tick(level, pos, state);
            if (!(level.getFluidState(pos).getType() instanceof BossWaterFluid)) {
                data.forget(pos);
            }
        }
    }

    public static final class Source extends BossWaterFluid {
        @Override
        public boolean isSource(FluidState state) {
            return true;
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }
    }

    public static final class Flowing extends BossWaterFluid {
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }
    }
}
