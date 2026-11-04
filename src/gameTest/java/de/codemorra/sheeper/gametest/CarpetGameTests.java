package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.PrimedSheeperWool;
import de.codemorra.sheeper.registry.ModBlocks;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Representative step, crouch, and harvesting contracts for Sheeper carpets.
 */
final class CarpetGameTests {
    private CarpetGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        // Checks real contact, area damage, launch delivery, and preserved blocks.
        tests.add(new SheeperGameTests.Scenario("carpet_step_damages_and_launches_nearby_entities",
                CarpetGameTests::step));
        // Checks that sustained sneaking is safe and standing can trigger afterward.
        tests.add(new SheeperGameTests.Scenario("crouching_preserves_carpet", CarpetGameTests::crouching));
        tests.add(new SheeperGameTests.Scenario("shears_safely_harvest_carpet",
                context -> WoolGameTests.mining(context, Items.SHEARS, ModBlocks.PURPLE_SHEEPER_CARPET.get())));
        tests.add(new SheeperGameTests.Scenario("unsafe_carpet_mining_preserves_surroundings",
                context -> WoolGameTests.mining(context, Items.AIR, ModBlocks.PURPLE_SHEEPER_CARPET.get())));
    }

    private static TestPlayer prepare(GameTestContext context) {
        context.set(SheeperConfig.CARPET_RADIUS, 2.0);
        context.set(SheeperConfig.CARPET_DAMAGE, 2.0);
        context.set(SheeperConfig.CARPET_KNOCKBACK, 1.0);
        context.set(SheeperConfig.CARPET_UPWARD_PUSH, 0.65);
        context.setBlock(12, 1, 12, ModBlocks.PURPLE_SHEEPER_CARPET.get());
        return context.player(12, 1, 16);
    }

    /**
     * Uses collision processing at carpet height instead of calling the block hook.
     */
    private static void contact(GameTestContext context, TestPlayer player) {
        player.standAt(context.point(12, 1, 12).add(-0.2D, 1.0D / 16.0D, 0.0D));
        player.setDeltaMovement(Vec3.ZERO);
        player.move(MoverType.SELF, new Vec3(0.1D, -0.1D, 0.0D));
    }

    private static Cow stationaryCow(GameTestContext context, int x) {
        Cow cow = context.spawn(EntityType.COW, x, 1, 12);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setDeltaMovement(Vec3.ZERO);
        return cow;
    }

    private static void step(GameTestContext context) {
        TestPlayer player = prepare(context);
        context.rule(GameRules.RULE_MOBGRIEFING, true);
        Cow nearby = stationaryCow(context, 13);
        Cow outside = stationaryCow(context, 16);
        context.setBlock(12, 1, 11, Blocks.DIRT);
        context.setBlock(12, 1, 13, ModBlocks.WHITE_SHEEPER_WOOL.get());
        context.setBlock(11, 1, 12, Blocks.TNT);

        // Wait for newly spawned entities to enter the level's query index.
        context.at(3, () -> {
            float playerHealth = player.getHealth();
            float nearbyHealth = nearby.getHealth();
            float outsideHealth = outside.getHealth();
            Vec3 outsideMotion = outside.getDeltaMovement();
            player.motionPackets.clear();
            contact(context, player);

            context.assertBlockPresent(Blocks.AIR, 12, 1, 12);
            context.near(player.getHealth(), playerHealth - 2.0F,
                    "Unprotected trigger must take one heart of step damage");
            context.near(nearby.getHealth(), nearbyHealth - 2.0F,
                    "Exposed nearby cow must also take one heart of step damage");
            context.expect(nearby.getDeltaMovement().x > 0.0D && nearby.getDeltaMovement().y > 0.0D,
                    "Nearby cow must be launched upward and away from the carpet");
            context.near(outside.getHealth(), outsideHealth, "Cow outside the radius must remain unharmed");
            context.expect(outside.getDeltaMovement().equals(outsideMotion),
                    "Cow outside the radius must receive no launch");
            context.expect(player.motionPackets.size() == 1, "Trigger must receive one immediate velocity packet");
            ClientboundSetEntityMotionPacket launch = player.motionPackets.get(0);
            context.expect(launch.getId() == player.getId() && launch.getXa() / 8000.0D < -0.5D
                    && launch.getYa() / 8000.0D > 0.4D,
                    "Player packet must deliver a substantial upward and outward launch");
            context.assertBlockPresent(Blocks.DIRT, 12, 1, 11);
            context.assertBlockPresent(ModBlocks.WHITE_SHEEPER_WOOL.get(), 12, 1, 13);
            context.assertBlockPresent(Blocks.TNT, 11, 1, 12);
            context.expect(context.entities(PrimedTnt.class).isEmpty()
                    && context.entities(PrimedSheeperWool.class).isEmpty(),
                    "Step blast must not prime neighboring TNT or wool");

            // Remove damage immunity so a repeated blast cannot hide behind it.
            player.invulnerableTime = 0;
            contact(context, player);
            context.near(player.getHealth(), playerHealth - 2.0F, "Consumed carpet must not damage twice");
            context.expect(player.motionPackets.size() == 1, "Consumed carpet must not launch twice");
        });
        context.at(6, () -> {
            context.expect(context.count(ModBlocks.PURPLE_SHEEPER_CARPET.get().asItem()) == 0,
                    "Step explosion must consume the carpet without a carpet drop");
            context.pass();
        });
    }

    private static void crouching(GameTestContext context) {
        TestPlayer player = prepare(context);
        float health = player.getHealth();
        context.at(3, () -> {
            player.setShiftKeyDown(true);
            player.setPose(Pose.CROUCHING);
            player.motionPackets.clear();
            contact(context, player);
            context.expect(player.getDeltaMovement().equals(Vec3.ZERO),
                    "Sneaking contact must not add a launch impulse");
            context.expect(player.motionPackets.isEmpty(), "Sneaking contact must send no immediate launch packet");
        });
        context.at(8, () -> {
            context.assertBlockPresent(ModBlocks.PURPLE_SHEEPER_CARPET.get(), 12, 1, 12);
            context.near(player.getHealth(), health, "Sustained sneaking must cause no carpet damage");

            // Ordinary entity tracking may send velocity packets between ticks.
            // Isolate packets from the next contact instead of counting that traffic.
            player.motionPackets.clear();
            // Model the client's change back to standing before the next contact.
            player.setShiftKeyDown(false);
            player.setPose(Pose.STANDING);
            contact(context, player);
            context.assertBlockPresent(Blocks.AIR, 12, 1, 12);
            context.near(player.getHealth(), health - 2.0F, "Standing must trigger the previously safe carpet");
            context.expect(player.motionPackets.size() == 1, "Standing contact must deliver the launch");
            context.pass();
        });
    }
}
