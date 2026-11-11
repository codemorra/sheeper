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
        int minimum = SheeperConfig.WATER_MOVEMENT_INTERVAL_MIN.get();
        int maximum = Math.max(minimum, SheeperConfig.WATER_MOVEMENT_INTERVAL_MAX.get());
        nextMovementTick = boss.level.getGameTime() + minimum + boss.getRandom().nextInt(maximum - minimum + 1);
    }

    @Override
    public boolean canUse() {
        if (boss.isPerformingSalvo()) {
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
        for (BlockPos pos : boss.getArenaSurfacePositions()) {
            Vec3 point = Vec3.atBottomCenterOf(pos);
            if (boss.isWithinMovementArea(point.x, point.z)
                    && WaterArenaData.isLoaded((ServerLevel) boss.level, pos)
                    && WaterArenaSearch.isSurface(boss.level, pos)
                    && boss.level.noCollision(boss, boss.getBoundingBox().move(point.subtract(boss.position())))
                    && boss.getRandom().nextInt(++count) == 0) {
                destination = pos;
            }
        }
        if (destination == null) {
            return false;
        }
        path = boss.getNavigation().createPath(destination, 0);
        return path != null && path.canReach() && isPathWithinArena(path);
    }

    private boolean isPathWithinArena(Path candidate) {
        // Endpoints alone are insufficient: obstacle detours can leave the circle.
        for (int index = 0; index < candidate.getNodeCount(); index++) {
            Vec3 point = candidate.getEntityPosAtNode(boss, index);
            if (!boss.isWithinMovementArea(point.x, point.z)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !boss.isPerformingSalvo() && !boss.getNavigation().isDone()
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
