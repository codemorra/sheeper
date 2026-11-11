package de.codemorra.sheeper.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterProjectile;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Observable warning, launch, phase, persistence and local weather contracts.
 */
final class WaterFountainGameTests {
    private WaterFountainGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("water_fountain_warning_and_dodge", WaterFountainGameTests::dodge));
        tests.add(new SheeperGameTests.Scenario("water_fountain_players_and_arena",
                WaterFountainGameTests::distribution));
        tests.add(new SheeperGameTests.Scenario("water_fountain_water_validation", WaterFountainGameTests::water));
        tests.add(new SheeperGameTests.Scenario("water_fountain_reload_and_height", WaterFountainGameTests::reload));
        tests.add(new SheeperGameTests.Scenario("water_fountain_rain_and_tail", WaterFountainGameTests::rain));
        tests.add(new SheeperGameTests.Scenario("water_fountain_random_phase_selection",
                WaterFountainGameTests::selection));
        tests.add(new SheeperGameTests.Scenario("water_fountain_duration_range", WaterFountainGameTests::duration));
    }

    private static WaterSheeperEntity boss(GameTestContext context) {
        context.set(SheeperConfig.WATER_FOUNTAIN_CHANCE, 1.0D);
        context.set(SheeperConfig.WATER_FOUNTAIN_WARNING, 20);
        context.set(SheeperConfig.WATER_FOUNTAIN_INTERVAL_MIN, 80);
        context.set(SheeperConfig.WATER_FOUNTAIN_INTERVAL_MAX, 240);
        context.set(SheeperConfig.WATER_FOUNTAIN_ARENA_COUNT, 1);
        context.set(SheeperConfig.WATER_SIZE, 1.0D);
        context.set(SheeperConfig.WATER_COMBAT_RADIUS, 10.0D);
        context.set(SheeperConfig.WATER_MOVEMENT_RADIUS, 0.0D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_STRENGTH, 0.0D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_UPWARD_STRENGTH, 0.0D);
        context.set(SheeperConfig.WATER_ARENA_SEARCH_BUDGET, 10000);
        context.set(SheeperConfig.WATER_ARENA_BLOCK_BUDGET, 10000);
        context.set(SheeperConfig.WATER_FOUNTAIN_RAIN_ENABLED, false);
        WaterSheeperEntity boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 12, 1, 12);
        boss.getRandom().setSeed(831L);
        return boss;
    }

    private static TestPlayer player(GameTestContext context, int x, int y, int z) {
        TestPlayer player = context.player(x, y, z);
        player.setDeltaMovement(Vec3.ZERO);
        player.motionPackets.clear();
        player.particlePackets.clear();
        return player;
    }

    private static void tick(WaterSheeperEntity boss, int ticks) {
        for (int i = 0; i < ticks; i++) {
            boss.tick();
        }
    }

    private static boolean warningAt(TestPlayer observer, BlockPos pos) {
        return observer.particlePackets.stream().anyMatch(p -> p.getParticle() == ParticleTypes.FISHING
                && p.getX() == pos.getX() + 0.5D && p.getZ() == pos.getZ() + 0.5D);
    }

    private static void awaitWarning(GameTestContext context, WaterSheeperEntity boss, TestPlayer observer,
            BlockPos pos) {
        for (int i = 0; i < 100 && !warningAt(observer, pos); i++) {
            boss.tick();
        }
        context.expect(warningAt(observer, pos), "Each participant must receive a visible fixed-position warning: "
                + "surface=" + boss.getArenaSurfacePositions().contains(pos) + ", complete="
                + boss.isArenaSearchComplete()
                + ", state=" + context.level.getBlockState(pos) + ", position=" + observer.position()
                + ", warnings="
                + observer.particlePackets.stream().filter(p -> p.getParticle() == ParticleTypes.FISHING)
                        .map(p -> p.getX() + "/" + p.getZ()).distinct().toList()
                + ", phase=" + boss.saveWithoutId(new CompoundTag()).getString("WaterAttackPhase"));
    }

    private static void dodge(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        context.set(SheeperConfig.WATER_FOUNTAIN_WARNING, 30);
        TestPlayer dodging = player(context, 6, 1, 12);
        awaitWarning(context, boss, dodging, context.pos(6, 1, 12));
        dodging.standAt(context.point(20, 1, 20));
        TestPlayer entering = player(context, 6, 1, 12);
        TestPlayer adjacent = player(context, 7, 1, 12);
        tick(boss, 29);
        context.expect(entering.motionPackets.isEmpty(), "Warning must last a full 1.5 seconds before launch");
        boss.tick();
        context.expect(entering.getDeltaMovement().y > 0 && adjacent.getDeltaMovement().y > 0,
                "One fountain must launch all eligible players inside its 3 by 3 area");
        context.expect(dodging.motionPackets.isEmpty(), "Leaving the fixed warning must avoid the launch");
        context.expect(entering.getHealth() == 20 && adjacent.getHealth() == 20,
                "Fountains must not inflict direct damage");
        context.expect(boss.getTarget() == null && context.entities(WaterProjectile.class).isEmpty(),
                "A fountain phase must have no direct focus or simultaneous regular shots");
        context.pass();
    }

    private static void distribution(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        context.set(SheeperConfig.WATER_FOUNTAIN_INTERVAL_MIN, 10);
        context.set(SheeperConfig.WATER_FOUNTAIN_INTERVAL_MAX, 20);
        context.set(SheeperConfig.WATER_FOUNTAIN_ARENA_COUNT, 3);
        context.set(SheeperConfig.WATER_FOUNTAIN_WARNING, 30);
        TestPlayer first = player(context, 6, 1, 12);
        TestPlayer second = player(context, 18, 1, 12);
        Set<Integer> warningTicks = new HashSet<>();
        for (int i = 0; i < 160; i++) {
            int before = first.particlePackets.size();
            boss.tick();
            if (first.particlePackets.subList(before, first.particlePackets.size()).stream()
                    .anyMatch(p -> p.getParticle() == ModParticles.WATER_FOUNTAIN.get())) {
                warningTicks.add(i);
            }
        }
        context.expect(warningAt(first, context.pos(6, 1, 12)) && warningAt(first, context.pos(18, 1, 12)),
                "Both players must independently receive fountains during the same phase");
        context.expect(!first.motionPackets.isEmpty() && !second.motionPackets.isEmpty(),
                "Both participants must be affected without choosing a boss focus");
        context.expect(warningTicks.size() > 3, "Fountains must erupt at staggered times");
        context.expect(first.particlePackets.stream().filter(p -> p.getParticle() == ModParticles.WATER_FOUNTAIN.get())
                .count() >= 9 * 20, "Dense arena batches must produce at least twenty eruptions in eight seconds");
        context.expect(first.particlePackets.stream().anyMatch(p -> p.getParticle() == ParticleTypes.FISHING
                && p.getX() != context.point(6, 1, 12).x && p.getX() != context.point(18, 1, 12).x),
                "Random arena fountains must also appear away from player positions");
        context.pass();
    }

    private static void water(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        TestPlayer player = player(context, 6, 1, 12);
        awaitWarning(context, boss, player, context.pos(6, 1, 12));
        context.setBlock(6, 1, 12, Blocks.AIR);
        tick(boss, 20);
        context.expect(player.motionPackets.isEmpty(), "Removing water during warning must cancel its fountain: "
                + context.level.getBlockState(context.pos(6, 1, 12)) + ", motion=" + player.motionPackets.size()
                + ", fountains=" + boss.saveWithoutId(new CompoundTag()).getCompound("WaterFountains"));
        // Regular flowing water is also valid, and must survive the attack.
        for (int x = 5; x <= 7; x++) {
            for (int z = 11; z <= 13; z++) {
                context.setBlock(x, 1, z, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3));
            }
        }
        player.particlePackets.clear();
        context.set(SheeperConfig.WATER_FOUNTAIN_INTERVAL_MIN, 1);
        context.set(SheeperConfig.WATER_FOUNTAIN_INTERVAL_MAX, 1);
        // A short new participant timer avoids waiting for the previous random delay.
        TestPlayer arriving = player(context, 6, 1, 12);
        awaitWarning(context, boss, arriving, context.pos(6, 1, 12));
        tick(boss, 20);
        context.expect(!arriving.motionPackets.isEmpty(), "Flowing vanilla water must support a fountain");
        context.assertBlockPresent(Blocks.WATER, 6, 1, 12);
        context.pass();
    }

    private static void reload(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        TestPlayer player = player(context, 6, 1, 12);
        awaitWarning(context, boss, player, context.pos(6, 1, 12));
        tick(boss, 5);
        CompoundTag saved = boss.saveWithoutId(new CompoundTag());
        boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        WaterSheeperEntity restored = ModEntities.WATER_SHEEPER.get().create(context.level);
        restored.load(saved);
        restored.setNoAi(true);
        tick(restored, 20);
        context.expect(player.motionPackets.isEmpty(), "NoAI must pause pending fountains after reload");
        restored.setNoAi(false);
        tick(restored, 14);
        context.expect(player.motionPackets.isEmpty(), "Reload must retain the remaining warning time");
        restored.tick();
        context.expect(player.motionPackets.size() == 1, "A saved warning must erupt exactly once");
        double velocity = player.getDeltaMovement().y;
        double rise = 0;
        for (int i = 0; i < 100 && velocity > 0; i++) {
            rise += velocity;
            velocity = (velocity - 0.08D) * 0.98D;
        }
        context.expect(rise > 9.9D && rise <= 10.00001D,
                "The dry-air launch trajectory must reach at most ten blocks; water may lower it");
        context.setBlock(6, 4, 12, Blocks.STONE);
        double before = player.getY();
        player.move(MoverType.SELF, new Vec3(0, 10, 0));
        context.expect(player.getY() - before < 2, "Normal collision must prevent launching through a low ceiling");
        player.causeFallDamage(10, 1, DamageSource.FALL);
        context.expect(player.getHealth() < 20, "A fountain must not grant general immunity to fall damage");
        context.pass();
    }

    private static long rainCount(TestPlayer player) {
        return player.particlePackets.stream().filter(p -> p.getParticle() == ModParticles.ARENA_RAIN.get()).count();
    }

    private static void rain(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        context.set(SheeperConfig.WATER_FOUNTAIN_RAIN_ENABLED, true);
        context.set(SheeperConfig.WATER_FOUNTAIN_INTERVAL_MIN, 1);
        context.set(SheeperConfig.WATER_FOUNTAIN_INTERVAL_MAX, 1);
        context.set(SheeperConfig.WATER_FOUNTAIN_DURATION_MIN, 50);
        context.set(SheeperConfig.WATER_FOUNTAIN_DURATION_MAX, 50);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MIN, 2);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MAX, 2);
        TestPlayer player = player(context, 6, 1, 12);
        boolean raining = context.level.isRaining();
        float rain = context.level.getRainLevel(1);
        context.level.setRainLevel(0);
        try {
            tick(boss, 30);
            context.expect(rainCount(player) == 0, "Rain must wait until shortly after the first eruption");
            boss.tick();
            context.expect(rainCount(player) > 0, "Local rain must start after its configured delay: "
                    + boss.saveWithoutId(new CompoundTag()).getCompound("WaterFountains"));
            context.expect(player.particlePackets.stream().filter(p -> p.getParticle() == ModParticles.ARENA_RAIN.get())
                    .allMatch(p -> boss.getArenaSurfacePositions()
                            .contains(new BlockPos(p.getX(), context.pos(0, 1, 0).getY(), p.getZ()))),
                    "Every raindrop must remain above a discovered arena column");
            tick(boss, 19);
            long atEnd = rainCount(player);
            tick(boss, 3);
            context.expect(rainCount(player) > atEnd,
                    "Starting a new fountain phase must preserve the preceding rain tail");
            boss.setNoAi(true);
            player.standAt(context.point(22, 1, 22));
            tick(boss, 100);
            context.expect(rainCount(player) > atEnd, "Rain must continue five seconds without participants or AI");
            long afterTail = rainCount(player);
            tick(boss, 5);
            context.expect(rainCount(player) == afterTail, "Rain must stop after the five-second tail");
            context.expect(!context.level.isRaining(), "Local rain must not modify world weather");
            boss.setNoAi(false);
            player.standAt(context.point(6, 1, 12));
            context.level.setRainLevel(1);
            tick(boss, 250);
            context.expect(rainCount(player) == afterTail && context.level.isRaining(),
                    "Natural rain must suppress cosmetic rain and remain active");
        } finally {
            context.level.setRainLevel(rain);
        }
        context.expect(context.level.isRaining() == raining, "Weather state must be restored after the scene");
        context.pass();
    }

    private static void duration(GameTestContext context) {
        Set<Integer> durations = new HashSet<>();
        TestPlayer player = player(context, 6, 1, 12);
        for (int sample = 0; sample < 5; sample++) {
            WaterSheeperEntity boss = boss(context);
            boss.getRandom().setSeed(831L + sample * 1009L);
            context.set(SheeperConfig.WATER_FOUNTAIN_DURATION_MIN, 40);
            context.set(SheeperConfig.WATER_FOUNTAIN_DURATION_MAX, 60);
            context.set(SheeperConfig.WATER_FOUNTAIN_INTERVAL_MIN, 12000);
            context.set(SheeperConfig.WATER_FOUNTAIN_INTERVAL_MAX, 12000);
            context.set(SheeperConfig.WATER_ATTACK_PAUSE_MIN, 0);
            context.set(SheeperConfig.WATER_ATTACK_PAUSE_MAX, 0);
            context.set(SheeperConfig.WATER_PROJECTILE_INTERVAL, 1);
            boss.tick();
            // Only the following phase may change type; the active duration stays fixed.
            context.set(SheeperConfig.WATER_FOUNTAIN_CHANCE, 0.0D);
            int elapsed = 1;
            while (context.entities(WaterProjectile.class).isEmpty() && elapsed < 65) {
                boss.tick();
                elapsed++;
            }
            context.expect(elapsed >= 41 && elapsed <= 61,
                    "The next projectile phase must start within the sampled fountain duration bounds");
            durations.add(elapsed - 1);
            boss.remove(Entity.RemovalReason.DISCARDED);
            context.entities(WaterProjectile.class).forEach(Entity::discard);
        }
        context.expect(durations.size() > 1, "Fountain durations must be sampled again for each phase");
        context.expect(player.getHealth() == 20, "Duration checks must not damage the observer");
        context.pass();
    }

    private static void selection(GameTestContext context) {
        WaterSheeperEntity boss = boss(context);
        context.set(SheeperConfig.WATER_FOUNTAIN_CHANCE, 0.5D);
        context.set(SheeperConfig.WATER_FOUNTAIN_DURATION_MIN, 1);
        context.set(SheeperConfig.WATER_FOUNTAIN_DURATION_MAX, 1);
        context.set(SheeperConfig.WATER_PROJECTILE_DURATION_MIN, 1);
        context.set(SheeperConfig.WATER_PROJECTILE_DURATION_MAX, 1);
        context.set(SheeperConfig.WATER_PROJECTILE_INTERVAL, 1);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MIN, 1);
        context.set(SheeperConfig.WATER_ATTACK_PAUSE_MAX, 1);
        TestPlayer player = player(context, 6, 1, 12);
        Set<Integer> shotTicks = new HashSet<>();
        int previous = 0;
        for (int i = 0; i < 80; i++) {
            boss.tick();
            int count = context.entities(WaterProjectile.class).size();
            if (count > previous) {
                shotTicks.add(i);
            }
            previous = count;
        }
        context.expect(shotTicks.size() > 0 && shotTicks.size() < 40,
                "Both attacks must participate in random phase selection with rest between phases");
        context.expect(player.getHealth() == 20, "Unmoved projectiles and fountain warnings must not damage players");
        context.pass();
    }
}
