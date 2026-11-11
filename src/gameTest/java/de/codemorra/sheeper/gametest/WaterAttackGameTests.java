package de.codemorra.sheeper.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterProjectile;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * Attack phases and shared rest periods, observed through projectile launches.
 */
final class WaterAttackGameTests {
    private WaterAttackGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("water_attack_duration_and_rest", WaterAttackGameTests::phases));
        tests.add(
                new SheeperGameTests.Scenario("water_attack_random_durations", WaterAttackGameTests::randomDurations));
        tests.add(new SheeperGameTests.Scenario("water_attack_pause_and_reload", WaterAttackGameTests::reload));
        tests.add(new SheeperGameTests.Scenario("water_attack_focus_and_reload", WaterAttackGameTests::focus));
    }

    private static WaterSheeperEntity boss(GameTestContext context, int duration, int rest, int interval) {
        context.set(SheeperConfig.WATER_FOUNTAIN_CHANCE, 0.0D);
        context.set(SheeperConfig.WATER_SIZE, 1.0D);
        context.set(SheeperConfig.WATER_MOVEMENT_RADIUS, 0.0D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_STRENGTH, 0.0D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_UPWARD_STRENGTH, 0.0D);
        context.set(SheeperConfig.WATER_ARENA_SEARCH_BUDGET, 1);
        context.set(SheeperConfig.WATER_PROJECTILE_DURATION_MIN, duration);
        context.set(SheeperConfig.WATER_PROJECTILE_DURATION_MAX, duration);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MIN, rest);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MAX, rest);
        context.set(SheeperConfig.WATER_PROJECTILE_INTERVAL, interval);
        return context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 2, 8);
    }

    private static int shots(GameTestContext context) {
        return context.entities(WaterProjectile.class).size();
    }

    private static void tick(WaterSheeperEntity boss, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            boss.tick();
        }
    }

    private static void phases(GameTestContext context) {
        WaterSheeperEntity boss = boss(context, 6, 3, 2);
        context.player(16, 2, 8);
        // Reversed bounds must use the minimum, matching movement intervals.
        context.set(SheeperConfig.WATER_PROJECTILE_DURATION_MAX, 1);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MAX, 1);
        for (int tick = 1; tick <= 6; tick++) {
            boss.tick();
            context.expect(shots(context) == tick / 2, "Only full shot intervals may launch a projectile");
        }
        tick(boss, 3);
        context.expect(shots(context) == 3, "Rest must prevent launches and retain already flying shots");
        boss.tick();
        context.expect(shots(context) == 3, "Each new phase must wait a full interval before firing");
        boss.tick();
        context.expect(shots(context) == 4, "The same attack must be able to repeat after rest");
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MIN, 0);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MAX, 0);
        tick(boss, 4);
        context.expect(shots(context) == 6, "The second phase must also honor its complete duration");
        tick(boss, 2);
        context.expect(shots(context) == 7, "Zero rest must add no extra idle tick between phases");
        context.pass();
    }

    private static void randomDurations(GameTestContext context) {
        WaterSheeperEntity boss = boss(context, 3, 2, 1);
        context.set(SheeperConfig.WATER_PROJECTILE_DURATION_MAX, 7);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MAX, 5);
        context.player(16, 2, 8);
        boss.getRandom().setSeed(12345L);
        Set<Integer> attacks = new HashSet<>();
        Set<Integer> pauses = new HashSet<>();
        boolean attacking = true;
        int run = 0;
        for (int tick = 0; tick < 200; tick++) {
            int before = shots(context);
            boss.tick();
            boolean fired = shots(context) > before;
            if (fired != attacking) {
                context.expect(run >= (attacking ? 3 : 2) && run <= (attacking ? 7 : 5),
                        "Every completed attack/rest must remain within its configured inclusive bounds");
                (attacking ? attacks : pauses).add(run);
                attacking = fired;
                run = 0;
            }
            run++;
        }
        context.expect(attacks.size() > 1 && pauses.size() > 1,
                "Both attack and rest durations must be sampled again rather than fixed across cycles");
        context.pass();
    }

    private static WaterSheeperEntity reload(GameTestContext context, WaterSheeperEntity boss) {
        CompoundTag saved = boss.saveWithoutId(new CompoundTag());
        boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        WaterSheeperEntity restored = ModEntities.WATER_SHEEPER.get().create(context.level);
        restored.load(saved);
        return restored;
    }

    private static void focus(GameTestContext context) {
        WaterSheeperEntity boss = boss(context, 6, 2, 2);
        TestPlayer first = context.player(16, 2, 8);
        TestPlayer second = context.player(8, 2, 16);
        tick(boss, 8);
        context.expect(boss.getTarget() == null, "A completed rest must still have no focus");
        boss.tick();
        context.expect(boss.getTarget() == second, "The second attack must focus the next participant");
        boss = reload(context, boss);
        boss.tick();
        context.expect(boss.getTarget() == second,
                "Reload must restore the same focus rather than select a new player");
        List<WaterProjectile> projectiles = context.entities(WaterProjectile.class);
        context.expect(projectiles.size() == 4 && projectiles.get(3).getDeltaMovement().z > 0.0D,
                "Shots after reload must continue aiming at the saved participant");
        second.setHealth(0.0F);
        boss.tick();
        context.expect(boss.getTarget() == null && shots(context) == 4,
                "Target death must abort the attack immediately without choosing a replacement");
        tick(boss, 2);
        context.expect(boss.getTarget() == null, "An aborted attack must complete rest before refocusing");
        boss.tick();
        context.expect(boss.getTarget() == first, "A new attack may acquire the remaining participant after rest");
        context.pass();
    }

    private static void reload(GameTestContext context) {
        WaterSheeperEntity boss = boss(context, 8, 5, 3);
        TestPlayer player = context.player(16, 2, 8);
        tick(boss, 4);
        context.expect(shots(context) == 1, "The initial phase must fire on its configured interval");
        boss = reload(context, boss);
        boss.tick();
        context.expect(shots(context) == 1, "Reload must preserve the remaining shot interval");
        boss.tick();
        context.expect(shots(context) == 2, "Reload must resume the pending shot without resetting its interval");
        tick(boss, 4);
        context.expect(shots(context) == 2, "Reload must retain the phase duration and enter rest on time");
        // Two of the five rest ticks have elapsed; reload must retain the other three.
        boss = reload(context, boss);
        boss.setNoAi(true);
        tick(boss, 10);
        boss.setNoAi(false);
        player.standAt(context.point(100, 2, 8));
        tick(boss, 10);
        player.standAt(context.point(16, 2, 8));
        tick(boss, 3);
        context.expect(shots(context) == 2, "Reload, NoAI, and absent players must not skip the remaining rest");
        tick(boss, 2);
        context.expect(shots(context) == 2, "A new phase must start with a fresh shot interval after rest");
        boss.tick();
        context.expect(shots(context) == 3, "Combat must resume normally after the saved rest period");
        context.pass();
    }
}
