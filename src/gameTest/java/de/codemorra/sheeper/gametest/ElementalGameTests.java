package de.codemorra.sheeper.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.codemorra.sheeper.config.LavaSheeperConfig;
import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.entity.ElementalSheeperEntity;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;

/**
 * Elemental boss lifecycle, independent configuration, and outbound boss bars.
 */
final class ElementalGameTests {
    private ElementalGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("elemental_boss_configuration", ElementalGameTests::configuration));
        tests.add(new SheeperGameTests.Scenario("elemental_boss_bars", ElementalGameTests::bossBars));
        tests.add(new SheeperGameTests.Scenario("elemental_boss_strength_persistence", ElementalGameTests::strength));
    }

    private static void strength(GameTestContext context) {
        for (ElementalSheeperEntity boss : bosses(context)) {
            context.near(boss.getStrengthMultiplier(), 1.0D, "New bosses must have neutral strength");
            boss.setStrengthMultiplier(1.75D);
            CompoundTag saved = boss.saveWithoutId(new CompoundTag());
            ElementalSheeperEntity loaded = (ElementalSheeperEntity) boss.getType().create(context.level);
            loaded.load(saved);
            context.near(loaded.getStrengthMultiplier(), 1.75D, "Individual strength must survive loading");
            checkSize(context, loaded, boss.getSizeMultiplier());
            checkHealth(context, loaded, boss.getMaxHealth(), boss.getHealth());

            for (double invalid : new double[] { 0.0D, -1.0D, Double.NaN, Double.POSITIVE_INFINITY }) {
                boolean rejected = false;
                try {
                    loaded.setStrengthMultiplier(invalid);
                } catch (IllegalArgumentException expected) {
                    rejected = true;
                }
                context.expect(rejected, "Invalid strength must be rejected by the setter");
                saved.putDouble("StrengthMultiplier", invalid);
                loaded.load(saved);
                context.near(loaded.getStrengthMultiplier(), 1.0D, "Invalid saved strength must use neutral strength");
            }
            saved.remove("StrengthMultiplier");
            loaded.load(saved);
            context.near(loaded.getStrengthMultiplier(), 1.0D, "Older saves must use neutral strength");
        }
        context.pass();
    }

    private static List<ElementalSheeperEntity> bosses(GameTestContext context) {
        context.set(LightningSheeperConfig.LIGHTNING_PREPARATION_DURATION, 0.0D);
        ElementalSheeperEntity lightning = context.spawn(ModEntities.LIGHTNING_SHEEPER.get(), 15, 1, 8);
        lightning.tick();
        return List.of(
                context.spawn(ModEntities.LAVA_SHEEPER.get(), 8, 1, 8),
                lightning,
                context.readyForCombat(context.spawn(ModEntities.WATER_SHEEPER.get(), 8, 1, 15)));
    }

    private static void configuration(GameTestContext context) {
        context.set(LavaSheeperConfig.LAVA_SIZE, 1.0D);
        context.set(LightningSheeperConfig.LIGHTNING_SIZE, 2.0D);
        context.set(WaterSheeperConfig.WATER_SIZE, 3.0D);
        context.set(LavaSheeperConfig.LAVA_MAX_HEALTH, 200.0D);
        context.set(LightningSheeperConfig.LIGHTNING_MAX_HEALTH, 120.0D);
        context.set(WaterSheeperConfig.WATER_MAX_HEALTH, 150.0D);
        List<ElementalSheeperEntity> bosses = bosses(context);
        float[] sizes = { 1.0F, 2.0F, 3.0F };
        float[] health = { 200.0F, 120.0F, 150.0F };
        for (int index = 0; index < bosses.size(); index++) {
            ElementalSheeperEntity boss = bosses.get(index);
            checkSize(context, boss, sizes[index]);
            checkHealth(context, boss, health[index], health[index]);
            if (boss instanceof WaterSheeperEntity) {
                // Arena damage permission has its own tests; this scene checks attributes.
                boss.setHealth(health[index] * 0.75F);
            } else {
                boss.hurt(DamageSource.GENERIC, health[index] * 0.25F);
            }
            checkHealth(context, boss, health[index], health[index] * 0.75F);
        }
        // Exercise both shrinking and growing maxima while one element stays unchanged.
        context.set(LavaSheeperConfig.LAVA_SIZE, 2.5D);
        context.set(LightningSheeperConfig.LIGHTNING_SIZE, 1.5D);
        context.set(LavaSheeperConfig.LAVA_MAX_HEALTH, 80.0D);
        context.set(LightningSheeperConfig.LIGHTNING_MAX_HEALTH, 240.0D);
        context.at(3, () -> {
            checkSize(context, bosses.get(0), 2.5F);
            checkSize(context, bosses.get(1), 1.5F);
            checkSize(context, bosses.get(2), 3.0F);
            checkHealth(context, bosses.get(0), 80.0F, 60.0F);
            checkHealth(context, bosses.get(1), 240.0F, 180.0F);
            checkHealth(context, bosses.get(2), 150.0F, 112.5F);
            context.pass();
        });
    }

    private static void checkSize(GameTestContext context, ElementalSheeperEntity boss, float size) {
        float width = EntityType.SHEEP.getWidth() * size;
        float height = EntityType.SHEEP.getHeight() * size;
        context.expect(Math.abs(boss.getSizeMultiplier() - size) < 0.001F
                && Math.abs(boss.getBbWidth() - width) < 0.001F
                && Math.abs(boss.getBbHeight() - height) < 0.001F
                && Math.abs(boss.getBoundingBox().getXsize() - width) < 0.001D
                && Math.abs(boss.getBoundingBox().getYsize() - height) < 0.001D
                && Math.abs(boss.getEyeHeight() - height * 0.95F) < 0.001F,
                "Render scale, physical bounds, and eye height must match size " + size);
    }

    private static void checkHealth(GameTestContext context, ElementalSheeperEntity boss, float maximum, float health) {
        context.expect(Math.abs(boss.getMaxHealth() - maximum) < 0.001F
                && Math.abs(boss.getHealth() - health) < 0.001F,
                "Expected boss health " + health + "/" + maximum + ", got "
                        + boss.getHealth() + "/" + boss.getMaxHealth());
    }

    private static void bossBars(GameTestContext context) {
        context.set(WaterSheeperConfig.WATER_COMBAT_RADIUS, 16.0D);
        context.set(WaterSheeperConfig.WATER_BOSS_BAR_DISTANCE, 2.5D);
        TestPlayer player = context.player(3, 1, 3);
        List<ElementalSheeperEntity> bosses = bosses(context);
        List<BossEvent.BossBarColor> colors = List.of(BossEvent.BossBarColor.RED,
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarColor.BLUE);
        bosses.forEach(boss -> boss.stopSeenByPlayer(player));
        player.bossPackets.clear();
        BossBarView view = new BossBarView();
        bosses.forEach(boss -> boss.startSeenByPlayer(player));
        // The water bar is sent when the arena updates, independent of entity tracking.
        bosses.get(2).tick();
        drainBossPackets(player, view);
        context.expect(view.bars.size() == 3, "Each boss must add its own bar within its display range");
        for (int index = 0; index < bosses.size(); index++) {
            Bar bar = view.named(bosses.get(index).getDisplayName().getString());
            context.expect(bar != null && bar.color() == colors.get(index) && bar.progress() == 1.0F,
                    "Each boss bar must start full with its element's color and display name");
        }
        ElementalSheeperEntity lava = bosses.get(0);
        lava.setCustomName(Component.literal("Renamed elemental boss"));
        lava.hurt(DamageSource.GENERIC, lava.getMaxHealth() * 0.25F);
        context.at(3, () -> {
            var water = bosses.get(2);
            String waterName = water.getDisplayName().getString();
            player.standAt(context.point(27, 1, 15));
            water.tick();
            drainBossPackets(player, view);
            context.expect(view.named(waterName) == null,
                    "The water bar must disappear beyond its configured distance from the arena");
            player.standAt(context.point(26, 1, 15));
            water.stopSeenByPlayer(player);
            water.tick();
            drainBossPackets(player, view);
            context.expect(view.named(waterName) != null,
                    "The water bar must return within the configured margin without a tracking event");
            player.standAt(context.point(3, 1, 3));
            drainBossPackets(player, view);
            Bar renamed = view.named("Renamed elemental boss");
            context.expect(renamed != null && Math.abs(renamed.progress() - 0.75F) < 0.001F,
                    "Boss packets must update the displayed name and health fraction");
            lava.stopSeenByPlayer(player);
            drainBossPackets(player, view);
            context.expect(view.bars.size() == 2 && view.named("Renamed elemental boss") == null,
                    "Leaving tracking must remove that boss's bar");
            lava.startSeenByPlayer(player);
            drainBossPackets(player, view);
            context.expect(view.bars.size() == 3 && view.named("Renamed elemental boss") != null,
                    "Tracking again must restore that boss's bar");
            bosses.get(1).discard();
            drainBossPackets(player, view);
            context.expect(view.bars.size() == 2, "Discarding a living boss must remove its bar");
            lava.discard();
            drainBossPackets(player, view);
            context.expect(view.bars.size() == 1 && view.named("Renamed elemental boss") == null,
                    "Removing another boss must preserve the remaining bar");
            bosses.get(2).discard();
            drainBossPackets(player, view);
            context.expect(view.bars.isEmpty(), "Removing the final boss must clear its bar");
            context.pass();
        });
    }

    private static void drainBossPackets(TestPlayer player, BossBarView view) {
        player.bossPackets.forEach(packet -> packet.dispatch(view));
        player.bossPackets.clear();
    }

    private record Bar(Component name, float progress, BossEvent.BossBarColor color) {
    }

    /** Reads the outbound protocol rather than inspecting private server fields. */
    private static final class BossBarView implements ClientboundBossEventPacket.Handler {
        private final Map<UUID, Bar> bars = new HashMap<>();

        private Bar named(String name) {
            return bars.values().stream().filter(bar -> bar.name().getString().equals(name)).findFirst().orElse(null);
        }

        @Override
        public void add(UUID id, Component name, float progress, BossEvent.BossBarColor color,
                BossEvent.BossBarOverlay overlay, boolean darken, boolean music, boolean fog) {
            bars.put(id, new Bar(name, progress, color));
        }

        @Override
        public void remove(UUID id) {
            bars.remove(id);
        }

        @Override
        public void updateProgress(UUID id, float progress) {
            bars.computeIfPresent(id, (key, bar) -> new Bar(bar.name(), progress, bar.color()));
        }

        @Override
        public void updateName(UUID id, Component name) {
            bars.computeIfPresent(id, (key, bar) -> new Bar(name, bar.progress(), bar.color()));
        }
    }
}
