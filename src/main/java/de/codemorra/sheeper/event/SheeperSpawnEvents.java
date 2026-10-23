package de.codemorra.sheeper.event;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.SheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles sheep spawn events
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SheeperSpawnEvents {
    private static final String REPLACEMENT_PENDING = SheeperMod.MODID + ":replacement_pending";

    private SheeperSpawnEvents() {
    }

    /**
     * Handles special spawn events for sheep
     *
     * @param event the spawn event
     */
    @SubscribeEvent
    public static void onSpecialSpawn(LivingSpawnEvent.SpecialSpawn event) {
        // Only handle sheep entities
        if (event.getEntity().getType() != EntityType.SHEEP) {
            return;
        }

        // Only handle natural spawning and initial chunk generation.
        MobSpawnType spawnReason = event.getSpawnReason();
        if (spawnReason != MobSpawnType.NATURAL && spawnReason != MobSpawnType.CHUNK_GENERATION) {
            return;
        }

        // Roll chance for replacement
        double chance = SheeperConfig.NATURAL_SPAWN_CHANCE.get();
        if (event.getEntity().getRandom().nextDouble() < chance / 100.0) {
            event.getEntity().getPersistentData().putBoolean(REPLACEMENT_PENDING, true);
        }
    }

    /**
     * Handles the replacement of sheep with Sheeper entities during their tick
     * event.
     *
     * @param event the living tick event
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        // Only handle sheep entities
        if (!(event.getEntity() instanceof Sheep sheep) || sheep.getType() != EntityType.SHEEP) {
            return;
        }

        // Only handle server-side logic
        if (!(sheep.getLevel() instanceof ServerLevel level)) {
            return;
        }

        // Check if the sheep is marked for replacement
        if (!sheep.getPersistentData().getBoolean(REPLACEMENT_PENDING)) {
            return;
        }
        // Remove the replacement pending flag before proceeding with the replacement
        sheep.getPersistentData().remove(REPLACEMENT_PENDING);

        // Create and initialize the Sheeper entity
        SheeperEntity sheeper = ModEntities.SHEEPER.get().create(level);
        if (sheeper == null) {
            return;
        }

        // Copy the sheep's position and attributes to the Sheeper entity
        sheeper.copyPosition(sheep);
        sheeper.setColor(sheep.getColor());
        sheeper.setAge(sheep.getAge());
        sheeper.setSheared(sheep.isSheared());

        // Spawn the Sheeper entity in the world
        if (level.addFreshEntity(sheeper)) {
            sheep.discard();
            event.setCanceled(true);
        }
    }
}
