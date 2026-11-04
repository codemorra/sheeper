package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.registry.ModItems;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** Representative unsafe/safe preparation, warning reset, eating, and milk. */
final class MuttonGameTests {
    private MuttonGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        // Checks that furnace overheating consumes one meat piece and preserves the
        // remaining drops.
        tests.add(
                new SheeperGameTests.Scenario("furnace_consumes_one_and_preserves_contents", MuttonGameTests::furnace));
        // Checks that the smoker finishes the meat recipe without an explosion.
        tests.add(new SheeperGameTests.Scenario("smoker_finishes_safely", MuttonGameTests::safeCooking));
        // Checks that removing meat cancels overheating and reinserting it starts a new
        // warning.
        tests.add(new SheeperGameTests.Scenario("removing_meat_resets_warning", MuttonGameTests::removeMeat));
        // Checks food damage intervals, the cooked speed bonus, and its removal when
        // the effect expires.
        tests.add(new SheeperGameTests.Scenario("mutton_effects_apply_and_expire", MuttonGameTests::eating));
        // Checks that milk clears both food effects and prevents further food damage or
        // speed boosts.
        tests.add(new SheeperGameTests.Scenario("milk_clears_both_effects", MuttonGameTests::milk));
    }

    private static AbstractFurnaceBlockEntity prepareFurnace(GameTestContext context, Block block, int count) {
        context.setBlock(12, 1, 12, block);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) context.level
                .getBlockEntity(context.pos(12, 1, 12));
        furnace.setItem(0, new ItemStack(ModItems.RAW_SHEEPER_MUTTON.get(), count));
        furnace.setItem(1, new ItemStack(Items.COAL, 3));
        return furnace;
    }

    private static void furnace(GameTestContext context) {
        context.set(SheeperConfig.COOKING_WARNING_SECONDS, 0.5);
        prepareFurnace(context, Blocks.FURNACE, 3);
        context.setBlock(13, 1, 12, Blocks.DIRT);
        ItemEntity marker = context.item(Items.DIAMOND, 2, 12, 1, 13);
        context.at(5, () -> context.assertBlockPresent(Blocks.FURNACE, 12, 1, 12));
        context.at(20, () -> {
            context.expect(context.level.getBlockState(context.pos(12, 1, 12)).isAir(),
                    "Overheated furnace must be removed");
            context.expect(context.count(ModItems.RAW_SHEEPER_MUTTON.get()) == 2,
                    "Reaction must consume exactly one of the three meat pieces");
            context.expect(context.count(Items.FURNACE) == 1, "Removed furnace must retain its ordinary block drop");
            context.expect(!marker.isRemoved() && marker.getItem().getCount() == 2,
                    "Cooking blast must preserve the known marker stack");
            context.assertBlockPresent(Blocks.DIRT, 13, 1, 12);
            context.expect(context.explosions.size() == 1, "Overheating must cause one blast");
            context.pass();
        });
    }

    private static void safeCooking(GameTestContext context) {
        context.set(SheeperConfig.COOKING_WARNING_SECONDS, 0.5);
        AbstractFurnaceBlockEntity smoker = prepareFurnace(context, Blocks.SMOKER, 1);
        context.at(130, () -> {
            context.assertBlockPresent(Blocks.SMOKER, 12, 1, 12);
            context.expect(
                    smoker.getItem(2).is(ModItems.COOKED_SHEEPER_MUTTON.get()) && smoker.getItem(2).getCount() == 1,
                    "Smoker must complete the real cooking recipe");
            context.expect(smoker.getItem(0).isEmpty() && context.explosions.isEmpty(),
                    "Smoking must finish without a blast");
            context.pass();
        });
    }

    private static void removeMeat(GameTestContext context) {
        context.set(SheeperConfig.COOKING_WARNING_SECONDS, 0.5);
        AbstractFurnaceBlockEntity furnace = prepareFurnace(context, Blocks.FURNACE, 3);
        context.at(5, () -> furnace.setItem(0, ItemStack.EMPTY));
        context.at(20, () -> {
            context.assertBlockPresent(Blocks.FURNACE, 12, 1, 12);
            context.expect(context.explosions.isEmpty(), "Removing input must cancel the old cooking deadline");
            furnace.setItem(0, new ItemStack(ModItems.RAW_SHEEPER_MUTTON.get(), 3));
        });
        context.at(25,
                () -> context.expect(context.explosions.isEmpty(), "Reinserted food must get a full new warning"));
        context.at(40, () -> {
            context.expect(context.explosions.size() == 1, "Reinserted food must eventually trigger once");
            context.pass();
        });
    }

    private static void prepareEating(GameTestContext context) {
        context.set(SheeperConfig.RAW_MUTTON_DURATION, 2.0);
        context.set(SheeperConfig.COOKED_MUTTON_DURATION, 2.0);
        context.set(SheeperConfig.RAW_MUTTON_INTERVAL, 1.0);
        context.set(SheeperConfig.COOKED_MUTTON_INTERVAL, 1.0);
        context.set(SheeperConfig.RAW_MUTTON_PUSH, 0.0);
    }

    private static void eating(GameTestContext context) {
        prepareEating(context);
        TestPlayer raw = context.player(8, 1, 12);
        TestPlayer cooked = context.player(16, 1, 12);
        double base = cooked.getAttributeValue(Attributes.MOVEMENT_SPEED);
        raw.consume(ModItems.RAW_SHEEPER_MUTTON.get());
        cooked.consume(ModItems.COOKED_SHEEPER_MUTTON.get());
        context.at(10, () -> {
            context.near(raw.foodDamage, 0, "Raw effect must wait a full first interval");
            context.near(cooked.foodDamage, 0, "Cooked effect must wait a full first interval");
            context.near(raw.getAttributeValue(Attributes.MOVEMENT_SPEED), base, "Raw meat must retain ordinary speed");
            context.near(cooked.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.5,
                    "Cooked meat must apply its speed bonus");
        });
        context.at(50, () -> {
            context.near(raw.foodDamage, 2,
                    "Raw effect must complete its two scheduled damage pulses; " + raw.damageEvents);
            context.near(cooked.foodDamage, 2,
                    "Cooked effect must complete its two scheduled damage pulses; " + cooked.damageEvents);
            context.near(cooked.getAttributeValue(Attributes.MOVEMENT_SPEED), base,
                    "Expired food effect must remove its speed bonus");
            context.pass();
        });
    }

    private static void milk(GameTestContext context) {
        prepareEating(context);
        TestPlayer player = context.player(12, 1, 12);
        double base = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        player.consume(ModItems.RAW_SHEEPER_MUTTON.get());
        player.consume(ModItems.COOKED_SHEEPER_MUTTON.get());
        context.expect(player.getPersistentData().getInt("sheeper:raw_mutton_ticks") > 0
                && player.getPersistentData().getInt("sheeper:cooked_mutton_ticks") > 0,
                "Milk scene must first activate both food effects");
        context.at(5, () -> {
            player.consume(Items.MILK_BUCKET);
            context.expect(player.getMainHandItem().is(Items.BUCKET), "Milk use must leave an empty bucket");
            context.expect(!player.getPersistentData().contains("sheeper:raw_mutton_ticks")
                    && !player.getPersistentData().contains("sheeper:cooked_mutton_ticks"),
                    "Finishing milk must immediately clear both food countdowns");
            context.near(player.getAttributeValue(Attributes.MOVEMENT_SPEED), base,
                    "Milk must remove the food speed bonus");
        });
        context.at(65, () -> {
            context.near(player.foodDamage, 0, "Milk must prevent later food damage; " + player.damageEvents);
            context.near(player.getAttributeValue(Attributes.MOVEMENT_SPEED), base,
                    "Speed bonus must remain cleared after milk");
            context.pass();
        });
    }
}
