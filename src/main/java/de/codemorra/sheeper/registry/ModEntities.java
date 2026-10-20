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
    /**
     * Deferred register for entity types.
     * This deferred register is used to register all custom entity types for the Sheeper mod.
     */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SheeperMod.MODID);

    /**
     * Registry object for the Sheeper entity.
     * Represents the custom Sheeper entity type.
     */
    public static final RegistryObject<EntityType<SheeperEntity>> SHEEPER =
        ENTITY_TYPES.register("sheeper", () ->
            EntityType.Builder.<SheeperEntity>of(
                SheeperEntity::new, MobCategory.CREATURE)
            .sized(
                EntityType.SHEEP.getWidth(),
                EntityType.SHEEP.getHeight())
            .build(SheeperMod.MODID + ":sheeper")
    );

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ModEntities() {
    }

    /**
     * Connects the deferred register to the mod event bus.
     *
     * @param eventBus the mod event bus used for registration
     */
    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
