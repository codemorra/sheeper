package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.particle.WaterMineWaveOptions;
import de.codemorra.sheeper.registry.ModParticles;
import de.codemorra.sheeper.world.ArenaWater;
import de.codemorra.sheeper.world.WaterArenaData;
import de.codemorra.sheeper.world.WaterArenaPushProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/** Schedules growing bubbles and resolves each detonation against the visible bubble's edge. */
final class WaterMineAttack {
    private static final double CONTACT_MARGIN = 0.25;
    private static final int VISUAL_INTERVAL = 4;
    private static final int MAX_MINES = 160;
    private static final double BASE_MINE_RATE = 5;
    private static final double PRESSURE_SPEED = 0.75;
    private static final int PRESSURE_VISUAL_INTERVAL = 2;
    private static final int NEAR_PLAYER_DELAY_MIN = 50;
    private static final int NEAR_PLAYER_DELAY_MAX = 80;
    private final WaterSheeperEntity boss;
    private final List<Mine> mines = new ArrayList<>();
    private final List<PressureWave> waves = new ArrayList<>();
    private int spawnTicks;
    private double ticksUntilMine;
    private int ticksUntilNearPlayer;

    private static final class Mine {
        final Vec3 center;
        final double radius;
        final float damage;
        final int fuse;
        int age;

        Mine(Vec3 center, double radius, float damage, int fuse) {
            this.center = center;
            this.radius = radius;
            this.damage = damage;
            this.fuse = fuse;
        }
    }

    private static final class PressureWave {
        final Vec3 center;
        final double maximum;
        final Set<UUID> hit = new HashSet<>();
        double radius;
        int ticks;

        PressureWave(Mine mine) {
            center = mine.center;
            radius = mine.radius + CONTACT_MARGIN;
            maximum = pressureReach(mine.radius);
        }
    }

    WaterMineAttack(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    void start() {
        clear();
        spawnTicks = randomTicks(WaterSheeperConfig.WATER_MINES_DURATION_MIN.get(),
                WaterSheeperConfig.WATER_MINES_DURATION_MAX.get());
        scheduleNearPlayerMine();
    }

    /** Continues already announced mines after the attack stops spawning new ones. */
    void tick(List<ServerPlayer> participants) {
        ServerLevel level = (ServerLevel) boss.level;
        var iterator = mines.iterator();
        while (iterator.hasNext()) {
            Mine mine = iterator.next();
            if (!boss.containsAttackPoint(mine.center) || !WaterArenaData.isLoaded(level, new BlockPos(mine.center))
                    || !ArenaWater.isClearWaterPoint(level, mine.center)) {
                iterator.remove();
                continue;
            }
            if (++mine.age >= mine.fuse) {
                explode(level, mine, participants);
                iterator.remove();
            } else if (mine.age % VISUAL_INTERVAL == 0) {
                show(level, mine);
            }
        }
        advanceWaves(level, participants);
        if (spawnTicks > 0) {
            spawnTicks--;
            if (ticksUntilNearPlayer > 0) {
                ticksUntilNearPlayer--;
            }
            double spawnRate = BASE_MINE_RATE * WaterSheeperConfig.WATER_MINES_COUNT_MULTIPLIER.get();
            while (ticksUntilMine <= 0) {
                if (mines.size() < MAX_MINES) {
                    placeMine(level, participants, spawnRate);
                }
                // Carry fractional ticks forward so the multiplier scales the average interval.
                ticksUntilMine += (3 + boss.getRandom().nextInt(3)) / spawnRate;
            }
            ticksUntilMine--;
        }
    }

    boolean isActive() {
        return spawnTicks > 0 || !mines.isEmpty() || !waves.isEmpty();
    }

    void clear() {
        spawnTicks = 0;
        ticksUntilMine = 0;
        ticksUntilNearPlayer = 0;
        mines.clear();
        waves.clear();
    }

    int cooldown() {
        return randomTicks(WaterSheeperConfig.WATER_MINES_COOLDOWN_MIN.get(),
                WaterSheeperConfig.WATER_MINES_COOLDOWN_MAX.get());
    }

    private void placeMine(ServerLevel level, List<ServerPlayer> participants, double spawnRate) {
        int fuse = findFuse(spawnRate);
        if (fuse == 0) {
            return;
        }
        double radius = randomValue(WaterSheeperConfig.WATER_MINES_DIAMETER_MIN.get(),
                WaterSheeperConfig.WATER_MINES_DIAMETER_MAX.get()) * 0.5;
        Vec3 center = null;
        if (ticksUntilNearPlayer == 0 && !participants.isEmpty()) {
            ServerPlayer player = participants.get(boss.getRandom().nextInt(participants.size()));
            center = findPosition(level, participants, radius, player);
            if (center != null) {
                scheduleNearPlayerMine();
            } else {
                // Keep the next opportunity soon while the player swims away from cover.
                ticksUntilNearPlayer = 10;
            }
        }
        if (center == null) {
            center = findPosition(level, participants, radius, null);
        }
        if (center != null) {
            float damage = (float) randomValue(WaterSheeperConfig.WATER_MINES_DAMAGE_MIN.get(),
                    WaterSheeperConfig.WATER_MINES_DAMAGE_MAX.get());
            Mine mine = new Mine(center, radius, damage, fuse);
            mines.add(mine);
            show(level, mine);
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.HOSTILE, 0.6F, 1.25F);
        }
    }

