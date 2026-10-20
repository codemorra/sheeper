package de.codemorra.sheeper.entity.goal;

import de.codemorra.sheeper.entity.SheeperEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Approaches the nearest eligible player while no player observes the Sheeper.
 */
public class ApproachUnobservedPlayerGoal extends Goal {
    private final SheeperEntity sheeper;

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
     * Executes the logic for this goal on each tick.
     */
    @Override
    public void tick() {
        Player target = sheeper.findApproachingTarget();

        // If the target is null, stop navigating and return early.
        if (target == null) {
            sheeper.getNavigation().stop();
            return;
        }

        // Otherwise, look at the target and move towards it.
        sheeper.getLookControl().setLookAt(target, 30.0F, 30.0F);
        sheeper.getNavigation().moveTo(target, 1.0);
    }

    /**
     * Stops the execution of this goal.
     */
    @Override
    public void stop() {
        sheeper.getNavigation().stop();
    }
}
