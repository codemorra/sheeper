package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterProjectile;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.registry.ModBlocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Straight flight, single impacts, persistence, and regular shot scheduling.
 */
final class WaterProjectileGameTests {
    private WaterProjectileGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(
                new SheeperGameTests.Scenario("water_projectile_dodge_and_lifetime", WaterProjectileGameTests::flight));
        tests.add(new SheeperGameTests.Scenario("water_projectile_player_impact",
                WaterProjectileGameTests::playerImpact));
        tests.add(new SheeperGameTests.Scenario("water_projectile_terrain_and_mob",
                WaterProjectileGameTests::collisions));
        tests.add(
                new SheeperGameTests.Scenario("water_projectile_timing_and_rotation", WaterProjectileGameTests::shots));
        tests.add(new SheeperGameTests.Scenario("water_projectile_continuous_target_look",
                WaterProjectileGameTests::look));
    }

    private static WaterSheeperEntity boss(GameTestContext context) {
        context.set(SheeperConfig.WATER_PROJECTILE_WEIGHT, 1.0D);
        context.set(SheeperConfig.WATER_SALVO_WEIGHT, 0.0D);
        context.set(SheeperConfig.WATER_FOUNTAIN_WEIGHT, 0.0D);
        context.set(SheeperConfig.WATER_SIZE, 1.0D);
        context.set(SheeperConfig.WATER_MOVEMENT_RADIUS, 0.0D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_STRENGTH, 0.0D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_UPWARD_STRENGTH, 0.0D);
        context.set(SheeperConfig.WATER_ARENA_SEARCH_BUDGET, 1);
        return context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 2, 8);
    }

    private static WaterProjectile fire(GameTestContext context, WaterSheeperEntity boss, TestPlayer target) {
        context.expect(boss.fireWaterProjectile(target), "A visible living target must allow a shot");
        return context.entities(WaterProjectile.class).get(0);
    }

    private static void flight(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        TestPlayer target = context.player(16, 2, 8);
        context.set(SheeperConfig.WATER_PROJECTILE_LIFETIME, 10);
        WaterProjectile shot = fire(context, boss, target);
        Vec3 origin = shot.position();
        Vec3 velocity = shot.getDeltaMovement();
        context.near(velocity.length(), 0.8D, "Shots must use the configured speed");
        target.standAt(context.point(16, 2, 14));
        // Boss water and ordinary water must not introduce drag or gravity.
        context.setBlock(8, 3, 8, Blocks.WATER);
        context.setBlock(9, 3, 8, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3));
        context.setBlock(10, 3, 8, ModBlocks.BOSS_WATER.get());
        context.setBlock(11, 3, 8, ModBlocks.BOSS_WATER.get().defaultBlockState().setValue(LiquidBlock.LEVEL, 3));
        for (int tick = 0; tick < 5; tick++) {
            shot.tick();
        }
        context.near(shot.position().distanceTo(origin.add(velocity.scale(5))), 0.0D,
                "Moving the target must not change the projectile trajectory");
        CompoundTag saved = shot.saveWithoutId(new CompoundTag());
        shot.discard();
        WaterProjectile restored = ModEntities.WATER_PROJECTILE.get().create(context.level);
        restored.load(saved);
        for (int tick = 0; tick < 5; tick++) {
            restored.tick();
        }
        context.expect(!restored.isRemoved(), "Reload must preserve the remaining flight budget");
        restored.tick();
        context.expect(restored.isRemoved(), "Reload must not reset the maximum lifetime");
        context.expect(target.damageEvents.isEmpty(), "A player moving off the shot line must dodge");
        context.pass();
    }

    private static void playerImpact(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        TestPlayer target = context.player(13, 2, 8);
        target.setDeltaMovement(Vec3.ZERO);
        WaterProjectile shot = fire(context, boss, target);
        Vec3 impulse = shot.getDeltaMovement().normalize().scale(0.5D);
        for (int tick = 0; tick < 12; tick++) {
            shot.tick();
        }
        context.expect(shot.isRemoved() && target.damageEvents.size() == 1,
                "An entity impact must consume the projectile and damage at most once");
        context.near(target.getHealth(), 18.0D, "The default hit must deal two health points");
        context.near(target.getDeltaMovement().distanceTo(impulse), 0.0D,
                "Knockback must follow the flight vector");
        context.expect(!target.motionPackets.isEmpty(), "The player's client must receive projectile knockback");
        context.near(boss.getHealth(), boss.getMaxHealth(), "Shots must never hit their owner");
        context.pass();
    }

    private static void collisions(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        TestPlayer target = context.player(16, 2, 8);
        WaterProjectile blocked = fire(context, boss, target);
        for (int y = 1; y <= 5; y++) {
            context.setBlock(11, y, 8, Blocks.STONE);
        }
        for (int tick = 0; tick < 12; tick++) {
            blocked.tick();
        }
        context.expect(blocked.isRemoved() && target.damageEvents.isEmpty(),
                "Terrain must stop shots before a player behind it is hit");
        context.expect(!boss.fireWaterProjectile(target), "Regular launch logic must reject blocked sight");
        Cow cow = context.spawn(EntityType.COW, 8, 2, 14);
        cow.setNoAi(true);
        WaterProjectile shot = context.spawn(ModEntities.WATER_PROJECTILE.get(), 5, 2, 14);
        shot.setOwner(boss);
        shot.setDeltaMovement(new Vec3(3.0D, 0.0D, 0.0D));
        context.set(SheeperConfig.WATER_PROJECTILE_DAMAGE, 3.0D);
        context.set(SheeperConfig.WATER_PROJECTILE_KNOCKBACK, 0.25D);
        shot.tick();
        shot.tick();
        context.expect(shot.isRemoved(), "Other living entities must also stop a shot");
        context.near(cow.getHealth(), cow.getMaxHealth() - 3.0D, "Mob hits must use configured damage once");
        context.near(cow.getDeltaMovement().x, 0.25D, "Mob hits must use configured flight-direction knockback");
        context.pass();
    }

    private static void look(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        TestPlayer first = context.player(16, 2, 8);
        context.player(8, 2, 16);
        // Stay between shots while the target moves around and above the boss.
        for (int tick = 0; tick < 20; tick++) {
            first.standAt(context.point(tick < 10 ? 16 : 2, 2 + tick % 3, 4 + tick % 8));
            boss.getNavigation().moveTo(context.point(8, 2, 2).x, boss.getY(), context.point(8, 2, 2).z, 1.0D);
            boss.tick();
            assertLookingAt(context, boss, first);
        }
        context.expect(context.entities(WaterProjectile.class).isEmpty(),
                "Continuous tracking must also work between shots");
        first.standAt(context.point(100, 2, 8));
        boss.tick();
        context.expect(boss.getTarget() == null,
                "An invalid target must release focus without selecting a replacement");
        context.pass();
    }

    private static void assertLookingAt(GameTestContext context, WaterSheeperEntity boss, TestPlayer target) {
        Vec3 expected = target.getEyePosition().subtract(boss.getEyePosition()).normalize();
        Vec3 actual = Vec3.directionFromRotation(boss.getXRot(), boss.getYHeadRot());
        context.expect(actual.dot(expected) > 0.9999D,
                "Head yaw and pitch must keep pointing at the current target after navigation and look control");
    }

    private static void shots(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        context.set(SheeperConfig.WATER_PROJECTILE_INTERVAL, 4);
        context.set(SheeperConfig.WATER_PROJECTILE_DURATION_MIN, 8);
        context.set(SheeperConfig.WATER_PROJECTILE_DURATION_MAX, 8);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MIN, 2);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MAX, 2);
        TestPlayer first = context.player(16, 2, 8);
        TestPlayer second = context.player(8, 2, 16);
        for (int tick = 0; tick < 3; tick++) {
            boss.tick();
        }
        context.expect(context.entities(WaterProjectile.class).isEmpty(), "Shots must wait for the interval");
        boss.tick();
        List<WaterProjectile> shots = context.entities(WaterProjectile.class);
        context.expect(shots.size() == 1 && shots.get(0).getDeltaMovement().x > 0.0D,
                "The first interval must shoot at the first participant");
        shots.get(0).discard();
        for (int tick = 0; tick < 4; tick++) {
            boss.tick();
        }
        shots = context.entities(WaterProjectile.class);
        context.expect(shots.size() == 1 && shots.get(0).getDeltaMovement().x > 0.0D,
                "All shots in one phase must keep the same participant");
        shots.get(0).discard();
        context.expect(boss.getTarget() == null, "The start of rest must immediately release focus");
        for (int tick = 0; tick < 2; tick++) {
            boss.tick();
            context.expect(boss.getTarget() == null, "Rest must not select another participant");
        }
        for (int tick = 0; tick < 4; tick++) {
            boss.tick();
            context.expect(boss.getTarget() == second, "Only the next phase may select the next participant");
        }
        shots = context.entities(WaterProjectile.class);
        context.expect(shots.size() == 1 && shots.get(0).getDeltaMovement().z > 0.0D,
                "The next phase must shoot at its newly selected participant");
        shots.get(0).discard();
        first.standAt(context.point(100, 2, 8));
        second.standAt(context.point(100, 2, 16));
        for (int tick = 0; tick < 8; tick++) {
            boss.tick();
        }
        context.expect(boss.getTarget() == null && context.entities(WaterProjectile.class).isEmpty(),
                "Combat without participants must pause shots");
        first.standAt(context.point(16, 2, 8));
        for (int tick = 0; tick < 2; tick++) {
            boss.tick();
            context.expect(boss.getTarget() == null, "Returning players must first complete the remaining rest");
        }
        for (int tick = 0; tick < 3; tick++) {
            boss.tick();
        }
        context.expect(context.entities(WaterProjectile.class).isEmpty(),
                "The next attack must wait its complete shot interval");
        boss.tick();
        context.expect(context.entities(WaterProjectile.class).size() == 1, "Returning players must resume shots");
        context.pass();
    }
}
