package de.codemorra.sheeper.entity;

import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Shared leaf penetration for water attacks, without opening temporary air
 * pockets.
 */
final class WaterAttackBlocks {
    private WaterAttackBlocks() {
    }

    /**
     * Treats arena leaves as transparent for aiming without destroying anything
     * during warnings.
     */
    static boolean hasLineOfSight(WaterSheeperEntity boss, Entity target) {
        if (boss.level != target.level || boss.getEyePosition().distanceToSqr(target.getEyePosition()) > 128 * 128) {
            return false;
        }
        ClipContext context = new ClipContext(boss.getEyePosition(), target.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss) {
            @Override
            public VoxelShape getBlockShape(BlockState state, BlockGetter level, BlockPos pos) {
                return state.is(BlockTags.LEAVES) && boss.isArenaWaterCell(pos) ? Shapes.empty()
                        : super.getBlockShape(state, level, pos);
            }
        };
        return boss.level.clip(context).getType() == HitResult.Type.MISS;
    }

    /**
     * Clears successive leaves, stopping at other terrain or a bounded per-tick
     * work limit.
     */
    static BlockHitResult clipBeam(WaterSheeperEntity boss, Vec3 start, Vec3 end) {
        BlockHitResult hit = boss.level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
        for (int cleared = 0; cleared < 64 && hit.getType() == HitResult.Type.BLOCK; cleared++) {
            if (!clearLeaves(boss, hit.getBlockPos())) {
                break;
            }
            hit = boss.level.clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
        }
        return hit;
    }

    /**
     * Honors mob griefing and block protection before replacing leaves with owned
     * source water.
     */
    static boolean clearLeaves(WaterSheeperEntity boss, BlockPos pos) {
        if (!(boss.level instanceof ServerLevel level) || !boss.isArenaWaterCell(pos)
                || !WaterArenaData.isLoaded(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.LEAVES) && ForgeEventFactory.getMobGriefingEvent(level, boss)
                && state.canEntityDestroy(level, pos, boss) && ForgeEventFactory.onEntityDestroyBlock(boss, pos, state)
                && WaterArenaData.get(level).replaceAttackLeaves(level, boss, pos);
    }
}
