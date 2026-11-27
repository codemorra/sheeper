package de.codemorra.sheeper.entity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.gametest.GameTestContext;
import de.codemorra.sheeper.gametest.SheeperGameTests.Scenario;
import de.codemorra.sheeper.gametest.TestPlayer;
import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.world.LightningStormData;
import de.codemorra.sheeper.world.LightningTornado;
import de.codemorra.sheeper.world.LightningTornadoMotion;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Storm behavior in small, isolated scenes with explicit balance settings. */
public final class LightningGameTests {
    private LightningGameTests() {
    }

    public static void add(List<Scenario> tests) {
        tests.add(new Scenario("lightning_spawn_requires_room", LightningGameTests::spawnClearance));
        tests.add(new Scenario("lightning_arena_gates_combat_and_indirect_damage", LightningGameTests::admission));
        tests.add(new Scenario("lightning_health_scales_and_recovers_after_grace", LightningGameTests::health));
        tests.add(new Scenario("lightning_teleport_removes_hitbox_until_landing", LightningGameTests::teleport));
        tests.add(new Scenario("lightning_death_keeps_storm_then_drains", LightningGameTests::death));
        tests.add(new Scenario("lightning_water_conducts_only_inside_arena", LightningGameTests::waterConduction));
        tests.add(new Scenario("lightning_rows_fire_together_and_then_teleport", LightningGameTests::rowSalvo));
        tests.add(new Scenario("lightning_row_teleports_prefer_distance_from_previous_position",
                LightningGameTests::rowTeleportDistance));
        tests.add(new Scenario("lightning_fan_charges_rises_and_lands_in_lightning_form", LightningGameTests::fanFlight));
        tests.add(new Scenario("lightning_tornado_retains_rider_during_overlap", LightningGameTests::tornadoOverlap));
        tests.add(new Scenario("lightning_rod_waits_and_shelters_players", LightningGameTests::rod));
    }

    /** Leaves only the hazards enabled explicitly by the scene. */
    private static void configure(GameTestContext c) {
        c.difficulty(Difficulty.NORMAL);
        c.set(LightningSheeperConfig.LIGHTNING_SIZE, 1.0);
        c.set(LightningSheeperConfig.LIGHTNING_MAX_HEALTH, 100.0);
        c.set(LightningSheeperConfig.LIGHTNING_COMBAT_RADIUS, 8.0);
        c.set(LightningSheeperConfig.LIGHTNING_ARENA_HEIGHT, 7);
        c.set(LightningSheeperConfig.LIGHTNING_ARENA_DEPTH, 1);
        c.set(LightningSheeperConfig.LIGHTNING_MOVEMENT_EDGE_DISTANCE, 1.0);
        c.set(LightningSheeperConfig.LIGHTNING_BOUNDARY_SLOWDOWN_WIDTH, 1.0);
        c.set(LightningSheeperConfig.LIGHTNING_PREPARATION_DURATION, 0.0);
        c.set(LightningSheeperConfig.LIGHTNING_COMBAT_ENTRY_DELAY, 0.0);
        c.set(LightningSheeperConfig.LIGHTNING_SPAWN_PUSH_SPEED, 0.0);
        c.set(LightningSheeperConfig.LIGHTNING_AMBIENT_STRIKES_ENABLED, false);
        c.set(LightningSheeperConfig.LIGHTNING_WIND_ENABLED, false);
        c.set(LightningSheeperConfig.LIGHTNING_CRACKLE_ENABLED, false);
        c.set(LightningSheeperConfig.LIGHTNING_CHARGE_RETALIATION_ENABLED, false);
        c.set(LightningSheeperConfig.LIGHTNING_DODGE_ENABLED, false);
        c.set(LightningSheeperConfig.LIGHTNING_LINE_ENABLED, false);
        c.set(LightningSheeperConfig.LIGHTNING_RING_ENABLED, false);
        c.set(LightningSheeperConfig.LIGHTNING_TORNADO_ENABLED, false);
        c.set(LightningSheeperConfig.LIGHTNING_FAN_ENABLED, false);
        c.set(LightningSheeperConfig.LIGHTNING_ROD_ENABLED, false);
    }