    private void scheduleNearPlayerMine() {
        ticksUntilNearPlayer = NEAR_PLAYER_DELAY_MIN
                + boss.getRandom().nextInt(NEAR_PLAYER_DELAY_MAX - NEAR_PLAYER_DELAY_MIN + 1);
    }

    /** Spreads blasts over the fuse window before assigning multiple blasts to a tick. */
    private int findFuse(double spawnRate) {
        int min = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_MINES_FUSE_MIN.get());
        int max = Math.max(min, SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_MINES_FUSE_MAX.get()));
        int count = max - min + 1;
        int start = boss.getRandom().nextInt(count);
        int[] scheduled = new int[count + 2];
        for (Mine mine : mines) {
            int index = mine.fuse - mine.age - min + 1;
            if (index >= 0 && index < scheduled.length) {
                scheduled[index]++;
            }
        }
        int shared = 0;
        // Each unscaled spawn interval averages four ticks. Allow enough slots
        // for the configured rate, while preferring the least crowded tick.
        int bestOccupancy = Mth.ceil(spawnRate / 4);
        for (int offset = 0; offset < count; offset++) {
            int index = 1 + (start + offset) % count;
            int fuse = min + index - 1;
            if (scheduled[index] == 0
                    && (spawnRate > 2 || scheduled[index - 1] == 0 && scheduled[index + 1] == 0)) {
                return fuse;
            }
            if (scheduled[index] > 0 && scheduled[index] < bestOccupancy) {
                shared = fuse;
                bestOccupancy = scheduled[index];
            }
        }
        return shared;
    }

    /** Samples the arena, or the full sphere around a swimmer for an occasional nearby mine. */
    @Nullable
    private Vec3 findPosition(ServerLevel level, List<ServerPlayer> participants, double radius,
            @Nullable ServerPlayer nearbyPlayer) {
        var volume = boss.getAttackVolume();
        if (volume == null) {
            return null;
        }
        double footprint = radius + CONTACT_MARGIN;
        double minY = volume.minimumY() + footprint;
        double maxY = volume.maximumY() + 1 - footprint;
        double reach = volume.radius() - footprint;
        if (reach <= 0 || minY > maxY) {
            return null;
        }
        double spawnClearance = footprint + 1;
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2;
            Vec3 center;
            if (nearbyPlayer == null) {
                double distance = Math.sqrt(boss.getRandom().nextDouble()) * reach;
                center = new Vec3(volume.center().getX() + 0.5 + Math.cos(angle) * distance,
                        randomValue(minY, maxY), volume.center().getZ() + 0.5 + Math.sin(angle) * distance);
            } else {
                double up = randomValue(-1, 1);
                double horizontal = Math.sqrt(1 - up * up);
                double distance = footprint + randomValue(0.25, 0.9);
                center = nearbyPlayer.getBoundingBox().getCenter().add(
                        Math.cos(angle) * horizontal * distance, up * distance,
                        Math.sin(angle) * horizontal * distance);
                Vec3 contact = nearestBodyPoint(center, nearbyPlayer);
                if (volume.interiorDistance(center) + 1.0E-6 < footprint || center.distanceToSqr(contact) < 0.0625
                        || !hasOpenBlastPath(level, center, contact)) {
                    continue;
                }
            }
            if (participants.stream().filter(player -> player != nearbyPlayer).anyMatch(player ->
                            nearestBodyPoint(center, player).distanceToSqr(center) < spawnClearance * spawnClearance)
                    || mines.stream().anyMatch(mine -> mine.center.distanceToSqr(center) < 1.0)) {
                continue;
            }
            AABB bounds = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
            if (WaterAttackBlocks.chunksLoaded(level, bounds) && ArenaWater.isClearWaterPoint(level, center)
                    && !level.getBlockCollisions(boss, bounds).iterator().hasNext()) {
                return center;
            }
        }
        return null;
    }

    private void show(ServerLevel level, Mine mine) {
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(mine.center) <= 64 * 64) {
                level.sendParticles(viewer, ModParticles.WATER_MINE.get(), true,
                        mine.center.x, mine.center.y, mine.center.z,
                        0, mine.radius + CONTACT_MARGIN, mine.fuse, mine.age, 1);
            }
        }
    }

    /** Direct contact damages once; the expanding shell can only apply the same push. */
    private void explode(ServerLevel level, Mine mine, List<ServerPlayer> participants) {
        var wave = new PressureWave(mine);
        clearBlastLeaves(level, wave.center, 0, wave.radius);
        level.sendParticles(ParticleTypes.BUBBLE, mine.center.x, mine.center.y, mine.center.z,
                24, mine.radius * 0.35, mine.radius * 0.35, mine.radius * 0.35, 0.3);
        level.playSound(null, mine.center.x, mine.center.y, mine.center.z,
                SoundEvents.DOLPHIN_SPLASH, SoundSource.HOSTILE, 0.9F, 0.7F);
        for (ServerPlayer player : participants) {
            if (!player.isAlive() || !boss.containsAttackPoint(player.position())) {
                continue;
            }
            Vec3 contact = nearestBodyPoint(mine.center, player);
            if (mine.center.distanceToSqr(contact) > wave.radius * wave.radius
                    || !hasOpenBlastPath(level, mine.center, contact)) {
                continue;
            }
            wave.hit.add(player.getUUID());
            if (mine.damage > 0) {
                player.hurt(DamageSource.mobAttack(boss), mine.damage);
            }
            if (player.isAlive()) {
                push(player, mine.center);
            }
        }
        waves.add(wave);
    }

    /** Sweeps the space between the previous and current radius without double-pushing a swimmer. */
    private void advanceWaves(ServerLevel level, List<ServerPlayer> participants) {
        var iterator = waves.iterator();
        while (iterator.hasNext()) {
            PressureWave wave = iterator.next();
            double previous = wave.radius;
            wave.radius = Math.min(wave.maximum, previous + PRESSURE_SPEED);
            clearBlastLeaves(level, wave.center, previous, wave.radius);
            for (ServerPlayer player : participants) {
                if (wave.hit.contains(player.getUUID()) || !player.isAlive()
                        || !boss.containsAttackPoint(player.position())) {
                    continue;
                }
                Vec3 contact = nearestBodyPoint(wave.center, player);
                if (wave.center.distanceToSqr(contact) <= wave.radius * wave.radius
                        && farthestBodyDistanceSquared(wave.center, player.getBoundingBox()) >= previous * previous
                        && hasOpenBlastPath(level, wave.center, contact)) {
                    wave.hit.add(player.getUUID());
                    push(player, wave.center);
                }
            }
            if (wave.ticks++ % PRESSURE_VISUAL_INTERVAL == 0 || wave.radius >= wave.maximum) {
                showWave(level, wave);
            }
            if (wave.radius >= wave.maximum) {
                iterator.remove();
            }
        }
    }

    /** Clears reachable leaves at the blast or wave front before resolving player contact. */
    private void clearBlastLeaves(ServerLevel level, Vec3 center, double previous, double radius) {
        if (!ForgeEventFactory.getMobGriefingEvent(level, boss)) {
            return;
        }
        var leaves = new ArrayList<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(center.subtract(radius, radius, radius)),
                new BlockPos(center.add(radius, radius, radius)))) {
            double x = Math.abs(pos.getX() + 0.5 - center.x);
            double y = Math.abs(pos.getY() + 0.5 - center.y);
            double z = Math.abs(pos.getZ() + 0.5 - center.z);
            if (lengthSquared(Math.max(0, x - 0.5), Math.max(0, y - 0.5), Math.max(0, z - 0.5))
                    <= radius * radius && lengthSquared(x + 0.5, y + 0.5, z + 0.5) >= previous * previous
                    && boss.isArenaWaterCell(pos) && WaterArenaData.isLoaded(level, pos)
                    && level.getBlockState(pos).is(BlockTags.LEAVES)) {
                leaves.add(pos.immutable());
            }
        }
        WaterAttackBlocks.clearReachableLeaves(boss, level, center, leaves, false);
    }

    private void showWave(ServerLevel level, PressureWave wave) {
        var volume = boss.getAttackVolume();
        if (volume == null) {
            return;
        }
        var options = new WaterMineWaveOptions(volume.center(), volume.radius(), volume.minimumY(), volume.maximumY());
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(wave.center) <= 64 * 64) {
                level.sendParticles(viewer, options, true, wave.center.x, wave.center.y, wave.center.z, 0,
                        wave.radius, PRESSURE_SPEED, wave.maximum, 1);
            }
        }
    }

    private boolean hasOpenBlastPath(ServerLevel level, Vec3 center, Vec3 contact) {
        BlockHitResult hit = WaterAttackBlocks.clipLoaded(boss, level, center, contact);
        return hit != null && hit.getType() == HitResult.Type.MISS;
    }

    private static Vec3 nearestBodyPoint(Vec3 center, ServerPlayer player) {
        AABB box = player.getBoundingBox();
        return new Vec3(Mth.clamp(center.x, box.minX, box.maxX), Mth.clamp(center.y, box.minY, box.maxY),
                Mth.clamp(center.z, box.minZ, box.maxZ));
    }

    private static double farthestBodyDistanceSquared(Vec3 center, AABB box) {
        double x = Math.max(Math.abs(center.x - box.minX), Math.abs(center.x - box.maxX));
        double y = Math.max(Math.abs(center.y - box.minY), Math.abs(center.y - box.maxY));
        double z = Math.max(Math.abs(center.z - box.minZ), Math.abs(center.z - box.maxZ));
        return lengthSquared(x, y, z);
    }

    private static double lengthSquared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    private static double pressureReach(double radius) {
        return radius * 4 + CONTACT_MARGIN;
    }

    /** Uses the same launch strengths and arena protection as the proximity knockback. */
    private static void push(ServerPlayer player, Vec3 center) {
        Vec3 away = new Vec3(player.getX() - center.x, 0, player.getZ() - center.z);
        if (away.lengthSqr() < 1.0E-8) {
            away = new Vec3(1, 0, 0);
        }
        Vec3 velocity = away.normalize().scale(WaterSheeperConfig.WATER_PROXIMITY_PUSH_STRENGTH.get());
        WaterArenaPushProtection.apply(player,
                new Vec3(velocity.x, WaterSheeperConfig.WATER_PROXIMITY_PUSH_UPWARD_STRENGTH.get(), velocity.z));
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.hurtMarked = false;
    }

    private int randomTicks(double minimum, double maximum) {
        int min = SheeperConfig.secondsToTicks(minimum);
        int max = Math.max(min, SheeperConfig.secondsToTicks(maximum));
        return min + boss.getRandom().nextInt(max - min + 1);
    }

    private double randomValue(double minimum, double maximum) {
        return minimum + boss.getRandom().nextDouble() * Math.max(0, maximum - minimum);
    }

    /** Keeps announced fuses and their individual rolls stable across a reload. */
    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("SpawnTicks", spawnTicks);
        tag.putDouble("NextMine", ticksUntilMine);
        tag.putInt("NextNearPlayerMine", ticksUntilNearPlayer);
        var entries = new ListTag();
        for (Mine mine : mines) {
            var entry = new CompoundTag();
            entry.putDouble("X", mine.center.x);
            entry.putDouble("Y", mine.center.y);
            entry.putDouble("Z", mine.center.z);
            entry.putDouble("Radius", mine.radius);
            entry.putFloat("Damage", mine.damage);
            entry.putInt("Fuse", mine.fuse);
            entry.putInt("Age", mine.age);
            entries.add(entry);
        }
        tag.put("Mines", entries);
        return tag;
    }

    void load(CompoundTag tag) {
        clear();
        spawnTicks = Mth.clamp(tag.getInt("SpawnTicks"), 0, 1200);
        double delay = tag.getDouble("NextMine");
        ticksUntilMine = Double.isFinite(delay) ? Mth.clamp(delay, -1, 20) : 0;
        ticksUntilNearPlayer = Mth.clamp(tag.getInt("NextNearPlayerMine"), 0, NEAR_PLAYER_DELAY_MAX);
        for (Tag value : tag.getList("Mines", Tag.TAG_COMPOUND)) {
            if (mines.size() >= MAX_MINES) {
                break;
            }
            CompoundTag entry = (CompoundTag) value;
            Vec3 center = new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z"));
            double radius = entry.getDouble("Radius");
            int fuse = entry.getInt("Fuse");
            int age = entry.getInt("Age");
            float damage = entry.getFloat("Damage");
            if (Double.isFinite(center.x) && Double.isFinite(center.y) && Double.isFinite(center.z)
                    && radius >= 0.375 && radius <= 3 && fuse >= 4 && fuse <= 100 && age >= 0 && age < fuse
                    && damage >= 0 && damage <= 10) {
                Mine mine = new Mine(center, radius, damage, fuse);
                mine.age = age;
                mines.add(mine);
            }
        }
    }
}
