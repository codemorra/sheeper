package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Fires eight terrain-following lightning rows from each of several teleport positions. */
final class LightningLineAttack {
    private static final double STRIKE_SPACING = 1.6;
    private static final double HIT_RADIUS = 1.15;
    private static final double EDGE_RADIUS = HIT_RADIUS + 1;
    private static final double HIT_HEIGHT = 3;
    private static final int ROW_INTERVAL_TICKS = 10;
    private static final int ROWS_PER_POSITION = 8;
    private static final int FIRST_ROW_DELAY_TICKS = 5;
    private static final int TURN_BEFORE_TELEPORT_TICKS = 10;
    private static final int CHARGE_FADE_TICKS = 20;

    private static final class Line {
        private final Vec3 origin;
        private final Vec3 direction;
        private final double length;
        private final long firedAt;
        private final List<Vec3> impacts = new ArrayList<>();
        private final Set<UUID> hit = new HashSet<>();
        private final Map<UUID, Long> pendingEdgeHits = new HashMap<>();
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
    private final LightningTeleportMovement teleports;
    private final List<Line> active = new ArrayList<>();
    @Nullable
    private UUID lastTarget;
    @Nullable
    private UUID secondLastTarget;
    private int chargeTicks;
    private int edgeHitDelay;
    private int positionsRemaining;
    private int rowsRemaining;
    private long chargeEndsAt;
    private long nextRowAt;
    private long nextPositionAt;
    private long landingAt = -1;
    private boolean destinationPlanned;
    @Nullable
    private Vec3 nextDestination;
    private long fadeStartedAt = -1;
    private double teleportDistance;
    private double blocksPerTick;
    private float damage;

    LightningLineAttack(LightningSheeperEntity boss, LightningTeleportMovement teleports) {
        this.boss = boss;
        this.teleports = teleports;
    }

    /** Charges at the current position before the first jump. */
    void start(ServerLevel level) {
        clear();
        int minimum = LightningSheeperConfig.LIGHTNING_LINE_WAVE_COUNT_MIN.get();
        int maximum = LightningSheeperConfig.LIGHTNING_LINE_WAVE_COUNT_MAX.get();
        positionsRemaining = minimum + boss.getRandom().nextInt(Math.max(0, maximum - minimum) + 1);
        chargeTicks = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_LINE_CHARGE_DURATION.get());
        teleportDistance = LightningSheeperConfig.LIGHTNING_LINE_TELEPORT_DISTANCE.get();
        blocksPerTick = LightningSheeperConfig.LIGHTNING_LINE_SPEED.get() / 20;
        edgeHitDelay = (int) Math.ceil(STRIKE_SPACING / blocksPerTick);
        damage = LightningSheeperConfig.LIGHTNING_LINE_DAMAGE.get().floatValue();
        chargeEndsAt = level.getGameTime() + chargeTicks;
        nextPositionAt = chargeEndsAt;
        float volume = LightningSheeperConfig.LIGHTNING_ATTACK_IMPACT_VOLUME.get().floatValue();
        LightningAttackSounds.play(boss, boss.getEyePosition(), ModSounds.LIGHTNING_FAN_CRACKLE.get(),
                volume * 0.85F, 0.72F);
    }

    boolean isActive() {
        return isFiring() || !active.isEmpty() || fadeStartedAt >= 0;
    }

    /** @return whether the Sheeper is charging or still has another row to launch */
    boolean isFiring() {
        return positionsRemaining > 0 || rowsRemaining > 0;
    }

    void tick(ServerLevel level, List<ServerPlayer> participants) {
        long now = level.getGameTime();
        if (isFiring() && now < chargeEndsAt) {
            boss.setLineCharge(1 - (chargeEndsAt - now) / (float) chargeTicks);
        }
        if (positionsRemaining > 0) {
            advanceTeleport(participants, now);
        }
        if (rowsRemaining > 0 && now >= nextRowAt) {
            fireRow(participants, now);
            nextRowAt += ROW_INTERVAL_TICKS;
            if (--rowsRemaining == 0 && positionsRemaining == 0) {
                fadeStartedAt = now;
            }
        }
        if (fadeStartedAt >= 0) {
            float strength = Math.max(0, 1 - (now - fadeStartedAt) / (float) CHARGE_FADE_TICKS);
            boss.setLineCharge(strength);
            if (strength == 0) {
                fadeStartedAt = -1;
            }
        }
        var targets = new ArrayList<LivingEntity>(participants);
        if (damage > 0 && !active.isEmpty()) {
            targets.addAll(LightningAreaDamage.arenaMobs(boss));
        }
        var lines = active.iterator();
        while (lines.hasNext()) {
            Line line = lines.next();
            tickLine(level, line, now);
            applyRowDamage(line, targets, now);
            if (line.reachedEdge && line.pendingEdgeHits.isEmpty()) {
                lines.remove();
            }
        }
    }

    /** Turns toward the next position even while the current position fires its last rows. */
    private void advanceTeleport(List<ServerPlayer> participants, long now) {
        if (landingAt >= 0) {
            if (now >= landingAt) {
                teleports.landPreferredAttackTravel(nextDestination);
                beginPosition(now);
            }
            return;
        }
        if (now < nextPositionAt - TURN_BEFORE_TELEPORT_TICKS) {
            return;
        }
        if (!destinationPlanned) {
            nextDestination = teleports.findPreferredAttackDestination(participants, teleportDistance);
            destinationPlanned = true;
        }
        if (nextDestination != null) {
            teleports.faceDestination(nextDestination);
        }
        if (now >= nextPositionAt) {
            if (nextDestination != null && teleports.beginPreferredAttackTravel(nextDestination)) {
                boss.setLineCharge(1);
                landingAt = now + LightningSheeperEntity.ARC_TRAVEL_TICKS;
            } else {
                beginPosition(now);
            }
        }
    }

