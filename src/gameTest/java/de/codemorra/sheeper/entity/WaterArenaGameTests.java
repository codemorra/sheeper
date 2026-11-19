package de.codemorra.sheeper.entity;

import java.util.List;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.gametest.GameTestContext;
import de.codemorra.sheeper.gametest.SheeperGameTests.Scenario;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;

/** Encounter membership, ownership and recovery contracts, independent of attack tuning. */
public final class WaterArenaGameTests {
    private WaterArenaGameTests() {
    }

    public static void add(List<Scenario> tests) {
        tests.add(new Scenario("water_membership_and_vertical_exits", WaterArenaGameTests::membership));
        tests.add(new Scenario("water_damage_requires_inside_owner", WaterArenaGameTests::damageOwnership));
        tests.add(new Scenario("water_empty_grace_return_and_reset", WaterArenaGameTests::recovery));
        tests.add(new Scenario("water_creative_damage_recovers_without_participation", WaterArenaGameTests::creative));
        tests.add(new Scenario("water_health_uses_simultaneous_peak", WaterArenaGameTests::health));
        tests.add(new Scenario("water_health_cap_and_saved_origin", WaterArenaGameTests::persistence));
    }

    private static void membership(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        var player = WaterTestScene.player(c, 13, 1, 10);
        player.standAt(c.point(13, 8, 10));
        c.expect(boss.getCombatParticipants().isEmpty(), "A player above the water must not enter combat yet");
        player.standAt(c.point(13, 1, 10));
        c.expect(boss.getCombatParticipants().contains(player), "Submerged entry must join the encounter");
        player.standAt(c.point(13, 8, 10));
        boss.enforceArenaPlayerBounds();
        c.expect(player.getBoundingBox().maxY <= boss.getArenaWaterSurfaceY() - 0.149D,
                "An entered player must be kept below the water surface");
        player.standAt(c.point(13, -1, 10));
        boss.enforceArenaPlayerBounds();
        c.expect(player.getY() >= boss.getArenaWaterMinimumY() + 0.149D,
                "An entered player must be restored above the lower boundary");
        player.standAt(c.point(20, 1, 10));
        c.expect(boss.getCombatParticipants().isEmpty(), "Lateral exit must immediately end membership");
        player.standAt(c.point(13, 1, 10));
        player.setGameMode(GameType.CREATIVE);
        c.expect(boss.getCombatParticipants().isEmpty(), "Creative players must not become combat participants");
        player.setGameMode(GameType.SPECTATOR);
        c.expect(boss.getCombatParticipants().isEmpty(), "Spectators must not become combat participants");
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(0);
        c.expect(boss.getCombatParticipants().isEmpty(), "Dead players must immediately lose membership");
        c.pass();
    }

    private static void damageOwnership(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        var inside = WaterTestScene.player(c, 13, 1, 10);
        var outside = WaterTestScene.player(c, 20, 1, 10);
        c.expect(!boss.isInvulnerableTo(DamageSource.playerAttack(inside)), "Inside player attacks must be allowed");
        c.expect(boss.isInvulnerableTo(DamageSource.playerAttack(outside)), "Outside melee attacks must be rejected");
        Arrow arrow = new Arrow(c.level, inside);
        c.expect(!boss.isInvulnerableTo(DamageSource.arrow(arrow, null)),
                "Indirect damage must resolve ownership through the projectile itself");
        inside.standAt(c.point(20, 1, 12));
        c.expect(boss.isInvulnerableTo(DamageSource.arrow(arrow, inside)),
                "An arrow fired inside must lose permission when its owner leaves");
        c.expect(boss.isInvulnerableTo(DamageSource.GENERIC)
                && boss.isInvulnerableTo(DamageSource.DROWN), "Environmental damage must not borrow player ownership");
        outside.standAt(c.point(13, 1, 10));
        outside.setGameMode(GameType.CREATIVE);
        c.expect(!boss.isInvulnerableTo(DamageSource.playerAttack(outside)), "Creative attacks inside must work");
        outside.setGameMode(GameType.SPECTATOR);
        c.expect(boss.isInvulnerableTo(DamageSource.playerAttack(outside)), "Spectators must never gain permission");
        c.pass();
    }

