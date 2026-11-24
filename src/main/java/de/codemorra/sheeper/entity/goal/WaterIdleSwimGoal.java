package de.codemorra.sheeper.entity.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Quietly roams the flooded arena while no combat target is present. Reuses
 * full-body water navigation, without combat orbiting or sprint boosts.
 */
public final class WaterIdleSwimGoal extends Goal {
    private final WaterSheeperEntity boss;
    private long nextPlanTick;

    /** @param boss owner whose captured arena bounds constrain every idle route */
    public WaterIdleSwimGoal(WaterSheeperEntity boss) {
        this.boss = boss;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return boss.isAlive() && !boss.isNoAi() && boss.isCombatReady() && boss.isInArenaWater()
                && !boss.isInBubbleForm()
                && boss.getTarget() == null && !boss.isPerformingBeam() && !boss.isPerformingMaelstrom()
                && boss.getMovementRadius() > 0
                && WaterSheeperConfig.WATER_MOVEMENT_SPEED.get() > 0
                && WaterSheeperConfig.WATER_IDLE_SPEED.get() > 0;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        nextPlanTick = 0;
    }

    @Override
    public void tick() {
        if (!canUse()) {
            // Goal selection can lag behind the shared target update by a tick.
            stop();
            return;
        }
        long now = boss.level.getGameTime();
        double speed = WaterSheeperConfig.WATER_MOVEMENT_SPEED.get() * WaterSheeperConfig.WATER_IDLE_SPEED.get();
        if (boss.getNavigation().isDone() && now >= nextPlanTick) {
            Path path = choosePath();
            if (boss.tryBubbleRecovery(path != null)) {
                return;
            }
            if (path != null) {
                boss.getNavigation().moveTo(path, speed);
            }
            int minimum = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_IDLE_INTERVAL_MIN.get());
            int maximum = Math.max(minimum,
                    SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_IDLE_INTERVAL_MAX.get()));
            nextPlanTick = now + minimum + boss.getRandom().nextInt(maximum - minimum + 1);
        }
        boss.getNavigation().setSpeedModifier(speed);
        Path path = boss.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            Vec3 next = path.getNextEntityPos(boss);
            boss.getLookControl().setLookAt(next.x, next.y + boss.getBbHeight() * 0.5D, next.z);
        }
    }

    /**
     * Tries bounded local swim legs so random destinations remain reachable in
     * uneven terrain. Failed choices wait for the next planning interval.
     *
     * @return a progressing route fully inside the allowed area, or null
     */
    @Nullable
    private Path choosePath() {
        double leg = Math.min(12, Math.max(3, boss.getMovementRadius() / 3));
        double offset = (int) (boss.getBbWidth() + 1.0F) * 0.5D;
        int pathAttempts = 0;
        for (int attempt = 0; attempt < 16 && pathAttempts < 4; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2;
            double distance = leg * (0.4D + boss.getRandom().nextDouble() * 0.6D);
            Vec3 desired = boss.clampSwimmingPosition(boss.position().add(Math.cos(angle) * distance,
                    (boss.getRandom().nextDouble() - 0.5D) * 8, Math.sin(angle) * distance));
            BlockPos node = new BlockPos(desired.x - offset, desired.y, desired.z - offset);
            Vec3 destination = new Vec3(node.getX() + offset, node.getY(), node.getZ() + offset);
            if (destination.distanceToSqr(boss.position()) < 1 || !boss.canSwimAt(destination)
                    || !boss.isWithinMovementArea(destination.x, destination.z)) {
                continue;
            }
            pathAttempts++;
            Path path = boss.getNavigation().createPath(node, 0);
            if (path != null && path.getNodeCount() > 1 && pathInsideArena(path)
                    && path.getEntityPosAtNode(boss, path.getNodeCount() - 1).distanceToSqr(boss.position()) >= 1) {
                return path;
            }
        }
        return null;
    }

    /**
     * Rechecks whole-body clearance and keeps idle detours within the movement
     * boundary.
     */
    private boolean pathInsideArena(Path path) {
        for (int index = 1; index < path.getNodeCount(); index++) {
            Vec3 point = path.getEntityPosAtNode(boss, index);
            if (!boss.canSwimAt(point) || !boss.isWithinMovementArea(point.x, point.z)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void stop() {
        boss.resetBubbleRecovery();
        boss.getNavigation().stop();
        boss.setDeltaMovement(Vec3.ZERO);
        boss.setXxa(0);
        boss.setYya(0);
        boss.setZza(0);
    }

    /**
     * Discards the current route and planning delay after full encounter recovery.
     */
    public void reset() {
        stop();
        nextPlanTick = 0;
    }
}