    /** Starts the next rows one quarter second after arrival and resets their target history. */
    private void beginPosition(long now) {
        boss.setLineCharge(1);
        positionsRemaining--;
        rowsRemaining = ROWS_PER_POSITION;
        nextPositionAt = now + ROWS_PER_POSITION * ROW_INTERVAL_TICKS;
        nextRowAt = now + FIRST_ROW_DELAY_TICKS;
        nextDestination = null;
        destinationPlanned = false;
        landingAt = -1;
        lastTarget = null;
        secondLastTarget = null;
    }

    /** Counts untargeted rows too so a player cannot be selected again until the third following row. */
    private Vec3 chooseDirection(Vec3 origin, List<ServerPlayer> participants) {
        var eligible = participants.stream()
                .filter(player -> player.isAlive() && !player.getUUID().equals(lastTarget)
                        && !player.getUUID().equals(secondLastTarget))
                .toList();
        ServerPlayer target = eligible.isEmpty() ? null : eligible.get(boss.getRandom().nextInt(eligible.size()));
        secondLastTarget = lastTarget;
        lastTarget = target == null ? null : target.getUUID();
        if (target != null) {
            Vec3 offset = target.position().subtract(origin);
            Vec3 direction = new Vec3(offset.x, 0, offset.z).normalize();
            if (direction.lengthSqr() > 0) {
                return direction;
            }
        }
        double angle = boss.getRandom().nextDouble() * Math.PI * 2;
        return new Vec3(Math.cos(angle), 0, Math.sin(angle));
    }

    /** Clips each row against the captured arena boundary before it starts travelling. */
    private void fireRow(List<ServerPlayer> participants, long now) {
        Vec3 origin = boss.position();
        Vec3 direction = chooseDirection(origin, participants);
        Vec3 relative = origin.subtract(Vec3.atBottomCenterOf(boss.getArenaCenter()));
        double projection = direction.dot(relative);
        double radius = boss.getCombatRadius();
        double discriminant = projection * projection + radius * radius - relative.horizontalDistanceSqr();
        if (discriminant >= 0) {
            double length = -projection + Math.sqrt(discriminant);
            if (length > 1.0E-6) {
                // Stay just inside the boundary despite floating-point error at the exit.
                active.add(new Line(origin, direction, length - 1.0E-6, now));
            }
        }
    }

    private void tickLine(ServerLevel level, Line line, long now) {
        double advance = (now - line.firedAt) * blocksPerTick;
        while (!line.reachedEdge && line.nextDistance <= advance) {
            Vec3 point = findImpact(level, line);
            if (point != null) {
                line.surfaceY = point.y;
                Vec3 hit = LightningStrikeEffect.spawnWeatherBolt(level, boss.getUUID(), point,
                        boss.getStormCloudY(point), line.points % 4 == 0);
                if (hit.equals(point)) {
                    line.impacts.add(point);
                }
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

    /** Gives the next impact in a row time to register as a direct hit before applying edge damage. */
    private void applyRowDamage(Line line, List<LivingEntity> targets, long now) {
        if (damage > 0 && (!line.impacts.isEmpty() || !line.pendingEdgeHits.isEmpty())) {
            for (LivingEntity target : targets) {
                UUID id = target.getUUID();
                if (!target.isAlive() || line.hit.contains(id)) {
                    continue;
                }
                Vec3 direct = directImpact(target, line.impacts);
                Long edgeDue = line.pendingEdgeHits.get(id);
                if (direct != null || edgeDue != null && now >= edgeDue) {
                    line.pendingEdgeHits.remove(id);
                    if (hurtTarget(target, direct)) {
                        line.hit.add(id);
                    }
                } else if (edgeDue == null && line.impacts.stream().anyMatch(impact ->
                        LightningAreaDamage.contains(boss, target, impact, EDGE_RADIUS, HIT_HEIGHT))) {
                    line.pendingEdgeHits.put(id, now + edgeHitDelay);
                }
            }
            line.pendingEdgeHits.values().removeIf(due -> due <= now);
        }
        line.impacts.clear();
    }

    /** Returns the exact bolt position for a central hit, even if several impacts overlap. */
    @Nullable
    private Vec3 directImpact(LivingEntity target, List<Vec3> impacts) {
        for (Vec3 impact : impacts) {
            if (LightningAreaDamage.contains(boss, target, impact, HIT_RADIUS, HIT_HEIGHT)) {
                return impact;
            }
        }
        return null;
    }

    private boolean hurtTarget(LivingEntity target, @Nullable Vec3 direct) {
        float amount = direct == null ? damage * 0.5F : damage;
        if (target instanceof ServerPlayer player) {
            return LightningAreaDamage.hurtPlayer(boss, player, amount);
        }
        return direct == null ? LightningAreaDamage.hurtMob(boss, target, amount)
                : LightningAreaDamage.strikeMob(target, direct, amount);
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
        if (landingAt >= 0) {
            boss.clearTravelArc();
        }
        active.clear();
        positionsRemaining = 0;
        rowsRemaining = 0;
        nextDestination = null;
        destinationPlanned = false;
        landingAt = -1;
        fadeStartedAt = -1;
        lastTarget = null;
        secondLastTarget = null;
        boss.setLineCharge(0);
    }
}
