package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Persistence and configuration contracts for the water encounter foundation.
 */
final class WaterGameTests {
    private WaterGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("water_arena_origin_persistence", WaterGameTests::origin));
        tests.add(new SheeperGameTests.Scenario("water_encounter_radii", WaterGameTests::radii));
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
