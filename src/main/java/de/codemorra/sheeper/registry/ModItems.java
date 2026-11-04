package de.codemorra.sheeper.registry;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.item.RawSheeperMuttonItem;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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
     * This deferred register is used to register all custom items for the Sheeper
     * mod.
     */
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            SheeperMod.MODID);

    /**
     * Registry object for the Sheeper spawn egg item.
     */
    public static final RegistryObject<ForgeSpawnEggItem> SHEEPER_SPAWN_EGG = ITEMS.register("sheeper_spawn_egg",
            () -> new ForgeSpawnEggItem(
                    ModEntities.SHEEPER,
                    // Primary color of the spawn egg
                    0x0DA70B,
                    // Secondary color of the spawn egg
                    0xEF8FFF,
                    new Item.Properties().tab(CreativeModeTab.TAB_MISC)));

    // Raw Sheeper Mutton item registration
    public static final RegistryObject<Item> RAW_SHEEPER_MUTTON = ITEMS.register("raw_sheeper_mutton",
            () -> new RawSheeperMuttonItem(new Item.Properties()
                    .food(Foods.MUTTON)
                    .tab(CreativeModeTab.TAB_FOOD)));

    // Cooked Sheeper Mutton item registration
    public static final RegistryObject<Item> COOKED_SHEEPER_MUTTON = ITEMS.register("cooked_sheeper_mutton",
            () -> new Item(new Item.Properties()
                    .food(Foods.COOKED_MUTTON)
                    .tab(CreativeModeTab.TAB_FOOD)));

    // Sheeper Wool item registrations
    public static final RegistryObject<BlockItem> WHITE_SHEEPER_WOOL = registerBlockItem(ModBlocks.WHITE_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> ORANGE_SHEEPER_WOOL = registerBlockItem(
            ModBlocks.ORANGE_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> MAGENTA_SHEEPER_WOOL = registerBlockItem(
            ModBlocks.MAGENTA_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> LIGHT_BLUE_SHEEPER_WOOL = registerBlockItem(
            ModBlocks.LIGHT_BLUE_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> YELLOW_SHEEPER_WOOL = registerBlockItem(
            ModBlocks.YELLOW_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> LIME_SHEEPER_WOOL = registerBlockItem(ModBlocks.LIME_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> PINK_SHEEPER_WOOL = registerBlockItem(ModBlocks.PINK_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> GRAY_SHEEPER_WOOL = registerBlockItem(ModBlocks.GRAY_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> LIGHT_GRAY_SHEEPER_WOOL = registerBlockItem(
            ModBlocks.LIGHT_GRAY_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> CYAN_SHEEPER_WOOL = registerBlockItem(ModBlocks.CYAN_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> PURPLE_SHEEPER_WOOL = registerBlockItem(
            ModBlocks.PURPLE_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> BLUE_SHEEPER_WOOL = registerBlockItem(ModBlocks.BLUE_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> BROWN_SHEEPER_WOOL = registerBlockItem(ModBlocks.BROWN_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> GREEN_SHEEPER_WOOL = registerBlockItem(ModBlocks.GREEN_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> RED_SHEEPER_WOOL = registerBlockItem(ModBlocks.RED_SHEEPER_WOOL);
    public static final RegistryObject<BlockItem> BLACK_SHEEPER_WOOL = registerBlockItem(ModBlocks.BLACK_SHEEPER_WOOL);

    // Sheeper carpet item registrations
    public static final RegistryObject<BlockItem> WHITE_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.WHITE_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> ORANGE_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.ORANGE_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> MAGENTA_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.MAGENTA_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> LIGHT_BLUE_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.LIGHT_BLUE_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> YELLOW_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.YELLOW_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> LIME_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.LIME_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> PINK_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.PINK_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> GRAY_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.GRAY_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> LIGHT_GRAY_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.LIGHT_GRAY_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> CYAN_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.CYAN_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> PURPLE_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.PURPLE_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> BLUE_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.BLUE_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> BROWN_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.BROWN_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> GREEN_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.GREEN_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> RED_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.RED_SHEEPER_CARPET);
    public static final RegistryObject<BlockItem> BLACK_SHEEPER_CARPET = registerBlockItem(
            ModBlocks.BLACK_SHEEPER_CARPET);

    /**
     * Registers a placeable item under the same registry path as its block.
     * The ID is available immediately; the block is resolved inside the supplier
     * after block registration has completed.
     *
     * @param block the deferred reference to the block
     * @return the deferred reference to its inventory item
     */
    private static RegistryObject<BlockItem> registerBlockItem(RegistryObject<? extends Block> block) {
        return ITEMS.register(block.getId().getPath(),
                () -> new BlockItem(block.get(), new Item.Properties().tab(CreativeModeTab.TAB_BUILDING_BLOCKS)));
    }

    /**
     * Connects the deferred register to the mod event bus.
     *
     * @param eventBus the mod event bus used for registration
     */
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
