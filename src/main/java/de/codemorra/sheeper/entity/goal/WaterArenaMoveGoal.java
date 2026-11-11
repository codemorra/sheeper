package de.codemorra.sheeper.entity.goal;

import java.util.EnumSet;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterArenaSearch;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Random surface navigation around the saved origin, independent of players.
 */
public final class WaterArenaMoveGoal extends Goal {
    private final WaterSheeperEntity boss;
    private long nextMovementTick = -1;
    private Path path;

    public WaterArenaMoveGoal(WaterSheeperEntity boss) {
        this.boss = boss;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private void scheduleNextMovement() {
        int minimum = SheeperConfig.secondsToTicks(SheeperConfig.WATER_MOVEMENT_INTERVAL_MIN.get());
        int maximum = Math.max(minimum, SheeperConfig.secondsToTicks(SheeperConfig.WATER_MOVEMENT_INTERVAL_MAX.get()));
        nextMovementTick = boss.level.getGameTime() + minimum + boss.getRandom().nextInt(maximum - minimum + 1);
    }

    @Override
    public boolean canUse() {
        if (!boss.isCombatReady() || boss.isPerformingSalvo()) {
            return false;
        }
        if (nextMovementTick < 0) {
            scheduleNextMovement();
        }
        if (boss.level.getGameTime() < nextMovementTick) {
            return false;
        }
        scheduleNextMovement();
        if (boss.getMovementRadius() <= 0.0D || SheeperConfig.WATER_MOVEMENT_SPEED.get() <= 0.0D) {
            return false;
        }
        // Reservoir sampling avoids allocating a second copy of the arena each time.
        BlockPos destination = null;
        int count = 0;
        boolean returning = !boss.isWithinMovementArea(boss.getX(), boss.getZ());
        double nearestDistance = Double.POSITIVE_INFINITY;
        // Navigation finishes near a waypoint, so recovery needs an interior target.
        double recoveryRadius = Math.max(0.0D, boss.getMovementRadius() - Math.max(1.0D, boss.getBbWidth()));
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        for (BlockPos pos : boss.getArenaSurfacePositions()) {
            Vec3 point = Vec3.atBottomCenterOf(pos);
            if (boss.isWithinMovementArea(point.x, point.z)
                    && (!returning || point.subtract(center).horizontalDistanceSqr() <= recoveryRadius * recoveryRadius)
                    && WaterArenaData.isLoaded((ServerLevel) boss.level, pos)
                    && WaterArenaSearch.isSurface(boss.level, pos)
                    && boss.level.noCollision(boss, boss.getBoundingBox().move(point.subtract(boss.position())))) {
                double distance = point.distanceToSqr(boss.position());
                // Recover via the closest surface instead of wandering toward a
                // random point whose approach may run along the outer boundary.
                if (returning ? distance < nearestDistance : boss.getRandom().nextInt(++count) == 0) {
                    destination = pos;
                    nearestDistance = distance;
                }
            }
        }
        if (destination == null) {
            return false;
        }
        path = boss.getNavigation().createPath(destination, 0);
        return path != null && path.canReach() && isPathWithinArena(path);
    }

    private boolean isPathWithinArena(Path candidate) {
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double radiusSquared = boss.getMovementRadius() * boss.getMovementRadius();
        double limitSquared = Math.max(radiusSquared, boss.position().subtract(center).horizontalDistanceSqr());
        // The first node is the occupied start cell. Remaining nodes must stay
        // inside, or lead progressively inward if the boss was displaced outside.
        for (int index = Math.max(1, candidate.getNextNodeIndex()); index < candidate.getNodeCount(); index++) {
            Vec3 point = candidate.getEntityPosAtNode(boss, index);
            double distanceSquared = point.subtract(center).horizontalDistanceSqr();
            if (distanceSquared > limitSquared + 1.0E-7D) {
                return false;
            }
            limitSquared = Math.max(radiusSquared, distanceSquared);
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return boss.isCombatReady() && !boss.isPerformingSalvo() && !boss.getNavigation().isDone()
                && boss.level.getGameTime() < nextMovementTick
                && SheeperConfig.WATER_MOVEMENT_SPEED.get() > 0.0D
                && isPathWithinArena(boss.getNavigation().getPath());
    }

    @Override
    public void start() {
        boss.getNavigation().moveTo(path, SheeperConfig.WATER_MOVEMENT_SPEED.get());
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        boss.getNavigation().setSpeedModifier(SheeperConfig.WATER_MOVEMENT_SPEED.get());
    }

    @Override
    public void stop() {
        boss.getNavigation().stop();
        // Avoid carrying movement input into the pause between navigation targets.
        Vec3 velocity = boss.getDeltaMovement();
        boss.setDeltaMovement(0.0D, velocity.y, 0.0D);
        boss.setXxa(0.0F);
        boss.setZza(0.0F);
    }
}
