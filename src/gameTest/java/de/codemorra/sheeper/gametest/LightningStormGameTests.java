package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.entity.LightningStrikeEffect;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Core storm flow, persistent player cooldowns, and zone damage with a shared
 * phase tail.
 */
final class LightningStormGameTests {
    private LightningStormGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(
                new SheeperGameTests.Scenario("lightning_storm_rounds_and_warnings", LightningStormGameTests::rounds));
        tests.add(new SheeperGameTests.Scenario("lightning_storm_player_cooldown_reload",
                LightningStormGameTests::cooldown));
        tests.add(
                new SheeperGameTests.Scenario("lightning_storm_zone_damage_and_tail", LightningStormGameTests::zones));
    }

    private static LightningSheeperEntity boss(GameTestContext context) {
        context.set(SheeperConfig.LIGHTNING_SIZE, 1.0D);
        context.set(SheeperConfig.LIGHTNING_PROXIMITY_PUSH_RADIUS, 0.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_RADIUS, 10.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE, 3.0D);
        context.set(SheeperConfig.LIGHTNING_MOVEMENT_RADIUS, 0.0D);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_DURATION, 0.0D);
        context.set(SheeperConfig.LIGHTNING_ATTACK_PAUSE_MIN, 0.05D);
        context.set(SheeperConfig.LIGHTNING_ATTACK_PAUSE_MAX, 0.05D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_WEIGHT, 1.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_WEIGHT, 0.0D);
        context.set(SheeperConfig.LIGHTNING_RING_WEIGHT, 0.0D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_DURATION_MIN, 3.0D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_DURATION_MAX, 3.0D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_INTERVAL_MIN, 0.05D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_INTERVAL_MAX, 0.05D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_PLAYER_COOLDOWN, 5.0D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_ARENA_COUNT, 0);
        context.set(SheeperConfig.LIGHTNING_STRIKE_WARNING, 0.2D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_RADIUS, 1.5D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_DAMAGE, 0.0D);
        context.set(SheeperConfig.LIGHTNING_FOLLOWUP_INNER_RADIUS_MIN, 2.0D);
        context.set(SheeperConfig.LIGHTNING_FOLLOWUP_INNER_RADIUS_MAX, 3.0D);
        context.set(SheeperConfig.LIGHTNING_FOLLOWUP_OUTER_RADIUS_MIN, 4.0D);
        context.set(SheeperConfig.LIGHTNING_FOLLOWUP_OUTER_RADIUS_MAX, 5.0D);
        context.set(SheeperConfig.LIGHTNING_AFTERSHOCK_DAMAGE, 0.0D);
        context.set(SheeperConfig.LIGHTNING_AFTERSHOCK_AFTER, 1.0D);
        LightningSheeperEntity boss = context.spawn(ModEntities.LIGHTNING_SHEEPER.get(), 12, 1, 12);
        boss.setNoAi(true);
        tick(boss, 10);
        boss.setNoAi(false);
        boss.getRandom().setSeed(571L);
        return boss;
    }

    private static Vec3 groundPoint(GameTestContext context, LightningSheeperEntity boss, int x, int z) {
        Vec3 point = context.point(x, 0, z);
        return new Vec3(point.x, boss.getY(), point.z);
    }

    private static TestPlayer player(GameTestContext context, LightningSheeperEntity boss) {
        TestPlayer player = context.player(10, 0, 12);
        player.standAt(groundPoint(context, boss, 10, 12));
        return player;
    }

    private static void tick(LightningSheeperEntity boss, int count) {
        for (int index = 0; index < count; index++) {
            boss.tick();
        }
    }

    private static List<ClientboundLevelParticlesPacket> warnings(TestPlayer player) {
        return player.particlePackets.stream().filter(packet -> packet.getParticle() == ParticleTypes.END_ROD).toList();
    }

    private static List<ClientboundLevelParticlesPacket> zoneParticles(TestPlayer player) {
        return player.particlePackets.stream().filter(packet -> packet.getParticle() instanceof DustParticleOptions)
                .toList();
    }

    private static Vec3 point(ClientboundLevelParticlesPacket packet) {
        return new Vec3(packet.getX(), packet.getY() - 0.15D, packet.getZ());
    }

    private static LightningSheeperEntity restore(GameTestContext context, LightningSheeperEntity boss) {
        CompoundTag saved = boss.saveWithoutId(new CompoundTag());
        boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        LightningSheeperEntity restored = ModEntities.LIGHTNING_SHEEPER.get().create(context.level);
        restored.load(saved);
        return restored;
    }

    private static int bolts(GameTestContext context) {
        return context.entities(LightningStrikeEffect.class).size();
    }

    private static void rounds(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        TestPlayer player = player(context, boss);
        Vec3 origin = boss.position();
        boss.tick();
        context.expect(warnings(player).size() == 1 && bolts(context) == 0,
                "The first center must be announced before any bolt appears");
        Vec3 center = point(warnings(player).get(0));
        player.standAt(groundPoint(context, boss, 18, 12));
        boss.tick();
        boss = restore(context, boss);
        tick(boss, 2);
        context.expect(bolts(context) == 0, "Loading must retain the complete central warning delay");
        player.particlePackets.clear();
        boss.tick();
        context.expect(bolts(context) == 1
                && context.entities(LightningStrikeEffect.class).get(0).position().distanceToSqr(center) < 1.0E-8D,
                "The central bolt must hit the original marked position after the player moves");
        int inner = warnings(player).size();
        context.expect(inner >= 3 && inner <= 4,
                "The central impact must immediately announce three to four inner bolts");
        checkRound(context, player, center, 2.0D, 3.0D);
        tick(boss, 2);
        boss = restore(context, boss);
        boss.tick();
        context.expect(bolts(context) == 1, "Reloaded inner warnings must not strike early");
        player.particlePackets.clear();
        boss.tick();
        context.expect(bolts(context) == 1 + inner, "All inner bolts must hit after their full warning");
        int outer = warnings(player).size();
        context.expect(outer >= 5 && outer <= 7,
                "The inner impact must immediately announce five to seven outer bolts");
        checkRound(context, player, center, 4.0D, 5.0D);
        boss = restore(context, boss);
        tick(boss, 3);
        context.expect(bolts(context) == 1 + inner, "Outer warnings must also retain their full delay after loading");
        boss.tick();
        context.expect(bolts(context) == 1 + inner + outer,
                "The third round must strike once without further recursion");
        tick(boss, 4);
        context.expect(bolts(context) == 1 + inner + outer, "Outer bolts must not create another round");
        context.near(boss.position().distanceToSqr(origin), 0,
                "The boss must remain stationary during its storm phase");
        boss.discard();
        context.pass();
    }

    private static void checkRound(GameTestContext context, TestPlayer player, Vec3 center, double min, double max) {
        for (ClientboundLevelParticlesPacket packet : warnings(player)) {
            double distance = Math.sqrt(point(packet).subtract(center).horizontalDistanceSqr());
            context.expect(distance >= min - 1.0E-6D && distance <= max + 1.0E-6D,
                    "The announced round must stay within its configured ring around the original center");
        }
    }

    private static void cooldown(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        TestPlayer player = player(context, boss);
        boss.tick();
        player.standAt(groundPoint(context, boss, 18, 12));
        int nextCenter = -1;
        Vec3 nextPosition = groundPoint(context, boss, 18, 12);
        for (int elapsed = 2; elapsed <= 125; elapsed++) {
            if (elapsed == 20 || elapsed == 100) {
                boss = restore(context, boss);
            }
            if (elapsed == 30) {
                player.standAt(groundPoint(context, boss, 24, 12));
            } else if (elapsed == 35) {
                player.standAt(groundPoint(context, boss, 18, 12));
            }
            player.particlePackets.clear();
            boss.tick();
            if (warnings(player).stream().anyMatch(packet -> point(packet).distanceToSqr(nextPosition) < 1.0E-8D)) {
                nextCenter = elapsed;
                break;
            }
        }
        // The original third round hits at tick 13; five full seconds end at tick 113.
        context.expect(nextCenter >= 113 && nextCenter <= 125,
                "Player cooldown must survive loading, arena reentry and phase changes: next center=" + nextCenter);
        boss.discard();
        context.pass();
    }

    private static void zones(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        TestPlayer player = player(context, boss);
        context.set(SheeperConfig.LIGHTNING_STRIKE_DURATION_MIN, 2.0D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_DURATION_MAX, 2.0D);
        context.set(SheeperConfig.LIGHTNING_AFTERSHOCK_DAMAGE, 1.0D);
        Vec3 center = player.position();
        for (int elapsed = 1; elapsed <= 60; elapsed++) {
            player.tick();
            boss.tick();
            if (elapsed == 1) {
                context.set(SheeperConfig.LIGHTNING_ATTACK_PAUSE_MIN, 60.0D);
                context.set(SheeperConfig.LIGHTNING_ATTACK_PAUSE_MAX, 60.0D);
            }
            if (elapsed == 15 || elapsed == 50) {
                boss = restore(context, boss);
            }
            if (elapsed == 39) {
                context.expect(!zoneParticles(player).isEmpty(), "The first zone must remain active until phase end");
                player.particlePackets.clear();
            }
            if (elapsed == 55) {
                context.expect(!zoneParticles(player).isEmpty(),
                        "Zones must remain visible during the saved post-phase tail");
            }
        }
        context.near(player.getHealth(), 17.0D,
                "The central zone must deal one health point every second without resetting its cooldown on reload");
        for (ClientboundLevelParticlesPacket packet : zoneParticles(player)) {
            DustParticleOptions dust = (DustParticleOptions) packet.getParticle();
            context.expect(dust.getColor().x() == 1.0F && dust.getColor().y() == 1.0F && dust.getColor().z() == 1.0F,
                    "Residual zone dust must be white");
            Vec3 pos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
            context.expect(pos.subtract(center).horizontalDistanceSqr() <= 1.25D * 1.25D + 1.0E-6D
                    && pos.y >= center.y && pos.y <= center.y + 1.0D,
                    "Only the central impact may leave a zone, distributed over its footprint up to one block high");
        }
        context.expect(zoneParticles(player).stream().anyMatch(packet -> packet.getY() > center.y + 0.5D),
                "The residual effect must extend above the ground");
        player.particlePackets.clear();
        for (int index = 0; index < 20; index++) {
            player.tick();
            boss.tick();
        }
        context.expect(zoneParticles(player).isEmpty(),
                "The zone must expire together with the post-phase tail, including after loading");
        context.near(player.getHealth(), 17.0D, "Expired zones must stop dealing damage");
        boss.discard();
        context.pass();
    }

}
