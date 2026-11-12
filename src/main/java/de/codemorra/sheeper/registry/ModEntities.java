package de.codemorra.sheeper.registry;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.SheeperEntity;
import de.codemorra.sheeper.entity.ElementalSheeperEntity;
import de.codemorra.sheeper.entity.ElementalSheeperEntity.Element;
import de.codemorra.sheeper.entity.PrimedSheeperWool;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.entity.WaterProjectile;
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
     * Summonable elemental boss types sharing the sheep model and boss foundation.
     */
    public static final RegistryObject<EntityType<ElementalSheeperEntity>> LAVA_SHEEPER = registerElemental(
            "lava_sheeper", Element.LAVA);
    public static final RegistryObject<EntityType<LightningSheeperEntity>> LIGHTNING_SHEEPER = ENTITY_TYPES.register(
            "lightning_sheeper", () -> EntityType.Builder.<LightningSheeperEntity>of(
                    LightningSheeperEntity::new, MobCategory.MISC)
                    .sized(EntityType.SHEEP.getWidth(), EntityType.SHEEP.getHeight())
                    .clientTrackingRange(10)
                    .build(SheeperMod.MODID + ":lightning_sheeper"));
    public static final RegistryObject<EntityType<WaterSheeperEntity>> WATER_SHEEPER = ENTITY_TYPES.register(
            "water_sheeper", () -> EntityType.Builder.<WaterSheeperEntity>of(
                    WaterSheeperEntity::new, MobCategory.MISC)
                    .sized(EntityType.SHEEP.getWidth(), EntityType.SHEEP.getHeight())
                    .clientTrackingRange(10)
                    .build(SheeperMod.MODID + ":water_sheeper"));

    public static final RegistryObject<EntityType<WaterProjectile>> WATER_PROJECTILE = ENTITY_TYPES.register(
            "water_projectile", () -> EntityType.Builder.<WaterProjectile>of(WaterProjectile::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(SheeperMod.MODID + ":water_projectile"));

    /**
     * MISC categorizes these bosses separately from naturally spawning creatures.
     */
    private static RegistryObject<EntityType<ElementalSheeperEntity>> registerElemental(String name, Element element) {
        return ENTITY_TYPES.register(name, () -> EntityType.Builder.<ElementalSheeperEntity>of(
                (type, level) -> new ElementalSheeperEntity(type, level, element), MobCategory.MISC)
                .sized(EntityType.SHEEP.getWidth(), EntityType.SHEEP.getHeight())
                .clientTrackingRange(10)
                .build(SheeperMod.MODID + ":" + name));
    }

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
