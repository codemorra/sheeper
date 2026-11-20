package de.codemorra.sheeper.entity;

import java.util.List;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.gametest.GameTestContext;
import de.codemorra.sheeper.gametest.SheeperGameTests.Scenario;
import de.codemorra.sheeper.gametest.TestPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Focused controller regressions, using explicit short timing and damage values. */
public final class WaterAttackGameTests {
    private WaterAttackGameTests() {
    }

    public static void add(List<Scenario> tests) {
        tests.add(new Scenario("water_default_cycle_target_loss_and_melee", WaterAttackGameTests::defaultCycle));
        tests.add(new Scenario("water_proximity_discharge_hits_group", WaterAttackGameTests::proximity));
        tests.add(new Scenario("water_beam_extension_contact_and_reload", WaterAttackGameTests::beam));
        tests.add(new Scenario("water_beam_entry_recovery_and_cooldown", WaterAttackGameTests::beamScheduling));
        tests.add(new Scenario("water_maelstrom_initial_cooldown_and_melee_delay", WaterAttackGameTests::maelstromScheduling));
        tests.add(new Scenario("water_maelstrom_phases_and_immunity", WaterAttackGameTests::maelstrom));
        tests.add(new Scenario("water_wave_contact_safe_ring_and_damage_end", WaterAttackGameTests::wave));
        tests.add(new Scenario("water_wave_transport_steps_and_walls", WaterAttackGameTests::transport));
        tests.add(new Scenario("water_mine_blast_and_nondamaging_pressure", WaterAttackGameTests::minePressure));
        tests.add(new Scenario("water_mine_count_scales_without_shortening_attack", WaterAttackGameTests::mineCount));
    }

    private static void defaultCycle(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_PROJECTILE_DURATION_MIN, 0.3D);
        c.set(WaterSheeperConfig.WATER_PROJECTILE_DURATION_MAX, 0.3D);
        c.set(WaterSheeperConfig.WATER_PROJECTILE_INTERVAL, 0.11);
        var a = WaterTestScene.player(c, 15, 1, 10);
        var b = WaterTestScene.player(c, 10, 1, 15);
        WaterDefaultAttack attack = new WaterDefaultAttack(boss);
        for (int tick = 0; tick < 39; tick++) {
            boss.setTarget(attack.tickTarget(List.of(a), true));
            attack.tickProjectiles();
        }
        c.expect(c.entities(WaterProjectile.class).isEmpty(), "Entry pause must precede the first projectile");
        boss.setTarget(attack.tickTarget(List.of(a), true));
        attack.tickProjectiles();
        c.expect(c.entities(WaterProjectile.class).size() == 1, "The first firing tick must shoot immediately");
        WaterTestScene.ticks(2, attack::tickProjectiles);
        c.expect(c.entities(WaterProjectile.class).size() == 1, "Shots must respect the scene's interval");
        attack.tickProjectiles();
        c.expect(c.entities(WaterProjectile.class).size() == 2, "A fractional interval must round up before the next shot");
        a.standAt(c.point(12, 1, 10));
        boss.setTarget(attack.tickTarget(List.of(a), true));
        WaterTestScene.ticks(4, attack::tickProjectiles);
        c.expect(attack.isClosingOnTarget() && c.entities(WaterProjectile.class).size() == 2,
                "Close range must switch to approach mode and suppress shots");
        a.standAt(c.point(15, 1, 10));
        attack.tickTarget(List.of(a), true);
        c.expect(!attack.isClosingOnTarget(), "Moving beyond the melee distance must restore ranged mode");
        WaterTestScene.ticks(3, () -> attack.tickTarget(List.of(a, b), true));
        c.expect(attack.tickTarget(List.of(a, b), true) == b && attack.isFiring(),
                "The midpoint must switch to another player without a firing pause");
        c.expect(attack.tickTarget(List.of(a), true) == a, "Losing the current participant must reselect immediately");
        WaterDefaultAttack restored = new WaterDefaultAttack(boss);
        restored.load(attack.save());
        WaterTestScene.ticks(4, () -> restored.tickTarget(List.of(a), true));
        c.expect(restored.isFiring(), "Reload must preserve the remaining second interval");
        restored.tickTarget(List.of(a), true);
        c.expect(restored.isComplete() && !restored.isFiring(),
                "The second interval must yield to the special attack check");

