package de.codemorra.sheeper.gametest;

import java.util.List;
import java.util.UUID;

import de.codemorra.sheeper.network.MuttonVisualPacket;
import de.codemorra.sheeper.registry.ModBlocks;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;

/** One loaded dye recipe and lossless serialization of effect snapshots. */
final class DataGameTests {
    private DataGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        // Checks one loaded dye recipe and keeps its result outside the ordinary wool
        // ingredient tag.
        tests.add(new SheeperGameTests.Scenario("dye_recipe_preserves_explosive_wool", DataGameTests::dyeRecipes));
        // Checks that effect-packet encoding and decoding preserve every field and
        // final-burst flag.
        tests.add(new SheeperGameTests.Scenario("visual_packet_round_trip", DataGameTests::packets));
    }

    private static CraftingContainer grid() {
        return new CraftingContainer(new AbstractContainerMenu(MenuType.CRAFTING, -1) {
            @Override
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                return true;
            }
        }, 3, 3);
    }

    private static ItemStack craft(GameTestContext context, CraftingContainer grid) {
        return context.level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, context.level)
                .map(recipe -> recipe.assemble(grid)).orElse(ItemStack.EMPTY);
    }

    private static void dyeRecipes(GameTestContext context) {
        CraftingContainer grid = grid();
        grid.setItem(0, new ItemStack(ModBlocks.WHITE_SHEEPER_WOOL.get()));
        grid.setItem(1, new ItemStack(Items.BLUE_DYE));
        ItemStack result = craft(context, grid);
        context.expect(result.is(ModBlocks.BLUE_SHEEPER_WOOL.get().asItem()) && result.getCount() == 1,
                "Loaded dye recipe must produce one matching explosive wool block");
        context.expect(!result.is(ItemTags.WOOL),
                "Explosive wool must remain outside the ordinary wool ingredient tag");
        context.pass();
    }

    private static void packets(GameTestContext context) {
        UUID playerId = UUID.fromString("3ba45ff4-9dca-4cc6-9616-b99ee2861951");
        for (int flags = 0; flags < 8; flags++) {
            // Zero remaining ticks with burst=true represents the final actual pulse.
            MuttonVisualPacket original = new MuttonVisualPacket(playerId, 0, 12000,
                    (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                original.encode(buffer);
                context.expect(original.equals(MuttonVisualPacket.decode(buffer)),
                        "Packet round trip must retain every field and burst flag");
                context.expect(buffer.readableBytes() == 0, "Decoder must consume the complete encoded packet");
            } finally {
                buffer.release();
            }
        }
        context.pass();
    }
}