    private static LightningSheeperEntity boss(GameTestContext c) {
        var boss = c.spawn(ModEntities.LIGHTNING_SHEEPER.get(), 12, 1, 12);
        boss.setNoAi(true);
        boss.tick();
        return boss;
    }

    private static void spawnClearance(GameTestContext c) {
        configure(c);
        c.set(LightningSheeperConfig.LIGHTNING_SIZE, 2.0);
        c.set(LightningSheeperConfig.LIGHTNING_COMBAT_RADIUS, 1.0);
        var rejected = boss(c);
        c.expect(rejected.isRemoved() && LightningStormData.get(c.level).getArena(rejected.getUUID()) == null,
                "A storm too narrow for the scaled boss must not be registered");

        c.set(LightningSheeperConfig.LIGHTNING_COMBAT_RADIUS, 8.0);
        c.setBlock(12, 1, 12, Blocks.STONE);
        var placed = boss(c);
        var storm = LightningStormData.get(c.level).getArena(placed.getUUID());
        c.expect(!placed.isRemoved() && storm != null && storm.center().equals(placed.blockPosition())
                && !placed.blockPosition().equals(c.pos(12, 1, 12)),
                "An obstructed spawn must relocate to a clear nearby position and capture it as the storm center");
        c.pass();
    }

    private static void admission(GameTestContext c) {
        configure(c);
        c.set(LightningSheeperConfig.LIGHTNING_STORM_ENABLED, true);
        c.set(LightningSheeperConfig.LIGHTNING_PREPARATION_DURATION, 0.5);
        var boss = boss(c);
        var player = c.player(15, 1, 12);
        var melee = DamageSource.playerAttack(player);
        c.expect(!boss.hurt(melee, 5) && boss.getCombatParticipants().isEmpty(),
                "Players must not enter combat or damage the boss while the storm builds");
        player.standAt(c.point(21, 1, 12));
        c.at(12, () -> {
            player.standAt(c.point(12, 1, 12).add(7.5, 0, 0));
            var arrow = DamageSource.arrow(new Arrow(c.level, player), null);
            c.expect(boss.getCombatParticipants().isEmpty() && boss.isInvulnerableTo(arrow),
                    "Crossing only the outer part of the storm must not grant combat or arrow damage");
            player.standAt(c.point(15, 1, 12));
            c.expect(boss.getCombatParticipants().contains(player) && boss.hurt(arrow, 5),
                    "Past the inner edge the original projectile owner must be allowed to damage the boss");
            player.standAt(c.point(12, 1, 12).add(7.5, 0, 0));
            c.expect(boss.getCombatParticipants().contains(player) && !boss.isInvulnerableTo(arrow),
                    "The side band must still count on the way out until the actual boundary");
            player.standAt(c.point(21, 1, 12));
            c.expect(boss.isInvulnerableTo(arrow) && boss.getCombatParticipants().isEmpty(),
                    "An in-flight arrow must lose permission as soon as its owner leaves");
            player.standAt(c.point(15, 1, 12));
            player.setGameMode(GameType.CREATIVE);
            c.expect(!boss.isInvulnerableTo(melee) && boss.getCombatParticipants().isEmpty(),
                    "Creative attacks inside are allowed without starting combat");
            player.setGameMode(GameType.SURVIVAL);
            c.expect(boss.getCombatParticipants().contains(player), "The player must be readmitted in survival");
            var inner = c.point(15, 1, 12);
            player.standAt(new Vec3(inner.x, boss.getArenaMaximumY(), inner.z));
            c.expect(boss.isInvulnerableTo(arrow) && boss.getCombatParticipants().isEmpty(),
                    "Passing through the open storm ceiling must end arena participation");
            c.pass();
        });
    }

