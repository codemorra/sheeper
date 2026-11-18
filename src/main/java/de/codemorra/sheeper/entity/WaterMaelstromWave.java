package de.codemorra.sheeper.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/**
 * Expanding pressure wave with live contact checks, a safe outer ring and
 * wave-limited damage.
 */
final class WaterMaelstromWave {
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
        maximumRadius = boss.getCombatRadius() + Math.sqrt(dx * dx + dz * dz);
        for (ServerPlayer player : boss.getCombatParticipants()) {
            pending.put(player.getUUID(), player.position().subtract(origin).horizontalDistance());
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
        players.forEach(player -> current.put(player.getUUID(), player));
        if (radius < maximumRadius) {
            double previousRadius = radius;
            radius = Math.min(maximumRadius, radius + WaterSheeperConfig.WATER_MAELSTROM_WAVE_SPEED.get() / 20.0D);
            if (visualTicks++ % 2 == 0 || radius == maximumRadius) {
                showFront(players);
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
            int interval = damageInterval();
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
        transports.tick(players.stream().filter(ServerPlayer::isAlive).toList());
    }

    private int damageInterval() {
        return SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_MAELSTROM_DAMAGE_INTERVAL.get());
    }

    /**
     * @return the inner edge of the configurable safe ring, measured from the arena
     *         origin
     */
    static double dangerRadius(WaterSheeperEntity boss) {
        return Math.max(0, boss.getCombatRadius() - WaterSheeperConfig.WATER_MAELSTROM_SAFE_OUTER_WIDTH.get());
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
        for (ServerPlayer player : players) {
            ((ServerLevel) boss.level).sendParticles(player, ModParticles.MAELSTROM_WAVE.get(), true,
                    origin.x, bottom, origin.z, 0, radius, height,
                    WaterSheeperConfig.WATER_MAELSTROM_WAVE_SPEED.get() / 20.0D, 1);
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
                damageDelays.put(entry.getUUID("Player"), Mth.clamp(entry.getInt("Delay"), 0, damageInterval()));
            }
        }
        if (radius >= maximumRadius) {
            pending.clear();
            damageDelays.clear();
        }
        transports.load(tag.getList("Transports", Tag.TAG_COMPOUND));
    }
}
