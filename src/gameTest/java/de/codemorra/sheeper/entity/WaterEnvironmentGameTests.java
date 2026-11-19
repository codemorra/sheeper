package de.codemorra.sheeper.entity;

import java.util.List;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.gametest.GameTestContext;
import de.codemorra.sheeper.gametest.SheeperGameTests.Scenario;
import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.world.ArenaWater;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

/** Water ownership, actual collision hooks and terrain penetration regressions. */
public final class WaterEnvironmentGameTests {
    private WaterEnvironmentGameTests() {
    }

    public static void add(List<Scenario> tests) {
        tests.add(new Scenario("water_volume_generation_and_owned_cleanup", WaterEnvironmentGameTests::generation));
        tests.add(new Scenario("water_native_breathing_and_floor_sliding", WaterEnvironmentGameTests::breathingAndFloor));
        tests.add(new Scenario("water_swim_bounds_and_config_relationships", WaterEnvironmentGameTests::swimming));
        tests.add(new Scenario("water_swimming_approaches_target", WaterEnvironmentGameTests::movement));
        tests.add(new Scenario("water_attacks_pierce_leaves_without_air", WaterEnvironmentGameTests::leaves));
        tests.add(new Scenario("water_projectile_difficulty_and_lifetime", WaterEnvironmentGameTests::projectile));
    }

    private static void generation(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        boss.discard();
        c.set(WaterSheeperConfig.WATER_COMBAT_RADIUS, 3.0D);
        c.set(WaterSheeperConfig.WATER_ARENA_SEARCH_BUDGET_PER_SECOND, 20480);
        c.set(WaterSheeperConfig.WATER_ARENA_BLOCK_BUDGET_PER_SECOND, 20480);
        c.setBlock(11, 2, 10, Blocks.WATER);
        c.setBlock(9, 2, 10, Blocks.STONE);
        var real = c.spawn(ModEntities.WATER_SHEEPER.get(), 10, 1, 10);
        real.setNoAi(true);
        c.expect(!real.isCombatReady(), "New arena must not permit combat before water generation");
        for (int tick = 0; tick < 30 && !real.isCombatReady(); tick++) {
            real.tick();
        }
        WaterArenaData data = WaterArenaData.get(c.level);
        c.expect(real.isCombatReady(), "A small loaded volume must finish generation within its budget");
        BlockPos upper = c.pos(10, 5, 10);
        c.expect(data.isOwnedBy(real.getUUID(), upper) && c.level.getFluidState(upper).isSource(),
                "Generation must fill upper volume cells with tracked source water");
        c.expect(!data.isOwnedBy(real.getUUID(), c.pos(11, 2, 10)), "Natural water must not be claimed");
        c.expect(c.level.getBlockState(c.pos(9, 2, 10)).is(Blocks.STONE), "Generation must preserve solid terrain");
        WaterArenaData restored = WaterArenaData.load(data.save(new CompoundTag()));
        restored.release(real.getUUID());
        restored.advanceCleanup(c.level, 10000);
        c.expect(c.level.getFluidState(upper).isEmpty(), "Restored cleanup must remove owned upper water");
        c.expect(c.level.getFluidState(c.pos(11, 2, 10)).getType().isSame(Fluids.WATER),
                "Cleanup must preserve original natural water");
        c.expect(c.level.getBlockState(c.pos(9, 2, 10)).is(Blocks.STONE), "Cleanup must preserve terrain");
        c.pass();
    }

