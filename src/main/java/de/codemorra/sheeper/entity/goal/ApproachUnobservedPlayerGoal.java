package de.codemorra.sheeper.entity.goal;

import de.codemorra.sheeper.entity.SheeperEntity;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;

import java.util.EnumSet;

/**
 * Selects the nearest eligible player when pursuit starts and keeps that target
 * while it remains valid and within detection range and no eligible player
 * observes the Sheeper.
 */
public class ApproachUnobservedPlayerGoal extends Goal {
    private final SheeperEntity sheeper;
    private int fuseTicks;
    private Player target;

    /**
     * Constructs a new ApproachUnobservedPlayerGoal for the given SheeperEntity.
     *
     * @param sheeper The SheeperEntity that will execute this goal
     */
    public ApproachUnobservedPlayerGoal(SheeperEntity sheeper) {
        this.sheeper = sheeper;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * Determines whether an adult Sheeper can start pursuing a player.
     *
     * @return true if this Sheeper is an adult and an eligible target exists
     */
    @Override
    public boolean canUse() {
        if (sheeper.isBaby()) {
            return false;
        }

        target = sheeper.findApproachingTarget();
        return target != null;
    }

    /**
     * Determines whether this goal should continue executing.
     *
     * @return true if the goal should continue executing, false otherwise
     */
    @Override
    public boolean canContinueToUse() {
        if (sheeper.isBaby()) {
            return false;
        }

        double radius = SheeperConfig.DETECTION_RADIUS.get();

        // If there is no target, or the target is invalid, or outside the detection
        // radius, stop executing.
        if (target == null) {
            return false;
        }

        // If the target is not eligible, stop executing.
        if (!sheeper.isEligiblePlayer(target)) {
            return false;
        }

        // If the target is outside the detection radius, stop executing.
        if (radius * radius < sheeper.distanceToSqr(target)) {
            return false;
        }

        // If the Sheeper is being observed by any player, stop executing.
        for (Player player : sheeper.getLevel().players()) {
            if (!sheeper.isEligiblePlayer(player)) {
                continue;
            }

            if (sheeper.isObservedBy(player)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Requests updates every tick so observation stops navigation promptly.
     *
     * @return true to update this goal every tick while it is running
     */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /**
     * Executes the logic for this goal on each tick, including approaching the
     * target player and handling the fuse countdown.
     */
    @Override
    public void tick() {
        // If pursuit cannot continue, reset the state and stop navigating.
        if (!canContinueToUse()) {
            stop();
            return;
        }

        // Make the Sheeper look at the target player.
        sheeper.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double fuseDistance = SheeperConfig.getFuseDistance();

        // Keep pursuing the stored target outside fuse distance.
        if (sheeper.distanceToSqr(target) > fuseDistance * fuseDistance) {
            fuseTicks = 0;
            sheeper.setWarningState(true, 0.0f);
            sheeper.getNavigation().moveTo(target, 1.0);
            return;
        }

        // Stop navigating as the target is within fuse distance.
        sheeper.getNavigation().stop();

        // Play the warning once when a new fuse starts.
        if (fuseTicks == 0) {
            sheeper.playSound(SoundEvents.SHEEP_AMBIENT, 1.0F, 1.0F);
        }

        fuseTicks++;

        // Calculate the required ticks for the fuse based on the configured duration.
        int requiredTicks = (int) Math.ceil(
                SheeperConfig.getFuseDuration() * 20.0);

        // Update the warning state with the current fuse progress.
        sheeper.setWarningState(true, (float) fuseTicks / requiredTicks);

        // If the fuse has reached the required duration, trigger the explosion.
        if (fuseTicks >= requiredTicks) {
            sheeper.explode();
        }
    }

    /**
     * Stops the execution of this goal.
     */
    @Override
    public void stop() {
        fuseTicks = 0;
        target = null;
        sheeper.setWarningState(false, 0.0f);
        sheeper.getNavigation().stop();
    }
}
