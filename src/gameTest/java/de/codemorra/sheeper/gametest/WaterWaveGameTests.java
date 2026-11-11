package de.codemorra.sheeper.gametest;

import java.util.List;
import java.util.UUID;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.fluid.BossWaterFluid;
import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.Blocks;

/** Timed outward flooding and slower, persistent outer-to-inner recession. */
final class WaterWaveGameTests {
    private WaterWaveGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("water_timed_flood_and_recession", WaterWaveGameTests::timing));
        tests.add(new SheeperGameTests.Scenario("water_drain_wave_persistence", WaterWaveGameTests::persistence));
        tests.add(
                new SheeperGameTests.Scenario("water_drain_defers_unloaded_outer_wave", WaterWaveGameTests::unloaded));
    }

    private static boolean wet(GameTestContext context, BlockPos pos) {
        return context.level.getFluidState(pos).getType() instanceof BossWaterFluid;
    }

    private static void timing(GameTestContext context) {
        context.set(SheeperConfig.WATER_COMBAT_RADIUS, 2.0D);
        context.set(SheeperConfig.WATER_ARENA_HEIGHT_RANGE, 0);
        context.set(SheeperConfig.WATER_ARENA_SEARCH_BUDGET_PER_SECOND, (512) * 20);
        context.set(SheeperConfig.WATER_ARENA_BLOCK_BUDGET_PER_SECOND, (512) * 20);
        context.set(SheeperConfig.WATER_ARENA_FLOOD_WAVE_SECONDS, (4) / 20.0D);
        context.set(SheeperConfig.WATER_ARENA_DRAIN_WAVE_SECONDS, (8) / 20.0D);
        for (int x = 5; x <= 11; x++) {
            for (int z = 5; z <= 11; z++) {
                if ((x - 8) * (x - 8) + (z - 8) * (z - 8) > 4) {
                    context.setBlock(x, 1, z, Blocks.STONE);
                }
            }
        }
        var boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 8);
        BlockPos center = context.pos(8, 1, 8);
        BlockPos middle = context.pos(9, 1, 8);
        BlockPos edge = context.pos(10, 1, 8);
        WaterArenaData data = WaterArenaData.get(context.level);
        context.at(2, () -> context.expect(wet(context, center) && !wet(context, middle),
                "Only the center may be flooded before the first wave interval passes"));
        context.at(7, () -> context.expect(context.level.getFluidState(middle).isSource()
                && !context.level.getFluidState(edge).isSource(),
                "The middle source layer must precede the outer source layer"));
        context.at(10, () -> context.expect(data.isGenerated(boss.getUUID())
                && context.level.getFluidState(edge).isSource(), "The final outward wave must complete generation"));
        context.at(12, () -> {
            boss.hurt(DamageSource.OUT_OF_WORLD, 10000.0F);
            data.advanceCleanup(context.level, 512);
            context.expect(!wet(context, edge) && wet(context, middle) && wet(context, center),
                    "Recession must remove the outermost layer first");
        });
        context.at(19, () -> context.expect(wet(context, middle) && wet(context, center),
                "The longer drain interval must keep inner layers visible while waiting"));
        context.at(20, () -> {
            data.advanceCleanup(context.level, 512);
            context.expect(!wet(context, middle) && wet(context, center),
                    "The middle layer must disappear before the center");
        });
        context.at(28, () -> {
            data.advanceCleanup(context.level, 512);
            context.expect(!wet(context, center) && !data.isGenerated(boss.getUUID()),
                    "The center must be removed last and finish cleanup");
            context.pass();
        });
    }

    private static void persistence(GameTestContext context) {
        context.set(SheeperConfig.WATER_ARENA_DRAIN_WAVE_SECONDS, (4) / 20.0D);
        WaterArenaData data = new WaterArenaData();
        UUID boss = UUID.randomUUID();
        BlockPos center = context.pos(8, 1, 8);
        BlockPos middle = context.pos(9, 1, 8);
        BlockPos edge = context.pos(10, 1, 8);
        data.claim(context.level, boss, center, 0);
        data.claim(context.level, boss, middle, 1);
        data.claim(context.level, boss, edge, 2);
        data.finishGeneration(boss);
        data.release(boss);
        data.advanceCleanup(context.level, 512);
        context.expect(!wet(context, edge) && wet(context, middle), "The first drain wave must remove only the edge");
        WaterArenaData restored = WaterArenaData.load(data.save(new CompoundTag()));
        context.at(1, () -> {
            restored.advanceCleanup(context.level, 512);
            context.expect(wet(context, middle), "Loading must preserve the remaining wave cooldown");
        });
        context.at(4, () -> {
            restored.advanceCleanup(context.level, 512);
            context.expect(!wet(context, middle) && wet(context, center),
                    "Loading must preserve the outer-to-inner layer order");
        });
        context.at(8, () -> {
            restored.advanceCleanup(context.level, 512);
            context.expect(!wet(context, center), "Restored cleanup must eventually reach the center");
            context.pass();
        });
    }

    private static void unloaded(GameTestContext context) {
        context.set(SheeperConfig.WATER_ARENA_DRAIN_WAVE_SECONDS, (4) / 20.0D);
        WaterArenaData data = new WaterArenaData();
        UUID boss = UUID.randomUUID();
        BlockPos center = context.pos(8, 1, 8);
        BlockPos edge = context.pos(9, 1, 8);
        BlockPos otherEdge = context.pos(8, 1, 9);
        BlockPos remote = new BlockPos(20_000_000, center.getY(), 20_000_000);
        data.claim(context.level, boss, center, 0);
        data.claim(context.level, boss, edge, 1);
        data.claim(context.level, boss, otherEdge, 1);
        CompoundTag saved = data.save(new CompoundTag());
        CompoundTag arena = saved.getList("Arenas", Tag.TAG_COMPOUND).getCompound(0);
        arena.putLongArray("Positions",
                new long[] { center.asLong(), edge.asLong(), otherEdge.asLong(), remote.asLong() });
        arena.putIntArray("Distances", new int[] { 0, 1, 1, 2 });
        WaterArenaData restored = WaterArenaData.load(saved);
        restored.release(boss);
        restored.advanceCleanup(context.level, 512);
        context.at(4, () -> {
            restored.advanceCleanup(context.level, 1);
            context.expect(!wet(context, edge) && wet(context, otherEdge) && wet(context, center),
                    "Cleanup must respect its per-tick budget within a wave");
            restored.advanceCleanup(context.level, 512);
            context.expect(!wet(context, otherEdge) && wet(context, center),
                    "An unavailable outer layer must not prevent loaded layers from receding");
        });
        context.at(8, () -> {
            restored.advanceCleanup(context.level, 512);
            context.expect(!wet(context, center) && !WaterArenaData.isLoaded(context.level, remote),
                    "Loaded cleanup must finish without forcing the outer chunk to load");
            var remaining = restored.save(new CompoundTag()).getList("Arenas", Tag.TAG_COMPOUND);
            context.expect(remaining.size() == 1 && remaining.getCompound(0).getBoolean("Cleaning")
                    && remaining.getCompound(0).getLongArray("Positions").length == 1,
                    "The deferred outer cell must remain saved for later cleanup");
            context.pass();
        });
    }
}