    private static void health(GameTestContext c) {
        configure(c);
        c.set(LightningSheeperConfig.LIGHTNING_HEALTH_PER_ADDITIONAL_PLAYER, 25.0);
        c.set(LightningSheeperConfig.LIGHTNING_EMPTY_ARENA_RECOVERY_ENABLED, true);
        c.set(LightningSheeperConfig.LIGHTNING_EMPTY_ARENA_GRACE, 0.3);
        c.set(LightningSheeperConfig.LIGHTNING_EMPTY_ARENA_REGENERATION, 20.0);
        c.set(LightningSheeperConfig.LIGHTNING_MOVEMENT_EDGE_DISTANCE, 7.0);
        var boss = boss(c);
        var first = c.player(9, 1, 12);
        var second = c.player(15, 1, 12);
        boss.setHealth(50);
        boss.setNoAi(false);
        c.at(20, () -> c.near(boss.getMaxHealth(), 100, "Brief entries must not scale health"));
        c.at(65, () -> {
            c.near(boss.getMaxHealth(), 125, "Two sustained participants must apply the configured health bonus");
            c.near(boss.getHealth(), 62.5, "Scaling must preserve the remaining health fraction");
            first.standAt(c.point(2, 1, 12));
            second.standAt(c.point(22, 1, 12));
        });
        c.at(69, () -> {
            c.near(boss.getHealth(), 62.5, "An empty arena must preserve health during the grace period");
            first.standAt(c.point(9, 1, 12));
        });
        c.at(77, () -> {
            c.near(boss.getHealth(), 62.5, "Returning during grace must cancel regeneration");
            c.set(LightningSheeperConfig.LIGHTNING_EMPTY_ARENA_RECOVERY_ENABLED, false);
            first.standAt(c.point(2, 1, 12));
        });
        c.at(92, () -> {
            c.near(boss.getHealth(), 62.5, "Disabled recovery must preserve damage beyond the grace period");
            c.near(boss.getMaxHealth(), 125, "Disabled recovery must preserve multiplayer scaling");
            c.set(LightningSheeperConfig.LIGHTNING_EMPTY_ARENA_RECOVERY_ENABLED, true);
        });
        c.at(104, () -> c.near(boss.getHealth(), 87.5, "An empty arena must heal at the configured rate after grace"));
        c.at(145, () -> {
            c.near(boss.getMaxHealth(), 100, "Full recovery must reset the multiplayer health bonus");
            c.near(boss.getHealth(), 100, "The recovered boss must be at full solo health");
            c.pass();
        });
    }

    private static void teleport(GameTestContext c) {
        configure(c);
        c.set(LightningSheeperConfig.LIGHTNING_TELEPORT_WARNING, 0.0);
        c.set(LightningSheeperConfig.LIGHTNING_TELEPORT_INTERVAL_MIN, 3.0);
        c.set(LightningSheeperConfig.LIGHTNING_TELEPORT_INTERVAL_MAX, 3.0);
        c.set(LightningSheeperConfig.LIGHTNING_TELEPORT_PLAYER_DISTANCE, 2.0);
        c.set(LightningSheeperConfig.LIGHTNING_MOVEMENT_ORBIT_DISTANCE, 5.0);
        var boss = boss(c);
        boss.getRandom().setSeed(41029);
        var player = c.player(10, 1, 12);
        var origin = boss.position();
        boss.setNoAi(false);
        c.at(1, () -> observeTeleport(c, boss, player, origin, false, 1));
    }

