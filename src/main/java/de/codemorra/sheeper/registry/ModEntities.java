package de.codemorra.sheeper.registry;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.SheeperEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers custom entities for the Sheeper mod.
 */
public class ModEntities {
    // Deferred register for entity types.
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SheeperMod.MODID);

    // Registry object for the Sheeper entity.
    public static final RegistryObject<EntityType<SheeperEntity>> SHEEPER =
        ENTITY_TYPES.register("sheeper", () ->
            EntityType.Builder.<SheeperEntity>of(
                SheeperEntity::new, MobCategory.CREATURE)
            .sized(
                EntityType.SHEEP.getWidth(),
                EntityType.SHEEP.getHeight())
            .build(SheeperMod.MODID + ":sheeper")
    );

    private ModEntities() {
    }

    // Connects the deferred register to the given event bus.
    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
