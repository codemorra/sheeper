package de.codemorra.sheeper.entity.goal;

import java.util.EnumSet;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.SheeperEntity;
import de.codemorra.sheeper.registry.ModSounds;
import de.codemorra.sheeper.util.SheeperBurst;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Pursues the first attacking player regardless of observation. Contact after a
 * short ignition produces a burst, followed by a brief pause before the next
 * run.
 */
public class RetaliatePlayerGoal extends Goal {
    private static final double MELEE_RANGE_SQUARED = 3.0D * 3.0D;
    private static final float FUSE_VISUAL_STRENGTH = 0.35F;

    private final SheeperEntity sheeper;
    private int fuseTicks;
    private int pauseTicks;

    /**
     * Reserves movement and look control while allowing the inherited float goal
     * to keep the Sheeper above water.
     *
     * @param sheeper the adult Sheeper that retaliates
     */
    public RetaliatePlayerGoal(SheeperEntity sheeper) {
        this.sheeper = sheeper;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * @return true while the adult has a valid retaliation target
     */
    @Override
    public boolean canUse() {
        return sheeper.isAlive() && !sheeper.isBaby() && sheeper.getRetaliationTarget() != null;
    }

    /**
     * Keeps control during the pause so ordinary panic or stealth cannot interrupt.
     *
     * @return true while the retaliation target remains valid
     */
    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    /** @return true so ignition and pause durations use individual game ticks */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /**
     * Runs toward the retained player, ignites within melee range, and bursts only
     * after the minimum fuse duration and physical contact with line of sight.
     */
    @Override
    public void tick() {
        Player target = sheeper.getRetaliationTarget();
        if (target == null) {
            return;
        }

        sheeper.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (pauseTicks > 0) {
            pauseTicks--;
            if (pauseTicks > 0) {
                sheeper.getNavigation().stop();
                sheeper.setWarningState(true, 0.0F);
                return;
            }
        }

        // Navigation controls acceleration; incoming vanilla knockback is retained.
        sheeper.getNavigation().moveTo(target, SheeperConfig.RETALIATION_SPEED_MULTIPLIER.get());
        if (sheeper.getNavigation().isDone() && sheeper.distanceToSqr(target) <= MELEE_RANGE_SQUARED
                && sheeper.hasLineOfSight(target)) {
            // A path can finish before the collision boxes actually touch.
            sheeper.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(),
                    SheeperConfig.RETALIATION_SPEED_MULTIPLIER.get());
        }

        if (sheeper.distanceToSqr(target) > MELEE_RANGE_SQUARED || !sheeper.hasLineOfSight(target)) {
            fuseTicks = 0;
            sheeper.setWarningState(true, 0.0F);
            return;
        }

        if (fuseTicks == 0) {
            sheeper.playSound(ModSounds.WARNING_FUSE.get(), 0.7F, 1.2F);
        }

        int requiredTicks = SheeperConfig.secondsToTicks(SheeperConfig.RETALIATION_FUSE_DURATION.get());
        fuseTicks = Math.min(fuseTicks + 1, requiredTicks);
        sheeper.setWarningState(true, FUSE_VISUAL_STRENGTH * fuseTicks / requiredTicks);

        // A small tolerance detects contact despite vanilla collision separation.
        if (fuseTicks >= requiredTicks
                && sheeper.getBoundingBox().inflate(0.1D).intersects(target.getBoundingBox())) {
            fuseTicks = 0;
            pauseTicks = SheeperConfig.secondsToTicks(SheeperConfig.RETALIATION_PAUSE.get());
            sheeper.getNavigation().stop();
            sheeper.setWarningState(true, 0.0F);
            SheeperBurst.explode(sheeper);
        }
    }

    /**
     * Clears the contact cycle when retaliation ends or the goal is interrupted.
     */
    @Override
    public void stop() {
        fuseTicks = 0;
        pauseTicks = 0;
        sheeper.getNavigation().stop();
        sheeper.setWarningState(false, 0.0F);
    }
}
