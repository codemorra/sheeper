package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.particle.MaelstromWaveOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Expanding pressure wave with live contact checks, a safe outer ring and
 * wave-limited damage.
 */
final class WaterMaelstromWave {
    // Half-second pulses respect vanilla damage immunity and keep armor scaling
    // stable.
    private static final int DAMAGE_INTERVAL_TICKS = 10;
    private final WaterSheeperEntity boss;
    private final WaterMaelstromTransport transports;
    private final Map<UUID, Double> pending = new HashMap<>();
    private final Map<UUID, Integer> damageDelays = new HashMap<>();
    private Vec3 origin = Vec3.ZERO;
    private double radius;
    private double maximumRadius;
    private int visualTicks;

    /** @param boss owner supplying current combat participants and arena bounds */
    WaterMaelstromWave(WaterSheeperEntity boss) {
        this.boss = boss;
        transports = new WaterMaelstromTransport(boss);
    }

    /**
     * Tracks the players present at release; later contact uses their current
     * positions.
     */
    void start(Vec3 impact) {
        clear();
        origin = impact;
        double dx = origin.x - boss.getArenaCenter().getX() - 0.5D;
        double dz = origin.z - boss.getArenaCenter().getZ() - 0.5D;
        maximumRadius = boss.getArenaRadius() + Math.sqrt(dx * dx + dz * dz);
        for (ServerPlayer player : boss.getCombatParticipants()) {
            if (boss.containsAttackPoint(player.position())) {
                pending.put(player.getUUID(), player.position().subtract(origin).horizontalDistance());
            }
        }
    }

    /**
     * Resolves contact against the moving wave front and current player position.
     * Damage is allowed only while the wave expands; reaching the outer safe ring
     * immediately ends damage and forced transport for that player.
     *
     * @param players current participants; arena exit or death also removes their
     *                effects
     */
    void tick(List<ServerPlayer> players) {
        Map<UUID, ServerPlayer> current = new HashMap<>();
        for (ServerPlayer player : players) {
            if (player.isAlive() && boss.containsAttackPoint(player.position())) {
                current.put(player.getUUID(), player);
            }
        }
        if (isExpanding()) {
            double previousRadius = radius;
            radius = Math.min(maximumRadius, radius + WaterSheeperConfig.WATER_MAELSTROM_WAVE_SPEED.get() / 20.0D);
            clearFrontLeaves(previousRadius);
            if (visualTicks++ % 2 == 0 || radius == maximumRadius) {
                showFront(players);
            }
            if (visualTicks % 10 == 1) {
                playFrontSound(players);
            }
            var arrivals = pending.entrySet().iterator();
            while (arrivals.hasNext()) {
                var entry = arrivals.next();
                ServerPlayer player = current.get(entry.getKey());
                if (player == null) {
                    arrivals.remove();
                    continue;
                }
                double distance = player.position().subtract(origin).horizontalDistance();
                double bodyRadius = player.getBbWidth() * 0.5D;
                // The relative gap detects crossings by either the front or the
                // swimmer. A front that already passed cannot hit retroactively.
                boolean contact = entry.getValue() - previousRadius >= -bodyRadius
                        && distance - radius <= bodyRadius;
                entry.setValue(distance);
                if (contact && !isSafeArea(boss, player.position())) {
                    damageDelays.put(entry.getKey(), 0);
                    transports.start(player, origin);
                    arrivals.remove();
                }
            }
            int interval = DAMAGE_INTERVAL_TICKS;
            float amount = (float) (WaterSheeperConfig.WATER_MAELSTROM_DAMAGE.get() * interval / 20.0D);
            var damage = damageDelays.entrySet().iterator();
            while (damage.hasNext()) {
                var entry = damage.next();
                ServerPlayer player = current.get(entry.getKey());
                if (player == null || !player.isAlive() || isSafeArea(boss, player.position())) {
                    damage.remove();
                    continue;
                }
                int delay = Math.max(0, entry.getValue() - 1);
                if (delay == 0 && amount > 0) {
                    player.hurt(DamageSource.mobAttack(boss), amount);
                    // Shield blocks and ordinary immunity still consume this
                    // pulse; retrying every tick would spam blocked hits.
                    delay = interval;
                }
                entry.setValue(delay);
            }
        }
        if (radius >= maximumRadius) {
            // No lingering damage after the visible wave has finished, including
            // restored saves written by the former duration-based implementation.
            pending.clear();
            damageDelays.clear();
        }
        transports.tick(List.copyOf(current.values()));
    }

