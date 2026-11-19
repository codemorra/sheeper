package de.codemorra.sheeper.entity;

import java.util.List;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.gametest.GameTestContext;
import de.codemorra.sheeper.gametest.SheeperGameTests.Scenario;
import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.network.WaterArenaPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.Tag;
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

/** Virtual swimming, actual collision hooks and terrain penetration regressions. */
public final class WaterEnvironmentGameTests {
    private WaterEnvironmentGameTests() {
    }

    public static void add(List<Scenario> tests) {
        tests.add(new Scenario("water_spawn_respects_world_and_water_bounds", WaterEnvironmentGameTests::spawnBounds));
        tests.add(new Scenario("water_spawn_requires_clear_scaled_body", WaterEnvironmentGameTests::spawnSpace));
        tests.add(new Scenario("water_invalid_saved_volumes_are_not_synchronized", WaterEnvironmentGameTests::invalidVolumes));
        tests.add(new Scenario("water_native_breathing_and_floor_sliding", WaterEnvironmentGameTests::breathingAndFloor));
        tests.add(new Scenario("water_swim_bounds_and_config_relationships", WaterEnvironmentGameTests::swimming));
        tests.add(new Scenario("water_swimming_approaches_target", WaterEnvironmentGameTests::movement));
        tests.add(new Scenario("water_attacks_pierce_leaves_without_air", WaterEnvironmentGameTests::leaves));
        tests.add(new Scenario("water_projectile_difficulty_and_lifetime", WaterEnvironmentGameTests::projectile));
    }

    /** Small geometry isolates spawn clearance from unrelated combat behavior. */
    private static void configureSpawn(GameTestContext c) {
        c.set(WaterSheeperConfig.WATER_SIZE, 2.0D);
        c.set(WaterSheeperConfig.WATER_COMBAT_RADIUS, 4.0D);
        c.set(WaterSheeperConfig.WATER_MOVEMENT_EDGE_DISTANCE, 0.0D);
        c.set(WaterSheeperConfig.WATER_ARENA_HEIGHT_RANGE, 2);
        c.set(WaterSheeperConfig.WATER_ARENA_WATER_HEIGHT, 4);
        c.set(WaterSheeperConfig.WATER_SPAWN_PUSH_SPEED, 0.0D);
        c.set(WaterSheeperConfig.WATER_ARENA_SEARCH_BUDGET_PER_SECOND, 20);
    }

    private static WaterSheeperEntity spawnAt(GameTestContext c, Vec3 point) {
        var boss = ModEntities.WATER_SHEEPER.get().create(c.level);
        boss.setPos(point);
        boss.setNoAi(true);
        c.expect(c.level.addFreshEntity(boss), "Test boss must enter the level before first-tick validation");
        boss.tick();
        return boss;
    }

    private static void spawnBounds(GameTestContext c) {
        configureSpawn(c);
        int bottom = c.level.getMinBuildHeight();
        int top = c.level.getMaxBuildHeight();
        Vec3 column = c.point(10, 1, 10);
        WaterArenaData data = WaterArenaData.get(c.level);
        for (int y : new int[] { bottom - 1, top + 10, top - 1 }) {
            var rejected = spawnAt(c, new Vec3(column.x, y, column.z));
            c.expect(rejected.isRemoved() && data.getVolume(rejected.getUUID()) == null,
                    "Out-of-world or partially exposed spawns must be removed before creating an arena at Y=" + y);
        }
        for (int y : new int[] { bottom, top - 3 }) {
            // The flat test world's bedrock may occupy the lower build boundary.
            for (BlockPos cell : BlockPos.betweenClosed(new BlockPos(column.x - 1, y, column.z - 1),
                    new BlockPos(column.x + 1, y + 2, column.z + 1))) {
                c.level.setBlock(cell, Blocks.AIR.defaultBlockState(), 2);
            }
            var valid = spawnAt(c, new Vec3(column.x, y, column.z));
            try {
                var bounds = data.getVolume(valid.getUUID());
                c.expect(!valid.isRemoved() && bounds != null && bounds.isValidFor(c.level),
                        "A full body must fit even when the desired water volume is clipped at Y=" + y);
                c.expect(y == bottom ? bounds.minimumY() == bottom : bounds.maximumY() == top - 1,
                        "Captured water must be clipped to the actual world boundary");
            } finally {
                // These placements are deliberately outside the shared scene's cleanup box.
                valid.discard();
            }
        }
        c.pass();
    }

