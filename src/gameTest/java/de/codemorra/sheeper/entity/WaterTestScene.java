package de.codemorra.sheeper.entity;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.gametest.GameTestContext;
import de.codemorra.sheeper.gametest.TestPlayer;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;

/** Small, submerged fixtures; every balance-sensitive assertion uses scene values. */
final class WaterTestScene {
    private WaterTestScene() {
    }

    static WaterSheeperEntity boss(GameTestContext context) {
        context.difficulty(Difficulty.NORMAL);
        context.set(WaterSheeperConfig.WATER_SIZE, 1.0D);
        context.set(WaterSheeperConfig.WATER_MAX_HEALTH, 100.0D);
        context.set(WaterSheeperConfig.WATER_COMBAT_RADIUS, 8.0D);
        context.set(WaterSheeperConfig.WATER_ARENA_WATER_HEIGHT, 6);
        context.set(WaterSheeperConfig.WATER_ARENA_HEIGHT_RANGE, 0);
        context.set(WaterSheeperConfig.WATER_MOVEMENT_EDGE_DISTANCE, 1.0D);
        context.set(WaterSheeperConfig.WATER_SPAWN_WAVE_RADIUS, 0.0D);
        context.set(WaterSheeperConfig.WATER_PROXIMITY_PUSH_RADIUS, 0.0D);
        context.set(WaterSheeperConfig.WATER_CLOSE_RANGE_DISTANCE, 3.0D);
        context.set(WaterSheeperConfig.WATER_CLOSE_RANGE_STOP_DISTANCE, 1.0D);
        var boss = context.spawn(ModEntities.WATER_SHEEPER.get(), 10, 1, 10);
        boss.setNoAi(true);
        // Capture bounds with AI disabled, then bypass the remaining flood work.
        boss.tick();
        context.readyForCombat(boss);
        return boss;
    }

    static TestPlayer player(GameTestContext context, int x, int y, int z) {
        for (int dy = 0; dy <= 2; dy++) {
            context.setBlock(x, y + dy, z, Blocks.WATER);
        }
        return context.player(x, y, z);
    }

    /** Fill only the small captured cylinder; do not involve fluid scheduling in controller tests. */
    static void fill(GameTestContext context, WaterSheeperEntity boss) {
        BlockPos center = boss.getArenaCenter();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, 0, -8), center.offset(8, 5, 8))) {
            if (boss.isArenaWaterCell(pos)) {
                context.level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
            }
        }
    }

    static void ticks(int count, Runnable action) {
        for (int tick = 0; tick < count; tick++) {
            action.run();
        }
    }
}
