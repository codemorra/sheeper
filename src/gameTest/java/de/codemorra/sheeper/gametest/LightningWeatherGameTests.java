package de.codemorra.sheeper.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.entity.LightningStrikeEffect;
import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.world.ArenaWeather;
import de.codemorra.sheeper.world.LightningStormData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Awakening timing and the lifecycle of real local rain and announced edge
 * strikes.
 */
final class LightningWeatherGameTests {
    private LightningWeatherGameTests() {
    }

    static void add(List<SheeperGameTests.Scenario> tests) {
        tests.add(new SheeperGameTests.Scenario("lightning_storm_awakening_reload",
                LightningWeatherGameTests::awakening));
        tests.add(new SheeperGameTests.Scenario("lightning_local_weather_and_edge_strikes",
                LightningWeatherGameTests::weather));
    }

    /**
     * Drives both entity and independent weather clocks, restoring the test world's
     * state on failure too.
     */
    private static final class Scene implements AutoCloseable {
        final GameTestContext context;
        final long originalTime;
        final float originalRain;
        final float originalThunder;
        final ServerLevelData levelData;
        long time;
        LightningSheeperEntity boss;

        Scene(GameTestContext context) {
            this.context = context;
            levelData = (ServerLevelData) context.level.getLevelData();
            originalTime = time = context.level.getGameTime();
            originalRain = context.level.getRainLevel(1);
            originalThunder = context.level.getThunderLevel(1);
            context.level.setRainLevel(0);
            context.level.setThunderLevel(0);
            boss = spawn(context);
        }

        void tick() {
            levelData.setGameTime(++time);
            for (var bolt : context.entities(LightningStrikeEffect.class)) {
                bolt.tick();
            }
            if (!boss.isRemoved() && boss.isAlive()) {
                boss.tick();
            }
            LightningStormData.get(context.level).tick(context.level);
        }

        @Override
        public void close() {
            boss.discard();
            levelData.setGameTime(originalTime);
            context.level.setRainLevel(originalRain);
            context.level.setThunderLevel(originalThunder);
        }
    }

