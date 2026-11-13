package de.codemorra.sheeper.gametest;

import java.util.ArrayList;
import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.particle.LightningRingOptions;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Ring preparation/persistence and jumpable, terrain-following single-hit
 * damage.
 */
final class LightningRingGameTests {
    private LightningRingGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("lightning_ring_sequence_reload", LightningRingGameTests::sequence));
        tests.add(new SheeperGameTests.Scenario("lightning_ring_jump_and_single_hit_reload",
                LightningRingGameTests::damage));
    }

    private static LightningSheeperEntity boss(GameTestContext context) {
        context.set(SheeperConfig.LIGHTNING_SIZE, 1.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_RADIUS, 9.0D);
        context.set(SheeperConfig.LIGHTNING_MOVEMENT_RADIUS, 0.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE, 4.0D);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_DURATION, 0.0D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_WEIGHT, 0.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_WEIGHT, 0.0D);
        context.set(SheeperConfig.LIGHTNING_RING_WEIGHT, 1.0D);
        context.set(SheeperConfig.LIGHTNING_ATTACK_INTERVAL_MIN, 0.05D);
        context.set(SheeperConfig.LIGHTNING_ATTACK_INTERVAL_MAX, 0.05D);
        context.set(SheeperConfig.LIGHTNING_RING_TELEPORT_COUNT_MIN, 0);
        context.set(SheeperConfig.LIGHTNING_RING_TELEPORT_COUNT_MAX, 0);
        context.set(SheeperConfig.LIGHTNING_RING_CHARGE, 0.2D);
        LightningSheeperEntity boss = context.spawn(ModEntities.LIGHTNING_SHEEPER.get(), 12, 1, 12);
        boss.setNoAi(true);
        for (int tick = 0; tick < 10; tick++) {
            boss.tick();
        }
        boss.setNoAi(false);
        boss.getRandom().setSeed(957L);
        return boss;
    }

    private static TestPlayer player(GameTestContext context, Vec3 position) {
        TestPlayer player = context.player(12, 0, 12);
        player.standAt(position);
        return player;
    }

    private static LightningSheeperEntity restore(GameTestContext context, LightningSheeperEntity boss) {
        CompoundTag saved = boss.saveWithoutId(new CompoundTag());
        boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        LightningSheeperEntity restored = ModEntities.LIGHTNING_SHEEPER.get().create(context.level);
        restored.load(saved);
        return restored;
    }

    private static void deferNextAttack(GameTestContext context) {
        context.set(SheeperConfig.LIGHTNING_ATTACK_INTERVAL_MIN, 60.0D);
        context.set(SheeperConfig.LIGHTNING_ATTACK_INTERVAL_MAX, 60.0D);
    }

    private static void sequence(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        context.set(SheeperConfig.LIGHTNING_MOVEMENT_RADIUS, 5.0D);
        context.set(SheeperConfig.LIGHTNING_RING_TELEPORT_COUNT_MIN, 3);
        context.set(SheeperConfig.LIGHTNING_RING_TELEPORT_COUNT_MAX, 3);
        context.set(SheeperConfig.LIGHTNING_RING_TELEPORT_INTERVAL, 0.1D);
        context.set(SheeperConfig.LIGHTNING_RING_COUNT_MIN, 3);
        context.set(SheeperConfig.LIGHTNING_RING_COUNT_MAX, 3);
        context.set(SheeperConfig.LIGHTNING_RING_INTERVAL, 0.3D);
        context.set(SheeperConfig.LIGHTNING_RING_SPEED, 10.0D);
        context.set(SheeperConfig.LIGHTNING_RING_DAMAGE, 0.0D);
        TestPlayer observer = player(context, boss.position().add(7, 0, 0));
        List<Integer> teleports = new ArrayList<>();
        List<Integer> releases = new ArrayList<>();
        Vec3 previous = boss.position();
        Vec3 origin = null;
        double furthest = 0;
        for (int tick = 1; tick <= 70; tick++) {
            observer.particlePackets.clear();
            boss.tick();
            if (boss.position().subtract(previous).horizontalDistanceSqr() > 1.0E-8D) {
                teleports.add(tick);
            }
            previous = boss.position();
            for (var packet : observer.particlePackets) {
                if (packet.getParticle() instanceof LightningRingOptions ring) {
                    Vec3 center = new Vec3(packet.getX(), packet.getY(), packet.getZ());
                    if (origin == null) {
                        origin = center;
                    }
                    context.near(center.distanceToSqr(origin), 0, "All waves must keep the final teleport's origin");
                    if (ring.radius() == 0) {
                        releases.add(tick);
                    }
                    furthest = Math.max(furthest, ring.radius());
                }
            }
            if (tick == 1) {
                deferNextAttack(context);
            }
            // Resume during teleports, charging and active rings without restarting any
            // stage.
            if (tick == 2 || tick == 7 || tick == 12) {
                boss = restore(context, boss);
            }
        }
        context.expect(teleports.equals(List.of(1, 3, 5)), "Loading must preserve rapid teleport count and interval");
        context.expect(releases.equals(List.of(9, 15, 21)),
                "Charging and all three wave intervals must survive loading");
        context.expect(furthest >= 9, "Rings must expand all the way to the arena edge");
        context.near(boss.position().distanceToSqr(origin), 0, "The boss must stay at the ring origin after charging");
        boss.discard();
        context.pass();
    }

    private static void damage(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        context.set(SheeperConfig.LIGHTNING_COMBAT_RADIUS, 4.0D);
        context.set(SheeperConfig.LIGHTNING_RING_COUNT_MIN, 2);
        context.set(SheeperConfig.LIGHTNING_RING_COUNT_MAX, 2);
        context.set(SheeperConfig.LIGHTNING_RING_INTERVAL, 2.0D);
        context.set(SheeperConfig.LIGHTNING_RING_SPEED, 1.0D);
        context.set(SheeperConfig.LIGHTNING_RING_DAMAGE, 2.0D);
        Vec3 center = boss.position();
        TestPlayer grounded = player(context, center.add(2, 0, 0));
        TestPlayer jumping = player(context, center.add(-2, 0.6D, 0));
        Vec3 slab = center.add(0, 0, 2);
        context.level.setBlockAndUpdate(new BlockPos(slab), Blocks.STONE_SLAB.defaultBlockState());
        TestPlayer raised = player(context, slab.add(0, 0.5D, 0));
        boolean restored = false;
        for (int tick = 1; tick <= 140; tick++) {
            grounded.tick();
            jumping.tick();
            raised.tick();
            boss.tick();
            if (tick == 1) {
                deferNextAttack(context);
            }
            if (!restored && !grounded.damageEvents.isEmpty()) {
                boss = restore(context, boss);
                restored = true;
            }
            if (tick == 65) {
                context.near(jumping.getHealth(), 20, "Jumping above the first ring must avoid damage");
                jumping.standAt(center.add(-2, 0, 0));
            }
        }
        context.expect(restored, "The first ring must hit a grounded player before loading");
        context.near(grounded.getHealth(), 16, "Each ring must deal one heart, without repeating after loading");
        context.near(raised.getHealth(), 16, "Ring damage must follow a raised slab's actual surface");
        context.near(jumping.getHealth(), 18, "Landing must allow the next ring to hit independently");
        context.expect(grounded.damageEvents.size() == 2 && raised.damageEvents.size() == 2
                && jumping.damageEvents.size() == 1, "Hit limits must be independent for each player and ring");
        boss.discard();
        context.pass();
    }
}
