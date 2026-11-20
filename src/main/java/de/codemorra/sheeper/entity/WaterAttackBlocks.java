package de.codemorra.sheeper.entity;

import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Shared leaf penetration for water attacks within the virtual arena.
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

    /** Clears nearer leaves first while solid or protected blocks continue to shield the leaves behind them. */
    static void clearReachableLeaves(WaterSheeperEntity boss, ServerLevel level, Vec3 origin,
            List<BlockPos> leaves, boolean horizontal) {
        leaves.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(origin.x,
                horizontal ? pos.getY() + 0.5 : origin.y, origin.z)));
        for (BlockPos pos : leaves) {
            Vec3 target = Vec3.atCenterOf(pos);
            Vec3 start = horizontal ? new Vec3(origin.x, target.y, origin.z) : origin;
            BlockHitResult hit = clipLoaded(boss, level, start, target);
            if (hit != null && (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos))) {
                clearLeaves(boss, pos);
            }
        }
    }

    /** Traces blast cover only through terrain the server already has loaded. */
    @Nullable
    static BlockHitResult clipLoaded(WaterSheeperEntity boss, ServerLevel level, Vec3 start, Vec3 end) {
        return chunksLoaded(level, new AABB(start, end))
                ? level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss))
                : null;
    }

    /** Checks the bounding chunks without loading or generating terrain. */
    static boolean chunksLoaded(ServerLevel level, AABB bounds) {
        for (int x = Mth.floor(bounds.minX) >> 4; x <= Mth.floor(bounds.maxX) >> 4; x++) {
            for (int z = Mth.floor(bounds.minZ) >> 4; z <= Mth.floor(bounds.maxZ) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Honors mob griefing and block protection before clearing arena leaves.
     * Naturally waterlogged leaves retain their water; no boss water is placed.
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
