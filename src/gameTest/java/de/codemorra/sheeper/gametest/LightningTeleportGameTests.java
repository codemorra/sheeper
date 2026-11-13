package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Observable teleport timing, destination safety, cancellation, and reload
 * continuity.
 */
final class LightningTeleportGameTests {
    private LightningTeleportGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("lightning_teleport_idle_timing", LightningTeleportGameTests::timing));
        tests.add(new SheeperGameTests.Scenario("lightning_teleport_no_safe_destination",
                LightningTeleportGameTests::blocked));
        tests.add(new SheeperGameTests.Scenario("lightning_teleport_rechecks_arrival",
                LightningTeleportGameTests::recheck));
        tests.add(new SheeperGameTests.Scenario("lightning_teleport_reload", LightningTeleportGameTests::reload));
    }

    private static LightningSheeperEntity boss(GameTestContext context) {
        context.set(SheeperConfig.LIGHTNING_SIZE, 1.0D);
        context.set(SheeperConfig.LIGHTNING_PROXIMITY_PUSH_RADIUS, 0.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_RADIUS, 6.0D);
        context.set(SheeperConfig.LIGHTNING_MOVEMENT_RADIUS, 4.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE, 3.0D);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_DURATION, 0.0D);
        context.set(SheeperConfig.LIGHTNING_TELEPORT_INTERVAL_MIN, 0.2D);
        context.set(SheeperConfig.LIGHTNING_TELEPORT_INTERVAL_MAX, 0.2D);
        context.set(SheeperConfig.LIGHTNING_TELEPORT_WARNING, 0.15D);
        context.set(SheeperConfig.LIGHTNING_TELEPORT_PLAYER_DISTANCE, 4.0D);
        LightningSheeperEntity boss = context.spawn(ModEntities.LIGHTNING_SHEEPER.get(), 12, 1, 12);
        // Settle onto the scene's floor without advancing the movement timer.
        boss.setNoAi(true);
        tick(boss, 10);
        boss.setNoAi(false);
        boss.getRandom().setSeed(713L);
        return boss;
    }

    private static TestPlayer observer(GameTestContext context) {
        TestPlayer player = context.player(22, 1, 12);
        player.setGameMode(GameType.SPECTATOR);
        return player;
    }

    private static void tick(LightningSheeperEntity boss, int count) {
        for (int index = 0; index < count; index++) {
            boss.tick();
        }
    }

    private static List<ClientboundLevelParticlesPacket> warnings(TestPlayer player) {
        return player.particlePackets.stream()
                .filter(packet -> packet.getParticle() == ParticleTypes.ELECTRIC_SPARK && packet.getCount() == 8)
                .toList();
    }

    private static Vec3 announcedDestination(GameTestContext context, TestPlayer observer) {
        List<ClientboundLevelParticlesPacket> warnings = warnings(observer);
        context.expect(!warnings.isEmpty(), "The teleport must announce a destination to nearby clients");
        ClientboundLevelParticlesPacket packet = warnings.get(warnings.size() - 1);
        return new Vec3(packet.getX(), packet.getY() - 0.2D, packet.getZ());
    }

    private static void atPosition(GameTestContext context, LightningSheeperEntity boss, Vec3 expected,
            String message) {
        context.expect(boss.position().distanceToSqr(expected) < 1.0E-8D, message);
    }

    private static void timing(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        TestPlayer observer = observer(context);
        Vec3 origin = boss.position();
        context.expect(boss.getCombatParticipants().isEmpty(), "Idle movement must not require combat participants");
        tick(boss, 3);
        atPosition(context, boss, origin, "The boss must wait the configured interval before moving");
        context.expect(warnings(observer).isEmpty(), "The destination must not be announced before the interval");
        boss.tick();
        Vec3 target = announcedDestination(context, observer);
        tick(boss, 2);
        atPosition(context, boss, origin, "The boss must remain at the departure point throughout the warning");
        boss.fallDistance = 10.0F;
        boss.tick();
        atPosition(context, boss, target, "The boss must arrive at the announced destination after the full warning");
        context.expect(boss.isWithinMovementArea(target.x, target.z) && context.level.noCollision(boss),
                "A teleport must land inside the movement area with space for the boss");
        context.near(boss.getDeltaMovement().lengthSqr(), 0, "Teleporting must clear carried movement");
        context.near(boss.fallDistance, 0, "Teleporting must clear accumulated fall distance");
        context.expect(
                observer.particlePackets.stream().filter(packet -> packet.getParticle() == ParticleTypes.ELECTRIC_SPARK
                        && packet.getCount() == 24).count() == 2,
                "Clients must receive departure and arrival effects");

        observer.particlePackets.clear();
        tick(boss, 2);
        boss.setNoAi(true);
        tick(boss, 10);
        atPosition(context, boss, target, "NoAI must pause teleport movement");
        context.expect(warnings(observer).isEmpty(), "NoAI must not announce new teleports");
        boss.setNoAi(false);
        boss.tick();
        context.expect(warnings(observer).isEmpty(), "Resuming AI must preserve the remaining interval");
        boss.tick();
        announcedDestination(context, observer);
        context.set(SheeperConfig.LIGHTNING_MOVEMENT_RADIUS, 0.0D);
        tick(boss, 5);
        atPosition(context, boss, target, "Disabling movement must cancel an already announced teleport");
        context.pass();
    }

    private static void blocked(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        TestPlayer player = observer(context);
        player.setGameMode(GameType.CREATIVE);
        context.set(SheeperConfig.LIGHTNING_TELEPORT_PLAYER_DISTANCE, 128.0D);
        Vec3 origin = boss.position();
        tick(boss, 20);
        atPosition(context, boss, origin, "The boss must stay put when every destination violates player distance");
        context.expect(warnings(player).isEmpty(), "Unsafe destinations must not be announced");
        context.set(SheeperConfig.LIGHTNING_TELEPORT_PLAYER_DISTANCE, 4.0D);
        context.set(SheeperConfig.LIGHTNING_TELEPORT_WARNING, 0.0D);
        boolean moved = false;
        for (int index = 0; index < 20 && !moved; index++) {
            boss.tick();
            moved = boss.position().distanceToSqr(origin) >= 1.0D;
        }
        context.expect(moved && boss.isWithinMovementArea(boss.getX(), boss.getZ()),
                "The boss must retry and move once a safe destination becomes available");
        context.expect(warnings(player).isEmpty(), "Zero warning duration must teleport without an announcement delay");
        context.expect(boss.distanceToSqr(player) >= 16.0D,
                "Successful destinations must respect distance to Creative players too");
        context.pass();
    }

    private static void recheck(GameTestContext context) {
        TestPlayer observer = observer(context);
        for (String obstruction : List.of("block", "water", "fire", "missing_floor", "player")) {
            LightningSheeperEntity boss = boss(context);
            Vec3 origin = boss.position();
            observer.particlePackets.clear();
            tick(boss, 4);
            Vec3 target = announcedDestination(context, observer);
            BlockPos feet = new BlockPos(target);
            BlockState previousFeet = context.level.getBlockState(feet);
            BlockState previousFloor = context.level.getBlockState(feet.below());
            switch (obstruction) {
                case "block" -> context.level.setBlockAndUpdate(feet, Blocks.STONE.defaultBlockState());
                case "water" -> context.level.setBlockAndUpdate(feet, Blocks.WATER.defaultBlockState());
                case "fire" -> context.level.setBlockAndUpdate(feet, Blocks.FIRE.defaultBlockState());
                case "missing_floor" -> context.level.setBlockAndUpdate(feet.below(), Blocks.AIR.defaultBlockState());
                case "player" -> {
                    observer.setGameMode(GameType.CREATIVE);
                    observer.standAt(target.add(2, 0, 0));
                }
            }
            boss.tick();
            // Remove the obstruction after cancellation: the old warning must not resume.
            context.level.setBlockAndUpdate(feet.below(), previousFloor);
            context.level.setBlockAndUpdate(feet, previousFeet);
            observer.setGameMode(GameType.SPECTATOR);
            observer.standAt(context.point(22, 1, 12));
            tick(boss, 2);
            atPosition(context, boss, origin, "An arrival invalidated by " + obstruction + " must cancel the teleport");
            boss.discard();
        }
        context.pass();
    }

    private static LightningSheeperEntity restore(GameTestContext context, LightningSheeperEntity boss) {
        CompoundTag saved = boss.saveWithoutId(new CompoundTag());
        boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        LightningSheeperEntity restored = ModEntities.LIGHTNING_SHEEPER.get().create(context.level);
        restored.load(saved);
        return restored;
    }

    private static void reload(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        TestPlayer observer = observer(context);
        Vec3 origin = boss.position();
        tick(boss, 2);
        boss = restore(context, boss);
        boss.tick();
        context.expect(warnings(observer).isEmpty(), "Loading must preserve the interval before announcement");
        boss.tick();
        Vec3 target = announcedDestination(context, observer);
        boss.tick();
        boss = restore(context, boss);
        boss.tick();
        atPosition(context, boss, origin, "Loading a warning must preserve its remaining delay");
        boss.tick();
        atPosition(context, boss, target, "Loading must retain the announced destination and finish on time");
        boss = restore(context, boss);
        observer.particlePackets.clear();
        tick(boss, 3);
        atPosition(context, boss, target, "Loading after arrival must not repeat the completed teleport");
        context.expect(warnings(observer).isEmpty(), "Loading after arrival must retain the next movement interval");
        boss.discard();
        context.pass();
    }
}
