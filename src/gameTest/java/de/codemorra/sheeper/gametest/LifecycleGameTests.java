package de.codemorra.sheeper.gametest;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.SheeperEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.event.ForgeEventFactory;

/** Immediate baby reaction and deterministic natural-spawn replacement. */
final class LifecycleGameTests {
    private LifecycleGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        // Checks that damage immediately detonates a baby without Sheeper loot.
        tests.add(new SheeperGameTests.Scenario("damage_detonates_baby", LifecycleGameTests::babyDamage));
        // Checks guaranteed natural-spawn replacement while preserving age category,
        // color, and fleece state.
        tests.add(new SheeperGameTests.Scenario("natural_spawn_replaces_sheep", LifecycleGameTests::spawn));
    }

    private static void babyDamage(GameTestContext context) {
        SheeperEntity baby = context.sheeper(12, 1, 12, true, false);
        baby.hurt(DamageSource.GENERIC, 1);
        context.expect(baby.isRemoved() && context.explosions.size() == 1, "Damage must immediately detonate the baby");
        context.expect(context.sheeperDrops().isEmpty(), "Baby detonation must not produce Sheeper loot");
        context.pass();
    }

    private static void spawn(GameTestContext context) {
        context.set(SheeperConfig.NATURAL_SPAWN_CHANCE, 100.0);
        Sheep original = context.spawn(EntityType.SHEEP, 12, 1, 12);
        original.setNoAi(true);
        original.setAge(-400);
        original.setColor(DyeColor.CYAN);
        original.setSheared(true);
        ForgeEventFactory.doSpecialSpawn(original, context.level, (float) original.getX(), (float) original.getY(),
                (float) original.getZ(), null, MobSpawnType.NATURAL);
        context.at(5, () -> {
            List<SheeperEntity> replacements = context.entities(SheeperEntity.class);
            context.expect(original.isRemoved() && replacements.size() == 1,
                    "One natural sheep must become exactly one Sheeper");
            SheeperEntity replacement = replacements.get(0);
            context.expect(replacement.isBaby() && replacement.isSheared() && replacement.getColor() == DyeColor.CYAN,
                    "Replacement must retain age category, sheared state, and color");
            context.pass();
        });
    }
}