    private static void observeTeleport(GameTestContext c, LightningSheeperEntity boss, TestPlayer player,
            Vec3 origin, boolean sawArc, int tick) {
        if (boss.getTravelArc() != null) {
            c.expect(!boss.isPickable() && !boss.hurt(DamageSource.playerAttack(player), 5),
                    "The body at the departure point must not be targetable or take damage during travel");
            sawArc = true;
        } else if (sawArc) {
            c.expect(boss.isPickable() && boss.hurt(DamageSource.playerAttack(player), 5),
                    "The normal hitbox and damage permission must return on landing");
            c.expect(boss.position().subtract(origin).horizontalDistance() > 1
                    && boss.isWithinMovementArea(boss.getX(), boss.getZ())
                    && boss.position().subtract(player.position()).horizontalDistance() >= 2,
                    "The landing must move the boss within its arena and honor player clearance");
            player.standAt(origin.add(0, 0, 2));
            Vec3 aim = boss.getBoundingBox().getCenter();
            double direction = aim.x >= origin.x ? 1 : -1;
            shootAlongXAxis(c, player, aim, direction);
            c.at(tick + 7, () -> {
                c.expect(boss.getTravelArc() == null && boss.getHealth() < 95,
                        "An incoming arrow must hit while dodging is disabled");
                c.set(LightningSheeperConfig.LIGHTNING_DODGE_ENABLED, true);
                float health = boss.getHealth();
                shootAlongXAxis(c, player, boss.getBoundingBox().getCenter(), direction);
                c.at(tick + 11, () -> {
                    c.expect(boss.getTravelArc() != null && !boss.isPickable(),
                            "Enabling dodging must turn a collision-course arrow into an early combat teleport");
                    c.near(boss.getHealth(), health, "The dodge must start before the projectile reaches the boss");
                    c.pass();
                });
            });
            return;
        }
        c.expect(tick < 80, "A combat teleport must visibly travel and land");
        boolean observed = sawArc;
        c.at(tick + 1, () -> observeTeleport(c, boss, player, origin, observed, tick + 1));
    }

    /** Launches a real, level arrow into the boss's hitbox with several ticks of reaction time. */
    private static void shootAlongXAxis(GameTestContext c, TestPlayer player, Vec3 aim, double direction) {
        var arrow = new Arrow(c.level, player);
        arrow.setPos(aim.x + 5 * direction, aim.y, aim.z);
        arrow.setNoGravity(true);
        arrow.setBaseDamage(6);
        arrow.setDeltaMovement(-direction, 0, 0);
        c.level.addFreshEntity(arrow);
    }

    private static void death(GameTestContext c) {
        configure(c);
        c.set(LightningSheeperConfig.LIGHTNING_ARENA_DRAIN_DURATION, 1.3);
        var boss = boss(c);
        var player = c.player(15, 1, 12);
        var storm = LightningStormData.get(c.level);
        c.expect(boss.hurt(DamageSource.playerAttack(player), 200), "A lethal participant hit must start death");
        c.at(1, () -> awaitDrain(c, boss, player, storm, 1));
    }

    private static void awaitDrain(GameTestContext c, LightningSheeperEntity boss, TestPlayer player,
            LightningStormData storm, int tick) {
        var arena = storm.getArena(boss.getUUID());
        c.expect(arena != null, "The storm must persist through death and start a visible retreat");
        if (!boss.isRemoved()) {
            c.expect(!arena.isDraining() && boss.getCombatParticipants().isEmpty(),
                    "The death animation must keep the arena visible with combat disabled");
            c.expect(tick < 120, "The death animation must eventually end");
            c.at(tick + 1, () -> awaitDrain(c, boss, player, storm, tick + 1));
            return;
        }
        c.expect(arena.isDraining(), "Removal after death must start the retreat");
        c.at(tick + 13, () -> {
            var retreating = storm.getArena(boss.getUUID());
            c.expect(retreating != null && retreating.radiusAt(c.level.getGameTime()) < retreating.radius(),
                    "The storm must shrink before vanishing");
        });
        c.at(tick + 27, () -> {
            c.expect(storm.getArena(boss.getUUID()) == null, "The storm must end after its configured retreat");
            c.near(player.getHealth(), player.getMaxHealth(), "Death lightning and the retreat must be harmless");
            c.pass();
        });
    }

    private static void waterConduction(GameTestContext c) {
        configure(c);
        var boss = boss(c);
        var swimmer = c.player(16, 1, 12);
        var conduction = new LightningWaterConduction(c.level, boss);
        for (int x = 13; x <= 16; x++) {
            c.setBlock(x, 1, 12, Blocks.WATER);
        }
        c.expect(conduction.reaches(c.pos(13, 1, 12), swimmer), "Connected water must conduct to a wading player");
        c.setBlock(15, 1, 12, Blocks.STONE);
        c.expect(!conduction.reaches(c.pos(13, 1, 12), swimmer), "A solid division must isolate the pools");
        for (int x = 16; x <= 21; x++) {
            c.setBlock(x, 1, 12, Blocks.WATER);
        }
        c.expect(!conduction.reaches(c.pos(21, 1, 12), swimmer), "Water outside the arena must not conduct into it");
        var creeper = c.spawn(EntityType.CREEPER, 10, 1, 12);
        c.expect(LightningAreaDamage.strikeMob(creeper, creeper.position(), 3) && creeper.isPowered(),
                "A direct boss lightning strike must still charge a creeper");
        c.pass();
    }

