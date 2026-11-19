package de.codemorra.sheeper.entity;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.gametest.GameTestContext;
import de.codemorra.sheeper.gametest.TestPlayer;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.world.Difficulty;

/** Small virtual-water fixtures; every balance-sensitive assertion uses scene values. */
final class WaterTestScene {
    private WaterTestScene() {
    }

    static WaterSheeperEntity boss(GameTestContext context) {
        return boss(context, 10);
    }

    static WaterSheeperEntity boss(GameTestContext context, int x) {
        context.difficulty(Difficulty.NORMAL);
        context.set(WaterSheeperConfig.WATER_SIZE, 1.0D);
        context.set(WaterSheeperConfig.WATER_MAX_HEALTH, 100.0D);
        context.set(WaterSheeperConfig.WATER_COMBAT_RADIUS, 8.0D);
        context.set(WaterSheeperConfig.WATER_BOUNDARY_SLOWDOWN_WIDTH, 1.0D);
        context.set(WaterSheeperConfig.WATER_ARENA_WATER_HEIGHT, 6);
        context.set(WaterSheeperConfig.WATER_ARENA_HEIGHT_RANGE, 0);
        context.set(WaterSheeperConfig.WATER_MOVEMENT_EDGE_DISTANCE, 1.0D);
        context.set(WaterSheeperConfig.WATER_SPAWN_PUSH_SPEED, 0.0D);
        context.set(WaterSheeperConfig.WATER_PROXIMITY_PUSH_RADIUS, 0.0D);
        context.set(WaterSheeperConfig.WATER_CLOSE_RANGE_DISTANCE, 3.0D);
        context.set(WaterSheeperConfig.WATER_CLOSE_RANGE_STOP_DISTANCE, 1.0D);
        var boss = context.spawn(ModEntities.WATER_SHEEPER.get(), x, 1, 10);
        boss.setNoAi(true);
        // Capture bounds with AI disabled, then bypass the remaining startup delay.
        boss.tick();
        context.readyForCombat(boss);
        return boss;
    }

    static TestPlayer player(GameTestContext context, int x, int y, int z) {
        return context.player(x, y, z);
    }

    static void ticks(int count, Runnable action) {
        for (int tick = 0; tick < count; tick++) {
            action.run();
        }
    }
}
