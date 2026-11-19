package de.codemorra.sheeper.entity;

import java.util.List;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.gametest.GameTestContext;
import de.codemorra.sheeper.gametest.SheeperGameTests.Scenario;
import de.codemorra.sheeper.network.WaterArenaPacket;
import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.world.ArenaWater;
import de.codemorra.sheeper.world.WaterArenaData;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

/** Observable virtual-arena lifecycle with explicit scene timing and no simulated client movement. */
public final class WaterLifecycleGameTests {
    private WaterLifecycleGameTests() {
    }

    public static void add(List<Scenario> tests) {
        tests.add(new Scenario("water_virtual_startup_preserves_terrain_and_reloads", WaterLifecycleGameTests::startup));
        tests.add(new Scenario("water_spawn_wave_trails_front_and_retries_walls", WaterLifecycleGameTests::spawnWave));
        tests.add(new Scenario("water_death_keeps_arena_until_animation_ends", WaterLifecycleGameTests::death));
        tests.add(new Scenario("water_drain_reloads_and_lowers_beyond_virtual_floor", WaterLifecycleGameTests::drain));
        tests.add(new Scenario("water_drain_preserves_natural_and_overlapping_water", WaterLifecycleGameTests::overlap));
    }

    private static void configure(GameTestContext c, double startupSeconds, double drainSeconds) {
        // Previous scenes may leave receding arenas; isolate the lifecycle under test.
        c.level.getDataStorage().set("sheeper_water_arenas", new WaterArenaData());
        c.set(WaterSheeperConfig.WATER_SIZE, 1.0D);
        c.set(WaterSheeperConfig.WATER_COMBAT_RADIUS, 8.0D);
        c.set(WaterSheeperConfig.WATER_ARENA_WATER_HEIGHT, 6);
        c.set(WaterSheeperConfig.WATER_ARENA_HEIGHT_RANGE, 0);
        c.set(WaterSheeperConfig.WATER_ARENA_BUILD_DURATION, startupSeconds);
        c.set(WaterSheeperConfig.WATER_ARENA_DRAIN_DURATION, drainSeconds);
        c.set(WaterSheeperConfig.WATER_SPAWN_PUSH_SPEED, 0.0D);
    }

    private static WaterSheeperEntity boss(GameTestContext c, int x, boolean ready) {
        var boss = c.spawn(ModEntities.WATER_SHEEPER.get(), x, 1, 10);
        boss.setNoAi(true);
        boss.tick();
        if (ready) {
            c.readyForCombat(boss);
        }
        return boss;
    }