    private static void breathingAndFloor(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        var player = WaterTestScene.player(c, 13, 1, 10);
        c.expect(!player.canDrownInFluidType(ForgeMod.WATER_TYPE.get()),
                "The actual drowning hook must protect natural water inside the arena");
        player.standAt(c.point(21, 1, 10));
        c.expect(player.canDrownInFluidType(ForgeMod.WATER_TYPE.get()), "Natural water outside must retain drowning");
        player.standAt(c.point(13, 1, 10));
        c.setBlock(13, 0, 10, Blocks.AIR);
        c.setBlock(14, 0, 10, Blocks.AIR);
        double floor = boss.getArenaWaterMinimumY() + ArenaWater.BOUNDARY_MARGIN;
        player.standAt(new Vec3(player.getX(), floor + 0.05D, player.getZ()));
        player.move(MoverType.SELF, new Vec3(0.25D, -0.4D, 0));
        c.near(player.getY(), floor, "Actual entity collision must stop a downward crossing at the virtual floor");
        double x = player.getX();
        player.move(MoverType.SELF, new Vec3(0.25D, -0.1D, 0));
        c.near(player.getY(), floor, "Repeated floor contact must not oscillate vertically");
        c.near(player.getX(), x + 0.25D, "Floor contact must permit horizontal sliding");
        player.move(MoverType.SELF, new Vec3(0, 0.2D, 0));
        c.near(player.getY(), floor + 0.2D, "The virtual floor must not prevent swimming upward");
        c.pass();
    }

    private static void swimming(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        WaterTestScene.fill(c, boss);
        c.expect(boss.canSwimAt(boss.position().add(0, 1, 0)), "Open water above the boss must be navigable");
        Vec3 limited = boss.clampSwimmingPosition(boss.position().add(30, 30, 0));
        c.expect(boss.isWithinMovementArea(limited.x, limited.z)
                && limited.y + boss.getBbHeight() < boss.getArenaWaterSurfaceY(),
                "Swim destinations must retain edge and ceiling clearance");
        c.expect(!boss.isPushedByFluid(ForgeMod.WATER_TYPE.get()), "Ordinary currents must not displace the boss");
        c.setBlock(10, 2, 10, Blocks.STONE);
        c.expect(!boss.canSwimAt(boss.position().add(0, 1, 0)), "Navigation must reject full-body terrain collisions");
        c.set(WaterSheeperConfig.WATER_CLOSE_RANGE_STOP_DISTANCE, 7.0D);
        c.set(WaterSheeperConfig.WATER_PROXIMITY_PUSH_RADIUS, 2.0D);
        c.near(boss.getMeleeStopDistance(), 2, "Conflicting stop distance must remain within usable push reach");
        c.near(WaterSheeperConfig.WATER_CLOSE_RANGE_STOP_DISTANCE.get(), 7,
                "Resolving dependent settings must not rewrite the user's config");
        c.set(WaterSheeperConfig.WATER_MAELSTROM_SAFE_OUTER_WIDTH, 20.0D);
        c.near(WaterMaelstromWave.dangerRadius(boss), 4, "Safe-ring tuning must retain a central danger area");
        c.pass();
    }

    private static void movement(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        WaterTestScene.fill(c, boss);
        c.set(WaterSheeperConfig.WATER_MOVEMENT_SPEED, 1.0D);
        c.set(WaterSheeperConfig.WATER_MOVEMENT_ORBIT_DISTANCE, 3.0D);
        c.set(WaterSheeperConfig.WATER_MOVEMENT_HEIGHT_OFFSET, 0.0D);
        c.set(WaterSheeperConfig.WATER_MOVEMENT_INTERVAL_MIN, 0.1D);
        c.set(WaterSheeperConfig.WATER_MOVEMENT_INTERVAL_MAX, 0.1D);
        c.set(WaterSheeperConfig.WATER_MOVEMENT_SPRINT_CHANCE, 0.0D);
        c.set(WaterSheeperConfig.WATER_CLOSE_RANGE_DISTANCE, 1.0D);
        var player = WaterTestScene.player(c, 16, 2, 10);
        // Keep the ordinary entry pause open so this scene isolates navigation.
        CompoundTag state = new CompoundTag();
        boss.addAdditionalSaveData(state);
        state.getCompound("WaterDefaultAttack").putInt("PhaseTicks", 1200);
        boss.readAdditionalSaveData(state);
        boss.setNoAi(false);
        Vec3 start = boss.position();
        double distance = start.distanceTo(player.position());
        c.at(45, () -> {
            c.expect(boss.position().distanceTo(start) > 1,
                    "The actual swimming AI must leave its spawn position");
            c.expect(boss.position().distanceTo(player.position()) < distance - 1,
                    "Swimming must make progress toward a distant target");
            c.expect(boss.isWithinMovementArea(boss.getX(), boss.getZ())
                    && boss.getY() >= boss.getArenaWaterMinimumY(),
                    "Swimming must retain the captured arena limits");
            c.pass();
        });
    }

