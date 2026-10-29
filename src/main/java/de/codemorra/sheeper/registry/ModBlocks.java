package de.codemorra.sheeper.registry;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.block.SheeperWoolBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers blocks for the Sheeper mod.
 */
public class ModBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
            SheeperMod.MODID);

    public static final RegistryObject<SheeperWoolBlock> WHITE_SHEEPER_WOOL = registerWool("white_sheeper_wool",
            Blocks.WHITE_WOOL, DyeColor.WHITE);
    public static final RegistryObject<SheeperWoolBlock> ORANGE_SHEEPER_WOOL = registerWool("orange_sheeper_wool",
            Blocks.ORANGE_WOOL, DyeColor.ORANGE);
    public static final RegistryObject<SheeperWoolBlock> MAGENTA_SHEEPER_WOOL = registerWool("magenta_sheeper_wool",
            Blocks.MAGENTA_WOOL, DyeColor.MAGENTA);
    public static final RegistryObject<SheeperWoolBlock> LIGHT_BLUE_SHEEPER_WOOL = registerWool(
            "light_blue_sheeper_wool", Blocks.LIGHT_BLUE_WOOL, DyeColor.LIGHT_BLUE);
    public static final RegistryObject<SheeperWoolBlock> YELLOW_SHEEPER_WOOL = registerWool("yellow_sheeper_wool",
            Blocks.YELLOW_WOOL, DyeColor.YELLOW);
    public static final RegistryObject<SheeperWoolBlock> LIME_SHEEPER_WOOL = registerWool("lime_sheeper_wool",
            Blocks.LIME_WOOL, DyeColor.LIME);
    public static final RegistryObject<SheeperWoolBlock> PINK_SHEEPER_WOOL = registerWool("pink_sheeper_wool",
            Blocks.PINK_WOOL, DyeColor.PINK);
    public static final RegistryObject<SheeperWoolBlock> GRAY_SHEEPER_WOOL = registerWool("gray_sheeper_wool",
            Blocks.GRAY_WOOL, DyeColor.GRAY);
    public static final RegistryObject<SheeperWoolBlock> LIGHT_GRAY_SHEEPER_WOOL = registerWool(
            "light_gray_sheeper_wool", Blocks.LIGHT_GRAY_WOOL, DyeColor.LIGHT_GRAY);
    public static final RegistryObject<SheeperWoolBlock> CYAN_SHEEPER_WOOL = registerWool("cyan_sheeper_wool",
            Blocks.CYAN_WOOL, DyeColor.CYAN);
    public static final RegistryObject<SheeperWoolBlock> PURPLE_SHEEPER_WOOL = registerWool("purple_sheeper_wool",
            Blocks.PURPLE_WOOL, DyeColor.PURPLE);
    public static final RegistryObject<SheeperWoolBlock> BLUE_SHEEPER_WOOL = registerWool("blue_sheeper_wool",
            Blocks.BLUE_WOOL, DyeColor.BLUE);
    public static final RegistryObject<SheeperWoolBlock> BROWN_SHEEPER_WOOL = registerWool("brown_sheeper_wool",
            Blocks.BROWN_WOOL, DyeColor.BROWN);
    public static final RegistryObject<SheeperWoolBlock> GREEN_SHEEPER_WOOL = registerWool("green_sheeper_wool",
            Blocks.GREEN_WOOL, DyeColor.GREEN);
    public static final RegistryObject<SheeperWoolBlock> RED_SHEEPER_WOOL = registerWool("red_sheeper_wool",
            Blocks.RED_WOOL, DyeColor.RED);
    public static final RegistryObject<SheeperWoolBlock> BLACK_SHEEPER_WOOL = registerWool("black_sheeper_wool",
            Blocks.BLACK_WOOL, DyeColor.BLACK);

    private ModBlocks() {
    }

    /**
     * Resolves a fleece color to its registered Sheeper wool block.
     * Call only after block registration has completed.
     *
     * @param color the fleece color
     * @return the matching wool block
     */
    public static SheeperWoolBlock getWool(DyeColor color) {
        return switch (color) {
            case WHITE -> WHITE_SHEEPER_WOOL.get();
            case ORANGE -> ORANGE_SHEEPER_WOOL.get();
            case MAGENTA -> MAGENTA_SHEEPER_WOOL.get();
            case LIGHT_BLUE -> LIGHT_BLUE_SHEEPER_WOOL.get();
            case YELLOW -> YELLOW_SHEEPER_WOOL.get();
            case LIME -> LIME_SHEEPER_WOOL.get();
            case PINK -> PINK_SHEEPER_WOOL.get();
            case GRAY -> GRAY_SHEEPER_WOOL.get();
            case LIGHT_GRAY -> LIGHT_GRAY_SHEEPER_WOOL.get();
            case CYAN -> CYAN_SHEEPER_WOOL.get();
            case PURPLE -> PURPLE_SHEEPER_WOOL.get();
            case BLUE -> BLUE_SHEEPER_WOOL.get();
            case BROWN -> BROWN_SHEEPER_WOOL.get();
            case GREEN -> GREEN_SHEEPER_WOOL.get();
            case RED -> RED_SHEEPER_WOOL.get();
            case BLACK -> BLACK_SHEEPER_WOOL.get();
        };
    }

    /**
     * Registers a wool variant using the physical properties of its vanilla color.
     * Tags, models, recipes, and loot tables are defined separately as resources.
     *
     * @param name        the registry path within the Sheeper namespace
     * @param vanillaWool the vanilla wool block whose properties are copied
     * @param color       the fixed color of this wool variant
     * @return the deferred reference to the wool block
     */
    private static RegistryObject<SheeperWoolBlock> registerWool(
            String name, Block vanillaWool, DyeColor color) {
        return BLOCKS.register(name, () -> new SheeperWoolBlock(
                BlockBehaviour.Properties.copy(vanillaWool), color));
    }

    /**
     * Connects block registration to the mod lifecycle event bus.
     *
     * @param eventBus the mod event bus
     */
    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
