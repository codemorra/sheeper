package de.codemorra.sheeper.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Independent proximity timers, repeated pushes, and persistence. */
final class WaterProximityGameTests {
    private WaterProximityGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("water_proximity_push_and_reset", WaterProximityGameTests::proximity));
        tests.add(new SheeperGameTests.Scenario("water_proximity_push_reload_and_delays",
                WaterProximityGameTests::reload));
    }

    private static WaterSheeperEntity boss(GameTestContext context) {
        context.set(SheeperConfig.WATER_PROJECTILE_WEIGHT, 0.0D);
        context.set(SheeperConfig.WATER_FOUNTAIN_WEIGHT, 0.0D);
        context.set(SheeperConfig.WATER_SALVO_WEIGHT, 0.0D);
        context.set(SheeperConfig.WATER_MOVEMENT_RADIUS, 0.0D);
        context.set(SheeperConfig.WATER_SIZE, 1.0D);
        context.set(SheeperConfig.WATER_ARENA_SEARCH_BUDGET_PER_SECOND, (1) * 20);
        context.set(SheeperConfig.WATER_PROXIMITY_PUSH_DELAY_MIN, (4) / 20.0D);
        context.set(SheeperConfig.WATER_PROXIMITY_PUSH_DELAY_MAX, (4) / 20.0D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_STRENGTH, 0.8D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_UPWARD_STRENGTH, 0.2D);
        WaterSheeperEntity boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8);
        boss.getRandom().setSeed(831L);
        boss.setNoGravity(true);
        boss.tick(); // Complete the spawn push before any players enter.
        boss.setPos(context.point(14, 1, 8));
        return context.readyForCombat(boss);
    }

    private static void tick(WaterSheeperEntity boss, int count) {
        for (int i = 0; i < count; i++) {
            boss.tick();
        }
    }

    private static TestPlayer player(GameTestContext context, int x, int y, int z) {
        TestPlayer player = context.player(x, y, z);
        player.setDeltaMovement(Vec3.ZERO);
        player.motionPackets.clear();
        return player;
    }

    private static void proximity(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        TestPlayer first = player(context, 13, 1, 8);
        TestPlayer high = player(context, 14, 5, 8);
        TestPlayer outside = player(context, 18, 1, 8);
        TestPlayer creative = player(context, 15, 1, 8);
        creative.setGameMode(GameType.CREATIVE);
        TestPlayer spectator = player(context, 15, 1, 8);
        spectator.setGameMode(GameType.SPECTATOR);
        tick(boss, 2);
        TestPlayer second = player(context, 17, 1, 8); // Exactly on the default radius.
        tick(boss, 2);
        context.expect(first.motionPackets.size() == 1 && second.motionPackets.isEmpty(),
                "Each player's continuous proximity must use an independent timer: first="
                        + first.motionPackets.size() + ", second=" + second.motionPackets.size()
                        + ", boss=" + boss.position() + ", timers="
                        + boss.saveWithoutId(new CompoundTag()).getList("WaterProximityPush", 10));
        context.near(first.getDeltaMovement().x, -0.8D, "Push direction must use the current boss position");
        context.near(first.getDeltaMovement().y, 0.2D, "Proximity must reuse the spawn push strengths");
        first.standAt(context.point(20, 1, 8));
        boss.tick();
        first.standAt(context.point(13, 1, 8));
        boss.tick();
        context.expect(second.motionPackets.size() == 1, "The radius boundary must count as nearby");
        second.standAt(context.point(20, 1, 8));
        tick(boss, 2);
        context.expect(first.motionPackets.size() == 1, "Leaving proximity must reset accumulated time");
        boss.tick();
        context.expect(first.motionPackets.size() == 2, "Re-entry must require a full new delay");
        tick(boss, 4);
        context.expect(first.motionPackets.size() == 3, "Remaining nearby must eventually trigger another push");
        for (TestPlayer excluded : List.of(high, outside, creative, spectator)) {
            context.expect(excluded.motionPackets.isEmpty(), "Out-of-range and excluded modes must not be pushed");
        }
        context.expect(first.damageEvents.isEmpty() && second.damageEvents.isEmpty(),
                "Proximity pushes must deal no direct damage");
        context.pass();
    }

    private static void reload(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        TestPlayer player = player(context, 13, 1, 8);
        tick(boss, 2);
        context.expect(player.motionPackets.isEmpty(), "The initial proximity delay must not have expired");
        CompoundTag saved = boss.saveWithoutId(new CompoundTag());
        boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        WaterSheeperEntity restored = ModEntities.WATER_SHEEPER.get().create(context.level);
        restored.load(saved);
        restored.setNoAi(true);
        tick(restored, 10);
        context.expect(player.motionPackets.isEmpty(), "NoAI must pause the saved proximity timer: noAI="
                + restored.isNoAi() + ", packets=" + player.motionPackets.size() + ", saved="
                + saved.getList("WaterProximityPush", 10) + ", restored="
                + restored.saveWithoutId(new CompoundTag()).getList("WaterProximityPush", 10));
        restored.setNoAi(false);
        restored.tick();
        context.expect(player.motionPackets.isEmpty(), "Reload must retain the remaining delay");
        restored.tick();
        context.expect(player.motionPackets.size() == 1, "Reload must resume proximity without repeating spawn push");

        context.set(SheeperConfig.WATER_PROXIMITY_PUSH_RADIUS, 0.0D);
        tick(restored, 10);
        context.expect(player.motionPackets.size() == 1, "Radius zero must disable proximity pushes");
        context.set(SheeperConfig.WATER_PROXIMITY_PUSH_RADIUS, 3.0D);
        tick(restored, 3);
        context.expect(player.motionPackets.size() == 1, "Re-enabling proximity must start a fresh timer");
        restored.tick();
        context.expect(player.motionPackets.size() == 2, "Re-enabled proximity must push after the full delay");

        context.set(SheeperConfig.WATER_PROXIMITY_PUSH_DELAY_MIN, (3) / 20.0D);
        context.set(SheeperConfig.WATER_PROXIMITY_PUSH_DELAY_MAX, (5) / 20.0D);
        restored.getRandom().setSeed(831L);
        Set<Integer> delays = new HashSet<>();
        // The first countdown was sampled before changing the config.
        tick(restored, 4);
        for (int sample = 0; sample < 12; sample++) {
            int before = player.motionPackets.size();
            int elapsed = 0;
            while (player.motionPackets.size() == before && elapsed < 6) {
                restored.tick();
                elapsed++;
            }
            context.expect(elapsed >= 3 && elapsed <= 5, "Random delays must stay within the configured bounds");
            delays.add(elapsed);
        }
        context.expect(delays.size() > 1, "The delay must be sampled anew after each push");
        context.pass();
    }
}
