package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.entity.WaterArenaSearch;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Persistence and configuration contracts for the water encounter foundation.
 */
final class WaterGameTests {
    private WaterGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("water_arena_origin_persistence", WaterGameTests::origin));
        tests.add(new SheeperGameTests.Scenario("water_encounter_radii", WaterGameTests::radii));
        tests.add(new SheeperGameTests.Scenario("water_arena_connected_terrain", WaterGameTests::terrain));
        tests.add(new SheeperGameTests.Scenario("water_arena_surface_rules", WaterGameTests::surfaces));
        tests.add(new SheeperGameTests.Scenario("water_arena_incremental_discovery", WaterGameTests::incremental));
        tests.add(new SheeperGameTests.Scenario("water_arena_discovery_lifecycle", WaterGameTests::discoveryLifecycle));
    }

    private static void terrain(GameTestContext context) {
        // A raised path isolates this scene from the template's bedrock floor.
        context.setBlock(8, 3, 8, Blocks.STONE);
        context.setBlock(9, 4, 8, Blocks.DIRT);
        context.setBlock(10, 5, 8, Blocks.SANDSTONE);
        context.setBlock(11, 6, 8, Blocks.OAK_PLANKS);
        context.setBlock(8, 5, 9, Blocks.STONE); // Two-block cliff.
        context.setBlock(8, 2, 7, Blocks.STONE); // Connected descent.
        context.setBlock(8, 6, 8, Blocks.STONE); // Disconnected roof above the seed.
        context.setBlock(9, 1, 8, Blocks.STONE); // Disconnected lower floor.
        WaterArenaSearch search = new WaterArenaSearch(context.level, context.pos(8, 4, 8), 5.0D, 2, 1);
        finish(context, search);
        context.expect(search.getSurfacePositions().size() == 4
                && search.getSurfacePositions().containsAll(List.of(context.pos(8, 4, 8), context.pos(9, 5, 8),
                        context.pos(10, 6, 8), context.pos(8, 3, 7))),
                "Discovery must follow connected ascents/descents, reject cliffs and respect the height bound");

        WaterArenaSearch flat = new WaterArenaSearch(context.level, context.pos(8, 4, 8), 5.0D, 2, 0);
        finish(context, flat);
        context.expect(flat.getSurfacePositions().equals(java.util.Set.of(context.pos(8, 4, 8))),
                "Zero step height must not connect different levels");

        WaterArenaSearch airborne = new WaterArenaSearch(context.level, context.pos(8, 5, 8), 5.0D, 2, 1);
        finish(context, airborne);
        context.expect(airborne.getSurfacePositions().contains(context.pos(8, 4, 8)),
                "An airborne spawn must find the floor below it within the height bound");
        context.pass();
    }

    private static void surfaces(GameTestContext context) {
        for (int x = 5; x <= 15; x++) {
            context.setBlock(x, 3, 8, Blocks.STONE);
        }
        context.setBlock(5, 3, 8, Blocks.SAND);
        context.setBlock(6, 3, 8, Blocks.DIRT);
        context.setBlock(7, 3, 8, Blocks.OAK_LOG);
        context.setBlock(8, 3, 8, Blocks.OAK_PLANKS);
        context.setBlock(9, 3, 8, Blocks.STONE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
        context.setBlock(10, 4, 8, Blocks.WATER);
        // Independent side branches exercise exclusions without blocking the path.
        context.setBlock(7, 3, 9, Blocks.OAK_LEAVES);
        context.setBlock(8, 3, 9, Blocks.STONE);
        context.setBlock(8, 4, 9, Blocks.WHITE_CARPET);
        context.setBlock(9, 3, 9, Blocks.STONE);
        context.setBlock(9, 4, 9, Blocks.STONE_PRESSURE_PLATE);
        context.setBlock(10, 3, 9, Blocks.STONE_SLAB);
        WaterArenaSearch search = new WaterArenaSearch(context.level, context.pos(8, 4, 8), 3.0D, 0, 1);
        finish(context, search);
        context.expect(search.getSurfacePositions().size() == 8
                && search.getSurfacePositions().contains(context.pos(8, 4, 9)),
                "The supported cells and water-replaceable carpet inside the circular radius must be included");
        for (int x = 5; x <= 11; x++) {
            context.expect(search.getSurfacePositions().contains(context.pos(x, 4, 8)),
                    "Solid full-height surfaces and existing water must connect the arena");
        }
        context.assertBlockPresent(Blocks.WHITE_CARPET, 8, 4, 9);
        context.assertBlockPresent(Blocks.STONE_PRESSURE_PLATE, 9, 4, 9);
        context.assertBlockPresent(Blocks.WATER, 10, 4, 8);
        context.pass();
    }

    private static void incremental(GameTestContext context) {
        WaterArenaSearch search = new WaterArenaSearch(context.level, context.pos(8, 1, 8), 2.0D, 0, 1);
        search.advance(1);
        context.expect(!search.isComplete() && search.getSurfacePositions().size() <= 1,
                "A small budget must spread discovery over multiple calls");
        finish(context, search);
        context.expect(search.getSurfacePositions().size() == 13,
                "A radius-two disk on flat ground must contain thirteen surface cells");

        BlockPos unloadedOrigin = new BlockPos(20_000_000, context.pos(8, 1, 8).getY(), 20_000_000);
        ChunkPos unloadedChunk = new ChunkPos(unloadedOrigin);
        context.expect(!context.level.getChunkSource().hasChunk(unloadedChunk.x, unloadedChunk.z),
                "Remote test chunk must start unloaded");
        WaterArenaSearch deferred = new WaterArenaSearch(context.level, unloadedOrigin, 2.0D, 0, 1);
        deferred.advance(100);
        context.expect(!deferred.isComplete() && deferred.getSurfacePositions().isEmpty()
                && !context.level.getChunkSource().hasChunk(unloadedChunk.x, unloadedChunk.z),
                "An unavailable seed must wait without loading its chunk or declaring completion");
        context.pass();
    }

    private static void discoveryLifecycle(GameTestContext context) {
        context.set(SheeperConfig.WATER_COMBAT_RADIUS, 2.0D);
        context.set(SheeperConfig.WATER_ARENA_HEIGHT_RANGE, 0);
        context.set(SheeperConfig.WATER_ARENA_SEARCH_BUDGET, 64);
        WaterSheeperEntity boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8);
        context.at(5, () -> {
            context.expect(boss.isArenaSearchComplete() && boss.getArenaSurfacePositions().size() == 13,
                    "Server ticks must complete the boss's arena discovery");
            BlockPos origin = boss.getArenaCenter();
            boss.setPos(context.point(15, 1, 15));
            CompoundTag saved = boss.saveWithoutId(new CompoundTag());
            // Run the detached clone directly to avoid adding a duplicate saved UUID to the
            // world.
            WaterSheeperEntity restored = ModEntities.WATER_SHEEPER.get().create(context.level);
            restored.load(saved);
            context.expect(!restored.isArenaSearchComplete(), "Loading must reset derived discovery state");
            restored.tick();
            context.expect(restored.isArenaSearchComplete() && restored.getArenaSurfacePositions().size() == 13
                    && restored.getArenaSurfacePositions().contains(origin)
                    && !restored.getArenaSurfacePositions().contains(boss.blockPosition()),
                    "Reloading after movement must rediscover surfaces around the saved origin");
            context.pass();
        });
    }

    private static void finish(GameTestContext context, WaterArenaSearch search) {
        for (int tick = 0; tick < 100 && !search.isComplete(); tick++) {
            search.advance(64);
        }
        context.expect(search.isComplete(), "Discovery must finish for the loaded test scene");
    }

    private static void origin(GameTestContext context) {
        WaterSheeperEntity boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8);
        BlockPos spawn = boss.blockPosition();
        context.at(3, () -> {
            boss.setPos(context.point(15, 2, 15));
            context.expect(boss.getArenaCenter().equals(spawn), "Movement must not move the captured spawn origin");
            boss.setStrengthMultiplier(1.5D);
            CompoundTag saved = boss.saveWithoutId(new CompoundTag());
            WaterSheeperEntity loaded = ModEntities.WATER_SHEEPER.get().create(context.level);
            loaded.load(saved);
            context.expect(loaded.getArenaCenter().equals(spawn), "Loading must preserve the original arena center");
            context.near(loaded.getStrengthMultiplier(), 1.5D, "Water-specific saving must preserve shared strength");

            saved.remove("WaterArenaCenter");
            loaded.load(saved);
            context.expect(loaded.getArenaCenter().equals(boss.blockPosition()),
                    "Older saves must establish the origin at their loaded position");

            // Saving can happen before the entity's first world tick.
            WaterSheeperEntity unticked = ModEntities.WATER_SHEEPER.get().create(context.level);
            unticked.setPos(context.point(5, 2, 5));
            CompoundTag earlySave = unticked.saveWithoutId(new CompoundTag());
            loaded.load(earlySave);
            context.expect(loaded.getArenaCenter().equals(unticked.blockPosition()),
                    "Saving before the first tick must capture placement rather than constructor coordinates");
            context.pass();
        });
    }

    private static void radii(GameTestContext context) {
        WaterSheeperEntity boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8);
        context.set(SheeperConfig.WATER_COMBAT_RADIUS, 30.0D);
        context.set(SheeperConfig.WATER_MOVEMENT_RADIUS, 8.0D);
        context.near(boss.getCombatRadius(), 30.0D, "Combat radius must follow configuration");
        context.near(boss.getMovementRadius(), 8.0D, "Movement radius must follow configuration");
        context.set(SheeperConfig.WATER_COMBAT_RADIUS, 5.0D);
        context.near(boss.getMovementRadius(), 5.0D, "Movement radius must stay within the combat radius");
        context.set(SheeperConfig.WATER_MOVEMENT_RADIUS, 0.0D);
        context.near(boss.getMovementRadius(), 0.0D, "Zero movement radius must be supported");
        context.pass();
    }
}
