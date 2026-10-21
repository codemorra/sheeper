package de.codemorra.sheeper.entity;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.goal.ApproachUnobservedPlayerGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.event.ForgeEventFactory;
import javax.annotation.Nullable;

/**
 * A custom entity that inherits vanilla sheep behavior.
 */
public class SheeperEntity extends Sheep {
    public SheeperEntity(EntityType<? extends Sheep> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * Registers the goals for this Sheeper entity, including the custom approach
     * unobserved player goal.
     */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new ApproachUnobservedPlayerGoal(this));
    }

    /**
     * Checks whether a player observes this Sheeper within the configured
     * detection radius and observation angle.
     * Uses the player's eye position and look direction for the calculation.
     * 
     * @param player the player to check for observation
     * @return true if the player is observing the Sheeper, false otherwise
     */
    public boolean isObservedBy(Player player) {
        double radius = SheeperConfig.DETECTION_RADIUS.get();

        // Check if the player is within the detection radius.
        if (distanceToSqr(player) > radius * radius) {
            return false;
        }

        // Check if the player has line of sight to the Sheeper.
        if (!player.hasLineOfSight(this)) {
            return false;
        }

        // Calculate the direction vector from the player to the Sheeper.
        Vec3 directionToSheeper = getEyePosition()
                .subtract(player.getEyePosition());

        // Check if the direction vector is too small to be meaningful.
        if (directionToSheeper.lengthSqr() < 1.0E-8) {
            return true;
        }

        // Calculate the dot product between the player's look direction and the
        // direction to the Sheeper.
        double dot = player.getLookAngle()
                .dot(directionToSheeper.normalize());

        // Convert the observation angle from degrees to radians and calculate the
        // half-angle.
        double halfAngleRadians = Math.toRadians(
                SheeperConfig.OBSERVATION_ANGLE.get() / 2.0);

        return dot >= Math.cos(halfAngleRadians);
    }

    /**
     * Finds the nearest living Survival or Adventure player within detection range.
     * Living non-spectator observers, including Creative players, prevent pursuit.
     *
     * @return the nearest eligible player, or null if observed or no target exists
     */
    @Nullable
    public Player findApproachingTarget() {
        double radius = SheeperConfig.DETECTION_RADIUS.get();
        double nearestDistanceSquared = radius * radius;
        Player nearestPlayer = null;

        // Iterate through all players in the level to find the nearest eligible target.
        for (Player player : getLevel().players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }

            // Any observer prevents pursuit.
            if (isObservedBy(player)) {
                return null;
            }

            // Skip players who are in Creative mode, as they are not valid targets.
            if (player.isCreative()) {
                continue;
            }

            // Calculate the squared distance to the player.
            double distanceSquared = distanceToSqr(player);

            // Update the nearest player if this one is closer than the current nearest.
            if (distanceSquared <= nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearestPlayer = player;
            }
        }

        return nearestPlayer;
    }

    /**
     * Explodes with normal Creeper strength, respecting mob griefing.
     * Executes only on the Server.
     */
    public void explode() {
        if (getLevel().isClientSide) {
            return;
        }

        Explosion.BlockInteraction blockInteraction = ForgeEventFactory.getMobGriefingEvent(getLevel(), this)
                ? Explosion.BlockInteraction.DESTROY
                : Explosion.BlockInteraction.NONE;

        dead = true;
        getLevel().explode(
                this, getX(), getY(), getZ(), 3.0F, blockInteraction);
        discard();
    }
}
