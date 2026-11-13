package de.codemorra.sheeper.gametest;

import java.util.ArrayList;
import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Three spawn waves, their delayed radius-limited pushes and reload continuity.
 */
final class LightningSpawnGameTests {
    private LightningSpawnGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("lightning_spawn_three_waves", LightningSpawnGameTests::waves));
        tests.add(new SheeperGameTests.Scenario("lightning_spawn_radius_and_reload", LightningSpawnGameTests::reload));
    }

    private static LightningSheeperEntity boss(GameTestContext context) {
        context.set(SheeperConfig.LIGHTNING_SIZE, 1.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_RADIUS, 8.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE, 6.0D);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_DURATION, 4.0D);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_WAVE_COUNT, 3);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_WAVE_INTERVAL, 1.0D);
        context.set(SheeperConfig.LIGHTNING_SPAWN_PUSH_RADIUS, 3.0D);
        LightningSheeperEntity boss = context.spawn(ModEntities.LIGHTNING_SHEEPER.get(), 12, 1, 12);
        boss.setNoAi(true);
        boss.setNoGravity(true);
        return boss;
    }

    private static TestPlayer player(GameTestContext context, Vec3 position) {
        TestPlayer player = context.player(12, 1, 12);
        player.standAt(position);
        return player;
    }

    private static long pushes(TestPlayer player) {
        return player.motionPackets.stream().filter(packet -> packet.getId() == player.getId()).count();
    }

    private static LightningSheeperEntity restore(GameTestContext context, LightningSheeperEntity boss) {
        CompoundTag saved = boss.saveWithoutId(new CompoundTag());
        boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        LightningSheeperEntity restored = ModEntities.LIGHTNING_SHEEPER.get().create(context.level);
        restored.load(saved);
        return restored;
    }

    private static void waves(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        TestPlayer player = player(context, center.add(2, 0, 0));
        List<Integer> hits = new ArrayList<>();
        for (int elapsed = 1; elapsed <= 140; elapsed++) {
            player.particlePackets.clear();
            long before = pushes(player);
            // Existing momentum must not turn the spawn launch into a higher or sideways
            // flight.
            player.setDeltaMovement(3, 4, -2);
            boss.tick();
            if (pushes(player) > before) {
                hits.add(elapsed);
                context.near(player.getDeltaMovement().x, 1.5D, "Each wave must launch away from the arena center");
                context.near(player.getDeltaMovement().y, 0.55D, "Each wave must use the safe upward launch speed");
                context.near(player.getDeltaMovement().z, 0, "Existing sideways momentum must be replaced");
            }
            if (elapsed < 120) {
                context.expect(!boss.isCombatReady() && boss.isInvulnerableTo(DamageSource.GENERIC),
                        "The boss must remain protected until the third wave reaches the arena edge");
            } else {
                context.expect(boss.isCombatReady() && !boss.isInvulnerableTo(DamageSource.GENERIC),
                        "Combat must be released exactly after the third wave finishes");
            }
            if (elapsed == 50) {
                for (double radius : new double[] { 5, 3, 1 }) {
                    context.expect(
                            player.particlePackets.stream()
                                    .anyMatch(packet -> packet.getParticle() == ParticleTypes.ELECTRIC_SPARK
                                            && Math.abs(Math.hypot(packet.getX() - center.x, packet.getZ() - center.z)
                                                    - radius) < 1.0E-6D),
                            "All three overlapping waves must be visible at their independently expanding radii");
                }
            }
        }
        context.expect(hits.equals(List.of(23, 43, 63)),
                "Each wave must push once after its contact delay, with one second between waves: " + hits);
        context.expect(player.damageEvents.isEmpty(), "Spawn waves must deal no direct damage");
        boss.discard();
        context.pass();
    }

    private static void reload(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        context.set(SheeperConfig.LIGHTNING_SPAWN_PUSH_DELAY, 0.3D);
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        TestPlayer boundary = player(context, center.add(3, 0, 0));
        TestPlayer leaving = player(context, center.add(1, 0, 0));
        TestPlayer outside = player(context, center.add(4, 0, 0));
        TestPlayer above = player(context, center.add(0, 4, 0));
        TestPlayer creative = player(context, center.add(0, 0, 2));
        creative.setGameMode(GameType.CREATIVE);
        TestPlayer spectator = player(context, center.add(0, 0, -2));
        spectator.setGameMode(GameType.SPECTATOR);
        TestPlayer late = null;
        List<Integer> hits = new ArrayList<>();
        for (int elapsed = 1; elapsed <= 145; elapsed++) {
            if (elapsed == 12) {
                leaving.standAt(center.add(4, 0, 0));
            }
            if (elapsed == 65) {
                // The third wave's push will still be pending when preparation finishes.
                context.set(SheeperConfig.LIGHTNING_SPAWN_PUSH_DELAY, 3.0D);
            }
            if (elapsed == 121) {
                late = player(context, center.add(2, 0, 0));
            }
            long before = pushes(boundary);
            boss.tick();
            if (pushes(boundary) > before) {
                hits.add(elapsed);
            }
            if (elapsed == 35 || elapsed == 40 || elapsed == 125) {
                boss = restore(context, boss);
            }
            if (elapsed == 35) {
                context.set(SheeperConfig.LIGHTNING_PREPARATION_DURATION, 0.05D);
                context.set(SheeperConfig.LIGHTNING_PREPARATION_WAVE_COUNT, 1);
                context.set(SheeperConfig.LIGHTNING_PREPARATION_WAVE_INTERVAL, 10.0D);
            }
            context.expect(boss.isCombatReady() == (elapsed >= 120),
                    "Reload and config changes must retain the captured three-wave preparation timing");
        }
        context.expect(hits.equals(List.of(36, 56, 130)),
                "Pending and completed contacts must survive loading, including a push after preparation: " + hits);
        context.near(boundary.getHealth(), 20, "All three spawn pushes must remain damage-free");
        for (TestPlayer excluded : List.of(leaving, outside, above, creative, spectator, late)) {
            context.expect(pushes(excluded) == 0 && excluded.damageEvents.isEmpty(),
                    "Leaving, out-of-range, excluded modes and post-preparation arrivals must not be pushed");
        }
        boss.discard();
        context.pass();
    }
}
