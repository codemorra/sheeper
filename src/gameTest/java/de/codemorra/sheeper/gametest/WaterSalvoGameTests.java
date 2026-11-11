package de.codemorra.sheeper.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterProjectile;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Salvo warnings, timed firing, fixed focus, interruption and phase
 * integration.
 */
final class WaterSalvoGameTests {
    private WaterSalvoGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("water_salvo_warning_and_tracking", WaterSalvoGameTests::tracking));
        tests.add(new SheeperGameTests.Scenario("water_salvo_duration_range", WaterSalvoGameTests::durations));
        tests.add(new SheeperGameTests.Scenario("water_salvo_reload", WaterSalvoGameTests::persistence));
        tests.add(new SheeperGameTests.Scenario("water_salvo_sight_loss", WaterSalvoGameTests::sight));
        tests.add(new SheeperGameTests.Scenario("water_salvo_cancellation", WaterSalvoGameTests::cancellation));
        tests.add(new SheeperGameTests.Scenario("water_attack_three_way_selection", WaterSalvoGameTests::selection));
    }

    private static WaterSheeperEntity boss(GameTestContext context, int preparation, int duration, int interval) {
        context.set(SheeperConfig.WATER_PROJECTILE_WEIGHT, 0.0D);
        context.set(SheeperConfig.WATER_FOUNTAIN_WEIGHT, 0.0D);
        context.set(SheeperConfig.WATER_SALVO_WEIGHT, 1.0D);
        context.set(SheeperConfig.WATER_SALVO_PREPARATION, preparation);
        context.set(SheeperConfig.WATER_SALVO_DURATION_MIN, duration);
        context.set(SheeperConfig.WATER_SALVO_DURATION_MAX, duration);
        context.set(SheeperConfig.WATER_SALVO_INTERVAL, interval);
        context.set(SheeperConfig.WATER_SALVO_EYE_FADE, 2);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MIN, 3);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MAX, 3);
        context.set(SheeperConfig.WATER_SIZE, 1.0D);
        context.set(SheeperConfig.WATER_MOVEMENT_RADIUS, 5.0D);
        context.set(SheeperConfig.WATER_ARENA_SEARCH_BUDGET, 1);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_STRENGTH, 0.0D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_UPWARD_STRENGTH, 0.0D);
        WaterSheeperEntity boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 2, 8);
        boss.setNoGravity(true);
        return boss;
    }

    private static void tick(WaterSheeperEntity boss, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            boss.tick();
        }
    }

    private static int shots(GameTestContext context) {
        return context.entities(WaterProjectile.class).size();
    }

    private static WaterSheeperEntity reload(GameTestContext context, WaterSheeperEntity boss) {
        CompoundTag tag = boss.saveWithoutId(new CompoundTag());
        boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        WaterSheeperEntity restored = ModEntities.WATER_SHEEPER.get().create(context.level);
        restored.load(tag);
        return restored;
    }

    private static void tracking(GameTestContext context) {
        WaterSheeperEntity boss = boss(context, 4, 7, 2);
        TestPlayer target = context.player(16, 2, 8);
        context.player(8, 2, 16);
        Vec3 origin = boss.position();
        for (int tick = 1; tick <= 4; tick++) {
            boss.tick();
            context.expect(shots(context) == 0, "Preparation must never fire a projectile");
            context.near(boss.getSalvoEyeIntensity(1.0F), tick / 4.0D,
                    "Eyes must gradually reach full red before firing");
        }
        Vec3 firstVelocity = null;
        for (int tick = 1; tick <= 7; tick++) {
            target.standAt(context.point(16, 2, 8 + tick));
            boss.setDeltaMovement(0.4D, 0.0D, 0.3D);
            boss.move(MoverType.SELF, new Vec3(0.2D, 0.0D, 0.2D));
            boss.tick();
            context.near(boss.position().distanceTo(origin), 0.0D,
                    "The boss must remain stationary throughout the salvo");
            context.expect(shots(context) == (tick + 1) / 2,
                    "Salvo shots must respect their own interval without regular shots in parallel");
            if (tick == 1) {
                firstVelocity = context.entities(WaterProjectile.class).get(0).getDeltaMovement();
            }
            if (tick < 7) {
                context.expect(boss.getTarget() == target, "The salvo must retain its original participant");
                Vec3 expected = target.getEyePosition().subtract(boss.getEyePosition()).normalize();
                context.expect(Vec3.directionFromRotation(boss.getXRot(), boss.getYHeadRot()).dot(expected) > 0.9999D,
                        "The head must follow the moving participant between shots");
            }
            if (tick % 2 == 1) {
                WaterProjectile latest = context.entities(WaterProjectile.class).get(shots(context) - 1);
                Vec3 expected = target.getEyePosition().subtract(latest.position()).normalize();
                context.expect(latest.getDeltaMovement().normalize().dot(expected) > 0.9999D,
                        "Every new shot must aim at the current target position");
            }
        }
        context.near(context.entities(WaterProjectile.class).get(0).getDeltaMovement().distanceTo(firstVelocity), 0,
                "Later target movement must not redirect an existing projectile");
        context.expect(!boss.isPerformingSalvo() && boss.getTarget() == null,
                "The configured duration must end the salvo and release focus");
        tick(boss, 3);
        context.expect(shots(context) == 4 && boss.getSalvoEyeIntensity(1.0F) == 0,
                "Rest must prevent attacks and clear the red eyes");
        boss.move(MoverType.SELF, new Vec3(0.2D, 0, 0));
        context.expect(boss.position().distanceToSqr(origin) > 0.01D, "Movement must be released after the salvo");
        boss.tick();
        context.expect(boss.isPerformingSalvo() && boss.getTarget() != target,
                "The next salvo may start after ordinary rest and must rotate participants");
        context.pass();
    }

    private static void durations(GameTestContext context) {
        TestPlayer player = context.player(16, 2, 8);
        Set<Integer> observed = new HashSet<>();
        for (int sample = 0; sample < 12; sample++) {
            WaterSheeperEntity boss = boss(context, 1, 3, 1);
            context.set(SheeperConfig.WATER_SALVO_DURATION_MAX, 7);
            boss.getRandom().setSeed(12345L + sample * 1009L);
            boss.tick();
            // Changing bounds during preparation must not alter the sampled duration.
            context.set(SheeperConfig.WATER_SALVO_DURATION_MIN, 1);
            context.set(SheeperConfig.WATER_SALVO_DURATION_MAX, 1);
            int elapsed = 0;
            while (boss.isPerformingSalvo() && elapsed < 8) {
                boss.tick();
                elapsed++;
            }
            context.expect(elapsed >= 3 && elapsed <= 7 && shots(context) == elapsed,
                    "Firing must last for the sampled bounds, excluding preparation");
            observed.add(elapsed);
            boss.discard();
            context.entities(WaterProjectile.class).forEach(Entity::discard);
        }
        context.expect(observed.size() > 1, "Different salvos must sample different durations");
        WaterSheeperEntity boss = boss(context, 1, 5, 1);
        context.set(SheeperConfig.WATER_SALVO_DURATION_MAX, 1);
        tick(boss, 6);
        context.expect(!boss.isPerformingSalvo() && shots(context) == 5,
                "Reversed bounds must use the configured minimum duration");
        context.near(player.getHealth(), 20, "Unticked projectiles must not damage the observer");
        context.pass();
    }

    private static void persistence(GameTestContext context) {
        WaterSheeperEntity boss = boss(context, 4, 7, 2);
        TestPlayer target = context.player(16, 2, 8);
        context.player(8, 2, 16);
        tick(boss, 2);
        boss = reload(context, boss);
        context.near(boss.getSalvoEyeIntensity(1.0F), 0.5D, "Reload must preserve warning progress");
        tick(boss, 2);
        context.expect(shots(context) == 0 && boss.getTarget() == target,
                "Reload must retain the original focus and finish the remaining preparation");
        boss.tick();
        boss = reload(context, boss);
        boss.tick();
        context.expect(shots(context) == 1, "Reload must not reset or skip the remaining shot delay");
        boss.tick();
        context.expect(shots(context) == 2 && boss.getTarget() == target,
                "Firing must resume at the saved interval against the same participant");
        tick(boss, 4);
        context.expect(shots(context) == 4 && !boss.isPerformingSalvo(),
                "Reload must retain the sampled firing duration");
        boss.tick();
        boss = reload(context, boss);
        tick(boss, 2);
        context.expect(!boss.isPerformingSalvo() && shots(context) == 4 && boss.getSalvoEyeIntensity(1) == 0,
                "Reload during rest must retain both rest and eye fade");
        boss.tick();
        context.expect(boss.isPerformingSalvo(), "A new salvo may start after the saved rest completes");
        context.pass();
    }

    private static void wall(GameTestContext context, boolean blocked) {
        for (int y = 1; y <= 5; y++) {
            context.setBlock(11, y, 8, blocked ? Blocks.STONE : Blocks.AIR);
        }
    }

    private static void sight(GameTestContext context) {
        WaterSheeperEntity boss = boss(context, 2, 20, 1);
        context.set(SheeperConfig.WATER_SALVO_SIGHT_TIMEOUT, 3);
        context.player(16, 2, 8);
        tick(boss, 3);
        wall(context, true);
        tick(boss, 2);
        context.expect(boss.isPerformingSalvo() && shots(context) == 1,
                "Short sight loss must pause shots without ending the salvo");
        wall(context, false);
        boss.tick();
        context.expect(boss.isPerformingSalvo() && shots(context) == 2,
                "Regaining sight must resume firing and reset the sight-loss deadline");
        wall(context, true);
        tick(boss, 2);
        context.expect(boss.isPerformingSalvo(), "A second short occlusion must receive a fresh deadline");
        boss = reload(context, boss);
        boss.tick();
        context.expect(!boss.isPerformingSalvo() && boss.getTarget() == null && shots(context) == 2,
                "The saved sight-loss deadline must abort without extra shots");
        tick(boss, 2);
        context.near(boss.getSalvoEyeIntensity(1), 0, "Sight-loss abort must clear the red eyes");
        boss.discard();
        context.entities(WaterProjectile.class).forEach(Entity::discard);
        wall(context, false);
        boss = boss(context, 1, 4, 1);
        context.set(SheeperConfig.WATER_SALVO_SIGHT_TIMEOUT, 20);
        tick(boss, 2);
        wall(context, true);
        tick(boss, 3);
        context.expect(!boss.isPerformingSalvo() && shots(context) == 1,
                "Sight loss must not extend the sampled firing duration");
        context.pass();
    }

    private static void cancellation(GameTestContext context) {
        TestPlayer target = context.player(16, 2, 8);
        context.player(8, 2, 16);
        for (int reason = 0; reason < 5; reason++) {
            target.setHealth(20);
            target.setGameMode(GameType.SURVIVAL);
            target.standAt(context.point(16, 2, 8));
            WaterSheeperEntity boss = boss(context, 2, 20, 1);
            tick(boss, 2);
            switch (reason) {
                case 0 -> target.standAt(context.point(100, 2, 8));
                case 1 -> target.setGameMode(GameType.CREATIVE);
                case 2 -> target.setHealth(0);
                case 3 -> boss.setNoAi(true);
                case 4 -> {
                    boss.setHealth(0);
                    boss.die(DamageSource.GENERIC);
                }
                default -> throw new IllegalStateException("Unexpected cancellation case");
            }
            boss.tick();
            context.expect(!boss.isPerformingSalvo() && boss.getTarget() == null && shots(context) == 0,
                    "Invalid targets, disabled AI and boss death must cancel without retargeting or firing");
            tick(boss, 2);
            context.near(boss.getSalvoEyeIntensity(1), 0, "Every cancellation must finish the eye fade");
            boss.discard();
        }
        context.pass();
    }

    private static void selection(GameTestContext context) {
        WaterSheeperEntity boss = boss(context, 1, 2, 1);
        // Fountain phases wait for terrain discovery before their duration advances.
        context.set(SheeperConfig.WATER_COMBAT_RADIUS, 10.0D);
        context.set(SheeperConfig.WATER_ARENA_SEARCH_BUDGET, 10000);
        context.set(SheeperConfig.WATER_PROJECTILE_WEIGHT, 1.0D);
        context.set(SheeperConfig.WATER_SALVO_WEIGHT, 1.0D);
        context.set(SheeperConfig.WATER_FOUNTAIN_WEIGHT, 1.0D);
        context.set(SheeperConfig.WATER_PROJECTILE_DURATION_MIN, 3);
        context.set(SheeperConfig.WATER_PROJECTILE_DURATION_MAX, 3);
        context.set(SheeperConfig.WATER_PROJECTILE_INTERVAL, 1);
        context.set(SheeperConfig.WATER_FOUNTAIN_DURATION_MIN, 3);
        context.set(SheeperConfig.WATER_FOUNTAIN_DURATION_MAX, 3);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MIN, 2);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MAX, 2);
        context.player(16, 2, 8);
        boss.getRandom().setSeed(831L);
        // Equal defaults, percentage-style weights, and a disabled attack type.
        for (double[] weights : new double[][] { { 1, 1, 1 }, { 20, 30, 50 }, { 0, 1, 1 } }) {
            context.set(SheeperConfig.WATER_SALVO_WEIGHT, weights[0]);
            context.set(SheeperConfig.WATER_FOUNTAIN_WEIGHT, weights[1]);
            context.set(SheeperConfig.WATER_PROJECTILE_WEIGHT, weights[2]);
            int[] counts = new int[3];
            for (int sample = 0; sample < 90; sample++) {
                boss.tick();
                int type = boss.isPerformingSalvo() ? 0 : boss.getTarget() == null ? 1 : 2;
                counts[type]++;
                tick(boss, 2);
                context.expect(shots(context) == (type == 0 ? 2 : type == 1 ? 0 : 3),
                        "Only the selected attack may fire during each phase");
                tick(boss, 2);
                context.expect(!boss.isPerformingSalvo() && boss.getTarget() == null,
                        "Every attack type must complete the same rest before another selection");
                context.entities(WaterProjectile.class).forEach(Entity::discard);
            }
            double total = weights[0] + weights[1] + weights[2];
            for (int type = 0; type < counts.length; type++) {
                context.expect(weights[type] == 0 ? counts[type] == 0
                        : Math.abs(counts[type] / 90.0D - weights[type] / total) < 0.15D,
                        "Selection must follow relative weights and exclude zero weights: "
                                + java.util.Arrays.toString(counts));
            }
        }
        context.set(SheeperConfig.WATER_SALVO_WEIGHT, 0.0D);
        context.set(SheeperConfig.WATER_FOUNTAIN_WEIGHT, 0.0D);
        context.set(SheeperConfig.WATER_PROJECTILE_WEIGHT, 0.0D);
        tick(boss, 20);
        context.expect(shots(context) == 0 && !boss.isPerformingSalvo() && boss.getTarget() == null,
                "All zero weights must leave the boss idle without firing");
        context.set(SheeperConfig.WATER_SALVO_WEIGHT, 1.0D);
        boss.tick();
        context.expect(boss.isPerformingSalvo(),
                "Re-enabling an attack must immediately resume selection after all weights were zero");
        context.pass();
    }
}