    private static void recovery(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_EMPTY_ARENA_GRACE, 0.3D);
        c.set(WaterSheeperConfig.WATER_EMPTY_ARENA_REGENERATION, 25.0D);
        var player = WaterTestScene.player(c, 13, 1, 10);
        CompoundTag interrupted = new CompoundTag();
        boss.addAdditionalSaveData(interrupted);
        interrupted.getCompound("WaterSpecialAttacks").putInt("BeamCooldown", 400);
        interrupted.getCompound("WaterSpecialAttacks").putInt("MaelstromCooldown", 300);
        interrupted.getCompound("WaterDefaultAttack").putBoolean("Firing", true);
        boss.readAdditionalSaveData(interrupted);
        WaterArenaCombat combat = new WaterArenaCombat(boss);
        combat.tick(List.of(player));
        boss.setHealth(50);
        WaterTestScene.ticks(5, () -> combat.tick(List.of()));
        c.near(boss.getHealth(), 50, "Grace period must preserve damage");
        combat.tick(List.of(player));
        c.near(boss.getHealth(), 50, "Returning during grace must preserve health");
        WaterTestScene.ticks(6, () -> combat.tick(List.of()));
        c.near(boss.getHealth(), 75, "Empty arena must regenerate after the configured grace");
        WaterTestScene.ticks(19, () -> combat.tick(List.of()));
        c.near(boss.getHealth(), 75, "Regeneration must not run on every tick");
        combat.tick(List.of());
        c.near(boss.getHealth(), boss.getMaxHealth(), "Regeneration must finish at full health");
        CompoundTag special = new CompoundTag();
        boss.addAdditionalSaveData(special);
        c.expect(special.getCompound("WaterSpecialAttacks").getInt("MaelstromCooldown") == -1
                && special.getCompound("WaterSpecialAttacks").getInt("BeamCooldown") == 0
                && !special.getCompound("WaterDefaultAttack").getBoolean("Firing")
                && special.getCompound("WaterDefaultAttack").getInt("PhaseTicks") > 0,
                "Full recovery must reset special readiness and the encounter entry pause");
        c.pass();
    }

    private static void creative(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_EMPTY_ARENA_GRACE, 0.0D);
        c.set(WaterSheeperConfig.WATER_EMPTY_ARENA_REGENERATION, 25.0D);
        var player = WaterTestScene.player(c, 13, 1, 10);
        player.setGameMode(GameType.CREATIVE);
        c.expect(boss.hurt(DamageSource.playerAttack(player), 10), "Creative player must actually damage the boss");
        c.expect(boss.getCombatParticipants().isEmpty(), "Creative damage must not start player targeting");
        WaterArenaCombat combat = new WaterArenaCombat(boss);
        combat.tick(boss.getCombatParticipants());
        c.near(boss.getHealth(), boss.getMaxHealth(), "Creative-only damage must still regenerate");
        c.pass();
    }

    private static void health(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_HEALTH_PER_ADDITIONAL_PLAYER, 30.0D);
        var a = WaterTestScene.player(c, 12, 1, 10);
        var b = WaterTestScene.player(c, 14, 1, 10);
        var d = WaterTestScene.player(c, 16, 1, 10);
        // Use the entity-owned controller so its attribute refresh sees the peak,
        // while the entry pause and stationary navigation isolate health from attacks.
        c.set(WaterSheeperConfig.WATER_MOVEMENT_SPEED, 0.0D);
        c.set(WaterSheeperConfig.WATER_IDLE_SPEED, 0.0D);
        CompoundTag quiet = new CompoundTag();
        boss.addAdditionalSaveData(quiet);
        quiet.getCompound("WaterDefaultAttack").putInt("PhaseTicks", 1200);
        boss.readAdditionalSaveData(quiet);
        boss.setNoAi(false);
        boss.setHealth(50);
        for (int tick = 0; tick < 59; tick++) {
            boss.tick();
        }
        c.near(boss.getMaxHealth(), 100, "Brief entries must not immediately scale health");
        boss.tick();
        c.near(boss.getMaxHealth(), 160, "Only continuously present simultaneous players must raise health");
        c.near(boss.getHealth() / boss.getMaxHealth(), 0.5D, "Scaling must preserve remaining health fraction");
        b.standAt(c.point(21, 1, 10));
        d.standAt(c.point(21, 1, 12));
        boss.tick();
        var replacement = WaterTestScene.player(c, 14, 1, 10);
        WaterTestScene.ticks(60, boss::tick);
        c.near(boss.getMaxHealth(), 160, "Departures and replacement players must not inflate an existing peak");
        c.expect(boss.getCombatParticipants().containsAll(List.of(a, replacement)), "Remaining players must keep combat active");
        c.pass();
    }

    private static void persistence(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_MAX_HEALTH, 800.0D);
        c.set(WaterSheeperConfig.WATER_HEALTH_PER_ADDITIONAL_PLAYER, 75.0D);
        CompoundTag tag = new CompoundTag();
        boss.addAdditionalSaveData(tag);
        tag.getCompound("WaterArenaCombat").putInt("HighestPlayerCount", 3);
        boss.readAdditionalSaveData(tag);
        boss.refreshConfiguredHealth();
        c.near(boss.getMaxHealth(), 1024, "Player scaling must obey Minecraft's health ceiling");
        var origin = boss.getArenaCenter();
        boss.setPos(boss.position().add(3, 2, 0));
        CompoundTag saved = new CompoundTag();
        boss.addAdditionalSaveData(saved);
        boss.readAdditionalSaveData(saved);
        c.expect(boss.getArenaCenter().equals(origin), "Reload must preserve the placed origin independently of movement");
        c.near(boss.getMaxHealth(), 1024, "Reload must retain the encounter's peak player count");
        c.pass();
    }
}
