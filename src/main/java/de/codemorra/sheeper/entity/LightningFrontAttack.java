package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Staggered, fixed-direction lanes that announce jittered impacts from the boss
 * to the arena edge.
 */
final class LightningFrontAttack {
    private static final class Warning {
        private final Vec3 pos;
        private int remaining;

        Warning(Vec3 pos, int remaining) {
            this.pos = pos;
            this.remaining = remaining;
        }

        CompoundTag save() {
            CompoundTag tag = positionTag(pos);
            tag.putInt("Remaining", remaining);
            return tag;
        }
    }

    private static final class Lane {
        private final double angle;
        private final double length;
        private final List<Warning> warnings = new ArrayList<>();
        private final Set<UUID> hitPlayers = new HashSet<>();
        private double progress;
        private int delay;

        Lane(double angle, double length) {
            this.angle = angle;
            this.length = length;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("Angle", angle);
            tag.putDouble("Length", length);
            tag.putDouble("Progress", progress);
            tag.putInt("Delay", delay);
            ListTag pending = new ListTag();
            warnings.forEach(warning -> pending.add(warning.save()));
            tag.put("Warnings", pending);
            ListTag hits = new ListTag();
            for (UUID id : hitPlayers) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Player", id);
                hits.add(entry);
            }
            tag.put("HitPlayers", hits);
            return tag;
        }
    }

    private final LightningSheeperEntity boss;
    private final List<Lane> lanes = new ArrayList<>();
    private Vec3 origin = Vec3.ZERO;
    private int remaining;
    private int spawnDelay;
    private int spawnMin;
    private int spawnMax;
    private int maxActive;
    private int stepInterval;
    private int warningDuration;
    private double stepDistance;
    private double sideOffset;
    private double directionGap;
    private double hitRadius;
    private double hitHeight;
    private float damage;
    private double lookAngle;

    LightningFrontAttack(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    void start() {
        clear();
        origin = boss.position();
        int min = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_FRONT_DURATION_MIN.get());
        int max = Math.max(min, SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_FRONT_DURATION_MAX.get()));
        remaining = min + boss.getRandom().nextInt(max - min + 1);
        spawnMin = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_FRONT_SPAWN_INTERVAL_MIN.get());
        spawnMax = Math.max(spawnMin,
                SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_FRONT_SPAWN_INTERVAL_MAX.get()));
        maxActive = SheeperConfig.LIGHTNING_FRONT_MAX_ACTIVE.get();
        stepInterval = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_FRONT_STEP_INTERVAL.get());
        warningDuration = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_FRONT_WARNING.get());
        stepDistance = SheeperConfig.LIGHTNING_FRONT_STEP_DISTANCE.get();
        sideOffset = SheeperConfig.LIGHTNING_FRONT_SIDE_OFFSET.get();
        directionGap = Math.toRadians(SheeperConfig.LIGHTNING_FRONT_DIRECTION_GAP.get());
        hitRadius = SheeperConfig.LIGHTNING_FRONT_RADIUS.get();
        hitHeight = SheeperConfig.LIGHTNING_FRONT_HEIGHT.get();
        damage = SheeperConfig.LIGHTNING_FRONT_DAMAGE.get().floatValue();
        lookAngle = Math.toRadians(boss.getYRot() + 90.0D);
    }

    boolean isFinished() {
        return remaining <= 0;
    }

    Vec3 getLookPoint() {
        return origin.add(Math.cos(lookAngle) * 8.0D, boss.getEyeHeight(), Math.sin(lookAngle) * 8.0D);
    }

    void tick(List<ServerPlayer> players) {
        if (remaining <= 0) {
            return;
        }
        ServerLevel level = (ServerLevel) boss.level;
        var active = lanes.iterator();
        while (active.hasNext()) {
            Lane lane = active.next();
            tickLane(level, lane, players);
            if (lane.progress >= lane.length && lane.warnings.isEmpty()) {
                active.remove();
            }
        }
        if (--spawnDelay <= 0) {
            if (lanes.size() < maxActive) {
                Lane lane = findLane();
                if (lane != null) {
                    lanes.add(lane);
                    lookAngle = lane.angle;
                    showDirection(level, lane);
                    tickLane(level, lane, players);
                }
            }
            spawnDelay = spawnMin + boss.getRandom().nextInt(spawnMax - spawnMin + 1);
        }
        remaining--;
    }

    @Nullable
    private Lane findLane() {
        Vec3 relative = origin.subtract(Vec3.atBottomCenterOf(boss.getArenaCenter()));
        double radius = boss.getCombatRadius();
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2.0D;
            boolean tooClose = lanes.stream().anyMatch(lane -> Math.abs(Math.atan2(
                    Math.sin(angle - lane.angle), Math.cos(angle - lane.angle))) < directionGap);
            if (tooClose) {
                continue;
            }
            // Positive ray/circle intersection: the exit depends on the actual boss
            // position.
            double projection = relative.x * Math.cos(angle) + relative.z * Math.sin(angle);
            double discriminant = projection * projection + radius * radius - relative.horizontalDistanceSqr();
            if (discriminant < 0) {
                continue;
            }
            double length = -projection + Math.sqrt(discriminant) - 1.0E-6D;
            if (!Double.isFinite(length) || length <= 0) {
                continue;
            }
            double steps = Math.ceil(length / stepDistance);
            double travelTicks = (steps - 1) * stepInterval + warningDuration;
            if (remaining > travelTicks) {
                return new Lane(angle, length);
            }
        }
        return null;
    }

    private void tickLane(ServerLevel level, Lane lane, List<ServerPlayer> players) {
        var pending = lane.warnings.iterator();
        while (pending.hasNext()) {
            Warning warning = pending.next();
            if (!isValidPoint(level, warning.pos)) {
                pending.remove();
            } else if (--warning.remaining <= 0) {
                LightningStrikeEffect.spawn(boss, warning.pos);
                for (ServerPlayer player : players) {
                    if (!lane.hitPlayers.contains(player.getUUID())
                            && LightningAreaDamage.hurtPlayer(boss, player, warning.pos, hitRadius, hitHeight,
                                    damage)) {
                        lane.hitPlayers.add(player.getUUID());
                    }
                }
                pending.remove();
            } else if (warning.remaining % 4 == 0) {
                LightningStrikeEffect.showWarning(level, warning.pos, hitRadius);
            }
        }
        if (lane.progress < lane.length && --lane.delay <= 0) {
            lane.progress = Math.min(lane.length, lane.progress + stepDistance);
            double dx = Math.cos(lane.angle);
            double dz = Math.sin(lane.angle);
            double offset = (boss.getRandom().nextDouble() * 2.0D - 1.0D) * sideOffset;
            double x = origin.x + dx * lane.progress - dz * offset;
            double z = origin.z + dz * lane.progress + dx * offset;
            // At the edge a sideways jump can leave the circle; fall back to its
            // centerline.
            if (!isWithinArena(x, z)) {
                x = origin.x + dx * lane.progress;
                z = origin.z + dz * lane.progress;
            }
            Vec3 pos = surface(level, x, z);
            if (pos != null) {
                lane.warnings.add(new Warning(pos, warningDuration));
                LightningStrikeEffect.showWarning(level, pos, hitRadius);
            }
            lane.delay = stepInterval;
        }
    }

    private boolean isWithinArena(double x, double z) {
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double dx = x - center.x;
        double dz = z - center.z;
        return dx * dx + dz * dz <= boss.getCombatRadius() * boss.getCombatRadius();
    }

    @Nullable
    private Vec3 surface(ServerLevel level, double x, double z) {
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        if (!isWithinArena(x, z) || !level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
            return null;
        }
        Double y = boss.findArenaSurface(level, blockX, blockZ, Mth.floor(origin.y));
        if (y == null) {
            return null;
        }
        Vec3 pos = new Vec3(x, y, z);
        return isValidPoint(level, pos) ? pos : null;
    }

    private boolean isValidPoint(ServerLevel level, Vec3 pos) {
        return isWithinArena(pos.x, pos.z)
                && Math.abs(pos.y - boss.getArenaCenter().getY()) <= SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE.get()
                && pos.y >= level.getMinBuildHeight() && pos.y < level.getMaxBuildHeight()
                && level.getChunkSource().hasChunk(Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4);
    }

    private void showDirection(ServerLevel level, Lane lane) {
        for (double distance = 0; distance <= Math.min(9.0D, lane.length); distance += 1.0D) {
            Vec3 pos = surface(level, origin.x + Math.cos(lane.angle) * distance,
                    origin.z + Math.sin(lane.angle) * distance);
            if (pos != null) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.15D, pos.z,
                        3, 0.05D, 0.05D, 0.05D, 0.01D);
            }
        }
    }

    void clear() {
        lanes.clear();
        remaining = 0;
        spawnDelay = 0;
    }

    CompoundTag save() {
        CompoundTag tag = positionTag(origin);
        tag.putInt("Remaining", remaining);
        tag.putInt("SpawnDelay", spawnDelay);
        tag.putInt("SpawnMin", spawnMin);
        tag.putInt("SpawnMax", spawnMax);
        tag.putInt("MaxActive", maxActive);
        tag.putInt("StepInterval", stepInterval);
        tag.putInt("WarningDuration", warningDuration);
        tag.putDouble("StepDistance", stepDistance);
        tag.putDouble("SideOffset", sideOffset);
        tag.putDouble("DirectionGap", directionGap);
        tag.putDouble("HitRadius", hitRadius);
        tag.putDouble("HitHeight", hitHeight);
        tag.putFloat("Damage", damage);
        tag.putDouble("LookAngle", lookAngle);
        ListTag active = new ListTag();
        lanes.forEach(lane -> active.add(lane.save()));
        tag.put("Lanes", active);
        return tag;
    }

    private static CompoundTag positionTag(Vec3 pos) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", pos.x);
        tag.putDouble("Y", pos.y);
        tag.putDouble("Z", pos.z);
        return tag;
    }

    @Nullable
    private static Vec3 readPosition(CompoundTag tag) {
        Vec3 pos = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z) ? pos : null;
    }

    private static double bounded(CompoundTag tag, String key, double fallback, double min, double max) {
        double value = tag.getDouble(key);
        return Double.isFinite(value) ? Mth.clamp(value, min, max) : fallback;
    }

    void load(CompoundTag tag) {
        clear();
        Vec3 savedOrigin = readPosition(tag);
        origin = savedOrigin != null ? savedOrigin : boss.position();
        remaining = Mth.clamp(tag.getInt("Remaining"), 0, 12000);
        spawnDelay = Mth.clamp(tag.getInt("SpawnDelay"), 0, 1200);
        spawnMin = Mth.clamp(tag.getInt("SpawnMin"), 1, 1200);
        spawnMax = Mth.clamp(tag.getInt("SpawnMax"), spawnMin, 1200);
        maxActive = Mth.clamp(tag.getInt("MaxActive"), 1, 16);
        stepInterval = Mth.clamp(tag.getInt("StepInterval"), 1, 200);
        warningDuration = Mth.clamp(tag.getInt("WarningDuration"), 1, 1200);
        stepDistance = bounded(tag, "StepDistance", 3.0D, 0.5D, 16.0D);
        sideOffset = bounded(tag, "SideOffset", 2.5D, 0, 16.0D);
        directionGap = bounded(tag, "DirectionGap", Math.PI / 4, 0, Math.PI);
        hitRadius = bounded(tag, "HitRadius", 1.0D, 0.1D, 16.0D);
        hitHeight = bounded(tag, "HitHeight", 3.0D, 0.1D, 32.0D);
        damage = (float) bounded(tag, "Damage", 2.0D, 0, 1024.0D);
        lookAngle = bounded(tag, "LookAngle", 0.0D, -Math.PI * 2, Math.PI * 2);
        for (Tag value : tag.getList("Lanes", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            double angle = entry.getDouble("Angle");
            double length = entry.getDouble("Length");
            if (lanes.size() >= maxActive || !Double.isFinite(angle) || !Double.isFinite(length) || length <= 0) {
                continue;
            }
            Lane lane = new Lane(angle, Math.min(length, 512.0D));
            lane.progress = bounded(entry, "Progress", 0, 0, lane.length);
            lane.delay = Mth.clamp(entry.getInt("Delay"), 0, 200);
            for (Tag pending : entry.getList("Warnings", Tag.TAG_COMPOUND)) {
                CompoundTag warning = (CompoundTag) pending;
                Vec3 pos = readPosition(warning);
                if (pos != null) {
                    lane.warnings.add(new Warning(pos, Mth.clamp(warning.getInt("Remaining"), 1, 1200)));
                }
            }
            for (Tag hit : entry.getList("HitPlayers", Tag.TAG_COMPOUND)) {
                CompoundTag player = (CompoundTag) hit;
                if (player.hasUUID("Player")) {
                    lane.hitPlayers.add(player.getUUID("Player"));
                }
            }
            lanes.add(lane);
        }
    }
}