    private static void rowSalvo(GameTestContext c) {
        configure(c);
        c.set(LightningSheeperConfig.LIGHTNING_LINE_POSITION_COUNT_MIN, 2);
        c.set(LightningSheeperConfig.LIGHTNING_LINE_POSITION_COUNT_MAX, 2);
        c.set(LightningSheeperConfig.LIGHTNING_LINE_ROW_COUNT_MIN, 3);
        c.set(LightningSheeperConfig.LIGHTNING_LINE_ROW_COUNT_MAX, 3);
        c.set(LightningSheeperConfig.LIGHTNING_LINE_CHARGE_DURATION, 0.5);
        c.set(LightningSheeperConfig.LIGHTNING_LINE_PREVIOUS_POSITION_DISTANCE, 3.0);
        c.set(LightningSheeperConfig.LIGHTNING_LINE_TELEPORT_DISTANCE, 1.0);
        c.set(LightningSheeperConfig.LIGHTNING_LINE_SPEED, 20.0);
        c.set(LightningSheeperConfig.LIGHTNING_LINE_DAMAGE, 0.0);
        var boss = boss(c);
        boss.getRandom().setSeed(80641);
        var first = c.player(9, 1, 12);
        var second = c.player(15, 1, 12);
        var attack = new LightningLineAttack(boss, new LightningTeleportMovement(boss));
        attack.start(c.level);
        awaitRowSalvo(c, boss, attack, first, second, boss.position(), null, 1);
    }

    private static void awaitRowSalvo(GameTestContext c, LightningSheeperEntity boss, LightningLineAttack attack,
            TestPlayer first, TestPlayer second, Vec3 start, Vec3 origin, int tick) {
        attack.tick(c.level, List.of(first, second));
        if (origin == null && boss.position().subtract(start).horizontalDistance() > 1) {
            origin = boss.position();
            // Put both players well inside the arena in visibly different directions from the new position.
            Vec3 inward = start.subtract(origin).multiply(1, 0, 1).normalize();
            Vec3 side = new Vec3(-inward.z, 0, inward.x);
            first.standAt(origin.add(inward.scale(2)).add(side.scale(3)));
            second.standAt(origin.add(inward.scale(2)).subtract(side.scale(3)));
        }
        if (origin != null) {
            Vec3 firingPosition = origin;
            List<Vec3> directions = c.entities(LightningStrikeEffect.class).stream()
                    .map(strike -> strike.position().subtract(firingPosition).multiply(1, 0, 1))
                    .filter(offset -> offset.horizontalDistanceSqr() > 0.01).map(Vec3::normalize).toList();
            if (!directions.isEmpty()) {
                c.expect(directions.size() == 3, "All three rows must advance away from the firing position together");
                for (TestPlayer player : List.of(first, second)) {
                    Vec3 towardPlayer = player.position().subtract(origin).multiply(1, 0, 1).normalize();
                    // Successive impacts can be offset sideways, leaving a fan around each aimed direction.
                    c.expect(directions.stream().anyMatch(direction -> direction.dot(towardPlayer) > 0.8),
                            "Each participant must have its own aimed row before the remaining row is assigned");
                }
                c.at(tick + 1, () -> awaitRowTeleport(c, boss, attack, first, second, firingPosition, tick + 1));
                return;
            }
        }
        c.expect(tick < 50, "The first salvo must start after the opening charge and teleport");
        Vec3 observedOrigin = origin;
        c.at(tick + 1, () -> awaitRowSalvo(c, boss, attack, first, second, start, observedOrigin, tick + 1));
    }

