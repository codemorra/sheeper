package de.codemorra.sheeper.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.entity.LightningStrikeEffect;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Lane warning/progression and the per-player damage limit across loading. */
final class LightningFrontGameTests {
    private record Mark(Vec3 pos, int tick) {
    }

    private LightningFrontGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("lightning_lane_progression_and_warnings",
                LightningFrontGameTests::progression));
        tests.add(
                new SheeperGameTests.Scenario("lightning_lane_single_hit_reload", LightningFrontGameTests::singleHit));
    }

    private static LightningSheeperEntity boss(GameTestContext context) {
        context.set(SheeperConfig.LIGHTNING_SIZE, 1.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_RADIUS, 9.0D);
        context.set(SheeperConfig.LIGHTNING_MOVEMENT_RADIUS, 0.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE, 3.0D);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_DURATION, 0.0D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_WEIGHT, 0.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_WEIGHT, 1.0D);
        context.set(SheeperConfig.LIGHTNING_RING_WEIGHT, 0.0D);
        context.set(SheeperConfig.LIGHTNING_ATTACK_INTERVAL_MIN, 0.05D);
        context.set(SheeperConfig.LIGHTNING_ATTACK_INTERVAL_MAX, 0.05D);
        context.set(SheeperConfig.LIGHTNING_FRONT_DURATION_MIN, 2.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_DURATION_MAX, 2.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_SPAWN_INTERVAL_MIN, 60.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_SPAWN_INTERVAL_MAX, 60.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_MAX_ACTIVE, 1);
        context.set(SheeperConfig.LIGHTNING_FRONT_STEP_DISTANCE, 2.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_STEP_INTERVAL, 0.1D);
        context.set(SheeperConfig.LIGHTNING_FRONT_WARNING, 0.2D);
        context.set(SheeperConfig.LIGHTNING_FRONT_SIDE_OFFSET, 1.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_DAMAGE, 0.0D);
        LightningSheeperEntity boss = context.spawn(ModEntities.LIGHTNING_SHEEPER.get(), 12, 1, 12);
        boss.setNoAi(true);
        for (int index = 0; index < 10; index++) {
            boss.tick();
        }
        boss.setNoAi(false);
        boss.getRandom().setSeed(957L);
        return boss;
    }

    private static TestPlayer player(GameTestContext context, LightningSheeperEntity boss, int x, int z) {
        TestPlayer player = context.player(x, 0, z);
        player.standAt(groundPoint(context, boss, x, z));
        return player;
    }

    private static Vec3 groundPoint(GameTestContext context, LightningSheeperEntity boss, int x, int z) {
        Vec3 point = context.point(x, 0, z);
        return new Vec3(point.x, boss.getY(), point.z);
    }

    private static List<ClientboundLevelParticlesPacket> warnings(TestPlayer player) {
        return player.particlePackets.stream().filter(packet -> packet.getParticle() == ParticleTypes.END_ROD).toList();
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

    private static void progression(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        TestPlayer player = player(context, boss, 10, 10);
        Vec3 origin = boss.position();
        Vec3 direction = null;
        List<Mark> marks = new ArrayList<>();
        Set<UUID> impacts = new HashSet<>();
        for (int elapsed = 1; elapsed <= 45; elapsed++) {
            player.particlePackets.clear();
            boss.tick();
            for (ClientboundLevelParticlesPacket packet : warnings(player)) {
                marks.add(new Mark(point(packet), elapsed));
            }
            if (elapsed == 1) {
                List<ClientboundLevelParticlesPacket> cue = player.particlePackets.stream()
                        .filter(packet -> packet.getParticle() == ParticleTypes.ELECTRIC_SPARK
                                && packet.getCount() == 3)
                        .toList();
                context.expect(cue.size() > 1, "A new lane must visibly announce its starting direction");
                Vec3 end = point(cue.get(cue.size() - 1)).subtract(origin);
                direction = new Vec3(end.x, 0, end.z).normalize();
                player.standAt(groundPoint(context, boss, 18, 12));
                context.set(SheeperConfig.LIGHTNING_ATTACK_INTERVAL_MIN, 60.0D);
                context.set(SheeperConfig.LIGHTNING_ATTACK_INTERVAL_MAX, 60.0D);
            }
            if (elapsed == 2) {
                boss = restore(context, boss);
            }
            for (LightningStrikeEffect bolt : context.entities(LightningStrikeEffect.class)) {
                if (impacts.add(bolt.getUUID())) {
                    Mark mark = marks.stream()
                            .filter(candidate -> candidate.pos.distanceToSqr(bolt.position()) < 1.0E-8D)
                            .findFirst().orElse(null);
                    context.expect(mark != null && elapsed - mark.tick == 4,
                            "Each bolt must hit its fixed marker after the full warning, including after loading");
                }
            }
        }
        context.expect(marks.size() == 5 && impacts.size() == marks.size(),
                "A single lane must reach the arena edge and finish every announced impact");
        double previous = 0;
        boolean sideways = false;
        for (Mark mark : marks) {
            Vec3 relative = mark.pos.subtract(origin);
            double forward = relative.x * direction.x + relative.z * direction.z;
            double offset = relative.x * -direction.z + relative.z * direction.x;
            context.expect(forward > previous && Math.abs(offset) <= 1.0D + 1.0E-6D
                    && relative.horizontalDistanceSqr() <= 81.0D + 1.0E-6D,
                    "Markers must advance along one fixed direction with bounded sideways jumps inside the arena");
            sideways |= Math.abs(offset) > 1.0E-4D;
            previous = forward;
        }
        context.expect(sideways && previous > 8.9D,
                "The lane must use sideways variation and end at the arena boundary");
        context.near(boss.position().distanceToSqr(origin), 0,
                "The boss must stay at the lane origin during the attack");
        boss.discard();
        context.pass();
    }

    private static void singleHit(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        TestPlayer first = player(context, boss, 10, 10);
        TestPlayer second = player(context, boss, 14, 10);
        context.set(SheeperConfig.LIGHTNING_COMBAT_RADIUS, 6.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_DURATION_MIN, 5.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_DURATION_MAX, 5.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_STEP_DISTANCE, 1.0D);
        // More than vanilla's half-second protection separates successive hits.
        context.set(SheeperConfig.LIGHTNING_FRONT_STEP_INTERVAL, 0.6D);
        context.set(SheeperConfig.LIGHTNING_FRONT_WARNING, 0.1D);
        context.set(SheeperConfig.LIGHTNING_FRONT_SIDE_OFFSET, 0.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_DAMAGE, 2.0D);
        int marks = 0;
        for (int elapsed = 1; elapsed <= 80; elapsed++) {
            first.tick();
            second.tick();
            first.particlePackets.clear();
            boss.tick();
            for (ClientboundLevelParticlesPacket warning : warnings(first)) {
                marks++;
                Vec3 pos = point(warning);
                first.standAt(pos);
                if (marks >= 2) {
                    second.standAt(pos.add(0.8D, 0, 0));
                }
            }
            if (elapsed == 3) {
                context.near(first.getHealth(), 18, "The first lane hit must deal one heart before armor");
            }
            if (elapsed == 5) {
                boss = restore(context, boss);
            }
        }
        context.expect(marks >= 5 && context.entities(LightningStrikeEffect.class).size() == marks,
                "Both players must be exposed to multiple completed impacts of the same lane");
        context.near(first.getHealth(), 18, "Loading must retain the first player's already consumed lane hit");
        context.near(second.getHealth(), 18, "Each player must have an independent single-hit limit for the lane");
        context.expect(first.damageEvents.size() == 1 && second.damageEvents.size() == 1,
                "Repeated lane impacts must not bypass the per-player damage limit");
        boss.discard();
        context.pass();
    }
}
