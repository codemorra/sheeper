package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.SheeperEntity;
import de.codemorra.sheeper.registry.ModBlocks;
import de.codemorra.sheeper.registry.ModItems;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Basic adult fuse, observation, death-loot, and retaliation contracts. */
final class AdultGameTests {
    private AdultGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        // Checks that an unobserved adult finishes one fuse without loot or mobGriefing
        // block damage.
        tests.add(new SheeperGameTests.Scenario("adult_fuse_explodes_once", AdultGameTests::fuse));
        // Checks that observation cancels the fuse and a restart gets its full
        // duration.
        tests.add(new SheeperGameTests.Scenario("observation_resets_fuse", AdultGameTests::cancellation));
        // Checks that ordinary adult death drops matching wool and raw Sheeper mutton.
        tests.add(new SheeperGameTests.Scenario("ordinary_death_drops_sheeper_loot", AdultGameTests::ordinaryDeath));
        // Checks that fatal fire ends in one delayed explosion without Sheeper drops.
        tests.add(new SheeperGameTests.Scenario("fatal_fire_drops_no_sheeper_loot", AdultGameTests::fatalFire));
        // Checks that retaliation keeps the first attacker until that player becomes
        // ineligible.
        tests.add(new SheeperGameTests.Scenario("retaliation_retains_first_attacker", AdultGameTests::retainsAttacker));
    }

    private static SheeperEntity stationaryAdult(GameTestContext context) {
        SheeperEntity sheeper = context.sheeper(12, 1, 12, false, true);
        sheeper.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        return sheeper;
    }

    private static void fuse(GameTestContext context) {
        context.player(12, 1, 14);
        context.set(SheeperConfig.FUSE_DURATION, 1.0);
        SheeperEntity sheeper = stationaryAdult(context);
        context.setBlock(13, 1, 12, Blocks.DIRT);
        context.at(10, () -> {
            context.expect(sheeper.isWarningActive() && sheeper.getFuseProgress() > 0,
                    "Adult must start its fuse inside range while unobserved");
            context.expect(context.explosions.isEmpty(), "Fuse must not explode halfway through its duration");
        });
        context.at(40, () -> {
            context.expect(sheeper.isRemoved() && context.explosions.size() == 1,
                    "Completed fuse must remove the adult and explode exactly once");
            context.expect(context.sheeperDrops().isEmpty(), "Self-detonation must not produce Sheeper loot");
            context.assertBlockPresent(Blocks.DIRT, 13, 1, 12);
            context.pass();
        });
    }

    private static void cancellation(GameTestContext context) {
        TestPlayer player = context.player(12, 1, 14);
        context.set(SheeperConfig.FUSE_DURATION, 1.0);
        SheeperEntity sheeper = stationaryAdult(context);
        context.at(10, () -> {
            context.expect(sheeper.getFuseProgress() > 0, "Scene must first start a real fuse");
            player.lookAt(EntityAnchorArgument.Anchor.EYES, sheeper.getEyePosition());
        });
        context.at(15, () -> context.near(sheeper.getFuseProgress(), 0, "Observation must clear fuse progress"));
        context.at(35, () -> {
            context.expect(context.explosions.isEmpty(), "Cancelled fuse must not explode at its old deadline");
            player.setYRot(0);
            player.setXRot(0);
        });
        context.at(45, () -> context.expect(context.explosions.isEmpty(), "Restart must wait a full new fuse"));
        context.at(70, () -> {
            context.expect(context.explosions.size() == 1, "Restarted fuse must eventually explode once");
            context.pass();
        });
    }

    private static void ordinaryDeath(GameTestContext context) {
        SheeperEntity sheeper = context.sheeper(12, 1, 12, false, false);
        sheeper.hurt(DamageSource.GENERIC, 1000);
        context.at(2, () -> {
            int meat = context.count(ModItems.RAW_SHEEPER_MUTTON.get());
            context.expect(meat >= 1 && meat <= 2, "Ordinary adult death must drop 1-2 raw Sheeper mutton");
            context.expect(context.count(ModBlocks.WHITE_SHEEPER_WOOL.get().asItem()) == 1,
                    "Unshorn white adult must drop one matching Sheeper wool");
            context.expect(context.explosions.isEmpty(), "Ordinary death must not trigger destructive self-explosion");
            context.pass();
        });
    }

    private static void fatalFire(GameTestContext context) {
        context.set(SheeperConfig.FUSE_DURATION, 1.0);
        SheeperEntity sheeper = context.sheeper(12, 1, 12, false, false);
        sheeper.hurt(DamageSource.ON_FIRE, 1000);
        context.expect(sheeper.isFireDeathActive() && !sheeper.isRemoved(),
                "Fatal fire must start the final fuse");
        context.at(10, () -> context.expect(context.explosions.isEmpty(), "Fatal-fire fuse must not finish early"));
        context.at(35, () -> {
            context.expect(sheeper.isRemoved() && context.explosions.size() == 1,
                    "Fatal fire must end with one self-explosion");
            context.expect(context.sheeperDrops().isEmpty(),
                    "Fatal fire must not produce Sheeper loot: " + context.sheeperDrops());
            context.pass();
        });
    }

    private static void retainsAttacker(GameTestContext context) {
        TestPlayer first = context.player(12, 1, 16);
        TestPlayer second = context.player(16, 1, 12);
        SheeperEntity sheeper = context.sheeper(12, 1, 12, false, false);
        sheeper.hurt(DamageSource.playerAttack(first), 1);
        sheeper.invulnerableTime = 0;
        sheeper.hurt(DamageSource.playerAttack(second), 1);
        context.expect(sheeper.getRetaliationTarget() == first, "Later attacker must not replace the first attacker");
        first.setGameMode(GameType.CREATIVE);
        context.expect(sheeper.getRetaliationTarget() == null, "Invalid game mode must end retaliation eligibility");
        context.pass();
    }
}