    private static void awaitRowTeleport(GameTestContext c, LightningSheeperEntity boss, LightningLineAttack attack,
            TestPlayer first, TestPlayer second, Vec3 origin, int tick) {
        attack.tick(c.level, List.of(first, second));
        if (boss.getTravelArc() != null) {
            c.expect(boss.position().subtract(origin).horizontalDistanceSqr() < 0.01,
                    "The second jump must leave from the position that fired the first salvo");
            c.pass();
            return;
        }
        c.expect(tick < 65, "The boss must teleport again after releasing one salvo");
        c.at(tick + 1, () -> awaitRowTeleport(c, boss, attack, first, second, origin, tick + 1));
    }

    private static void rowTeleportDistance(GameTestContext c) {
        configure(c);
        var boss = boss(c);
        boss.getRandom().setSeed(63042);
        Vec3 origin = c.point(8, 1, 12);
        boss.setPos(origin);
        var player = c.player(16, 1, 12);
        var teleports = new LightningTeleportMovement(boss);
        Vec3 destination = teleports.findLineAttackDestination(List.of(player), 9, 8);
        c.expect(destination != null && destination.subtract(origin).horizontalDistance() >= 9
                && destination.subtract(player.position()).horizontalDistance() < 8,
                "When the distance preferences conflict, the jump from the previous position must take priority");
        Vec3 fallback = teleports.findLineAttackDestination(List.of(player), 20, 20);
        c.expect(fallback != null && boss.isWithinMovementArea(fallback.x, fallback.z),
                "Unreachable distance preferences must still allow a safe landing in a small arena");
        c.pass();
    }

    private enum FanPhase {
        CHARGE, ASCENT, HOVER, DESCENT
    }

    private static void fanFlight(GameTestContext c) {
        configure(c);
        c.set(LightningSheeperConfig.LIGHTNING_FAN_ENABLED, true);
        c.set(LightningSheeperConfig.LIGHTNING_FAN_PREPARATION, 0.75);
        c.set(LightningSheeperConfig.LIGHTNING_FAN_DURATION_MIN, 2.5);
        c.set(LightningSheeperConfig.LIGHTNING_FAN_DURATION_MAX, 2.5);
        c.set(LightningSheeperConfig.LIGHTNING_FAN_DAMAGE, 0.0);
        var boss = boss(c);
        var player = c.player(17, 1, 12);
        c.set(LightningSheeperConfig.LIGHTNING_CRACKLE_DAMAGE, 2.0);
        Vec3 ground = boss.position();
        boss.setNoAi(false);
        // Observe the real combat controller, including restoration of the hitbox on landing.
        c.at(1, () -> observeFanFlight(c, boss, player, ground, new LinkedHashSet<>(), 1));
    }

    private static void observeFanFlight(GameTestContext c, LightningSheeperEntity boss, TestPlayer player, Vec3 ground,
            Set<FanPhase> observed, int tick) {
        var arc = boss.getTravelArc();
        if (arc != null) {
            c.expect(!boss.isPickable(), "The boss must remain incorporeal throughout its lightning flight");
            if (arc.compact() && arc.isCharging()) {
                observed.add(FanPhase.HOVER);
                c.near(boss.getY() - ground.y, 5, "An unobstructed fan must hover five blocks above the floor");
            } else if (arc.end().y > arc.start().y) {
                observed.add(FanPhase.ASCENT);
            } else if (arc.end().y < arc.start().y) {
                observed.add(FanPhase.DESCENT);
            }
        } else if (observed.contains(FanPhase.DESCENT)) {
            c.expect(List.copyOf(observed).equals(List.of(FanPhase.CHARGE, FanPhase.ASCENT, FanPhase.HOVER,
                    FanPhase.DESCENT)), "The fan must charge, ascend, hover, and descend in that order");
            c.expect(boss.isPickable(), "The boss must become targetable again only after its return flight");
            c.near(boss.position().distanceTo(ground), 0, "The boss must materialize back on the takeoff floor");
            c.pass();
            return;
        } else if (boss.getLineCharge(1) > 0 && observed.add(FanPhase.CHARGE)) {
            c.expect(boss.isPickable(), "The initial charge must happen while the boss still has its natural body");
            float health = player.getHealth();
            var melee = DamageSource.playerAttack(player);
            c.expect(boss.hurt(melee, 2), "The player must be able to hit the charging boss");
            c.near(player.getHealth(), health, "Disabling charge retaliation must leave the player's health unchanged");
            c.set(LightningSheeperConfig.LIGHTNING_CHARGE_RETALIATION_ENABLED, true);
            // A stronger second hit remains valid during vanilla's brief hit immunity.
            c.expect(boss.hurt(melee, 4), "A stronger melee hit must still be accepted");
            c.near(player.getHealth(), health - 2, "Charge retaliation must use the configured damage");
        }
        c.expect(tick < 100, "The complete fan flight and landing must be visible in one attack");
        c.at(tick + 1, () -> observeFanFlight(c, boss, player, ground, observed, tick + 1));
    }