    /** Exercises both saved clocks and the wire format using the same active world state. */
    private static void reload(GameTestContext c) {
        WaterArenaData data = WaterArenaData.get(c.level);
        var before = WaterArenaData.animatedVolumes(c.level);
        c.level.getDataStorage().set("sheeper_water_arenas", WaterArenaData.load(data.save(new CompoundTag())));
        c.expect(before.equals(WaterArenaData.animatedVolumes(c.level)),
                "Reload must preserve geometry and lifecycle clocks without restarting either phase");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new WaterArenaPacket(c.level.dimension().location(), before, 0, 1.25D, 0.3D);
            packet.encode(buffer);
            c.expect(packet.equals(WaterArenaPacket.decode(buffer)),
                    "Animated geometry and both clocks must survive the actual network format");
        } finally {
            buffer.release();
        }
    }

    private static void startup(GameTestContext c) {
        configure(c, 1.2D, 1.0D);
        c.setBlock(12, 0, 12, Blocks.DIRT);
        c.setBlock(12, 1, 12, Blocks.FERN);
        var button = Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        c.setBlock(11, 1, 13, button);
        var boss = boss(c, 10, false);
        var player = c.player(15, 2, 10);
        var cow = c.spawn(EntityType.COW, 14, 2, 11);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        Vec3 sample = player.getEyePosition();
        c.expect(!boss.isCombatReady() && !ArenaWater.contains(c.level, sample),
                "Combat and distant virtual swimming must wait for the startup front");
        c.expect(!boss.hurt(DamageSource.playerAttack(player), 1), "The preparing boss must reject combat damage");
        c.at(8, () -> {
            var volume = WaterArenaData.animatedVolumes(c.level).get(0);
            double radius = volume.at(c.level.getGameTime()).radius();
            c.expect(radius > 1 && radius < volume.bounds().radius(), "The usable radius must grow over time");
            reload(c);
        });
        c.at(28, () -> {
            c.expect(boss.isCombatReady() && ArenaWater.contains(c.level, sample),
                    "The configured startup duration must finish even after reloading");
            c.expect(c.level.getFluidState(new BlockPos(sample)).isEmpty()
                    && player.isInWater() && player.getEyeInFluidType() == ForgeMod.WATER_TYPE.get(),
                    "Actual movement and eye-fluid hooks must supply water physics in air blocks");
            c.expect(cow.isInWater() && cow.getEyeInFluidType() == ForgeMod.WATER_TYPE.get(),
                    "A normal mob must receive both body and eye water state through the same hooks");
            c.expect(c.level.getBlockState(c.pos(12, 1, 12)).is(Blocks.FERN)
                    && c.level.getBlockState(c.pos(11, 1, 13)).equals(button),
                    "Arena startup must preserve plants and attached blocks");
            c.pass();
        });
    }

    private static void spawnWave(GameTestContext c) {
        configure(c, 2.0D, 1.0D);
        c.set(WaterSheeperConfig.WATER_SPAWN_PUSH_SPEED, 4.0D);
        c.set(WaterSheeperConfig.WATER_SPAWN_PUSH_EDGE_DISTANCE, 2.0D);
        c.set(WaterSheeperConfig.WATER_SPAWN_PUSH_LIFT_HEIGHT, 1.0D);
        boss(c, 10, false);
        var blocked = c.player(13, 2, 10);
        var opposite = c.player(7, 2, 10);
        var outer = c.player(17, 2, 10);
        var creative = c.player(10, 2, 13);
        creative.setGameMode(GameType.CREATIVE);
        c.setBlock(14, 2, 10, Blocks.STONE);
        c.setBlock(14, 3, 10, Blocks.STONE);
        c.at(12, () -> {
            WaterArenaData.tick(c.level);
            c.near(blocked.getDeltaMovement().x, 0, "The front must pass before transport begins behind it");
        });
        c.at(30, () -> {
            blocked.move(MoverType.SELF, new Vec3(1, 0, 0));
            blocked.standAt(blocked.position());
            WaterArenaData.tick(c.level);
            c.expect(blocked.getDeltaMovement().x > 0 && opposite.getDeltaMovement().x < 0,
                    "The wave must carry multiple players outward on either side of the center: blocked="
                            + blocked.getDeltaMovement() + ", opposite=" + opposite.getDeltaMovement()
                            + ", front=" + WaterArenaData.animatedVolumes(c.level));
            c.near(outer.getDeltaMovement().x, 0, "Players already in the destination ring must remain untouched");
            c.expect(ArenaWater.contains(c.level, creative.getEyePosition()) && !creative.isInWater()
                    && creative.getEyeInFluidType().isAir(), "Creative must bypass virtual water physics in the active arena");
            c.near(creative.getDeltaMovement().horizontalDistanceSqr(), 0,
                    "The spawn wave must not push Creative players after reaching them");
            Vec3 position = blocked.position();
            blocked.move(MoverType.SELF, new Vec3(blocked.getDeltaMovement().x, 0, 0));
            c.near(blocked.getX(), position.x, "The pressure wave must respect real wall collision");
            WaterArenaData.tick(c.level);
            c.expect(blocked.getDeltaMovement().x > 0, "A blocked tick must not cancel the remaining push");
        });
        c.at(35, () -> {
            WaterArenaData.tick(c.level);
            c.expect(blocked.getDeltaMovement().x > 0, "The wave must keep pushing against a taller wall");
        });
        c.at(43, () -> {
            WaterArenaData.tick(c.level);
            c.near(blocked.getDeltaMovement().x, 0, "Wave completion must release remaining forced transport");
            c.expect(blocked.getHealth() == blocked.getMaxHealth() && opposite.getHealth() == opposite.getMaxHealth(),
                    "Spawn transport must not deal damage");
            c.pass();
        });
    }

    private static void death(GameTestContext c) {
        configure(c, 0.5D, 1.5D);
        var boss = boss(c, 10, true);
        Vec3 sample = boss.position().add(0, 1, 0);
        var shot = c.spawn(ModEntities.WATER_PROJECTILE.get(), 10, 4, 10);
        shot.setOwner(boss);
        boss.hurt(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
        shot.tick();
        c.expect(!boss.isAlive() && shot.isRemoved(), "Defeat must immediately cancel lingering boss projectiles");
        c.at(1, () -> c.expect(!boss.isRemoved() && boss.getDeathAnimationProgress(0) > 0
                && ArenaWater.contains(c.level, sample), "The death animation must retain its arena before removal"));
        awaitDeath(c, boss, sample, 2);
    }

    private static void awaitDeath(GameTestContext c, WaterSheeperEntity boss, Vec3 sample, int tick) {
        c.at(tick, () -> {
            if (!boss.isRemoved()) {
                awaitDeath(c, boss, sample, tick + 1);
                return;
            }
            c.expect(ArenaWater.contains(c.level, sample)
                    && WaterArenaData.animatedVolumes(c.level).stream().anyMatch(volume -> volume.isDraining()),
                    "Finishing the death animation must start drainage instead of deleting the water immediately");
            c.pass();
        });
    }

    private static void drain(GameTestContext c) {
        configure(c, 0.5D, 1.3D);
        var boss = boss(c, 10, true);
        c.setBlock(13, 0, 10, Blocks.AIR);
        c.setBlock(13, -1, 10, Blocks.AIR);
        c.setBlock(13, -2, 10, Blocks.STONE);
        var player = c.player(13, 1, 10);
        double floor = boss.getArenaWaterMinimumY() + ArenaWater.BOUNDARY_MARGIN;
        player.standAt(new Vec3(player.getX(), floor, player.getZ()));
        player.setOnGround(true);
        boss.discard();
        c.at(2, () -> WaterArenaData.tick(c.level));
        c.at(8, () -> {
            var volume = WaterArenaData.animatedVolumes(c.level).get(0);
            double surface = volume.at(c.level.getGameTime()).maximumY() + 1;
            c.expect(surface > volume.bounds().minimumY() && surface < volume.bounds().maximumY() + 1,
                    "The water surface must recede before drainage completes");
            c.expect(Math.abs(surface - Math.rint(surface)) > 0.001D,
                    "The receding surface must support fractional heights instead of block-sized steps");
            reload(c);
        });
        c.at(30, () -> {
            c.expect(WaterArenaData.activeVolumes(c.level).isEmpty(), "Expired water must leave no virtual floor or fog volume");
            player.standAt(new Vec3(player.getX(), floor - 0.5D, player.getZ()));
            player.setOnGround(false);
            WaterArenaData.tick(c.level);
            c.expect(player.getDeltaMovement().y < 0, "Saved descent must continue below the former virtual floor");
            player.standAt(c.point(13, -1, 10));
            player.setOnGround(true);
            player.setDeltaMovement(0, 0.1D, 0);
            WaterArenaData.tick(c.level);
            c.near(player.getDeltaMovement().y, 0.1D, "Real ground must end forced descent");
            float health = player.getHealth();
            player.causeFallDamage(12, 1, DamageSource.FALL);
            c.near(player.getHealth(), health, "Landing after arena drainage must not cause fall damage");
            player.invulnerableTime = 0;
            player.causeFallDamage(4, 1, DamageSource.FALL);
            c.expect(player.getHealth() < health, "Landing protection must not grant permanent fall immunity");
            c.pass();
        });
    }

    private static void overlap(GameTestContext c) {
        configure(c, 0.5D, 1.0D);
        var draining = boss(c, 10, true);
        var remaining = boss(c, 16, true);
        var overlap = c.player(14, 2, 10);
        var natural = c.player(7, 2, 10);
        c.setBlock(7, 2, 10, Blocks.WATER);
        draining.discard();
        c.at(15, () -> {
            overlap.setDeltaMovement(0, 0.125D, 0);
            natural.setDeltaMovement(0, 0.125D, 0);
            WaterArenaData.tick(c.level);
            c.near(overlap.getDeltaMovement().y, 0.125D, "Another arena must prevent forced descent in its water");
            c.near(natural.getDeltaMovement().y, 0.125D, "Natural water must retain its own swimming behavior");
            c.expect(c.level.getBlockState(c.pos(7, 2, 10)).is(Blocks.WATER), "Drainage must not remove natural water blocks");
        });
        c.at(24, () -> {
            c.expect(remaining.isCombatReady() && ArenaWater.contains(c.level, overlap.getEyePosition()),
                    "One arena's completion must preserve another active arena's water and readiness");
            c.pass();
        });
    }
}
