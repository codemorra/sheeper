package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Movement, combat participation, and one-time spawn protection contracts. */
final class WaterCombatGameTests {
    private WaterCombatGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(
                new SheeperGameTests.Scenario("water_combat_participants_and_rotation", WaterCombatGameTests::targets));
        tests.add(new SheeperGameTests.Scenario("water_spawn_push_and_reload", WaterCombatGameTests::spawnPush));
        tests.add(new SheeperGameTests.Scenario("water_spawn_push_early_save", WaterCombatGameTests::earlySave));
        tests.add(new SheeperGameTests.Scenario("water_free_movement_and_no_melee", WaterCombatGameTests::movement));
    }

    private static void targets(GameTestContext context) {
        context.set(SheeperConfig.WATER_FOUNTAIN_CHANCE, 0.0D);
        context.set(SheeperConfig.WATER_COMBAT_RADIUS, 5.0D);
        context.set(SheeperConfig.WATER_COMBAT_HEIGHT_RANGE, 15.0D);
        context.set(SheeperConfig.WATER_ARENA_SEARCH_BUDGET, 1);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_STRENGTH, 0.0D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_UPWARD_STRENGTH, 0.0D);
        WaterSheeperEntity boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8);
        boss.getArenaCenter();
        TestPlayer first = context.player(11, 1, 8);
        TestPlayer second = context.player(8, 1, 13);
        second.setGameMode(GameType.ADVENTURE);
        TestPlayer creative = context.player(8, 1, 10);
        creative.setGameMode(GameType.CREATIVE);
        TestPlayer spectator = context.player(8, 1, 11);
        spectator.setGameMode(GameType.SPECTATOR);
        context.player(14, 1, 8);
        TestPlayer upper = context.player(8, 1, 8);
        upper.standAt(context.point(8, 1, 8).add(0, 15, 0));
        TestPlayer lower = context.player(8, 1, 8);
        lower.standAt(context.point(8, 1, 8).add(0, -15, 0));
        context.expect(boss.getCombatParticipants().size() == 4
                && boss.getCombatParticipants().containsAll(List.of(first, second, upper, lower)),
                "Combat must use a horizontal circle and include survival/adventure at both height boundaries");
        upper.standAt(upper.position().add(0, 0.01D, 0));
        lower.standAt(lower.position().add(0, -0.01D, 0));
        context.expect(boss.getCombatParticipants().size() == 2,
                "Players just outside either vertical boundary must be excluded");
        // The center remains fixed even when the boss moves elsewhere.
        boss.setPos(context.point(18, 1, 18));
        context.expect(boss.selectNextAttackTarget() == first && boss.selectNextAttackTarget() == second
                && boss.selectNextAttackTarget() == first,
                "Selection must rotate over participants around the saved origin");
        second.setGameMode(GameType.CREATIVE);
        context.expect(boss.selectNextAttackTarget() == first, "Invalid participants must leave the rotation");
        first.standAt(context.point(14, 1, 8));
        context.expect(boss.selectNextAttackTarget() == null && boss.getTarget() == null,
                "Combat must pause without eligible participants");
        second.setGameMode(GameType.ADVENTURE);
        boss.tick();
        context.expect(boss.getTarget() == second && !boss.isArenaSearchComplete(),
                "Combat must resume before arena discovery or flooding completes");
        second.setHealth(0.0F);
        boss.tick();
        context.expect(boss.getTarget() == null, "Death must invalidate a target on the next tick");
        context.pass();
    }

    private static void spawnPush(GameTestContext context) {
        context.set(SheeperConfig.WATER_SPAWN_PUSH_RADIUS, 4.0D);
        context.set(SheeperConfig.WATER_COMBAT_HEIGHT_RANGE, 2.0D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_STRENGTH, 0.8D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_UPWARD_STRENGTH, 0.2D);
        WaterSheeperEntity boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8);
        boss.setNoAi(true);
        TestPlayer center = context.player(8, 1, 8);
        TestPlayer edge = context.player(4, 1, 8);
        edge.setGameMode(GameType.ADVENTURE);
        TestPlayer outside = context.player(13, 1, 8);
        TestPlayer high = context.player(8, 4, 8);
        TestPlayer creative = context.player(11, 1, 8);
        creative.setGameMode(GameType.CREATIVE);
        TestPlayer spectator = context.player(8, 1, 5);
        spectator.setGameMode(GameType.SPECTATOR);
        List<TestPlayer> players = List.of(center, edge, outside, high, creative, spectator);
        players.forEach(player -> {
            player.setDeltaMovement(Vec3.ZERO);
            player.motionPackets.clear();
        });
        boss.tick();
        context.near(center.getDeltaMovement().x, 0.8D, "An exact-center player needs a finite horizontal direction");
        context.near(center.getDeltaMovement().y, 0.2D, "Configured upward impulse must apply");
        context.near(edge.getDeltaMovement().x, -0.8D, "Boundary players must be pushed away from the origin");
        for (TestPlayer player : List.of(outside, high, creative, spectator)) {
            context.expect(player.motionPackets.isEmpty(), "Out-of-range and excluded modes must receive no push");
            context.near(player.getDeltaMovement().lengthSqr(), 0.0D, "Excluded players must keep their velocity");
        }
        context.expect(players.stream().allMatch(player -> player.damageEvents.isEmpty()),
                "Spawn protection must deal no direct damage");
        context.expect(center.motionPackets.size() == 1 && edge.motionPackets.size() == 1,
                "Eligible players must receive the launch on their client");
        boss.tick();
        CompoundTag saved = boss.saveWithoutId(new CompoundTag());
        boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        WaterSheeperEntity restored = ModEntities.WATER_SHEEPER.get().create(context.level);
        restored.load(saved);
        restored.tick();
        // Legacy bosses with a saved origin must also avoid a new spawn push.
        saved.remove("WaterSpawnPushApplied");
        restored.load(saved);
        restored.tick();
        context.expect(center.motionPackets.size() == 1 && edge.motionPackets.size() == 1,
                "Later ticks, chunk unloading, reload, and legacy saves must not repeat the push");
        context.pass();
    }

    private static void earlySave(GameTestContext context) {
        TestPlayer player = context.player(8, 1, 8);
        player.setDeltaMovement(Vec3.ZERO);
        player.motionPackets.clear();
        WaterSheeperEntity fresh = ModEntities.WATER_SHEEPER.get().create(context.level);
        fresh.setPos(context.point(8, 1, 8));
        fresh.setNoAi(true);
        CompoundTag saved = fresh.saveWithoutId(new CompoundTag());
        WaterSheeperEntity restored = ModEntities.WATER_SHEEPER.get().create(context.level);
        restored.load(saved);
        restored.tick();
        restored.tick();
        context.expect(player.motionPackets.size() == 1,
                "Saving before the first tick must preserve the pending push and apply it only once");
        context.near(player.motionPackets.get(0).getXa() / 8000.0D,
                SheeperConfig.WATER_SPAWN_PUSH_STRENGTH.get(),
                "The default spawn impulse must reach the client without motion-packet clipping");
        context.pass();
    }

    private static void movement(GameTestContext context) {
        context.set(SheeperConfig.WATER_FOUNTAIN_CHANCE, 0.0D);
        context.set(SheeperConfig.WATER_PROJECTILE_DURATION_MIN, 1200);
        context.set(SheeperConfig.WATER_PROJECTILE_DURATION_MAX, 1200);
        context.set(SheeperConfig.WATER_PROJECTILE_INTERVAL, 12000);
        context.set(SheeperConfig.WATER_SIZE, 1.0D);
        context.set(SheeperConfig.WATER_COMBAT_RADIUS, 7.0D);
        context.set(SheeperConfig.WATER_MOVEMENT_RADIUS, 0.0D);
        context.set(SheeperConfig.WATER_MOVEMENT_INTERVAL_MIN, 10);
        context.set(SheeperConfig.WATER_MOVEMENT_INTERVAL_MAX, 10);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_STRENGTH, 0.0D);
        context.set(SheeperConfig.WATER_SPAWN_PUSH_UPWARD_STRENGTH, 0.0D);
        context.set(SheeperConfig.WATER_ARENA_FLOOD_WAVE_TICKS, 200);
        // Navigation must respect both collision and the circular movement bound.
        for (int z = 5; z <= 11; z++) {
            for (int y = 1; y <= 4; y++) {
                context.setBlock(10, y, z, Blocks.STONE);
            }
        }
        WaterSheeperEntity boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8);
        Vec3 origin = boss.position();
        TestPlayer player = context.player(14, 1, 8);
        context.at(25, () -> {
            context.expect(boss.position().distanceToSqr(origin) < 0.01D,
                    "Zero movement radius must disable voluntary movement");
            context.set(SheeperConfig.WATER_MOVEMENT_RADIUS, 4.0D);
        });
        boolean[] moved = { false };
        for (int tick = 26; tick <= 200; tick++) {
            context.at(tick, () -> {
                context.expect(boss.isWithinMovementArea(boss.getX(), boss.getZ()),
                        "Free movement and obstacle detours must remain inside the inner circle: " + boss.position());
                moved[0] |= boss.position().distanceToSqr(origin) > 0.25D;
                context.expect(player.damageEvents.isEmpty(), "A selected player must not receive melee damage");
            });
        }
        context.at(170, () -> player.standAt(boss.position()));
        context.at(201, () -> {
            context.expect(moved[0], "The boss must navigate to free arena positions");
            context.expect(boss.getTarget() == player,
                    "Combat target selection must coexist with independent movement");
            // Repeated outward motion must remain bounded even after navigation stops.
            boss.getNavigation().stop();
            boss.setPos(origin.add(3.9D, 0.0D, 0.0D));
            for (int step = 0; step < 10; step++) {
                boss.move(MoverType.SELF, new Vec3(0.2D, 0.0D, 1.0D));
                context.expect(boss.isWithinMovementArea(boss.getX(), boss.getZ()),
                        "Residual velocity and water currents must not drift beyond the movement radius");
            }
            context.pass();
        });
    }
}
