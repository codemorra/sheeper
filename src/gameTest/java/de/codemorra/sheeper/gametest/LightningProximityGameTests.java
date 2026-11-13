package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Shared proximity timing, damage/knockback and persistence across attack
 * recovery.
 */
final class LightningProximityGameTests {
    private LightningProximityGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("lightning_proximity_shared_timer_and_reset",
                LightningProximityGameTests::sharedTimer));
        tests.add(new SheeperGameTests.Scenario("lightning_proximity_reload_and_recovery",
                LightningProximityGameTests::reload));
    }

    private static LightningSheeperEntity boss(GameTestContext context) {
        context.difficulty(Difficulty.NORMAL);
        context.set(SheeperConfig.LIGHTNING_SIZE, 1.0D);
        context.set(SheeperConfig.LIGHTNING_MOVEMENT_RADIUS, 0.0D);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_DURATION, 0.0D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_WEIGHT, 0.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_WEIGHT, 0.0D);
        context.set(SheeperConfig.LIGHTNING_RING_WEIGHT, 0.0D);
        context.set(SheeperConfig.LIGHTNING_PROXIMITY_PUSH_DELAY_MIN, 1.0D);
        context.set(SheeperConfig.LIGHTNING_PROXIMITY_PUSH_DELAY_MAX, 1.0D);
        LightningSheeperEntity boss = context.spawn(ModEntities.LIGHTNING_SHEEPER.get(), 12, 1, 12);
        boss.setNoAi(true);
        for (int tick = 0; tick < 10; tick++) {
            boss.tick();
        }
        boss.setNoAi(false);
        return boss;
    }

    private static TestPlayer player(GameTestContext context, Vec3 position) {
        TestPlayer player = context.player(12, 0, 12);
        player.standAt(position);
        return player;
    }

    private static long pushCount(TestPlayer player) {
        // Tracking packets for other entities are also received when test players join.
        return player.motionPackets.stream().filter(packet -> packet.getId() == player.getId()).count();
    }

    private static void tick(LightningSheeperEntity boss, int count, TestPlayer... players) {
        for (int tick = 0; tick < count; tick++) {
            for (TestPlayer player : players) {
                player.tick();
                // The stationary fake client removes prior motion so the discharge impulse is
                // measurable.
                player.setDeltaMovement(Vec3.ZERO);
            }
            boss.tick();
        }
    }

    private static void sharedTimer(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        Vec3 center = boss.position();
        TestPlayer first = player(context, center.add(2, 0, 0));
        TestPlayer second = player(context, center.add(-5, 0, 0));
        TestPlayer outside = player(context, center.add(0, 0, 5));
        TestPlayer creative = player(context, center.add(0, 0, 2));
        creative.setGameMode(GameType.CREATIVE);
        tick(boss, 8, first, second);
        first.standAt(center.add(5, 0, 0));
        second.standAt(center.add(-2, 0, 0));
        tick(boss, 11, first, second);
        context.expect(first.damageEvents.isEmpty() && second.damageEvents.isEmpty(),
                "Changing nearby players must neither discharge early nor restart the shared delay");
        first.standAt(center.add(2, 0, 0));
        tick(boss, 1, first, second);
        context.near(first.getHealth(), 14, "Discharge must deal three hearts to every nearby participant");
        context.near(second.getHealth(), 14, "Player handover must preserve charge and include a recent arrival");
        context.near(first.getDeltaMovement().x, 3.9D, "The first player must be pushed away from the boss");
        context.near(second.getDeltaMovement().x, -3.9D,
                "The opposite player must be pushed in the opposite direction");
        context.near(first.getDeltaMovement().y, 1.0D, "The configured upward impulse must reach the server player");
        context.expect(pushCount(first) == 1 && pushCount(second) == 1,
                "Both players must receive the discharge motion packet");

        tick(boss, 7, first, second);
        first.standAt(center.add(5, 0, 0));
        second.standAt(center.add(-5, 0, 0));
        tick(boss, 1, first, second); // Only the creative player remains near the boss.
        first.standAt(center.add(2, 0, 0));
        tick(boss, 19, first, second);
        context.near(first.getHealth(), 14, "An empty eligible radius must discard the previous partial charge");
        tick(boss, 1, first, second);
        context.near(first.getHealth(), 8, "Re-entry must require a full delay before another discharge");
        context.near(second.getHealth(), 14, "A player who left the radius must not receive the next hit");
        for (TestPlayer excluded : List.of(outside, creative)) {
            context.expect(excluded.damageEvents.isEmpty() && pushCount(excluded) == 0,
                    "Out-of-range and creative players must receive neither damage nor knockback");
        }
        boss.discard();
        context.pass();
    }

    private static void reload(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        context.set(SheeperConfig.LIGHTNING_ATTACK_INTERVAL_MIN, 0.05D);
        context.set(SheeperConfig.LIGHTNING_ATTACK_INTERVAL_MAX, 0.05D);
        context.set(SheeperConfig.LIGHTNING_FRONT_WEIGHT, 1.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_DURATION_MIN, 0.3D);
        context.set(SheeperConfig.LIGHTNING_FRONT_DURATION_MAX, 0.3D);
        context.set(SheeperConfig.LIGHTNING_FRONT_DAMAGE, 0.0D);
        context.set(SheeperConfig.LIGHTNING_ATTACK_PAUSE_MIN, 5.0D);
        context.set(SheeperConfig.LIGHTNING_ATTACK_PAUSE_MAX, 5.0D);
        TestPlayer player = player(context, boss.position().add(2, 0, 0));
        tick(boss, 8, player); // The short lane attack has ended, but the proximity charge continues.
        context.expect(player.damageEvents.isEmpty(), "A phase change must not trigger an early discharge");
        CompoundTag saved = boss.saveWithoutId(new CompoundTag());
        boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        LightningSheeperEntity restored = ModEntities.LIGHTNING_SHEEPER.get().create(context.level);
        restored.load(saved);
        restored.setNoAi(true);
        tick(restored, 20, player);
        context.expect(player.damageEvents.isEmpty(), "NoAI must pause the saved proximity charge");
        restored.setNoAi(false);
        tick(restored, 11, player);
        context.expect(player.damageEvents.isEmpty(), "Loading must retain the remaining charge time");
        tick(restored, 1, player);
        context.near(player.getHealth(), 14, "The saved charge must discharge on time during attack recovery");
        context.expect(pushCount(player) == 1, "Resuming must produce exactly one defensive push");
        restored.discard();
        context.pass();
    }
}