    private static void configure(GameTestContext context) {
        context.set(SheeperConfig.LIGHTNING_SIZE, 1.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_RADIUS, 8.0D);
        context.set(SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE, 6.0D);
        context.set(SheeperConfig.LIGHTNING_MOVEMENT_RADIUS, 0.0D);
        context.set(SheeperConfig.LIGHTNING_SPAWN_PUSH_RADIUS, 0.0D);
        context.set(SheeperConfig.LIGHTNING_PROXIMITY_PUSH_RADIUS, 0.0D);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_DURATION, 0.1D);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_WAVE_COUNT, 3);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_WAVE_INTERVAL, 0.1D);
        context.set(SheeperConfig.LIGHTNING_STORM_ENABLED, true);
        context.set(SheeperConfig.LIGHTNING_STORM_AWAKENING, 3.0D);
        context.set(SheeperConfig.LIGHTNING_STORM_EDGE_ENABLED, false);
        context.set(SheeperConfig.LIGHTNING_STORM_RUMBLE_VOLUME, 0.0D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_WEIGHT, 0.0D);
        context.set(SheeperConfig.LIGHTNING_FRONT_WEIGHT, 0.0D);
        context.set(SheeperConfig.LIGHTNING_RING_WEIGHT, 0.0D);
        for (int x = 3; x <= 21; x++) {
            for (int z = 3; z <= 21; z++) {
                context.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }

    private static LightningSheeperEntity spawn(GameTestContext context) {
        LightningSheeperEntity boss = context.spawn(ModEntities.LIGHTNING_SHEEPER.get(), 12, 1, 12);
        boss.setNoGravity(true);
        return boss;
    }

    private static LightningSheeperEntity restore(GameTestContext context, CompoundTag saved) {
        LightningSheeperEntity boss = ModEntities.LIGHTNING_SHEEPER.get().create(context.level);
        boss.load(saved);
        return boss;
    }

    private static boolean hasWarning(TestPlayer player) {
        return player.particlePackets.stream().anyMatch(packet -> packet.getParticle() == ParticleTypes.END_ROD);
    }

    private static void awakening(GameTestContext context) {
        configure(context);
        context.set(SheeperConfig.LIGHTNING_STRIKE_WEIGHT, 1.0D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_INTERVAL_MIN, 0.05D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_INTERVAL_MAX, 0.05D);
        context.set(SheeperConfig.LIGHTNING_STRIKE_ARENA_COUNT, 0);
        context.set(SheeperConfig.LIGHTNING_ATTACK_PAUSE_MIN, 60.0D);
        context.set(SheeperConfig.LIGHTNING_ATTACK_PAUSE_MAX, 60.0D);
        try (Scene scene = new Scene(context)) {
            Vec3 center = Vec3.atBottomCenterOf(scene.boss.getArenaCenter());
            TestPlayer player = context.player(13, 1, 12);
            Set<UUID> seen = new HashSet<>();
            boolean centerBolt = false;
            boolean edgeBolt = false;
            for (int elapsed = 1; elapsed <= 66; elapsed++) {
                player.particlePackets.clear();
                scene.tick();
                if (elapsed < 66) {
                    context.expect(!scene.boss.isCombatReady() && scene.boss.isInvulnerableTo(DamageSource.GENERIC)
                            && !hasWarning(player),
                            "Awakening must stay protected and free of attack warnings for three seconds");
                }
                for (var bolt : context.entities(LightningStrikeEffect.class)) {
                    if (seen.add(bolt.getUUID())) {
                        Vec3 offset = bolt.position().subtract(center);
                        boolean core = Math.abs(offset.x) <= 1.01D && Math.abs(offset.z) <= 1.01D;
                        double radius = Math.sqrt(offset.horizontalDistanceSqr());
                        boolean edge = radius >= 6.0D - 1.0E-6D && radius <= 8.0D + 1.0E-6D;
                        context.expect(core || edge, "Decorative bolts must stay in the central 3x3 or outer edge");
                        centerBolt |= core;
                        edgeBolt |= edge;
                    }
                }
                if (elapsed == 20) {
                    CompoundTag saved = scene.boss.saveWithoutId(new CompoundTag());
                    scene.boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    scene.boss = restore(context, saved);
                }
                if (elapsed == 36) {
                    context.near(ArenaWeather.intensity(context.level, new BlockPos(center)), 0.5D,
                            "Rain must build gradually through the saved awakening");
                }
            }
            context.expect(centerBolt && edgeBolt && seen.size() > 2,
                    "Awakening must produce multiple center and edge bolts");
            context.expect(player.damageEvents.isEmpty(), "Ticking decorative lightning must not damage the player");
            context.expect(scene.boss.isCombatReady() && scene.boss.getTarget() == player && hasWarning(player),
                    "The first attack must start immediately after awakening, despite a long configured rest");
        }
        context.pass();
    }

    private static BlockPos skyPoint(GameTestContext context, int x, int z) {
        BlockPos column = context.pos(x, 0, z);
        int height = context.level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
        return new BlockPos(column.getX(), height + 8, column.getZ());
    }

    private static void weather(GameTestContext context) {
        configure(context);
        context.set(SheeperConfig.LIGHTNING_PREPARATION_DURATION, 0.0D);
        context.set(SheeperConfig.LIGHTNING_STORM_EDGE_WARNING, 1.5D);
        context.set(SheeperConfig.LIGHTNING_STORM_EDGE_DAMAGE, 2.0D);
        // Rain is column-based: probe above natural terrain instead of excavating the
        // underground arena.
        BlockPos exposed = skyPoint(context, 10, 12);
        BlockPos sheltered = skyPoint(context, 12, 10);
        BlockPos outside = skyPoint(context, 22, 12);
        BlockPos roof = sheltered.above(3);
        var oldRoof = context.level.getBlockState(roof);
        var enderman = context.spawn(EntityType.ENDERMAN, 10, 1, 12);
        enderman.setNoAi(true);
        enderman.setNoGravity(true);
        enderman.setPos(Vec3.atBottomCenterOf(exposed));
        try {
            context.level.setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
            weatherAndEdges(context, exposed, sheltered, outside, enderman);
        } finally {
            enderman.discard();
            context.level.setBlockAndUpdate(roof, oldRoof);
        }
        context.pass();
    }

    private static void weatherAndEdges(GameTestContext context, BlockPos exposed, BlockPos sheltered,
            BlockPos outside, Entity enderman) {
        try (Scene scene = new Scene(context)) {
            TestPlayer observer = context.player(22, 1, 12);
            for (int index = 0; index < 15; index++) {
                scene.tick();
            }
            context.expect(context.level.canSeeSky(exposed), "The rain control must be above natural terrain");
            context.expect(!context.level.isRaining() && context.level.isRainingAt(exposed)
                    && enderman.isInWaterRainOrBubble(),
                    "Local rain must wet exposed Endermen without changing dimension weather");
            context.expect(!context.level.isRainingAt(sheltered) && !context.level.isRainingAt(outside),
                    "Roofs and the arena boundary must block local rain");
            context.expect(scene.boss.getCombatParticipants().isEmpty() && !hasWarning(observer)
                    && context.entities(LightningStrikeEffect.class).isEmpty(),
                    "Weather must work without arena participants while disabled edge strikes stay inactive");
            CompoundTag saved = scene.boss.saveWithoutId(new CompoundTag());
            scene.boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            scene.tick();
            context.expect(context.level.isRainingAt(exposed), "Unloading the boss must preserve arena rain");

            context.set(SheeperConfig.LIGHTNING_STORM_EDGE_ENABLED, true);
            context.level.random.setSeed(1973L);
            Vec3 marker = null;
            for (int attempts = 0; attempts < 400 && marker == null; attempts++) {
                observer.particlePackets.clear();
                scene.tick();
                marker = observer.particlePackets.stream()
                        .filter(packet -> packet.getParticle() == ParticleTypes.END_ROD)
                        .map(packet -> new Vec3(packet.getX(), packet.getY() - 0.15D, packet.getZ())).findFirst()
                        .orElse(null);
            }
            context.expect(marker != null,
                    "Enabled edge lightning must announce strikes without a loaded boss or arena player");
            Vec3 offset = marker.subtract(Vec3.atBottomCenterOf(scene.boss.getArenaCenter()));
            context.expect(
                    offset.horizontalDistanceSqr() >= 36 - 1.0E-6D && offset.horizontalDistanceSqr() <= 64 + 1.0E-6D,
                    "Announced ambient strikes must stay in the outer two blocks");
            observer.standAt(marker);
            for (int elapsed = 1; elapsed <= 30; elapsed++) {
                observer.tick();
                scene.tick();
                if (elapsed < 30) {
                    context.near(observer.getHealth(), 20, "Edge lightning must honor the full warning delay");
                }
            }
            context.near(observer.getHealth(), 18, "The announced edge strike must deal one heart");
            context.set(SheeperConfig.LIGHTNING_STORM_EDGE_ENABLED, false);
            for (int elapsed = 0; elapsed < 40; elapsed++) {
                observer.particlePackets.clear();
                observer.tick();
                scene.tick();
                context.expect(!hasWarning(observer), "Disabling edge lightning must cancel pending warnings");
            }
            context.near(observer.getHealth(), 18, "Disabled edge lightning must not cause delayed damage");
            scene.boss = restore(context, saved);
            scene.boss.hurt(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
            context.expect(!context.level.isRainingAt(exposed), "Boss death must immediately end local rain");
            scene.boss.discard();
            scene.boss = spawn(context);
            scene.tick();
            scene.tick();
            context.expect(context.level.isRainingAt(exposed), "A new encounter must start its own local weather");
            scene.boss.discard();
            context.expect(!context.level.isRainingAt(exposed), "Permanent removal must also clear local rain");
        }
    }
}