    /** @return whether the pressure front can still contact players or blocks */
    boolean isExpanding() {
        return radius < maximumRadius;
    }

    /** Visits leaf-bearing sections along the cylindrical front at all arena heights. */
    private void clearFrontLeaves(double previousRadius) {
        var volume = boss.getAttackVolume();
        if (volume == null || !(boss.level instanceof ServerLevel level)
                || !ForgeEventFactory.getMobGriefingEvent(level, boss)) {
            return;
        }
        int minY = volume.minimumY();
        int maxY = Mth.floor(volume.maximumY());
        double innerSquared = previousRadius * previousRadius;
        double outerSquared = radius * radius;
        var leaves = new ArrayList<BlockPos>();
        var pos = new BlockPos.MutableBlockPos();
        for (int chunkX = Mth.floor(origin.x - radius) >> 4; chunkX <= Mth.floor(origin.x + radius) >> 4; chunkX++) {
            for (int chunkZ = Mth.floor(origin.z - radius) >> 4; chunkZ <= Mth.floor(origin.z + radius) >> 4; chunkZ++) {
                int startX = chunkX << 4;
                int startZ = chunkZ << 4;
                if (!intersectsFront(startX + 8, startZ + 8, 8, innerSquared, outerSquared)) {
                    continue;
                }
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (int sectionY = minY >> 4; sectionY <= maxY >> 4; sectionY++) {
                    var section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
                    // Palette checks skip stone, air and water sections before visiting individual blocks.
                    if (!section.maybeHas(state -> state.is(BlockTags.LEAVES))) {
                        continue;
                    }
                    int fromY = Math.max(minY, sectionY << 4);
                    int toY = Math.min(maxY, (sectionY << 4) + 15);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            if (!intersectsFront(startX + x + 0.5, startZ + z + 0.5, 0.5,
                                    innerSquared, outerSquared)
                                    || !volume.contains(pos.set(startX + x, minY, startZ + z))) {
                                continue;
                            }
                            for (int y = fromY; y <= toY; y++) {
                                if (section.getBlockState(x, y & 15, z).is(BlockTags.LEAVES)) {
                                    leaves.add(new BlockPos(startX + x, y, startZ + z));
                                }
                            }
                        }
                    }
                }
            }
        }
        WaterAttackBlocks.clearReachableLeaves(boss, level, origin, leaves, true);
    }

    /** Tests the nearest and farthest corners of a block column or chunk against the expanding front. */
    private boolean intersectsFront(double x, double z, double halfWidth, double innerSquared, double outerSquared) {
        double dx = Math.abs(x - origin.x);
        double dz = Math.abs(z - origin.z);
        double nearX = Math.max(0, dx - halfWidth);
        double nearZ = Math.max(0, dz - halfWidth);
        double farX = dx + halfWidth;
        double farZ = dz + halfWidth;
        return nearX * nearX + nearZ * nearZ <= outerSquared && farX * farX + farZ * farZ >= innerSquared;
    }

    /**
     * @return the inner edge of the configurable safe ring, measured from the arena
     *         origin
     */
    static double dangerRadius(WaterSheeperEntity boss) {
        return Math.max(0, boss.getArenaRadius() - boss.getMaelstromSafeOuterWidth());
    }

    /**
     * Uses live horizontal position, independently of swimming height or impact
     * offset.
     */
    static boolean isSafeArea(WaterSheeperEntity boss, Vec3 point) {
        if (WaterSheeperConfig.WATER_MAELSTROM_SAFE_OUTER_WIDTH.get() <= 0) {
            return false;
        }
        double dx = point.x - boss.getArenaCenter().getX() - 0.5D;
        double dz = point.z - boss.getArenaCenter().getZ() - 0.5D;
        double limit = dangerRadius(boss);
        return dx * dx + dz * dz >= limit * limit;
    }

    /**
     * One packet per viewer; the client creates only the visible arc at nearby
     * heights.
     */
    private void showFront(List<ServerPlayer> players) {
        double bottom = boss.getArenaWaterMinimumY();
        double height = boss.getArenaWaterSurfaceY() - bottom;
        var arena = new MaelstromWaveOptions(boss.getArenaCenter().getX() + 0.5D,
                boss.getArenaCenter().getZ() + 0.5D, boss.getArenaRadius());
        for (ServerPlayer player : players) {
            ((ServerLevel) boss.level).sendParticles(player, arena, true,
                    origin.x, bottom, origin.z, 0, radius, height,
                    WaterSheeperConfig.WATER_MAELSTROM_WAVE_SPEED.get() / 20.0D, 1);
        }
    }

    /**
     * Places quiet, spaced pulses on the nearest part of the expanding wave
     * cylinder.
     */
    private void playFrontSound(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            Vec3 listener = player.getEyePosition();
            Vec3 radial = new Vec3(listener.x - origin.x, 0, listener.z - origin.z);
            if (radial.lengthSqr() < 1.0E-8D) {
                radial = new Vec3(1, 0, 0);
            }
            Vec3 point = origin.add(radial.normalize().scale(radius));
            double height = Mth.clamp(listener.y, boss.getArenaWaterMinimumY(), boss.getArenaWaterSurfaceY());
            Vec3 source = new Vec3(point.x, height, point.z);
            if (boss.containsAttackPoint(source)) {
                WaterAttackSounds.nearby(player, source, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.65F, 0.65F);
            }
        }
    }

    /** Cancels damage and transport for boss death or a full encounter reset. */
    void clear() {
        pending.clear();
        damageDelays.clear();
        transports.clear();
        radius = 0;
        maximumRadius = 0;
        visualTicks = 0;
    }

    /**
     * @return wave progress, pending contacts, damage intervals and continuing
     *         transports
     */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 2);
        tag.putDouble("X", origin.x);
        tag.putDouble("Y", origin.y);
        tag.putDouble("Z", origin.z);
        tag.putDouble("Radius", radius);
        tag.putDouble("MaximumRadius", maximumRadius);
        ListTag waiting = new ListTag();
        pending.forEach((id, distance) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putDouble("Distance", distance);
            waiting.add(entry);
        });
        tag.put("Pending", waiting);
        ListTag active = new ListTag();
        damageDelays.forEach((id, delay) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Delay", delay);
            active.add(entry);
        });
        tag.put("Exposures", active);
        tag.put("Transports", transports.save());
        return tag;
    }

    /**
     * @param tag persisted wave; old independent damage durations are no longer
     *            resumed
     */
    void load(CompoundTag tag) {
        clear();
        Vec3 point = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        double savedRadius = tag.getDouble("Radius");
        double savedMaximum = tag.getDouble("MaximumRadius");
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || !Double.isFinite(savedRadius) || !Double.isFinite(savedMaximum)) {
            return;
        }
        origin = point;
        maximumRadius = Mth.clamp(savedMaximum, 0, 256);
        radius = Mth.clamp(savedRadius, 0, maximumRadius);
        for (Tag value : tag.getList("Pending", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            double distance = entry.getDouble("Distance");
            if (entry.hasUUID("Player") && Double.isFinite(distance)) {
                pending.put(entry.getUUID("Player"), Mth.clamp(distance, 0, 512));
            }
        }
        for (Tag value : tag.getList("Exposures", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player") && (tag.getInt("Version") >= 2 || entry.getInt("Ticks") > 0)) {
                damageDelays.put(entry.getUUID("Player"), Mth.clamp(entry.getInt("Delay"), 0, DAMAGE_INTERVAL_TICKS));
            }
        }
        if (radius >= maximumRadius) {
            pending.clear();
            damageDelays.clear();
        }
        transports.load(tag.getList("Transports", Tag.TAG_COMPOUND));
    }
}
