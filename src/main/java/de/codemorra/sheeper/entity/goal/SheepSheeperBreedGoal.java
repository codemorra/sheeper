package de.codemorra.sheeper.entity.goal;

import de.codemorra.sheeper.entity.SheeperEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Sheep;

/**
 * Allows a normal sheep to approach and breed with a Sheeper.
 */
public class SheepSheeperBreedGoal extends BreedGoal {
    private static final TargetingConditions PARTNER_TARGETING = TargetingConditions.forNonCombat().range(8.0D)
            .ignoreLineOfSight();

    /**
     * Constructs a new SheepSheeperBreedGoal for the specified sheep.
     *
     * @param sheep the sheep entity that will use this breed goal
     */
    public SheepSheeperBreedGoal(Sheep sheep) {
        super(sheep, 1.0D, SheeperEntity.class);
    }

    /**
     * Selects the nearest compatible Sheeper when this sheep is in love.
     *
     * @return true if a suitable Sheeper partner is found, false otherwise
     */
    @Override
    public boolean canUse() {
        partner = null;

        if (!animal.isInLove()) {
            return false;
        }

        double nearestDistance = Double.MAX_VALUE;

        // Iterate through nearby Sheeper entities to find the closest suitable partner.
        for (SheeperEntity candidate : level.getNearbyEntities(
                SheeperEntity.class,
                PARTNER_TARGETING,
                animal,
                animal.getBoundingBox().inflate(8.0D))) {
            double distance = animal.distanceToSqr(candidate);

            // Ask the Sheeper about compatibility because vanilla sheep reject other classes.
            // Keep the nearest compatible partner.
            if (candidate.canMate(animal) && distance < nearestDistance) {
                partner = candidate;
                nearestDistance = distance;
            }
        }

        return partner != null;
    }

    /**
     * Delegates breeding to the Sheeper parent so the mixed-pair offspring rule
     * applies even when the normal sheep initiates breeding.
     */
    @Override
    protected void breed() {
        if (partner instanceof SheeperEntity sheeper) {
            sheeper.spawnChildFromBreeding((ServerLevel) level, animal);
        }
    }
}
