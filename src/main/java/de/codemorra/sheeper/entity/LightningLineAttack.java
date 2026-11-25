package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Fires fixed, terrain-following lines of lightning toward successive arena players. */
final class LightningLineAttack {
    private static final double STRIKE_SPACING = 1.6;
    private static final double HIT_RADIUS = 1.15;
    private static final double HIT_HEIGHT = 3;

    private static final class Line {
        private final Vec3 origin;
        private final Vec3 direction;
        private final double length;
        private final long firedAt;
        private final Set<UUID> hit = new HashSet<>();
        private double surfaceY;
        private double nextDistance;
        private int points;
        private boolean reachedEdge;

        private Line(Vec3 origin, Vec3 direction, double length, long firedAt) {
            this.origin = origin;
            this.direction = direction;
            this.length = length;
            this.firedAt = firedAt;
            surfaceY = origin.y;
        }
    }

    private final LightningSheeperEntity boss;
    private final List<Line> active = new ArrayList<>();
    @Nullable
    private ServerPlayer aim;
    @Nullable
    private UUID previousTarget;
    private int shotsRemaining;
    private long nextShotAt;
    private double blocksPerTick;
    private float damage;

    LightningLineAttack(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    /** Starts with a visible aim before the first row travels out from the current boss position. */
    void start(ServerLevel level) {
        clear();
        shotsRemaining = LightningSheeperConfig.LIGHTNING_LINE_SHOTS.get();
        blocksPerTick = LightningSheeperConfig.LIGHTNING_LINE_SPEED.get() / 20;
        damage = LightningSheeperConfig.LIGHTNING_LINE_DAMAGE.get().floatValue();
        nextShotAt = level.getGameTime() + 8;
    }

    boolean isActive() {
        return shotsRemaining > 0 || !active.isEmpty();
    }

    void tick(ServerLevel level, List<ServerPlayer> participants) {
        long now = level.getGameTime();
        if (shotsRemaining > 0) {
            aimAndFire(level, participants, now);
        }
        var lines = active.iterator();
        while (lines.hasNext()) {
            Line line = lines.next();
            tickLine(level, line, participants, now);
            if (line.reachedEdge) {
                lines.remove();
            }
        }
    }

    private void aimAndFire(ServerLevel level, List<ServerPlayer> participants, long now) {
        if (aim == null || !aim.isAlive() || !participants.contains(aim)) {
            aim = chooseTarget(participants);
        }
        if (aim == null) {
            return;
        }
        boss.setTarget(aim);
        boss.lookAt(EntityAnchorArgument.Anchor.EYES, aim.getEyePosition());
        boss.setYHeadRot(boss.getYRot());
        if (now < nextShotAt) {
            if ((nextShotAt - now) % 3 == 0) {
                Vec3 head = boss.getEyePosition();
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, head.x, head.y, head.z, 5, 0.24, 0.18, 0.24, 0.02);
            }
            return;
        }

        fireTowards(aim, now);
        previousTarget = aim.getUUID();
        aim = null;
        shotsRemaining--;
        double interval = participants.size() > 1 ? LightningSheeperConfig.LIGHTNING_LINE_MULTIPLAYER_INTERVAL.get()
                : LightningSheeperConfig.LIGHTNING_LINE_SOLO_INTERVAL.get();
        nextShotAt = now + SheeperConfig.secondsToTicks(interval);
    }

    @Nullable
    private ServerPlayer chooseTarget(List<ServerPlayer> participants) {
        List<ServerPlayer> available = participants.stream()
                .filter(player -> player.isAlive() && !player.getUUID().equals(previousTarget)).toList();
        List<ServerPlayer> choices = available.isEmpty()
                ? participants.stream().filter(ServerPlayer::isAlive).toList() : available;
        return choices.isEmpty() ? null : choices.get(boss.getRandom().nextInt(choices.size()));
    }

    /** Locks the heading at release and clips the ray against the captured arena boundary. */
    private void fireTowards(ServerPlayer target, long now) {
        Vec3 origin = boss.position();
        Vec3 offset = target.position().subtract(origin);
        Vec3 direction = new Vec3(offset.x, 0, offset.z).normalize();
        if (direction.lengthSqr() == 0) {
            double yaw = Math.toRadians(boss.getYRot());
            direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        Vec3 relative = origin.subtract(Vec3.atBottomCenterOf(boss.getArenaCenter()));
        double projection = direction.dot(relative);
        double radius = boss.getCombatRadius();
        double discriminant = projection * projection + radius * radius - relative.horizontalDistanceSqr();
        if (discriminant >= 0) {
            double length = -projection + Math.sqrt(discriminant);
            if (length >= 0) {
                // Stay just inside the boundary despite floating-point error at the exit.
                active.add(new Line(origin, direction, Math.max(0, length - 1.0E-6), now));
            }
        }
    }

    private void tickLine(ServerLevel level, Line line, List<ServerPlayer> participants, long now) {
        double advance = (now - line.firedAt) * blocksPerTick;
        while (!line.reachedEdge && line.nextDistance <= advance) {
            Vec3 point = findImpact(level, line);
            if (point != null) {
                line.surfaceY = point.y;
                strike(level, line, point, participants);
            }
            line.points++;
            line.reachedEdge = line.nextDistance >= line.length;
            line.nextDistance = Math.min(line.length, line.nextDistance + STRIKE_SPACING);
        }
    }

    /** Scatters occasional impacts across the line while anchoring both ends to its center. */
    @Nullable
    private Vec3 findImpact(ServerLevel level, Line line) {
        Vec3 center = line.origin.add(line.direction.scale(line.nextDistance));
        if (line.nextDistance > 0 && line.nextDistance < line.length && boss.getRandom().nextInt(4) == 0) {
            double offset = 0.5 + boss.getRandom().nextDouble() * 0.5;
            if (boss.getRandom().nextBoolean()) {
                offset = -offset;
            }
            double x = center.x - line.direction.z * offset;
            double z = center.z + line.direction.x * offset;
            double y = surface(level, x, z, line.surfaceY);
            if (Double.isFinite(y)) {
                return new Vec3(x, y, z);
            }
        }
        double y = surface(level, center.x, center.z, line.surfaceY);
        return Double.isFinite(y) ? new Vec3(center.x, y, center.z) : null;
    }

    private void strike(ServerLevel level, Line line, Vec3 point, List<ServerPlayer> participants) {
        LightningStrikeEffect.spawnWeatherBolt(level, point, boss.getStormCloudY(point), line.points % 4 == 0);
        for (ServerPlayer player : participants) {
            if (player.isAlive() && !line.hit.contains(player.getUUID())
                    && LightningAreaDamage.hurtPlayer(boss, player, point, HIT_RADIUS, HIT_HEIGHT, damage)) {
                line.hit.add(player.getUUID());
            }
        }
    }

    private double surface(ServerLevel level, double x, double z, double referenceY) {
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double dx = x - center.x;
        double dz = z - center.z;
        if (dx * dx + dz * dz > boss.getCombatRadius() * boss.getCombatRadius()) {
            return Double.NaN;
        }
        return LightningRingSurface.findForStrike(level, x, z, referenceY, boss.getArenaMinimumY(),
                boss.getArenaMaximumY() - 1);
    }

    void clear() {
        active.clear();
        shotsRemaining = 0;
        aim = null;
        previousTarget = null;
    }
}
