package de.codemorra.sheeper.registry;

import de.codemorra.sheeper.SheeperMod;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers custom items for the Sheeper mod.
 */
public class ModItems {
    /**
     * Deferred register for items.
     * This deferred register is used to register all custom items for the Sheeper mod.
     */
    private static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, SheeperMod.MODID);

    /**
     * Registry object for the Sheeper spawn egg item.
     */
    public static final RegistryObject<ForgeSpawnEggItem> SHEEPER_SPAWN_EGG =
        ITEMS.register("sheeper_spawn_egg", () ->
            new ForgeSpawnEggItem(
                ModEntities.SHEEPER,
                // Primary color of the spawn egg
                0x0DA70B,
                // Secondary color of the spawn egg
                0xEF8FFF,
                new Item.Properties().tab(CreativeModeTab.TAB_MISC)
            )
        );
    
    /**
     * Connects the deferred register to the mod event bus.
     *
     * @param eventBus the mod event bus used for registration
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
