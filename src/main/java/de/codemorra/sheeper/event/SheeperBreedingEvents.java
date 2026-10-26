package de.codemorra.sheeper.event;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.goal.SheepSheeperBreedGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Adds mixed breeding behavior to vanilla sheep.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SheeperBreedingEvents {
    private SheeperBreedingEvents() {
    }

    /**
     * Adds the mixed breeding goal when a vanilla sheep enters the server level,
     * including when an existing sheep is loaded from disk.
     *
     * @param event the entity join event
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }

        // Restrict this addition to vanilla sheep; Sheeper register their own breeding goal.
        if (!(event.getEntity() instanceof Sheep sheep) || sheep.getType() != EntityType.SHEEP) {
            return;
        }

        // The same entity object can rejoin a level, so avoid adding the goal twice.
        boolean alreadyRegistered = sheep.goalSelector.getAvailableGoals().stream()
                .anyMatch(goal -> goal.getGoal() instanceof SheepSheeperBreedGoal);

        if (!alreadyRegistered) {
            sheep.goalSelector.addGoal(2, new SheepSheeperBreedGoal(sheep));
        }
    }
}