    private static void tornadoOverlap(GameTestContext c) {
        configure(c);
        var boss = boss(c);
        var rider = c.player(13, 2, 12);
        var storm = LightningStormData.get(c.level);
        long now = c.level.getGameTime();
        var original = funnel(boss, c.point(13, 1, 12), new Vec3(0.2, 0, 0), 0.7, now, now - 30);
        var crossing = funnel(boss, c.point(13, 1, 12), new Vec3(-0.2, 0, 0), 1.4, now, now - 20);
        storm.updateTornados(c.level, boss.getUUID(), List.of(original));
        c.expect(LightningTornadoMotion.apply(rider, MoverType.SELF, Vec3.ZERO).x > 0,
                "A rider must initially be carried with the first funnel");
        storm.updateTornados(c.level, boss.getUUID(), List.of(original, crossing));
        c.expect(LightningTornadoMotion.apply(rider, MoverType.SELF, Vec3.ZERO).x > 0,
                "A stronger crossing tornado must not steal or reverse the rider");
        storm.updateTornados(c.level, boss.getUUID(), List.of());
        c.expect(LightningTornadoMotion.apply(rider, MoverType.SELF, Vec3.ZERO).equals(Vec3.ZERO),
                "The rider must be released when the funnels are removed");
        c.pass();
    }

    private static LightningTornado funnel(LightningSheeperEntity boss, Vec3 position, Vec3 velocity,
            double strength, long now, long started) {
        return new LightningTornado(boss.getUUID(), boss.getArenaCenter(), boss.getCombatRadius(),
                boss.getArenaMinimumY(), boss.getArenaMaximumY(), position, velocity, 3, 5, strength,
                now, started, now + 30, now + 50);
    }

    private static void rod(GameTestContext c) {
        configure(c);
        c.set(LightningSheeperConfig.LIGHTNING_ROD_ENABLED, true);
        c.set(LightningSheeperConfig.LIGHTNING_ROD_COOLDOWN, 1.25);
        c.set(LightningSheeperConfig.LIGHTNING_ROD_CHARGE_DURATION, 2.0);
        c.set(LightningSheeperConfig.LIGHTNING_ROD_CALM_DURATION, 0.4);
        c.set(LightningSheeperConfig.LIGHTNING_ROD_FINALE_DURATION, 0.6);
        c.set(LightningSheeperConfig.LIGHTNING_ROD_FINALE_DAMAGE, 1.0);
        c.set(LightningSheeperConfig.LIGHTNING_ROD_GROUND_FADE_DURATION, 1.0);
        c.set(LightningSheeperConfig.LIGHTNING_ROD_SHELTER_RADIUS, 2.0);
        c.set(LightningSheeperConfig.LIGHTNING_ROD_SHELTER_DENSITY, 0.2);
        c.set(LightningSheeperConfig.LIGHTNING_ROD_SHELTER_DISSOLVE_DURATION, 0.5);
        c.set(LightningSheeperConfig.LIGHTNING_AMBIENT_SPEED_DURATION, 4.0);
        c.set(LightningSheeperConfig.LIGHTNING_TELEPORT_WARNING, 0.0);
        c.set(LightningSheeperConfig.LIGHTNING_TELEPORT_INTERVAL_MIN, 7.0);
        c.set(LightningSheeperConfig.LIGHTNING_TELEPORT_INTERVAL_MAX, 7.0);
        c.set(LightningSheeperConfig.LIGHTNING_TELEPORT_PLAYER_DISTANCE, 2.0);
        c.set(LightningSheeperConfig.LIGHTNING_MOVEMENT_ORBIT_DISTANCE, 5.0);
        var boss = boss(c);
        boss.getRandom().setSeed(92401);
        var sheltered = c.player(9, 1, 12);
        var exposed = c.player(17, 1, 12);
        boss.setNoAi(false);
        c.at(20, () -> c.expect(!LightningStormData.get(c.level).getArena(boss.getUUID()).rodDarkened(),
                "Lightning Rod must wait for its configured initial cooldown"));
        c.at(24, () -> awaitShelter(c, boss, sheltered, exposed, 24));
    }

