package de.codemorra.sheeper.gametest;

import java.util.ArrayList;
import java.util.List;

import com.electronwill.nightconfig.core.CommentedConfig;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.SheeperEntity;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Owns one sequential GameTest scene and restores its settings on success,
 * assertion failure, or timeout. Only test-world entities and settings are
 * touched.
 * Scene coordinates use floor y=0; Minecraft loads that floor one block above
 * the structure block used as GameTestHelper's origin.
 */
public final class GameTestContext implements AutoCloseable {
    private static GameTestContext active;

    final GameTestHelper helper;
    final ServerLevel level;
    final List<Explosion> explosions = new ArrayList<>();
    private final List<TestPlayer> players = new ArrayList<>();
    private final CommentedConfig originalConfig;
    private final List<Runnable> ruleRestorers = new ArrayList<>();
    private final AABB bounds;
    private final long startTick;
    private boolean closed;

    GameTestContext(GameTestHelper helper) {
        closeActive();
        this.helper = helper;
        level = helper.getLevel();
        startTick = helper.getTick();
        bounds = new AABB(pos(0, 0, 0), pos(24, 9, 24)).inflate(3);
        clearEntities();
        originalConfig = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.COMMON).stream()
                .filter(config -> config.getSpec() == SheeperConfig.SPEC)
                .findFirst().orElseThrow(() -> new IllegalStateException("Sheeper common config is not loaded"))
                .getConfigData();
        // A file-backed ConfigValue.set() autosaves and triggers asynchronous reloads.
        // Give this scene fresh defaults in memory; the real config remains untouched.
        CommentedConfig sceneConfig = CommentedConfig.inMemory();
        SheeperConfig.SPEC.correct(sceneConfig);
        SheeperConfig.SPEC.setConfig(sceneConfig);
        active = this;
        MinecraftForge.EVENT_BUS.register(this);
        set(SheeperConfig.NATURAL_SPAWN_CHANCE, 0.0);
        // Other scenarios isolate gameplay from timing; wave scenarios opt in
        // explicitly.
        set(SheeperConfig.WATER_ARENA_FLOOD_WAVE_TICKS, 0);
        set(SheeperConfig.WATER_ARENA_DRAIN_WAVE_TICKS, 0);
        rule(GameRules.RULE_MOBGRIEFING, false);
        rule(GameRules.RULE_DOMOBLOOT, true);
        rule(GameRules.RULE_DOBLOCKDROPS, true);
        rule(GameRules.RULE_DOMOBSPAWNING, false);
        rule(GameRules.RULE_DOFIRETICK, false);
        rule(GameRules.RULE_NATURAL_REGENERATION, false);
        int oldRandomTicks = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer());
        ruleRestorers.add(() -> level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING)
                .set(oldRandomTicks, level.getServer()));
    }

    static void closeActive() {
        if (active != null) {
            active.close();
        }
    }

    /**
     * Waits for every arena chunk to expose and tick its entities before actions
     * begin.
     */
    static void requireReady(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(BlockPos.ZERO);
        BlockPos max = helper.absolutePos(new BlockPos(23, 0, 23));
        for (int chunkX = min.getX() >> 4; chunkX <= max.getX() >> 4; chunkX++) {
            for (int chunkZ = min.getZ() >> 4; chunkZ <= max.getZ() >> 4; chunkZ++) {
                long chunk = net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
                BlockPos position = new BlockPos(chunkX * 16, min.getY(), chunkZ * 16);
                if (!level.areEntitiesLoaded(chunk) || !level.isPositionEntityTicking(position)) {
                    helper.fail("Waiting for arena chunks and entity tracking to become ready");
                }
            }
        }
    }

    /**
     * Captures explosions at this scene, including real Forge detonation events.
     */
    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() == level && bounds.contains(event.getExplosion().getPosition())) {
            explosions.add(event.getExplosion());
        }
    }

    <T> void set(ForgeConfigSpec.ConfigValue<T> value, T replacement) {
        value.set(replacement);
    }

    void rule(GameRules.Key<GameRules.BooleanValue> key, boolean value) {
        boolean old = level.getGameRules().getBoolean(key);
        ruleRestorers.add(() -> level.getGameRules().getRule(key).set(old, level.getServer()));
        level.getGameRules().getRule(key).set(value, level.getServer());
    }

    BlockPos pos(int x, int y, int z) {
        return helper.absolutePos(new BlockPos(x, y + 1, z));
    }

    void setBlock(int x, int y, int z, Block block) {
        setBlock(x, y, z, block.defaultBlockState());
    }

    void setBlock(int x, int y, int z, BlockState state) {
        helper.setBlock(x, y + 1, z, state);
    }

    void assertBlockPresent(Block block, int x, int y, int z) {
        helper.assertBlockPresent(block, x, y + 1, z);
    }

    Vec3 point(int x, int y, int z) {
        return Vec3.atBottomCenterOf(pos(x, y, z));
    }

    TestPlayer player(int x, int y, int z) {
        TestPlayer player = new TestPlayer(level, point(x, y, z));
        players.add(player);
        level.addNewPlayer(player);
        expect(level.players().contains(player), "Test player must participate in the world's player list");
        return player;
    }

    SheeperEntity sheeper(int x, int y, int z, boolean baby, boolean ai) {
        SheeperEntity sheeper = spawn(ModEntities.SHEEPER.get(), x, y, z);
        sheeper.setAge(baby ? -24000 : 0);
        sheeper.setNoAi(!ai);
        return sheeper;
    }

    <E extends Entity> E spawn(EntityType<E> type, int x, int y, int z) {
        E entity = helper.spawn(type, x, y + 1, z);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        return entity;
    }

    ItemEntity item(Item item, int count, int x, int y, int z) {
        Vec3 position = point(x, y, z);
        ItemEntity entity = new ItemEntity(level, position.x, position.y, position.z, new ItemStack(item, count));
        entity.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(entity);
        return entity;
    }

    int count(Item item) {
        return entities(ItemEntity.class).stream().filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    /**
     * Restricts loot assertions to this mod's items rather than unrelated world
     * drops.
     */
    List<ItemEntity> sheeperDrops() {
        return entities(ItemEntity.class).stream().filter(entity -> {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(entity.getItem().getItem());
            return id != null && id.getNamespace().equals(SheeperMod.MODID);
        }).toList();
    }

    <T extends Entity> List<T> entities(Class<T> type) {
        return level.getEntitiesOfClass(type, bounds, entity -> !entity.isRemoved());
    }

    /** Schedules an assertion relative to the prepared scene's first tick. */
    void at(int tick, Runnable action) {
        helper.runAtTickTime(startTick + tick, guarded(action));
    }

    private Runnable guarded(Runnable action) {
        return () -> {
            if (closed)
                return;
            try {
                action.run();
            } catch (RuntimeException failure) {
                close();
                throw failure;
            }
        };
    }

    void expect(boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    void near(double actual, double expected, String message) {
        expect(Math.abs(actual - expected) < 0.0001,
                message + ": expected " + expected + ", got " + actual);
    }

    void pass() {
        close();
        helper.succeed();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        MinecraftForge.EVENT_BUS.unregister(this);
        clearEntities();
        for (TestPlayer player : players) {
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
        SheeperConfig.SPEC.setConfig(originalConfig);
        for (int index = ruleRestorers.size() - 1; index >= 0; index--) {
            ruleRestorers.get(index).run();
        }
        if (active == this) {
            active = null;
        }
    }

    private void clearEntities() {
        // discard() avoids creating death bursts or loot while preparing/clearing a
        // scene.
        for (Entity entity : level.getEntitiesOfClass(Entity.class, bounds)) {
            if (!(entity instanceof net.minecraft.world.entity.player.Player)) {
                entity.discard();
            }
        }
    }
}