    private static void leaves(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        var target = WaterTestScene.player(c, 16, 1, 10);
        c.rule(GameRules.RULE_MOBGRIEFING, true);
        var leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
        BlockPos first = new BlockPos(boss.getEyePosition()).east(2);
        BlockPos second = first.east();
        c.level.setBlock(first, leaf, 3);
        // Model leaves that already contained natural water before arena flooding.
        WaterArenaData.get(c.level).forget(second);
        c.level.setBlock(second, leaf.setValue(BlockStateProperties.WATERLOGGED, true), 3);
        c.expect(WaterAttackBlocks.hasLineOfSight(boss, target), "Arena foliage must not prevent aiming");
        c.rule(GameRules.RULE_MOBGRIEFING, false);
        c.expect(!WaterAttackBlocks.clearLeaves(boss, first), "Leaf penetration must respect mob griefing");
        c.rule(GameRules.RULE_MOBGRIEFING, true);
        WaterProjectile shot = new WaterProjectile(ModEntities.WATER_PROJECTILE.get(), c.level);
        shot.setOwner(boss);
        shot.setPos(boss.getEyePosition());
        shot.setDeltaMovement(new Vec3(4, 0, 0));
        c.level.addFreshEntity(shot);
        shot.tick();
        c.expect(!shot.isRemoved() && shot.getX() > second.getX(), "A projectile must continue through consecutive leaves");
        WaterArenaData data = WaterArenaData.get(c.level);
        c.expect(data.isOwnedBy(boss.getUUID(), first) && c.level.getFluidState(first).isSource(),
                "Cleared dry leaves must immediately become tracked source water, without an air gap");
        c.expect(!data.isOwnedBy(boss.getUUID(), second)
                && c.level.getFluidState(second).getType().isSame(Fluids.WATER),
                "Waterlogged leaves must retain natural water ownership");
        c.level.setBlock(first, leaf, 3);
        BlockPos wall = second.east();
        c.level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        var hit = WaterAttackBlocks.clipBeam(boss, boss.getEyePosition(), boss.getEyePosition().add(7, 0, 0));
        c.expect(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(wall)
                && c.level.getFluidState(first).isSource(), "A beam must clear leaves and stop at the next solid wall");
        c.pass();
    }

    private static void projectile(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        var player = WaterTestScene.player(c, 15, 1, 10);
        c.set(WaterSheeperConfig.WATER_PROJECTILE_DAMAGE, 4.0D);
        c.set(WaterSheeperConfig.WATER_PROJECTILE_LIFETIME, 0.2D);
        float[] expected = { 3, 4, 6 };
        Difficulty[] difficulties = { Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD };
        for (int index = 0; index < difficulties.length; index++) {
            c.difficulty(difficulties[index]);
            player.invulnerableTime = 0;
            player.setHealth(20);
            WaterProjectile shot = new WaterProjectile(ModEntities.WATER_PROJECTILE.get(), c.level);
            shot.setOwner(boss);
            shot.setPos(player.position().add(-2, 1, 0));
            shot.setDeltaMovement(new Vec3(3, 0, 0));
            c.level.addFreshEntity(shot);
            shot.tick();
            c.near(player.getHealth(), 20 - expected[index], "Projectile damage must use vanilla mob difficulty scaling");
            c.expect(shot.isRemoved(), "An entity impact must consume the projectile");
        }
        WaterProjectile flight = new WaterProjectile(ModEntities.WATER_PROJECTILE.get(), c.level);
        flight.setOwner(boss);
        flight.setPos(boss.position().add(0, 3, 0));
        flight.setDeltaMovement(new Vec3(0, 0, 0.2D));
        c.level.addFreshEntity(flight);
        WaterTestScene.ticks(4, flight::tick);
        c.expect(!flight.isRemoved(), "Projectile must remain alive through its configured flight duration");
        flight.tick();
        c.expect(flight.isRemoved(), "Projectile must expire at the configured lifetime");
        c.pass();
    }
}
