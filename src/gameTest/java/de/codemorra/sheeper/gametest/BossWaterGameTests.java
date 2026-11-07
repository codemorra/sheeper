package de.codemorra.sheeper.gametest;

import java.util.List;
import java.util.UUID;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.world.WaterArenaData;
import de.codemorra.sheeper.registry.ModBlocks;
import de.codemorra.sheeper.fluid.BossWaterFluid;
import de.codemorra.sheeper.fluid.BossWaterlogging;
import java.util.Set;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Finite boss water, tracked descendants, waterlogging, and persistent cleanup.
 */
final class BossWaterGameTests {
    private BossWaterGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("arena_water_generation_and_death", BossWaterGameTests::generation));
        tests.add(new SheeperGameTests.Scenario("arena_water_shared_saved_ownership", BossWaterGameTests::overlap));
        tests.add(new SheeperGameTests.Scenario("arena_water_boss_unload_reload", BossWaterGameTests::bossReload));
        tests.add(new SheeperGameTests.Scenario("arena_water_flow_and_interactions", BossWaterGameTests::flow));
        tests.add(new SheeperGameTests.Scenario("arena_water_drains_without_sources", BossWaterGameTests::draining));
        tests.add(new SheeperGameTests.Scenario("arena_water_flows_over_edges", BossWaterGameTests::edge));
        tests.add(new SheeperGameTests.Scenario("arena_water_prevents_fall_damage", BossWaterGameTests::falling));
        tests.add(new SheeperGameTests.Scenario("arena_sources_fill_plant_cells", BossWaterGameTests::plantSources));
        tests.add(new SheeperGameTests.Scenario("arena_sources_promote_initial_flow", BossWaterGameTests::initialFlow));
        tests.add(new SheeperGameTests.Scenario("arena_sources_spread_outward", BossWaterGameTests::outward));
    }

    private static void outward(GameTestContext context) {
        context.set(SheeperConfig.WATER_COMBAT_RADIUS, 3.0D);
        context.set(SheeperConfig.WATER_ARENA_HEIGHT_RANGE, 0);
        context.set(SheeperConfig.WATER_ARENA_SEARCH_BUDGET, 512);
        context.set(SheeperConfig.WATER_ARENA_BLOCK_BUDGET, 1);
        for (int x = 4; x <= 12; x++) {
            for (int z = 4; z <= 12; z++) {
                if ((x - 8) * (x - 8) + (z - 8) * (z - 8) > 9) {
                    context.setBlock(x, 1, z, Blocks.STONE);
                }
            }
        }
        context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8);
        for (int tick : new int[] { 5, 10, 20 }) {
            context.at(tick, () -> {
                context.expect(context.level.getFluidState(context.pos(8, 1, 8)).isSource(),
                        "The flood must begin at the saved arena center");
                int furthestSource = -1;
                int nearestMissing = Integer.MAX_VALUE;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (dx * dx + dz * dz > 9) {
                            continue;
                        }
                        int distance = Math.abs(dx) + Math.abs(dz);
                        var fluid = context.level.getFluidState(context.pos(8 + dx, 1, 8 + dz));
                        if (fluid.getType() instanceof BossWaterFluid && fluid.isSource()) {
                            furthestSource = Math.max(furthestSource, distance);
                        } else {
                            nearestMissing = Math.min(nearestMissing, distance);
                        }
                    }
                }
                context.expect(furthestSource > 0 && nearestMissing >= furthestSource,
                        "A farther source layer must not begin before every nearer layer is filled");
            });
        }
        context.at(21, context::pass);
    }

    private static void plantSources(GameTestContext context) {
        prepare(context);
        context.set(SheeperConfig.WATER_ARENA_BLOCK_BUDGET, 2);
        context.setBlock(9, 0, 8, Blocks.GRASS_BLOCK);
        context.setBlock(8, 0, 7, Blocks.GRASS_BLOCK);
        context.setBlock(9, 1, 8, Blocks.DANDELION);
        context.setBlock(8, 1, 9, Blocks.RED_CARPET);
        context.setBlock(8, 1, 7, Blocks.FERN);
        context.setBlock(7, 0, 8, Blocks.GRASS_BLOCK);
        DoublePlantBlock.placeAt(context.level, Blocks.TALL_GRASS.defaultBlockState(), context.pos(7, 1, 8),
                Block.UPDATE_ALL);
        context.setBlock(9, 1, 9, Blocks.STONE_PRESSURE_PLATE);
        context.setBlock(7, 1, 7, Blocks.OAK_FENCE);
        WaterSheeperEntity boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8);
        context.at(20, () -> {
            for (BlockPos pos : List.of(context.pos(9, 1, 8), context.pos(8, 1, 9),
                    context.pos(8, 1, 7), context.pos(7, 1, 8), context.pos(8, 1, 8))) {
                context.expect(context.level.getFluidState(pos).getType() instanceof BossWaterFluid
                        && context.level.getFluidState(pos).isSource(),
                        "Plant cells must receive initial sources instead of remaining flowing channels");
            }
            BlockPos center = context.pos(8, 1, 8);
            context.expect(context.level.getFluidState(center).getFlow(context.level, center).lengthSqr() < 1.0E-8D,
                    "A flat surface surrounded by initial sources must have no horizontal current");
            context.expect(context.count(Items.DANDELION) == 1 && context.count(Items.RED_CARPET) == 1,
                    "Initial clearing must produce normal drops exactly once");
            context.assertBlockPresent(Blocks.AIR, 7, 2, 8);
            context.assertBlockPresent(Blocks.STONE_PRESSURE_PLATE, 9, 1, 9);
            context.assertBlockPresent(Blocks.OAK_FENCE, 7, 1, 7);
            boss.hurt(DamageSource.OUT_OF_WORLD, 10000.0F);
            WaterArenaData.get(context.level).advanceCleanup(context.level, 4096);
            context.assertBlockPresent(Blocks.AIR, 9, 1, 8);
            context.assertBlockPresent(Blocks.AIR, 8, 1, 9);
            context.assertBlockPresent(Blocks.OAK_FENCE, 7, 1, 7);
            context.pass();
        });
    }

    private static void initialFlow(GameTestContext context) {
        WaterArenaData data = WaterArenaData.get(context.level);
        UUID boss = UUID.randomUUID();
        BlockPos source = context.pos(8, 1, 8);
        BlockPos earlyFlow = context.pos(9, 1, 8);
        data.claim(context.level, boss, source);
        context.expect(data.inheritFlow(context.level, source, earlyFlow),
                "Early flow must belong to the generating arena");
        context.level.setBlockAndUpdate(earlyFlow,
                de.codemorra.sheeper.registry.ModFluids.FLOWING_BOSS_WATER.get().getFlowing(7, false)
                        .createLegacyBlock());
        context.expect(!context.level.getFluidState(earlyFlow).isSource(), "The fixture must start as flow");
        context.expect(data.claim(context.level, boss, earlyFlow) && context.level.getFluidState(earlyFlow).isSource(),
                "Flow arriving before a generation batch must be promoted to an initial source");
        data.finishGeneration(boss);
        BlockPos later = context.pos(10, 1, 8);
        context.expect(!data.claim(context.level, boss, later) && context.level.getBlockState(later).isAir(),
                "Completed generation must reject additional initial sources");
        data.release(boss);
        data.advanceCleanup(context.level, 4096);
        context.pass();
    }

    private static void prepare(GameTestContext context) {
        context.set(SheeperConfig.WATER_COMBAT_RADIUS, 2.0D);
        context.set(SheeperConfig.WATER_ARENA_HEIGHT_RANGE, 0);
        context.set(SheeperConfig.WATER_ARENA_SEARCH_BUDGET, 256);
        context.set(SheeperConfig.WATER_ARENA_BLOCK_BUDGET, 256);
        // Contain vanilla flow so death/unload assertions do not depend on refilling.
        for (int x = 5; x <= 11; x++) {
            for (int z = 5; z <= 11; z++) {
                if ((x - 8) * (x - 8) + (z - 8) * (z - 8) > 4) {
                    context.setBlock(x, 1, z, Blocks.STONE);
                }
            }
        }
    }

    private static void generation(GameTestContext context) {
        prepare(context);
        context.set(SheeperConfig.WATER_ARENA_BLOCK_BUDGET, 2);
        context.setBlock(9, 1, 8, Blocks.WATER);
        WaterSheeperEntity boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8);
        context.at(1, () -> context.expect(!WaterArenaData.get(context.level).isGenerated(boss.getUUID()),
                "Placement must respect its per-tick budget"));
        context.at(10, () -> {
            context.expect(WaterArenaData.get(context.level).isGenerated(boss.getUUID()),
                    "Server ticks must complete initial source placement");
            context.assertBlockPresent(ModBlocks.BOSS_WATER.get(), 8, 1, 9);
            context.setBlock(8, 1, 9, Blocks.STONE);
            WaterArenaData data = WaterArenaData.get(context.level);
            ListTag arenas = data.save(new CompoundTag()).getList("Arenas", Tag.TAG_COMPOUND);
            long[] placed = null;
            for (int index = 0; index < arenas.size(); index++) {
                CompoundTag arena = arenas.getCompound(index);
                if (arena.getUUID("Boss").equals(boss.getUUID())) {
                    placed = arena.getLongArray("Positions");
                    break;
                }
            }
            context.expect(placed != null && placed.length > 0, "Initial source positions must be saved");
            boss.hurt(DamageSource.OUT_OF_WORLD, 10000.0F);
            // Check removal before surviving vanilla water has a chance to refill cells.
            data.advanceCleanup(context.level, 256);
            for (long packed : placed) {
                BlockPos pos = BlockPos.of(packed);
                context.expect(!(context.level.getFluidState(pos).getType() instanceof BossWaterFluid),
                        "Death must remove each directly placed water cell");
            }
            context.assertBlockPresent(Blocks.WATER, 9, 1, 8);
            context.assertBlockPresent(Blocks.STONE, 8, 1, 9);
            context.expect(!data.isGenerated(boss.getUUID()), "Death must finish tracked-cell cleanup");
            context.pass();
        });
    }

    private static void overlap(GameTestContext context) {
        WaterArenaData data = new WaterArenaData();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BlockPos shared = context.pos(8, 1, 8);
        BlockPos exclusive = context.pos(9, 1, 8);
        BlockPos existing = context.pos(15, 1, 8);
        context.setBlock(15, 1, 8, Blocks.WATER);
        context.expect(!data.claim(context.level, first, existing), "Pre-existing water must never be claimed");
        context.expect(data.claim(context.level, first, shared) && data.claim(context.level, first, exclusive)
                && data.claim(context.level, second, shared), "Both arenas must share tracked spawn cells");
        data.finishGeneration(first);
        data.finishGeneration(second);
        WaterArenaData restored = WaterArenaData.load(data.save(new CompoundTag()));
        restored.release(first);
        restored.advanceCleanup(context.level, 10);
        context.expect(context.level.getBlockState(shared).is(ModBlocks.BOSS_WATER.get())
                && context.level.getBlockState(exclusive).isAir(),
                "Shared spawn water must survive the first owner's cleanup");
        restored.release(second);
        WaterArenaData pending = WaterArenaData.load(restored.save(new CompoundTag()));
        pending.advanceCleanup(context.level, 10);
        context.expect(context.level.getBlockState(shared).isAir(),
                "Saved cleanup must remove the final owner's tracked water");
        context.assertBlockPresent(Blocks.WATER, 15, 1, 8);
        context.pass();
    }

    private static void bossReload(GameTestContext context) {
        prepare(context);
        WaterSheeperEntity boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8);
        context.at(5, () -> {
            CompoundTag saved = boss.saveWithoutId(new CompoundTag());
            boss.remove(RemovalReason.UNLOADED_TO_CHUNK);
            context.assertBlockPresent(ModBlocks.BOSS_WATER.get(), 8, 1, 9);
            context.setBlock(8, 1, 9, Blocks.STONE);
            context.at(8, () -> {
                WaterSheeperEntity loaded = ModEntities.WATER_SHEEPER.get().create(context.level);
                loaded.load(saved);
                context.expect(context.level.addFreshEntity(loaded), "The saved boss must rejoin the world");
                context.at(12, () -> {
                    context.assertBlockPresent(Blocks.STONE, 8, 1, 9);
                    context.assertBlockPresent(ModBlocks.BOSS_WATER.get(), 9, 1, 8);
                    loaded.discard();
                });
            });
        });
        context.at(13, () -> {
            context.assertBlockPresent(Blocks.AIR, 9, 1, 8);
            context.assertBlockPresent(Blocks.STONE, 8, 1, 9);
            context.pass();
        });
    }

    private static Set<BlockPos> basin(GameTestContext context) {
        for (int x = 1; x <= 15; x++) {
            for (int z = 1; z <= 15; z++) {
                context.setBlock(x, 3, z, Blocks.GRASS_BLOCK);
                if (x == 1 || x == 15 || z == 1 || z == 15) {
                    context.setBlock(x, 4, z, Blocks.STONE);
                }
            }
        }
        context.setBlock(9, 4, 8, Blocks.DANDELION);
        context.setBlock(8, 4, 9, Blocks.RED_CARPET);
        context.setBlock(7, 4, 8, Blocks.OAK_FENCE);
        context.setBlock(8, 4, 7, Blocks.OAK_FENCE);
        Set<BlockPos> sources = Set.of(context.pos(8, 4, 8), context.pos(7, 4, 7), context.pos(6, 4, 6));
        return sources;
    }

    private static void noBossWater(GameTestContext context) {
        for (int x = 2; x <= 14; x++) {
            for (int z = 2; z <= 14; z++) {
                context.expect(!(context.level.getFluidState(context.pos(x, 4, z)).getType() instanceof BossWaterFluid),
                        "No flowing or waterlogged boss water may remain at " + x + "," + z);
            }
        }
    }

    private static void flow(GameTestContext context) {
        WaterArenaData data = WaterArenaData.get(context.level);
        UUID boss = UUID.randomUUID();
        Set<BlockPos> sources = basin(context);
        context.setBlock(14, 4, 8, Blocks.WATER);
        context.setBlock(8, 4, 10, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) context.level.getBlockEntity(context.pos(8, 4, 10));
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        context.setBlock(10, 4, 8, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        context.setBlock(9, 4, 7, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        for (BlockPos pos : sources) {
            context.expect(data.claim(context.level, boss, pos), "Initial source placement must succeed");
        }
        context.at(40, () -> {
            context.expect(BossWaterlogging.isWet(context.level.getBlockState(chest.getBlockPos()))
                    && chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 3,
                    "Waterlogging must preserve a chest's block entity and inventory");
            BlockPos fence = context.pos(7, 4, 8);
            for (BlockPos pos : List.of(context.pos(10, 4, 8), context.pos(9, 4, 7))) {
                context.expect(BossWaterlogging.isWet(context.level.getBlockState(pos))
                        && !context.level.getBlockState(pos).getValue(BlockStateProperties.LIT),
                        "Boss water must fill and extinguish candles and campfires without replacing them");
            }
            context.expect(context.level.getBlockState(fence).is(Blocks.OAK_FENCE)
                    && BossWaterlogging.isWet(context.level.getBlockState(fence))
                    && !context.level.getBlockState(fence).getValue(BlockStateProperties.WATERLOGGED)
                    && !context.level.getFluidState(fence).isSource(),
                    "Waterlogged fences must retain their identity and hold finite boss flow, not vanilla sources");
            var savedFence = NbtUtils.readBlockState(NbtUtils.writeBlockState(context.level.getBlockState(fence)));
            context.expect(BossWaterlogging.isWet(savedFence)
                    && savedFence.getFluidState().getType() instanceof BossWaterFluid,
                    "Boss waterlogging must survive normal block-state serialization");
            context.expect(!context.level.getBlockState(context.pos(9, 4, 8)).is(Blocks.DANDELION)
                    && !context.level.getBlockState(context.pos(8, 4, 9)).is(Blocks.RED_CARPET),
                    "Boss flow must wash away plants and carpets");
            for (int x = 2; x <= 14; x++) {
                for (int z = 2; z <= 14; z++) {
                    BlockPos pos = context.pos(x, 4, z);
                    var fluid = context.level.getFluidState(pos);
                    if (fluid.getType() instanceof BossWaterFluid && fluid.isSource()) {
                        context.expect(sources.contains(pos), "Flow must never create an additional source");
                    }
                }
            }
            for (BlockPos pos : List.of(context.pos(8, 3, 8), context.pos(9, 3, 8), context.pos(14, 3, 8))) {
                context.level.getBlockState(pos).randomTick(context.level, pos, context.level.random);
            }
            context.assertBlockPresent(Blocks.DIRT, 8, 3, 8);
            context.assertBlockPresent(Blocks.GRASS_BLOCK, 9, 3, 8);
            context.assertBlockPresent(Blocks.DIRT, 14, 3, 8);
            data.release(boss);
            data.advanceCleanup(context.level, 4096);
            noBossWater(context);
            context.assertBlockPresent(Blocks.OAK_FENCE, 7, 4, 8);
            context.expect(context.level.getBlockEntity(chest.getBlockPos()) == chest
                    && chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 3,
                    "Cleanup must preserve chest contents");
            context.assertBlockPresent(Blocks.WATER, 14, 4, 8);
            context.expect(!BossWaterlogging.isWet(context.level.getBlockState(fence)),
                    "Cleanup must clear only the boss fluid inside a fence");
        });
        context.at(50, () -> {
            noBossWater(context);
            context.expect(!context.level.getBlockState(context.pos(9, 4, 8)).is(Blocks.DANDELION)
                    && !context.level.getBlockState(context.pos(8, 4, 9)).is(Blocks.RED_CARPET),
                    "Washed-away blocks must not be restored");
            context.pass();
        });
    }

    private static void draining(GameTestContext context) {
        WaterArenaData data = WaterArenaData.get(context.level);
        UUID boss = UUID.randomUUID();
        Set<BlockPos> sources = basin(context);
        for (BlockPos pos : sources) {
            data.claim(context.level, boss, pos);
        }
        context.at(40, () -> {
            context.expect(BossWaterlogging.isWet(context.level.getBlockState(context.pos(7, 4, 8))),
                    "The draining scene must contain waterlogged flow");
            for (BlockPos pos : sources) {
                context.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        });
        context.at(180, () -> {
            noBossWater(context);
            data.release(boss);
            data.advanceCleanup(context.level, 4096);
            context.pass();
        });
    }

    private static void edge(GameTestContext context) {
        WaterArenaData data = WaterArenaData.get(context.level);
        UUID boss = UUID.randomUUID();
        context.setBlock(10, 0, 8, Blocks.GRASS_BLOCK);
        for (int x = 7; x <= 9; x++) {
            for (int z = 7; z <= 9; z++) {
                context.setBlock(x, 3, z, Blocks.STONE);
            }
        }
        BlockPos source = context.pos(8, 4, 8);
        data.claim(context.level, boss, source);
        context.at(60, () -> {
            BlockPos fall = context.pos(10, 2, 8);
            var fluid = context.level.getFluidState(fall);
            context.expect(fluid.getType() instanceof BossWaterFluid && !fluid.isSource(),
                    "Water must flow beyond its initial footprint and down a cliff without forming a source");
            context.expect(context.level.getFluidState(context.pos(11, 1, 8)).getType() instanceof BossWaterFluid,
                    "The waterfall must spread across the lower floor");
            BlockPos grass = context.pos(10, 0, 8);
            context.level.getBlockState(grass).randomTick(context.level, grass, context.level.random);
            context.assertBlockPresent(Blocks.DIRT, 10, 0, 8);
            CompoundTag saved = data.save(new CompoundTag());
            ListTag arenas = saved.getList("Arenas", Tag.TAG_COMPOUND);
            long[] tracked = null;
            for (int index = 0; index < arenas.size(); index++) {
                if (arenas.getCompound(index).getUUID("Boss").equals(boss)) {
                    tracked = arenas.getCompound(index).getLongArray("Positions");
                }
            }
            context.expect(tracked != null && tracked.length > 1, "Every waterfall descendant must be tracked");
            data.release(boss);
            data.advanceCleanup(context.level, 4096);
            for (long packed : tracked) {
                context.expect(!(context.level.getFluidState(BlockPos.of(packed)).getType() instanceof BossWaterFluid),
                        "Cleanup must include descendants beyond the arena and on lower levels");
            }
            context.pass();
        });
    }

    private static void falling(GameTestContext context) {
        WaterArenaData data = WaterArenaData.get(context.level);
        UUID boss = UUID.randomUUID();
        data.claim(context.level, boss, context.pos(8, 1, 8));
        var wet = context.spawn(EntityType.COW, 8, 7, 8);
        var dry = context.spawn(EntityType.COW, 20, 7, 8);
        wet.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        dry.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        context.at(30, () -> {
            context.near(wet.getHealth(), wet.getMaxHealth(), "Boss water must prevent landing damage");
            context.expect(wet.getY() < context.point(8, 3, 8).y && dry.getHealth() < dry.getMaxHealth(),
                    "Both subjects must actually fall, and the dry control must take damage");
            data.release(boss);
            data.advanceCleanup(context.level, 4096);
            context.pass();
        });
    }

}
