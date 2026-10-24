package de.codemorra.sheeper.entity.goal;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.SheeperEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Goal for a baby Sheeper to bump a nearby player and then retreat.
 */
public class BabyBumpPlayerGoal extends Goal {
    private enum Phase {
        APPROACH,
        RETREAT
    }

    private final SheeperEntity sheeper;
    private Player target;
    private Phase phase = Phase.APPROACH;
    private int retreatTicks;
    private long nextAttackTime;

    /**
     * Constructs a new BabyBumpPlayerGoal for the given SheeperEntity.
     *
     * @param sheeper The SheeperEntity that will execute this goal
     */
    public BabyBumpPlayerGoal(SheeperEntity sheeper) {
        this.sheeper = sheeper;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * Determines whether this goal should start executing.
     *
     * @return true if there is a player to bump, false otherwise
     */
    @Override
    public boolean canUse() {
        // Check if the Sheeper is a baby and if the cooldown has passed
        if (!sheeper.isBaby() || sheeper.getLevel().getGameTime() < nextAttackTime) {
            return false;
        }

        // Find eligible players within the detection radius
        double radious = SheeperConfig.DETECTION_RADIUS.get();
        List<Player> candidates = new ArrayList<>();
        target = null;

        // Iterate through all players in the level and add eligible ones within the
        // radius to the candidates list
        for (Player player : sheeper.getLevel().players()) {
            if (!sheeper.isEligiblePlayer(player)) {
                continue;
            }

            if (sheeper.distanceToSqr(player) <= radious * radious) {
                candidates.add(player);
            }
        }

        // If no eligible players are found, the goal cannot start
        if (candidates.isEmpty()) {
            return false;
        }

        // Select a random target from the list of eligible players
        int index = sheeper.getRandom().nextInt(candidates.size());
        target = candidates.get(index);

        return target != null;
    }

    /**
     * Starts executing this goal.
     */
    @Override
    public void start() {
        phase = Phase.APPROACH;
        retreatTicks = 0;
    }

    /**
     * Determines whether this goal should continue executing.
     *
     * @return true if the goal should continue, false otherwise
     */
    @Override
    public boolean canContinueToUse() {
        // Check if the Sheeper is still a baby and has a valid target
        if (!sheeper.isBaby() || target == null) {
            return false;
        }

        // Check if the target is still eligible
        if (!sheeper.isEligiblePlayer(target)) {
            return false;
        }

        // Check if the target is still within the detection radius
        double radious = SheeperConfig.DETECTION_RADIUS.get();

        // Calculate the squared distance to the target for comparison with the
        // detection radius squared
        if (sheeper.distanceToSqr(target) > radious * radious) {
            return false;
        }

        // If the Sheeper is retreating, check if it should continue retreating
        if (phase == Phase.RETREAT) {
            return retreatTicks > 0 && !sheeper.getNavigation().isDone();
        }

        return true;
    }

    /**
     * Indicates that this goal requires updates every tick.
     *
     * @return true, as this goal needs to be updated every tick
     */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /**
     * Updates the goal each tick.
     */
    @Override
    public void tick() {
        // Update the goal each tick, handling approach and retreat phases
        if (!canContinueToUse()) {
            stop();
            return;
        }

        // Handle retreat phase if active before proceeding with approach logic
        if (phase == Phase.RETREAT) {
            retreatTicks--;
            return;
        }

        // Look at the target while approaching
        sheeper.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // Check if the Sheeper has collided with the target and should initiate a bump
        if (sheeper.getBoundingBox().inflate(0.2).intersects(target.getBoundingBox())) {
            double directionX = sheeper.getX() - target.getX();
            double directionZ = sheeper.getZ() - target.getZ();

            // Use a fallback direction when the horizontal positions nearly coincide.
            if (directionX * directionX + directionZ * directionZ < 1.0E-8) {
                directionX = 1.0;
            }

            target.knockback(SheeperConfig.BABY_BUMP_KNOCKBACK.get(), directionX, directionZ);
            target.hurtMarked = true;

            beginRetreat();
            return;
        }

        // Move towards the target if not colliding and not retreating
        sheeper.getNavigation().moveTo(target, 1.0);

        // If the Sheeper has reached its navigation target and is close to the player,
        // adjust its movement to continue approaching
        if (sheeper.getNavigation().isDone()
                && sheeper.distanceToSqr(target) < 4.0 && sheeper.hasLineOfSight(target)) {
            sheeper.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
        }
    }

    /**
     * Initiates the retreat phase for the Sheeper.
     */
    private void beginRetreat() {
        phase = Phase.RETREAT;
        retreatTicks = 40;
        sheeper.getNavigation().stop();

        // Determine a retreat position away from the target
        Vec3 retreatPosition = DefaultRandomPos.getPosAway(sheeper, 6, 3, target.position());

        // If no valid retreat position is found, end the retreat early
        if (retreatPosition == null) {
            retreatTicks = 0;
            return;
        }

        // Attempt to start navigation towards the retreat position
        boolean pathStarted = sheeper.getNavigation().moveTo(
                retreatPosition.x,
                retreatPosition.y,
                retreatPosition.z,
                1.0);

        // If navigation towards the retreat position fails, end the retreat early
        if (!pathStarted) {
            retreatTicks = 0;
        }
    }

    /**
     * Stops the execution of this goal and resets relevant state.
     */
    @Override
    public void stop() {
        // Reset the goal state and schedule the next attack pause if there was a target
        if (target != null) {
            int minTicks = (int) Math.ceil(SheeperConfig.BABY_ATTACK_PAUSE_MIN.get() * 20.0);
            int maxTicks = Math.max(minTicks, (int) Math.ceil(SheeperConfig.BABY_ATTACK_PAUSE_MAX.get() * 20.0));
            int pauseTicks = minTicks + sheeper.getRandom().nextInt(maxTicks - minTicks + 1);

            nextAttackTime = sheeper.getLevel().getGameTime() + pauseTicks;
        }

        target = null;
        retreatTicks = 0;
        phase = Phase.APPROACH;
        sheeper.getNavigation().stop();
    }
}
