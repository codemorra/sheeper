package de.codemorra.sheeper.entity;

import java.util.List;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.gametest.GameTestContext;
import de.codemorra.sheeper.gametest.SheeperGameTests.Scenario;
import de.codemorra.sheeper.gametest.TestPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
        tests.add(new Scenario("water_fountain_fixed_warning_single_hit_and_reload", WaterAttackGameTests::fountain));
        tests.add(new Scenario("water_fountain_keeps_default_attack", WaterAttackGameTests::fountainScheduling));
        tests.add(new Scenario("water_maelstrom_initial_cooldown_and_melee_delay", WaterAttackGameTests::maelstromScheduling));
        tests.add(new Scenario("water_maelstrom_phases_and_immunity", WaterAttackGameTests::maelstrom));
        tests.add(new Scenario("water_wave_contact_safe_ring_and_damage_end", WaterAttackGameTests::wave));
        tests.add(new Scenario("water_wave_transport_steps_and_walls", WaterAttackGameTests::transport));
    }

    private static void defaultCycle(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_PROJECTILE_DURATION_MIN, 0.3D);
        c.set(WaterSheeperConfig.WATER_PROJECTILE_DURATION_MAX, 0.3D);
        c.set(WaterSheeperConfig.WATER_PROJECTILE_INTERVAL, 0.15D);
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
        c.expect(c.entities(WaterProjectile.class).size() == 2, "The configured interval must release the next shot");
        a.standAt(c.point(12, 1, 10));
        boss.setTarget(attack.tickTarget(List.of(a), true));
        WaterTestScene.ticks(4, attack::tickProjectiles);
        c.expect(attack.isClosingOnTarget() && c.entities(WaterProjectile.class).size() == 2,
                "Close range must switch to approach mode and suppress shots");
        a.standAt(c.point(15, 1, 10));
        attack.tickTarget(List.of(a), true);
        c.expect(!attack.isClosingOnTarget(), "Moving beyond the melee distance must restore ranged mode");
        c.expect(attack.tickTarget(List.of(b), true) == b, "Losing the current participant must reselect immediately");
        WaterTestScene.ticks(3, () -> attack.tickTarget(List.of(b), true));
        c.expect(!attack.isFiring(), "Configured firing duration must end in a selection pause");
        WaterDefaultAttack restored = new WaterDefaultAttack(boss);
        restored.load(attack.save());
        WaterTestScene.ticks(40, () -> boss.setTarget(restored.tickTarget(List.of(b), true)));
        c.expect(restored.isFiring() && boss.getTarget() == b, "A saved pause must resume the firing cycle");
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
        c.near(boss.getBeamOffset().length(), 12, "Beam range must be independent of target distance");
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
        restored.tick(player);
        c.expect(boss.getBeamOffset().length() < 3, "Solid terrain must clip the beam before players behind it");
        restored.stop();
        c.near(boss.getBeamOffset().length(), 0, "Stopping a beam must clear its collision segment");
        c.pass();
    }

    /** Seed other specials on cooldown to isolate one scheduler choice without random sampling. */
    private static WaterSpecialAttacks specials(WaterSheeperEntity boss, String ready) {
        WaterSpecialAttacks attacks = new WaterSpecialAttacks(boss);
        CompoundTag state = new CompoundTag();
        state.putInt("BeamCooldown", ready.equals("beam") ? 0 : 1000);
        state.putInt("FountainCooldown", ready.equals("fountain") ? 0 : 1000);
        state.putInt("MaelstromCooldown", ready.equals("maelstrom") ? -1 : 1000);
        attacks.load(state);
        return attacks;
    }

    private static WaterDefaultAttack firing(WaterSheeperEntity boss, TestPlayer player) {
        WaterDefaultAttack attack = new WaterDefaultAttack(boss);
        attack.resumeAfterSpecial();
        boss.setTarget(attack.tickTarget(List.of(player), true));
        return attack;
    }

    private static void beamScheduling(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        c.set(WaterSheeperConfig.WATER_BEAM_PREPARATION, 0.1D);
        c.set(WaterSheeperConfig.WATER_BEAM_DURATION_MIN, 0.5D);
        c.set(WaterSheeperConfig.WATER_BEAM_DURATION_MAX, 0.5D);
        c.set(WaterSheeperConfig.WATER_BEAM_COOLDOWN, 1.4D);
        c.set(WaterSheeperConfig.WATER_SPECIAL_RECOVERY, 0.2D);
        c.set(WaterSheeperConfig.WATER_SPECIAL_DEFAULT_ONLY_MIN, 0.3D);
        c.set(WaterSheeperConfig.WATER_SPECIAL_DEFAULT_ONLY_MAX, 0.3D);
        var player = WaterTestScene.player(c, 12, 1, 10);
        WaterDefaultAttack normal = firing(boss, player);
        WaterSpecialAttacks specials = specials(boss, "beam");
        specials.tryStart(player, normal);
        c.expect(!specials.isBeamActive(), "Beam must not start against a melee target");
        player.standAt(c.point(15, 1, 10));
        normal.tickTarget(List.of(player), true);
        specials.tryStart(player, normal);
        c.expect(specials.isBeamActive() && specials.suppressesDefault() && specials.locksMovement(),
                "An eligible beam must exclusively own firing and movement");
        player.standAt(c.point(12, 1, 10));
        specials.tickAttack();
        c.expect(specials.isBeamActive(), "Entering melee after beam start must not cancel it");
        WaterTestScene.ticks(11, specials::tickAttack);
        c.expect(!specials.isBeamActive() && specials.suppressesDefault() && !specials.locksMovement(),
                "Beam completion must unlock movement while retaining quiet recovery");
        WaterTestScene.ticks(4, () -> specials.tickTimers(List.of(player), normal));
        c.expect(!specials.suppressesDefault(), "Default combat must resume after configured recovery");
        player.standAt(c.point(15, 1, 10));
        normal.tickTarget(List.of(player), true);
        WaterTestScene.ticks(6, () -> specials.tickTimers(List.of(player), normal));
        specials.tryStart(player, normal);
        c.expect(!specials.isBeamActive(), "Default-only recovery must not bypass the longer special cooldown");
        WaterSpecialAttacks restored = new WaterSpecialAttacks(boss);
        restored.load(specials.save());
        WaterTestScene.ticks(18, () -> restored.tickTimers(List.of(player), normal));
        restored.tryStart(player, normal);
        c.expect(restored.isBeamActive(), "The saved configured cooldown must eventually permit another beam");
        c.pass();
    }

    private static void fountain(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        WaterTestScene.fill(c, boss);
        // Remove physical support: this column must start on the virtual arena floor.
        c.setBlock(14, 0, 10, Blocks.AIR);
        c.set(WaterSheeperConfig.WATER_FOUNTAIN_INTERVAL_MIN, 0.05D);
        c.set(WaterSheeperConfig.WATER_FOUNTAIN_INTERVAL_MAX, 0.05D);
        c.set(WaterSheeperConfig.WATER_FOUNTAIN_SIZE, 1);
        c.set(WaterSheeperConfig.WATER_FOUNTAIN_ARENA_COUNT, 1);
        c.set(WaterSheeperConfig.WATER_FOUNTAIN_WARNING, 0.2D);
        c.set(WaterSheeperConfig.WATER_FOUNTAIN_ACTIVE, 0.5D);
        c.set(WaterSheeperConfig.WATER_FOUNTAIN_DAMAGE, 3.0D);
        var player = WaterTestScene.player(c, 14, 2, 10);
        WaterFountainAttack attack = new WaterFountainAttack(boss);
        attack.start();
        attack.tick(List.of(player), 100);
        CompoundTag announced = attack.save();
        var columns = announced.getList("Currents", Tag.TAG_COMPOUND);
        c.expect(!columns.isEmpty(), "A virtual floor must support a warned fountain");
        long position = columns.getCompound(0).getLong("Pos");
        player.standAt(c.point(16, 2, 10));
        WaterTestScene.ticks(4, () -> attack.tick(List.of(player), 0));
        c.near(player.getHealth(), 20, "Leaving a warning must dodge its fixed column");
        c.expect(attack.save().getList("Currents", Tag.TAG_COMPOUND).getCompound(0).getLong("Pos") == position,
                "The warning must not follow its former target");
        player.standAt(c.point(14, 2, 10));
        attack.tick(List.of(player), 0);
        c.near(player.getHealth(), 17, "Column contact must deal its configured one-time damage");
        c.expect(player.getDeltaMovement().y > 0, "Fountain contact must start upward transport");
        WaterFountainAttack restored = new WaterFountainAttack(boss);
        restored.load(attack.save());
        player.invulnerableTime = 0;
        WaterTestScene.ticks(10, () -> restored.tick(List.of(player), 0));
        c.near(player.getHealth(), 17, "Reload and repeated column contact must not repeat damage");
        double top = boss.getArenaWaterSurfaceY() - player.getBbHeight() - 0.15D;
        player.standAt(new Vec3(player.getX(), top, player.getZ()));
        restored.tick(List.of(player), 0);
        c.expect(!restored.hasActiveEffects() && player.getDeltaMovement().y == 0,
                "Transport must release at the captured arena ceiling after the column ends");
        c.pass();
    }

    private static void fountainScheduling(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        var player = WaterTestScene.player(c, 15, 1, 10);
        var normal = firing(boss, player);
        var specials = specials(boss, "fountain");
        specials.tryStart(player, normal);
        c.expect(!specials.suppressesDefault() && !specials.locksMovement(),
                "Fountains must preserve default firing and movement");
        normal.tickProjectiles();
        c.expect(c.entities(WaterProjectile.class).size() == 1, "Default fire must actually continue during fountains");
        CompoundTag state = specials.save();
        state.putInt("BeamCooldown", 0);
        state.putInt("MaelstromCooldown", 0);
        specials.load(state);
        specials.tryStart(player, normal);
        c.expect(!specials.isBeamActive() && !specials.isMaelstromActive(),
                "Active fountains must exclude other ready specials");
        c.pass();
    }

    private static void maelstromScheduling(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        WaterTestScene.fill(c, boss);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_COOLDOWN, 1.0D);
        c.set(WaterSheeperConfig.WATER_MAELSTROM_MELEE_DELAY, 1.5D);
        var player = WaterTestScene.player(c, 12, 1, 10);
        var normal = firing(boss, player);
        var specials = specials(boss, "maelstrom");
        for (int tick = 0; tick < 20; tick++) {
            specials.tickTimers(List.of(player), normal);
            normal.tickTarget(List.of(player), true);
            specials.tryStart(player, normal);
        }
        c.expect(!specials.isMaelstromActive(), "Maelstrom must first earn its initial cooldown");
        specials.tickTimers(List.of(player), normal);
        specials.tryStart(player, normal);
        c.expect(!specials.isMaelstromActive(), "Melee eligibility must require continuous configured time");
        WaterTestScene.ticks(10, () -> normal.tickTarget(List.of(player), true));
        specials.tryStart(player, normal);
        c.expect(specials.isMaelstromActive() && specials.suppressesDefault() && specials.isMaelstromImmune(),
                "Earned cooldown and melee duration must permit an exclusive, immune Maelstrom");
        specials.tickTimers(List.of(), normal);
        c.expect(!specials.isMaelstromActive() && !specials.isMaelstromImmune(),
                "An empty arena must abort Maelstrom and release immunity");
        c.pass();
    }

    private static void maelstrom(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        WaterTestScene.fill(c, boss);
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
        var near = WaterTestScene.player(c, 11, 2, 10);
        var far = WaterTestScene.player(c, 14, 2, 10);
        var safe = WaterTestScene.player(c, 17, 2, 10);
        List<net.minecraft.server.level.ServerPlayer> players = List.of(near, far, safe);
        WaterMaelstromWave wave = new WaterMaelstromWave(boss);
        wave.start(boss.position());
        wave.tick(players);
        c.near(near.getHealth(), 20, "No damage may precede wave contact");
        wave.tick(players);
        c.near(near.getHealth(), 18, "First wave contact must deal half a second of normal damage immediately");
        c.near(far.getHealth(), 20, "Distant participants must wait for their own contact");
        WaterTestScene.ticks(6, () -> wave.tick(players));
        c.near(far.getHealth(), 18, "The expanding wave must reach distant participants later");
        c.near(safe.getHealth(), 20, "The configured outer ring must remain safe");
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
}
