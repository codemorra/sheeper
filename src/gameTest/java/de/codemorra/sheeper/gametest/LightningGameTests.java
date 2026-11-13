package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Lightning arena startup, reload continuity, and multiplayer eligibility. */
final class LightningGameTests {
    private LightningGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("lightning_arena_preparation", LightningGameTests::preparation));
        tests.add(new SheeperGameTests.Scenario("lightning_arena_reload", LightningGameTests::reload));
        tests.add(new SheeperGameTests.Scenario("lightning_arena_participants", LightningGameTests::participants));
    }

    private static LightningSheeperEntity boss(GameTestContext context) {
        context.set(SheeperConfig.LIGHTNING_SIZE, 1.0D);
        context.set(SheeperConfig.LIGHTNING_PROXIMITY_PUSH_RADIUS, 0.0D);
        context.set(SheeperConfig.LIGHTNING_SPAWN_PUSH_RADIUS, 0.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_RADIUS, 6.0D);
        context.set(SheeperConfig.LIGHTNING_MOVEMENT_RADIUS, 3.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE, 3.0D);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_DURATION, 0.2D);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_WAVE_COUNT, 1);
        return context.spawn(ModEntities.LIGHTNING_SHEEPER.get(), 12, 1, 12);
    }

    private static void tick(LightningSheeperEntity boss, int count) {
        for (int index = 0; index < count; index++) {
            boss.tick();
        }
    }

    private static double furthestSpark(TestPlayer player, Vec3 center) {
        return player.particlePackets.stream().filter(packet -> packet.getParticle() == ParticleTypes.ELECTRIC_SPARK)
                .mapToDouble(packet -> Math.hypot(packet.getX() - center.x, packet.getZ() - center.z))
                .max().orElse(0.0D);
    }

    private static void preparation(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        Vec3 origin = boss.position();
        TestPlayer player = context.player(14, 1, 12);
        context.setBlock(11, 1, 11, Blocks.DANDELION);
        context.expect(!boss.isCombatReady() && !boss.hurt(DamageSource.GENERIC, 10),
                "A newly spawned lightning boss must be protected during preparation");
        context.expect(!boss.isInvulnerableTo(DamageSource.OUT_OF_WORLD),
                "Preparation must retain void damage and administrative removal");
        boss.setNoAi(true);
        boss.setDeltaMovement(1, 0, 1);
        boss.move(MoverType.SELF, new Vec3(1, 0, 1));
        boss.tick();
        context.near(boss.getX(), origin.x, "Preparation must prevent horizontal movement");
        context.near(boss.getZ(), origin.z, "Preparation must prevent horizontal movement");
        context.set(SheeperConfig.LIGHTNING_PREPARATION_DURATION, 20.0D);
        boss.tick();
        double halfway = furthestSpark(player, origin);
        context.expect(halfway > 0 && halfway < boss.getCombatRadius(),
                "The preparation wave must visibly expand from the center, including below the world surface");
        boss.tick();
        context.expect(!boss.isCombatReady(), "Preparation must wait its entire captured duration");
        boss.tick();
        context.expect(boss.isCombatReady() && boss.hurt(DamageSource.GENERIC, 10),
                "The boss must become damageable after the captured duration, including with NoAI");
        context.near(furthestSpark(player, origin), boss.getCombatRadius(),
                "The completed preparation wave must reach the arena boundary");
        player.particlePackets.clear();
        boss.setNoAi(false);
        tick(boss, 10);
        context.near(furthestSpark(player, origin), boss.getCombatRadius(),
                "The arena boundary must remain visible during combat");
        context.expect(!boss.doHurtTarget(player) && player.damageEvents.isEmpty(),
                "The lightning boss must not deal melee or contact damage");
        context.assertBlockPresent(Blocks.DANDELION, 11, 1, 11);
        context.expect(context.explosions.isEmpty(), "Arena preparation must not explode terrain");
        boss.discard();
        player.particlePackets.clear();
        boss.tick();
        context.expect(player.particlePackets.isEmpty(), "Removed bosses must stop arena effects");
        context.pass();
    }

    private static LightningSheeperEntity restore(GameTestContext context, LightningSheeperEntity boss) {
        CompoundTag tag = boss.saveWithoutId(new CompoundTag());
        boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        LightningSheeperEntity restored = ModEntities.LIGHTNING_SHEEPER.get().create(context.level);
        restored.load(tag);
        return restored;
    }

    private static void reload(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        BlockPos origin = boss.blockPosition();
        boss = restore(context, boss); // Saving before the first tick must capture the original arena.
        boss.tick();
        boss = restore(context, boss);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_DURATION, 20.0D);
        tick(boss, 2);
        context.expect(!boss.isCombatReady(), "Reload must retain the remaining preparation time");
        boss.tick();
        context.expect(boss.isCombatReady(), "Reload must not restart or extend preparation");
        boss = restore(context, boss);
        context.expect(boss.isCombatReady() && boss.getArenaCenter().equals(origin),
                "Completed preparation and the original arena must survive reloading");
        boss.setPos(boss.getX() + 2, boss.getY(), boss.getZ());
        boss = restore(context, boss);
        context.expect(boss.getArenaCenter().equals(origin), "Moving the boss must not move its arena origin");
        boss.discard();
        LightningSheeperEntity immediate = boss(context);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_DURATION, 0.0D);
        immediate.tick();
        context.expect(immediate.isCombatReady(), "Zero preparation duration must start combat immediately");
        context.pass();
    }

    private static void participants(GameTestContext context) {
        LightningSheeperEntity boss = boss(context);
        TestPlayer boundary = context.player(18, 1, 12);
        TestPlayer adventure = context.player(12, 4, 12);
        adventure.setGameMode(GameType.ADVENTURE);
        TestPlayer creative = context.player(13, 1, 12);
        creative.setGameMode(GameType.CREATIVE);
        TestPlayer spectator = context.player(14, 1, 12);
        spectator.setGameMode(GameType.SPECTATOR);
        context.expect(boss.getCombatParticipants().size() == 2
                && boss.getCombatParticipants().containsAll(List.of(boundary, adventure)),
                "Living Survival and Adventure players on horizontal and vertical boundaries must participate");
        boundary.standAt(context.point(19, 1, 12));
        adventure.standAt(context.point(12, 5, 12));
        context.expect(boss.getCombatParticipants().isEmpty(),
                "Leaving either the horizontal or vertical arena bounds must remove participants");
        boundary.standAt(context.point(18, 1, 12));
        context.expect(boss.getCombatParticipants().equals(List.of(boundary)),
                "Returning players must rejoin the encounter");
        context.set(SheeperConfig.LIGHTNING_COMBAT_RADIUS, 2.0D);
        context.expect(boss.getCombatParticipants().isEmpty(), "Combat bounds must follow current configuration");
        context.near(boss.getMovementRadius(), 2.0D, "Movement must remain within the combat radius");
        context.expect(boss.isWithinMovementArea(context.point(14, 1, 12).x, context.point(14, 1, 12).z)
                && !boss.isWithinMovementArea(context.point(15, 1, 12).x, context.point(15, 1, 12).z),
                "The saved arena center must also bound future teleport movement");
        boss.discard();
        context.expect(boss.getCombatParticipants().isEmpty(), "Removed bosses must have no combat participants");
        context.pass();
    }
}
