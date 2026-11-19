package de.codemorra.sheeper.entity.navigation;

import de.codemorra.sheeper.registry.ModFluids;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/**
 * Three-dimensional water navigation with full-body collision and arena checks.
 * Recognizes custom boss water without enabling surface breaching.
 */
public final class WaterArenaNavigation extends WaterBoundPathNavigation {
    /**
     * Creates swim navigation with support for the arena's custom fluid.
     *
     * @param mob   entity whose dimensions and movement control are used for paths
     * @param level world containing the navigable terrain
     */
    public WaterArenaNavigation(WaterSheeperEntity mob, Level level) {
        super(mob, level);
        // Vertical detours around terrain need more nodes than short ground legs.
        setMaxVisitedNodesMultiplier(2.0F);
    }

    /**
     * Checks every path node against the scaled boss body instead of a center-point
     * ray.
     */
    @Override
    protected PathFinder createPathFinder(int budget) {
        nodeEvaluator = new ArenaSwimNodeEvaluator();
        return new PathFinder(nodeEvaluator, budget);
    }

    /** {@inheritDoc} Includes the custom arena fluid alongside ordinary water. */
    @Override
    protected boolean isInLiquid() {
        return mob.isInWaterOrBubble() || mob.isInFluidType(ModFluids.BOSS_WATER_TYPE.get());
    }

    /**
     * Large bosses need feet-based waypoint checks to match
     * Path.getEntityPosAtNode.
     */
    @Override
    protected Vec3 getTempMobPos() {
        return mob.position();
    }

    /**
     * Advances at the exact body-aligned path position with a tight spatial
     * tolerance so scaled swimmers do not skip vertical turns.
     */
    @Override
    protected void followThePath() {
        if (path == null || path.isDone()) {
            return;
        }
        Vec3 current = getTempMobPos();
        // Skip short zigzags and the snapped start node when a full-body water
        // corridor is clear. Bounded lookahead avoids expensive long-range scans.
        int furthest = Math.min(path.getNodeCount() - 1, path.getNextNodeIndex() + 4);
        for (int index = furthest; index > path.getNextNodeIndex(); index--) {
            if (canMoveDirectly(current, path.getEntityPosAtNode(mob, index))) {
                path.setNextNodeIndex(index);
                break;
            }
        }
        maxDistanceToWaypoint = 0.25F;
        if (current.distanceToSqr(path.getNextEntityPos(mob)) <= maxDistanceToWaypoint * maxDistanceToWaypoint) {
            path.advance();
        }
        doStuckDetection(current);
    }

    /**
     * Accepts only fully submerged, collision-free shortcuts for the whole body.
     * The swept box deliberately rejects tight diagonal corners and unloaded
     * chunks.
     */
    @Override
    protected boolean canMoveDirectly(Vec3 from, Vec3 to) {
        if (!(mob instanceof WaterSheeperEntity boss) || !(level instanceof ServerLevel server)
                || !boss.canSwimAt(to)) {
            return false;
        }
        AABB swept = mob.getBoundingBox().move(from.subtract(mob.position()))
                .expandTowards(to.subtract(from)).deflate(0.01D);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(swept.minX, swept.minY, swept.minZ),
                new BlockPos(swept.maxX, swept.maxY, swept.maxZ))) {
            if (!WaterArenaData.isLoaded(server, pos)) {
                return false;
            }
        }
        return !level.getBlockCollisions(mob, swept).iterator().hasNext();
    }

    private static final class ArenaSwimNodeEvaluator extends SwimNodeEvaluator {
        private ArenaSwimNodeEvaluator() {
            super(false);
        }

        /** Virtual water has no air-breaching surcharge in vanilla's swim evaluator. */
        @Override
        protected Node getNode(int x, int y, int z) {
            Node node = super.getNode(x, y, z);
            if (node != null) {
                node.costMalus = mob.getPathfindingMalus(BlockPathTypes.WATER);
            }
            return node;
        }

        @Override
        public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z, Mob mob,
                int width, int height, int depth, boolean openDoors, boolean passDoors) {
            double offset = (int) (mob.getBbWidth() + 1.0F) * 0.5D;
            if (!(mob instanceof WaterSheeperEntity boss) || !boss.canSwimAt(new Vec3(x + offset, y, z + offset))) {
                return BlockPathTypes.BLOCKED;
            }
            return BlockPathTypes.WATER;
        }
    }
}
