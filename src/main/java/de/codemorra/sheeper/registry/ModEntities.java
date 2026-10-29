package de.codemorra.sheeper.registry;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.SheeperEntity;
import de.codemorra.sheeper.entity.PrimedSheeperWool;
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
     * This deferred register is used to register all custom entity types for the
     * Sheeper mod.
     */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister
            .create(ForgeRegistries.ENTITY_TYPES, SheeperMod.MODID);

    /**
     * Registry object for the Sheeper entity.
     * Represents the custom Sheeper entity type.
     */
    public static final RegistryObject<EntityType<SheeperEntity>> SHEEPER = ENTITY_TYPES.register("sheeper",
            () -> EntityType.Builder.<SheeperEntity>of(
                    SheeperEntity::new, MobCategory.CREATURE)
                    .sized(
                            EntityType.SHEEP.getWidth(),
                            EntityType.SHEEP.getHeight())
                    .build(SheeperMod.MODID + ":sheeper"));

    /**
     * Color-preserving primed wool with vanilla TNT dimensions and tracking
     * settings.
     */
    public static final RegistryObject<EntityType<PrimedSheeperWool>> PRIMED_SHEEPER_WOOL = ENTITY_TYPES
            .register("primed_sheeper_wool", () -> EntityType.Builder.<PrimedSheeperWool>of(
                    PrimedSheeperWool::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(EntityType.TNT.getWidth(), EntityType.TNT.getHeight())
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .build(SheeperMod.MODID + ":primed_sheeper_wool"));

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