    private static void spawnSpace(GameTestContext c) {
        configureSpawn(c);
        // A two-block-high cave fits a normal sheep, but not the configured double-size body.
        c.setBlock(10, 3, 10, Blocks.STONE);
        var tooTall = spawnAt(c, c.point(10, 1, 10));
        c.expect(tooTall.isRemoved() && WaterArenaData.get(c.level).getVolume(tooTall.getUUID()) == null,
                "A ceiling intersecting the scaled head must reject the spawn without arena creation");
        c.setBlock(10, 3, 10, Blocks.AIR);
        c.setBlock(11, 1, 10, Blocks.STONE);
        var tooWide = spawnAt(c, c.point(10, 1, 10));
        c.expect(tooWide.isRemoved(), "A side wall must reject a spawn even when the center column is clear");
        c.set(WaterSheeperConfig.WATER_SIZE, 1.0D);
        c.setBlock(10, 3, 10, Blocks.STONE);
        var fits = spawnAt(c, c.point(10, 1, 10));
        c.expect(!fits.isRemoved(), "The same cave must accept a smaller body that actually fits");
        CompoundTag saved = new CompoundTag();
        fits.addAdditionalSaveData(saved);
        c.setBlock(10, 1, 10, Blocks.STONE);
        fits.readAdditionalSaveData(saved);
        fits.tick();
        c.expect(!fits.isRemoved(), "Reload must not reject an established boss after terrain changes");
        c.pass();
    }

    private static void invalidVolumes(GameTestContext c) {
        configureSpawn(c);
        var boss = spawnAt(c, c.point(10, 1, 10));
        WaterArenaData original = WaterArenaData.get(c.level);
        CompoundTag saved = original.save(new CompoundTag());
        for (Tag value : saved.getList("Arenas", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.getUUID("Boss").equals(boss.getUUID())) {
                // Valid packet geometry may still be outside this dimension's build range.
                entry.getCompound("Volume").putInt("MinimumY", c.level.getMaxBuildHeight() + 1);
                entry.getCompound("Volume").putInt("MaximumY", c.level.getMaxBuildHeight() + 4);
            }
        }
        try {
            c.level.getDataStorage().set("sheeper_water_arenas", WaterArenaData.load(saved));
            var volumes = WaterArenaData.activeVolumes(c.level);
            c.expect(volumes.stream().allMatch(bounds -> bounds.isValidFor(c.level)),
                    "Out-of-world saved volumes must not enter snapshots or player protection");
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var packet = new WaterArenaPacket(c.level.dimension().location(),
                        WaterArenaData.animatedVolumes(c.level));
                packet.encode(buffer);
                c.expect(packet.equals(WaterArenaPacket.decode(buffer)),
                        "Filtered server snapshots must round-trip through the client decoder");
            } finally {
                buffer.release();
            }
        } finally {
            c.level.getDataStorage().set("sheeper_water_arenas", original);
        }
        Vec3 column = c.point(14, 1, 10);
        BlockPos invalid = new BlockPos(column.x, c.level.getMaxBuildHeight() + 10, column.z);
        c.expect(original.prepareVolume(c.level, java.util.UUID.randomUUID(), invalid, 4, 2, 4) == null,
                "Direct volume preparation must reject inverted bounds as well");
        c.pass();
    }

    private static void breathingAndFloor(GameTestContext c) {
        var boss = WaterTestScene.boss(c);
        var player = WaterTestScene.player(c, 13, 1, 10);
        for (int y = 1; y <= 3; y++) {
            c.setBlock(13, y, 10, Blocks.WATER);
        }
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
        // Model leaves that already contained natural water.
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
        c.expect(c.level.getBlockState(first).isAir(),
                "Cleared dry leaves must leave air without placing water blocks");
        c.expect(c.level.getFluidState(second).getType().isSame(Fluids.WATER),
                "Waterlogged leaves must retain natural water");
        c.level.setBlock(first, leaf, 3);
        BlockPos wall = second.east();
        c.level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        var hit = WaterAttackBlocks.clipBeam(boss, boss.getEyePosition(), boss.getEyePosition().add(7, 0, 0));
        c.expect(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(wall)
                && c.level.getBlockState(first).isAir(), "A beam must clear leaves and stop at the next solid wall");
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
