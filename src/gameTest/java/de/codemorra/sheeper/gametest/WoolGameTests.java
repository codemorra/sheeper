package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.PrimedSheeperWool;
import de.codemorra.sheeper.entity.SheeperEntity;
import de.codemorra.sheeper.registry.ModBlocks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Safe/unsafe mining, a representative colored shearing, and redstone ignition.
 */
final class WoolGameTests {
    private WoolGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        // Checks that shears harvest matching wool without a blast or nearby damage.
        tests.add(
                new SheeperGameTests.Scenario("shears_safely_harvest_wool", context -> mining(context, Items.SHEARS)));
        // Checks that unsafe mining blasts without wool drops while preserving marker
        // items and blocks.
        tests.add(new SheeperGameTests.Scenario("unsafe_mining_preserves_surroundings",
                context -> mining(context, Items.AIR)));
        // Checks that redstone creates matching primed wool and completes its
        // configured fuse once.
        tests.add(new SheeperGameTests.Scenario("redstone_primes_matching_wool", WoolGameTests::ignition));
        // Checks that shearing yields matching wool and cannot duplicate it before
        // regrowth.
        tests.add(new SheeperGameTests.Scenario("shearing_drops_matching_wool_once",
                context -> shearing(context, DyeColor.BLUE)));
    }

    private static void mining(GameTestContext context, Item tool) {
        TestPlayer player = context.player(12, 1, 16);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        Item wool = ModBlocks.PURPLE_SHEEPER_WOOL.get().asItem();
        context.setBlock(12, 1, 12, ModBlocks.PURPLE_SHEEPER_WOOL.get());
        context.setBlock(12, 1, 13, Blocks.DIRT);
        ItemEntity marker = context.item(Items.DIAMOND, 3, 13, 1, 12);
        Cow victim = context.spawn(EntityType.COW, 13, 1, 12);
        victim.setNoAi(true);
        float health = victim.getHealth();
        context.expect(player.gameMode.destroyBlock(context.pos(12, 1, 12)), "Server mining must remove the wool");
        context.at(3, () -> {
            boolean unsafe = tool != Items.SHEARS;
            context.expect(context.count(wool) == (unsafe ? 0 : 1), "Only safe mining must drop matching wool");
            context.expect(context.explosions.size() == (unsafe ? 1 : 0), "Only unsafe mining must cause a blast");
            context.expect(!marker.isRemoved() && marker.getItem().getCount() == 3,
                    "Mining must preserve the known marker stack");
            context.assertBlockPresent(Blocks.DIRT, 12, 1, 13);
            context.expect(unsafe ? victim.getHealth() < health : victim.getHealth() == health,
                    "Only unsafe mining may damage the nearby cow");
            context.pass();
        });
    }

    private static void ignition(GameTestContext context) {
        context.set(SheeperConfig.WOOL_FUSE_SECONDS, 1.0);
        context.setBlock(12, 1, 12, ModBlocks.PURPLE_SHEEPER_WOOL.get());
        context.setBlock(13, 1, 12, Blocks.REDSTONE_BLOCK);
        List<PrimedSheeperWool> primed = context.entities(PrimedSheeperWool.class);
        context.expect(primed.size() == 1 && primed.get(0).getColor() == DyeColor.PURPLE,
                "Redstone must create one custom primed entity with matching wool color");
        context.expect(primed.get(0).getFuse() == 20, "Primed wool must use the configured fuse");
        context.at(10, () -> context.expect(context.explosions.isEmpty(), "Primed wool must not explode early"));
        context.at(35, () -> {
            context.expect(primed.get(0).isRemoved() && context.explosions.size() == 1,
                    "Primed wool must complete its fuse and explode once");
            context.pass();
        });
    }

    private static void shearing(GameTestContext context, DyeColor color) {
        TestPlayer player = context.player(12, 1, 16);
        SheeperEntity sheeper = context.sheeper(12, 1, 12, false, false);
        sheeper.setColor(color);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        player.getMainHandItem().interactLivingEntity(player, sheeper, InteractionHand.MAIN_HAND);
        context.expect(sheeper.isSheared(), "Hand shearing must change the fleece state");
        Item wool = ModBlocks.getWool(color).asItem();
        int firstCount = context.count(wool);
        context.expect(firstCount >= 1 && firstCount <= 3, "Hand shearing must drop 1-3 matching wool blocks");
        player.getMainHandItem().interactLivingEntity(player, sheeper, InteractionHand.MAIN_HAND);
        context.expect(context.count(wool) == firstCount, "Shearing again must not duplicate wool");
        context.pass();
    }
}
