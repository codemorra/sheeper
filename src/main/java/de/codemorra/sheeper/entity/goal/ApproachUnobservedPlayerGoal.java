package de.codemorra.sheeper.entity.goal;

import de.codemorra.sheeper.entity.SheeperEntity;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;

import java.util.EnumSet;

/**
 * Approaches the nearest eligible player while no player observes the Sheeper.
 */
public class ApproachUnobservedPlayerGoal extends Goal {
    private final SheeperEntity sheeper;
    private int fuseTicks;

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
     * Determines whether this goal should start executing.
     *
     * @return true if there is a player to approach, false otherwise
     */
    @Override
    public boolean canUse() {
        return sheeper.findApproachingTarget() != null;
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
        Player target = sheeper.findApproachingTarget();

        // If there is no target, reset the fuse and stop navigating.
        if (target == null) {
            fuseTicks = 0;
            sheeper.getNavigation().stop();
            return;
        }

        // If the target is within fuse distance, start the fuse countdown.
        sheeper.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double fuseDistance = SheeperConfig.getFuseDistance();

        // Check if the target is outside the fuse distance and reset the fuse if so.
        if (sheeper.distanceToSqr(target) > fuseDistance * fuseDistance) {
            fuseTicks = 0;
            sheeper.getNavigation().moveTo(target, 1.0);
            return;
        }

        // Stop navigating as the target is within fuse distance.
        sheeper.getNavigation().stop();

        // Play the ambient sound when the fuse starts.
        if (fuseTicks == 0) {
            sheeper.playSound(SoundEvents.SHEEP_AMBIENT, 1.0F, 1.0F);
        }

        fuseTicks++;

        // Calculate the required ticks for the fuse based on the configured duration.
        int requiredTicks = (int) Math.ceil(
                SheeperConfig.getFuseDuration() * 20.0);

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
        sheeper.getNavigation().stop();
    }
}