        // Exercise the entity's scheduler too. The mine cooldown expires partway through the fallback interval.
        b.standAt(c.point(21, 1, 10));
        c.entities(WaterProjectile.class).forEach(Entity::discard);
        c.set(WaterSheeperConfig.WATER_MOVEMENT_SPEED, 0.0);
        CompoundTag combat = new CompoundTag();
        boss.addAdditionalSaveData(combat);
        combat.getCompound("WaterDefaultAttack").putInt("PhaseTicks", 1);
        CompoundTag specials = combat.getCompound("WaterSpecialAttacks");
        specials.putInt("MineCooldown", 15);
        specials.putInt("BeamCooldown", 100);
        specials.putInt("MaelstromCooldown", 100);
        boss.readAdditionalSaveData(combat);
        boss.setNoAi(false);
        WaterTestScene.ticks(12, boss::tick);
        int fired = c.entities(WaterProjectile.class).size();
        WaterTestScene.ticks(6, boss::tick);
        c.expect(c.entities(WaterProjectile.class).size() > fired && !boss.isPerformingWaterMines()
                && boss.getTarget() == a, "A fallback interval must keep shooting until the next special check");
        boss.tick();
        c.expect(boss.isPerformingWaterMines(), "The next special check must start the newly available mines");
        c.pass();
    }

    private static void proximity(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_PROXIMITY_PUSH_RADIUS, 3.0D);
        c.set(WaterSheeperConfig.WATER_PROXIMITY_PUSH_DELAY, 0.2D);
        c.set(WaterSheeperConfig.WATER_PROXIMITY_PUSH_DAMAGE, 2.0D);
        c.set(WaterSheeperConfig.WATER_PROXIMITY_PUSH_STRENGTH, 0.75D);
        c.set(WaterSheeperConfig.WATER_PROXIMITY_PUSH_UPWARD_STRENGTH, 0.25D);
        var a = WaterTestScene.player(c, 12, 1, 10);
        var b = WaterTestScene.player(c, 8, 1, 10);
        var outside = WaterTestScene.player(c, 15, 1, 10);
        WaterProximityPush push = new WaterProximityPush(boss);
        WaterTestScene.ticks(3, () -> push.tick(List.of(a, outside)));
        c.near(a.getHealth(), 20, "Push must wait for its configured delay");
        push.tick(List.of(a, b, outside));
        c.near(a.getHealth(), 18, "Triggering player must take one hit");
        c.near(b.getHealth(), 18, "A newly nearby player behind the boss must share the discharge");
        c.near(outside.getHealth(), 20, "Players beyond push reach must remain unharmed");
        c.expect(a.getDeltaMovement().x > 0 && b.getDeltaMovement().x < 0,
                "Each caught player must be pushed away from the boss");
        c.near(b.getDeltaMovement().y, 0.25D, "Push must use the configured vertical strength");
        WaterTestScene.ticks(3, () -> push.tick(List.of(a)));
        push.tick(List.of());
        a.invulnerableTime = 0;
        WaterTestScene.ticks(3, () -> push.tick(List.of(a)));
        c.expect(a.damageEvents.size() == 1, "Leaving reach must clear the previous reaction timer");
        c.pass();
    }

    private static void beam(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_BEAM_PREPARATION, 0.1D);
        c.set(WaterSheeperConfig.WATER_BEAM_EXTENSION, 0.2D);
        c.set(WaterSheeperConfig.WATER_BEAM_RANGE, 12.0D);
        c.set(WaterSheeperConfig.WATER_BEAM_DURATION_MIN, 3.0D);
        c.set(WaterSheeperConfig.WATER_BEAM_DURATION_MAX, 3.0D);
        c.set(WaterSheeperConfig.WATER_BEAM_DAMAGE_PER_SECOND, 2.0D);
        var player = WaterTestScene.player(c, 14, 1, 10);
        WaterBeamAttack beam = new WaterBeamAttack(boss);
        beam.start();
        WaterTestScene.ticks(2, () -> beam.tick(player));
        c.near(boss.getBeamOffset().length(), 0, "Warning must not expose a damaging beam");
        beam.tick(player);
        c.near(boss.getBeamOffset().length(), 3, "Beam must extend gradually at the configured speed");
        beam.tick(player);
        c.near(player.getHealth(), 18, "First beam contact must deal immediate damage");
        player.standAt(c.point(16, 1, 10));
        WaterTestScene.ticks(2, () -> beam.tick(player));
        c.near(boss.getBeamOrigin().add(boss.getBeamOffset()).x,
                boss.getArenaCenter().getX() + 0.5D + boss.getArenaRadius(),
                "Beam range must be independent of target distance and stop at the arena edge");
        WaterBeamAttack restored = new WaterBeamAttack(boss);
        restored.load(beam.save());
        // Advance vanilla immunity explicitly; controller ticks alone do not tick players.
        player.invulnerableTime = 0;
        WaterTestScene.ticks(17, () -> restored.tick(player));
        c.near(player.getHealth(), 18, "Reload and target movement must not bypass the one-second damage interval");
        restored.tick(player);
        c.near(player.getHealth(), 16, "Continuous beam contact must hit again after one second");
        for (int y = 1; y <= 4; y++) {
            c.setBlock(12, y, 10, Blocks.STONE);
        }
        WaterTestScene.ticks(20, () -> restored.tick(player));
        c.expect(restored.tick(player), "A wall must not shorten the beam's firing duration");
        c.expect(boss.getBeamOffset().length() < 3, "Solid terrain must clip the beam before players behind it");
        c.near(player.getHealth(), 16, "The beam must not damage a player through the wall");
        restored.stop();
        c.near(boss.getBeamOffset().length(), 0, "Stopping a beam must clear its collision segment");
        c.pass();
    }

    /** Seed other specials on cooldown to isolate one scheduler choice without random sampling. */
    private static WaterSpecialAttacks specials(WaterSheeperEntity boss, String ready) {
        WaterSpecialAttacks attacks = new WaterSpecialAttacks(boss);
        CompoundTag state = new CompoundTag();
        state.putInt("MineCooldown", 1000);
        state.putInt("BeamCooldown", ready.equals("beam") ? 0 : 1000);
        state.putInt("MaelstromCooldown", ready.equals("maelstrom") ? -1 : 1000);
        attacks.load(state);
        return attacks;
    }

    private static WaterDefaultAttack completedProjectilePhase(GameTestContext c, WaterSheeperEntity boss,
            TestPlayer player) {
        c.set(WaterSheeperConfig.WATER_PROJECTILE_DURATION_MIN, 0.1D);
        c.set(WaterSheeperConfig.WATER_PROJECTILE_DURATION_MAX, 0.1D);
        WaterDefaultAttack attack = new WaterDefaultAttack(boss);
        attack.resumeAfterSpecial();
        WaterTestScene.ticks(5, () -> boss.setTarget(attack.tickTarget(List.of(player), true)));
        return attack;
    }

    private static void beamScheduling(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_BEAM_PREPARATION, 0.1D);
        c.set(WaterSheeperConfig.WATER_BEAM_DURATION_MIN, 0.5D);
        c.set(WaterSheeperConfig.WATER_BEAM_DURATION_MAX, 0.5D);
        c.set(WaterSheeperConfig.WATER_BEAM_COOLDOWN, 1.4D);
        c.set(WaterSheeperConfig.WATER_SPECIAL_RECOVERY, 0.2D);
        var player = WaterTestScene.player(c, 12, 1, 10);
        WaterDefaultAttack normal = completedProjectilePhase(c, boss, player);
        WaterSpecialAttacks specials = specials(boss, "beam");
        specials.tryStart(normal);
        c.expect(!specials.isBeamActive(), "Beam must not start against a melee target");
        player.standAt(c.point(15, 1, 10));
        for (int y = 1; y <= 4; y++) {
            c.setBlock(13, y, 10, Blocks.STONE);
        }
        normal.tickTarget(List.of(player), true);
        specials.tryStart(normal);
        c.expect(specials.isBeamActive() && specials.suppressesDefault() && specials.locksMovement(),
                "An eligible beam must start behind cover and exclusively own firing and movement");
        player.standAt(c.point(12, 1, 10));
        specials.tickAttack();
        c.expect(specials.isBeamActive(), "Entering melee after beam start must not cancel it");
        WaterTestScene.ticks(11, specials::tickAttack);
        c.expect(!specials.isBeamActive() && specials.suppressesDefault() && !specials.locksMovement(),
                "Beam completion must unlock movement while retaining quiet recovery");
        WaterTestScene.ticks(4, () -> specials.tickTimers(List.of(player), normal));
        c.expect(!specials.suppressesDefault(), "Default combat must resume after configured recovery");
        player.standAt(c.point(15, 1, 10));
        WaterTestScene.ticks(5, () -> normal.tickTarget(List.of(player), true));
        WaterTestScene.ticks(6, () -> specials.tickTimers(List.of(player), normal));
        specials.tryStart(normal);
        c.expect(!specials.isBeamActive(), "Finishing a projectile phase must not bypass the beam cooldown");
        WaterSpecialAttacks restored = new WaterSpecialAttacks(boss);
        CompoundTag state = specials.save();
        state.putInt("MineCooldown", 0);
        restored.load(state);
        WaterTestScene.ticks(18, () -> restored.tickTimers(List.of(player), normal));
        restored.tryStart(normal);
        c.expect(restored.isMineAttackActive() && !restored.isBeamActive(),
                "Reload must exclude the previous special even after its cooldown expires");
        restored.tickTimers(List.of(), normal);
        c.expect(restored.suppressesDefault() && !restored.locksMovement(),
                "Mines must use the same quiet recovery as the beam");
        c.pass();
    }

    private static void maelstromScheduling(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_COOLDOWN, 1.0D);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_MELEE_DELAY, 1.5D);
        var player = WaterTestScene.player(c, 12, 1, 10);
        var normal = completedProjectilePhase(c, boss, player);
        var specials = specials(boss, "maelstrom");
        for (int tick = 0; tick < 20; tick++) {
            specials.tickTimers(List.of(player), normal);
            normal.tickTarget(List.of(player), true);
            specials.tryStart(normal);
        }
        c.expect(!specials.isMaelstromActive(), "Maelstrom must first earn its initial cooldown");
        specials.tickTimers(List.of(player), normal);
        specials.tryStart(normal);
        c.expect(!specials.isMaelstromActive(), "Melee eligibility must require continuous configured time");
        WaterTestScene.ticks(10, () -> normal.tickTarget(List.of(player), true));
        specials.tryStart(normal);
        c.expect(specials.isMaelstromActive() && specials.suppressesDefault() && specials.isMaelstromImmune(),
                "Earned cooldown and melee duration must permit an exclusive, immune Maelstrom");
        specials.tickTimers(List.of(), normal);
        c.expect(!specials.isMaelstromActive() && !specials.isMaelstromImmune(),
                "An empty arena must abort Maelstrom and release immunity");
        c.pass();
    }

    private static void maelstrom(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_ROTATION_DURATION, 0.1D);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_ASCENT_DURATION, 0.1D);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_MEDIUM_DURATION, 0.1D);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_STRONG_DURATION, 0.1D);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_ASCENT_HEIGHT, 1.0D);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_SLAM_SPEED, 40.0D);
        c.set(WaterSheeperConfig.WATER_SPECIAL_RECOVERY, 0.2D);
        var player = WaterTestScene.player(c, 14, 1, 10);
        boss.setNoAi(false);
        var attack = new WaterMaelstromAttack(boss);
        c.expect(attack.start() && attack.isImmune(), "A clear center must start an immune preparation");
        double before = boss.getY();
        WaterTestScene.ticks(2, attack::tick);
        c.near(boss.getY(), before, "Initial rotation must not ascend yet");
        WaterTestScene.ticks(2, attack::tick);
        c.expect(boss.getY() > before && player.getDeltaMovement().x < 0,
                "Ascent must raise the boss and start pulling participants inward: before=" + before
                        + ", now=" + boss.getY() + ", player motion=" + player.getDeltaMovement() + ", state=" + attack.save());
        WaterMaelstromAttack restored = new WaterMaelstromAttack(boss);
        restored.load(attack.save());
        c.expect(restored.isImmune(), "Reload must preserve immunity during preparation");
        WaterTestScene.ticks(5, restored::tick);
        c.expect(restored.hasImpacted() && !restored.isImmune(), "Impact must immediately release immunity");
        c.expect(restored.isActive(), "Impact must retain a vulnerable recovery phase");
        WaterTestScene.ticks(4, restored::tick);
        // The scheduler releases the completed phase when tick() returns false.
        restored.finish();
        c.expect(!restored.isActive(), "Completed recovery must release the attack controller");
        c.pass();
    }

    private static void wave(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_WAVE_SPEED, 10.0D);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_TRANSPORT_SPEED, 2.0D);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_SAFE_OUTER_WIDTH, 2.0D);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_DAMAGE, 4.0D);
        c.rule(GameRules.RULE_MOBGRIEFING, true);
        var exposedLeaf = c.pos(13, 5, 10);
        var shelteredLeaf = c.pos(10, 5, 13);
        c.setBlock(13, 5, 10, Blocks.OAK_LEAVES);
        c.setBlock(10, 5, 12, Blocks.STONE);
        c.setBlock(10, 5, 13, Blocks.OAK_LEAVES);
        var near = WaterTestScene.player(c, 11, 2, 10);
        var far = WaterTestScene.player(c, 14, 2, 10);
        var safe = WaterTestScene.player(c, 17, 2, 10);
        List<net.minecraft.server.level.ServerPlayer> players = List.of(near, far, safe);
        WaterMaelstromWave wave = new WaterMaelstromWave(boss);
        wave.start(boss.position());
        wave.tick(players);
        c.near(near.getHealth(), 20, "No damage may precede wave contact");
        c.expect(c.level.getBlockState(exposedLeaf).is(Blocks.OAK_LEAVES), "Leaves must wait for the wave front");
        wave.tick(players);
        c.near(near.getHealth(), 18, "First wave contact must deal half a second of normal damage immediately");
        c.near(far.getHealth(), 20, "Distant participants must wait for their own contact");
        WaterTestScene.ticks(6, () -> wave.tick(players));
        c.near(far.getHealth(), 18, "The expanding wave must reach distant participants later");
        c.near(safe.getHealth(), 20, "The configured outer ring must remain safe");
        c.expect(c.level.getBlockState(exposedLeaf).isAir()
                && c.level.getBlockState(shelteredLeaf).is(Blocks.OAK_LEAVES),
                "Maelstrom must clear exposed leaves even high in the arena while respecting solid cover");
        WaterMaelstromWave restored = new WaterMaelstromWave(boss);
        restored.load(wave.save());
        near.invulnerableTime = 0;
        WaterTestScene.ticks(4, () -> restored.tick(players));
        c.near(near.getHealth(), 16, "An inner participant must receive additional pulses during expansion");
        far.standAt(c.point(17, 2, 10));
        far.invulnerableTime = 0;
        WaterTestScene.ticks(30, () -> restored.tick(players));
        c.near(far.getHealth(), 18, "Entering the safe ring must end damage immediately");
        float ended = near.getHealth();
        near.invulnerableTime = 0;
        WaterTestScene.ticks(20, () -> restored.tick(players));
        c.near(near.getHealth(), ended, "Damage must not linger after wave expansion finishes");
        c.pass();
    }

    private static void transport(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_SAFE_OUTER_WIDTH, 0.0D);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_TRANSPORT_SPEED, 10.0D);
        var player = WaterTestScene.player(c, 12, 1, 10);
        var transport = new WaterMaelstromTransport(boss);
        c.setBlock(13, 1, 10, Blocks.STONE);
        transport.start(player, boss.position());
        transport.tick(List.of(player));
        c.near(player.getY(), c.point(12, 2, 10).y, "Wave transport must step over a one-block obstruction");
        transport.clear();
        player.standAt(c.point(12, 1, 10));
        c.setBlock(13, 2, 10, Blocks.STONE);
        c.setBlock(13, 3, 10, Blocks.STONE);
        transport.start(player, boss.position());
        transport.tick(List.of(player));
        c.near(player.getX(), c.point(12, 1, 10).x, "A tall wall must not be crossed");
        c.near(player.getY(), c.point(12, 1, 10).y, "A tall wall must not trigger repeated upward steps");
        c.expect(player.getDeltaMovement().equals(Vec3.ZERO), "Blocked transport must release forced motion");
        c.near(player.fallDistance, 0, "Wave transport must clear fall damage");
        c.pass();
    }

    private static void minePressure(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_PROXIMITY_PUSH_STRENGTH, 0.6);
        c.set(WaterSheeperConfig.WATER_PROXIMITY_PUSH_UPWARD_STRENGTH, 0.2);
        c.rule(GameRules.RULE_MOBGRIEFING, true);
        var blastLeaf = c.pos(11, 2, 10);
        var waveLeaf = c.pos(8, 2, 10);
        var shelteredLeaf = c.pos(12, 2, 13);
        c.setBlock(11, 2, 10, Blocks.OAK_LEAVES);
        c.setBlock(8, 2, 10, Blocks.OAK_LEAVES);
        c.setBlock(12, 2, 13, Blocks.OAK_LEAVES);
        var direct = WaterTestScene.player(c, 14, 2, 10);
        var swept = WaterTestScene.player(c, 16, 2, 10);
        var sheltered = WaterTestScene.player(c, 12, 2, 14);
        direct.standAt(c.point(12, 2, 10).add(1.7, 0, 0));
        Vec3 center = c.point(12, 2, 10).add(0, 0.6, 0);
        for (int y = 1; y <= 4; y++) {
            c.setBlock(12, y, 12, Blocks.STONE);
        }
        List<net.minecraft.server.level.ServerPlayer> players = List.of(direct, swept, sheltered);
        players.forEach(player -> player.setDeltaMovement(Vec3.ZERO));

        var state = new CompoundTag();
        var entries = new ListTag();
        var mine = new CompoundTag();
        mine.putDouble("X", center.x);
        mine.putDouble("Y", center.y);
        mine.putDouble("Z", center.z);
        mine.putDouble("Radius", 1.25);
        mine.putFloat("Damage", 3);
        mine.putInt("Fuse", 4);
        mine.putInt("Age", 2);
        entries.add(mine);
        state.put("Mines", entries);
        var attack = new WaterMineAttack(boss);
        attack.load(state);

        attack.tick(players);
        c.near(direct.getHealth(), 20, "An announced mine must wait for its fuse");
        attack.tick(players);
        c.near(direct.getHealth(), 17, "A player whose body reaches the bubble edge must take damage");
        c.near(direct.getDeltaMovement().x, 0.6, "A direct hit must push away from the mine");
        c.expect(swept.getDeltaMovement().equals(Vec3.ZERO), "Pressure must take time to reach distant swimmers");
        c.expect(c.level.getBlockState(blastLeaf).isAir() && c.level.getBlockState(waveLeaf).is(Blocks.OAK_LEAVES),
                "The blast must clear nearby leaves immediately while distant ones wait for the front");
        direct.setDeltaMovement(Vec3.ZERO);
        WaterTestScene.ticks(3, () -> attack.tick(players));
        c.near(swept.getHealth(), 20, "The outer pressure wave must not deal damage");
        c.near(swept.getDeltaMovement().x, 0.6, "The pressure wave must push as hard as a direct hit");
        c.expect(direct.getDeltaMovement().equals(Vec3.ZERO), "The pressure wave must not push a direct victim again");
        c.expect(sheltered.getDeltaMovement().equals(Vec3.ZERO), "Solid cover must block a mine's pressure");
        c.expect(c.level.getBlockState(waveLeaf).isAir() && c.level.getBlockState(shelteredLeaf).is(Blocks.OAK_LEAVES),
                "The pressure front must clear reachable leaves and respect solid cover");
        c.pass();
    }

    private static void mineCount(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_MINES_DURATION_MIN, 1.0);
        c.set(WaterSheeperConfig.WATER_MINES_DURATION_MAX, 1.0);
        c.set(WaterSheeperConfig.WATER_MINES_FUSE, 5.0);
        c.set(WaterSheeperConfig.WATER_MINES_DIAMETER, 0.75);
        int sparse = spawnedMines(c, boss, 0.25);
        int dense = spawnedMines(c, boss, 1.25);
        c.expect(sparse > 0 && dense > sparse * 2,
                "A higher count multiplier must place more mines within the same firing window");
        c.pass();
    }

    private static int spawnedMines(GameTestContext c, WaterSheeperEntity boss, double multiplier) {
        c.set(WaterSheeperConfig.WATER_MINES_COUNT_MULTIPLIER, multiplier);
        var attack = new WaterMineAttack(boss);
        attack.start();
        WaterTestScene.ticks(19, () -> attack.tick(List.of()));
        c.expect(attack.save().getInt("SpawnTicks") > 0, "The multiplier must not end spawning early");
        attack.tick(List.of());
        CompoundTag state = attack.save();
        c.expect(state.getInt("SpawnTicks") == 0 && attack.isActive(),
                "The configured window must end while announced mines keep their fuses");
        return state.getList("Mines", Tag.TAG_COMPOUND).size();
    }
}