    private static void awaitShelter(GameTestContext c, LightningSheeperEntity boss, TestPlayer sheltered,
            TestPlayer exposed, int tick) {
        var storm = LightningStormData.get(c.level);
        Vec3 safety = null;
        Vec3 danger = null;
        for (int x = 6; x <= 18; x++) {
            for (int z = 6; z <= 18; z++) {
                Vec3 feet = c.point(x, 1, z);
                if (feet.subtract(c.point(12, 1, 12)).horizontalDistance() > 6) {
                    continue;
                }
                if (storm.isSheltered(feet.add(0, sheltered.getBbHeight() / 2, 0))) {
                    safety = feet;
                } else {
                    danger = feet;
                }
            }
        }
        if (safety == null || danger == null) {
            c.expect(tick < 125, "Lightning Rod must form at least one usable shelter after cooldown");
            c.at(tick + 1, () -> awaitShelter(c, boss, sheltered, exposed, tick + 1));
            return;
        }
        Vec3 target = safety.add(0, sheltered.getBbHeight() / 2, 0);
        Vec3 interception = storm.clipLightning(target.add(0, 10, 0), target);
        c.expect(interception.y > target.y, "Lightning must stop at the shelter surface before reaching its player");
        sheltered.standAt(safety);
        exposed.standAt(danger);
        c.at(tick + 1, () -> awaitRodDamage(c, boss, sheltered, exposed, tick + 1));
    }

    private static void awaitRodDamage(GameTestContext c, LightningSheeperEntity boss, TestPlayer sheltered,
            TestPlayer exposed, int tick) {
        if (exposed.damageEvents.isEmpty()) {
            c.expect(tick < 180, "The final barrage must hit an exposed participant");
            c.at(tick + 1, () -> awaitRodDamage(c, boss, sheltered, exposed, tick + 1));
            return;
        }
        c.expect(boss.getTravelArc() != null && !boss.isPickable(),
                "The boss must still be in lightning form during the final barrage");
        c.expect(exposed.hasEffect(MobEffects.MOVEMENT_SPEED),
                "An accepted barrage hit must grant the lightning speed bonus");
        int firstHitCount = exposed.damageEvents.size();
        c.at(tick + 8, () -> c.expect(exposed.damageEvents.size() > firstHitCount,
                "Electrified ground must damage players again before vanilla hit immunity expires"));
        c.at(tick + 18, () -> {
            c.expect(sheltered.damageEvents.isEmpty(), "A fading sphere must still protect its player");
            int fadeHitCount = exposed.damageEvents.size();
            c.at(tick + 27, () -> c.expect(exposed.damageEvents.size() > fadeHitCount,
                    "The floor must keep damaging exposed players after the final strike"));
        });
        c.at(tick + 36, () -> {
            int finalHitCount = exposed.damageEvents.size();
            c.expect(sheltered.damageEvents.isEmpty(), "The shelter must protect its player through the barrage");
            c.at(tick + 42, () -> {
                c.expect(exposed.damageEvents.size() == finalHitCount, "Ground damage must stop after the fade ends");
                c.pass();
            });
        });
    }
}
