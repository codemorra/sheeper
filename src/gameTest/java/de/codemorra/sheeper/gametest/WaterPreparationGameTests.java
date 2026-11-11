package de.codemorra.sheeper.gametest;

import java.util.List;
import java.util.UUID;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterProjectile;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Preparation protection, contact-driven spawn waves and reload continuity. */
final class WaterPreparationGameTests {
    private WaterPreparationGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("water_preparation_and_combat_start",
                WaterPreparationGameTests::preparation));
        tests.add(new SheeperGameTests.Scenario("water_spawn_wave_contact_and_delay", WaterPreparationGameTests::wave));
        tests.add(new SheeperGameTests.Scenario("water_spawn_wave_reload", WaterPreparationGameTests::reload));
        tests.add(new SheeperGameTests.Scenario("water_spawn_wave_radius", WaterPreparationGameTests::radius));
    }

    private static WaterSheeperEntity boss(GameTestContext context) {
        context.set(SheeperConfig.WATER_SIZE, 1.0D);
        context.set(SheeperConfig.WATER_COMBAT_RADIUS, 10.0D);
        context.set(SheeperConfig.WATER_ARENA_SEARCH_BUDGET, 1);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_DELAY, 10);
        context.set(SheeperConfig.WATER_SPAWN_WAVE_HEIGHT, 10.0D);
        context.set(SheeperConfig.WATER_SPAWN_WAVE_HORIZONTAL_STRENGTH, 0.2D);
        WaterSheeperEntity boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8);
        boss.setNoAi(true);
        return boss;
    }

    private static TestPlayer player(GameTestContext context, int x) {
        TestPlayer player = context.player(x, 1, 8);
        player.setDeltaMovement(Vec3.ZERO);
        player.motionPackets.clear();
        return player;
    }

    private static void tick(WaterSheeperEntity boss, int ticks) {
        for (int i = 0; i < ticks; i++) {
            boss.tick();
        }
    }

    private static long launches(TestPlayer player) {
        return player.motionPackets.stream().filter(packet -> packet.getId() == player.getId()).count();
    }

    private static void wave(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        TestPlayer touching = player(context, 9);
        TestPlayer foreign = player(context, 12);
        TestPlayer vanilla = player(context, 14);
        TestPlayer creative = player(context, 10);
        creative.setGameMode(GameType.CREATIVE);
        boss.tick();
        context.expect(launches(touching) == 0, "Spawning must not push players before water contact");
        WaterArenaData data = WaterArenaData.get(context.level);
        data.claim(context.level, boss.getUUID(), context.pos(9, 1, 8));
        data.claim(context.level, boss.getUUID(), context.pos(10, 1, 8));
        data.claim(context.level, UUID.randomUUID(), context.pos(12, 1, 8));
        context.setBlock(14, 1, 8, Blocks.WATER);
        boss.tick(); // First contact; the full ten-tick delay starts here.
        tick(boss, 4);
        data.finishGeneration(boss.getUUID());
        tick(boss, 5);
        context.expect(launches(touching) == 0, "Water contact must wait the full configured delay: configured="
                + SheeperConfig.WATER_SPAWN_PUSH_DELAY.get() + ", state="
                + boss.saveWithoutId(new CompoundTag()).getList("WaterWaveDelays", 10));
        touching.setDeltaMovement(3.0D, 4.0D, -2.0D);
        boss.tick();
        context.expect(launches(touching) == 1,
                "A pending wave must still push after preparation has finished");
        context.near(touching.getDeltaMovement().x, 0.2D, "The water wave must give only a small outward velocity");
        context.near(touching.getDeltaMovement().z, 0.0D, "Existing sideways velocity must not extend the launch");
        assertLaunchHeight(context, touching, 10.0D);
        for (TestPlayer excluded : List.of(foreign, vanilla, creative)) {
            context.expect(launches(excluded) == 0,
                    "Foreign water, vanilla water and Creative must not trigger the wave");
        }
        TestPlayer late = player(context, 10);
        tick(boss, 15);
        context.expect(launches(touching) == 1 && launches(late) == 0,
                "The spawn wave must not repeat or accept new contacts after combat begins");
        context.expect(touching.damageEvents.isEmpty(), "The water wave must deal no direct damage");
        context.pass();
    }

    private static WaterSheeperEntity restore(GameTestContext context, WaterSheeperEntity boss) {
        CompoundTag saved = boss.saveWithoutId(new CompoundTag());
        boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        WaterSheeperEntity restored = ModEntities.WATER_SHEEPER.get().create(context.level);
        restored.load(saved);
        return restored;
    }

    private static void radius(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        context.set(SheeperConfig.WATER_SPAWN_WAVE_RADIUS, 3.0D);
        TestPlayer boundary = player(context, 11);
        TestPlayer outside = player(context, 12);
        TestPlayer leaving = player(context, 9);
        WaterArenaData data = WaterArenaData.get(context.level);
        for (int x : new int[] { 9, 11, 12 }) {
            context.expect(data.claim(context.level, boss.getUUID(), context.pos(x, 1, 8)),
                    "The radius scene must contain owned boss water at each player");
        }
        boss.tick();
        leaving.standAt(context.point(12, 1, 8));
        tick(boss, 10);
        context.expect(launches(boundary) == 1,
                "Contact exactly on the configured radius must trigger the spawn wave");
        context.expect(launches(outside) == 0, "Boss water outside the spawn radius must not launch players");
        context.expect(launches(leaving) == 0, "Leaving the radius during the delay must cancel the launch");
        context.pass();
    }

    private static void assertLaunchHeight(GameTestContext context, TestPlayer player, double height) {
        double rise = 0;
        double velocity = player.getDeltaMovement().y;
        for (int tick = 0; tick < 1000 && velocity > 0; tick++) {
            rise += velocity;
            velocity = (velocity - 0.08D) * 0.98D;
        }
        context.expect(rise > height - 0.1D && rise <= height + 0.00001D,
                "The launch must reach the configured dry-air apex; water drag may lower it");
    }

    private static void reload(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        context.set(SheeperConfig.WATER_SPAWN_WAVE_HEIGHT, 4.0D);
        context.set(SheeperConfig.WATER_SPAWN_WAVE_HORIZONTAL_STRENGTH, 0.0D);
        boss = restore(context, boss); // Save before its first tick.
        TestPlayer player = player(context, 9);
        WaterArenaData.get(context.level).claim(context.level, boss.getUUID(), context.pos(9, 1, 8));
        boss.tick();
        tick(boss, 2);
        boss = restore(context, boss);
        tick(boss, 7);
        context.expect(launches(player) == 0, "Reload must preserve the remaining contact delay");
        boss.tick();
        context.expect(launches(player) == 1, "A saved water contact must push exactly once");
        context.near(player.getDeltaMovement().horizontalDistanceSqr(), 0.0D,
                "Zero horizontal strength must allow a purely vertical launch");
        assertLaunchHeight(context, player, 4.0D);
        boss = restore(context, boss);
        tick(boss, 15);
        context.expect(launches(player) == 1, "Completed contacts must survive reload without another push");
        context.pass();
    }

    private static void preparation(GameTestContext context) {
        context.set(SheeperConfig.WATER_SIZE, 1.0D);
        context.set(SheeperConfig.WATER_COMBAT_RADIUS, 2.0D);
        context.set(SheeperConfig.WATER_MOVEMENT_RADIUS, 1.0D);
        context.set(SheeperConfig.WATER_ARENA_SEARCH_BUDGET, 1);
        context.set(SheeperConfig.WATER_ARENA_HEIGHT_RANGE, 0);
        context.set(SheeperConfig.WATER_ARENA_BLOCK_BUDGET, 2);
        context.set(SheeperConfig.WATER_ARENA_FLOOD_WAVE_TICKS, 2);
        context.set(SheeperConfig.WATER_PROJECTILE_WEIGHT, 1.0D);
        context.set(SheeperConfig.WATER_FOUNTAIN_WEIGHT, 0.0D);
        context.set(SheeperConfig.WATER_SALVO_WEIGHT, 0.0D);
        context.set(SheeperConfig.WATER_PROJECTILE_INTERVAL, 1);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_STRENGTH, 0.0D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_UPWARD_STRENGTH, 0.0D);
        context.set(SheeperConfig.WATER_SPAWN_WAVE_HEIGHT, 0.0D);
        context.set(SheeperConfig.WATER_SPAWN_WAVE_HORIZONTAL_STRENGTH, 0.0D);
        WaterSheeperEntity[] boss = { context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8) };
        Vec3 origin = boss[0].position();
        TestPlayer target = player(context, 10);
        context.at(1, () -> {
            context.expect(!boss[0].isCombatReady() && !boss[0].hurt(DamageSource.GENERIC, 1),
                    "Preparation must protect the boss from damage");
            context.expect(!boss[0].fireWaterProjectile(target), "Preparation must reject direct projectile launches");
        });
        context.at(10, () -> {
            boss[0] = restore(context, boss[0]);
            context.expect(context.level.addFreshEntity(boss[0]), "The preparing boss must reload into the world");
        });
        for (int tick = 2; tick <= 149; tick++) {
            context.at(tick, () -> {
                if (!boss[0].isCombatReady()) {
                    context.expect(boss[0].position().distanceToSqr(origin) < 0.01D
                            && context.entities(WaterProjectile.class).isEmpty(),
                            "Preparation must not allow movement or automatic attacks");
                }
            });
        }
        context.at(150, () -> {
            context.expect(WaterArenaData.get(context.level).isGenerated(boss[0].getUUID()) && boss[0].isCombatReady(),
                    "Completing initial placement must release combat");
            context.expect(!boss[0].isInvulnerableTo(DamageSource.GENERIC), "Prepared bosses must become vulnerable");
            context.expect(!context.entities(WaterProjectile.class).isEmpty() || !target.damageEvents.isEmpty(),
                    "Automatic projectile attacks must begin after preparation");
            context.pass();
        });
    }
}
