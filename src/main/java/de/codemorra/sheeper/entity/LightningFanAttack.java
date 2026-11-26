package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.network.LightningFanPacket;
import de.codemorra.sheeper.network.ModNetwork;
import de.codemorra.sheeper.world.LightningStormData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Holds the Sheeper in lightning form while ground impacts track every participant. */
final class LightningFanAttack {
    private static final double HIT_RADIUS = 0.65;
    private static final double HIT_HEIGHT = 0.8;
    private static final int VISUAL_UPDATE_TICKS = 2;
    private final LightningSheeperEntity boss;
    private final LightningTeleportMovement teleports;
    private final Map<UUID, Long> nextHitTick = new HashMap<>();
    private final Map<UUID, Vec3> focuses = new HashMap<>();
    @Nullable
    private Hover hover;
    private long startedAt;
    private int durationTicks;
    private int liftTicks;
    private int preparationTicks;
    private int trackingTicks;
    private boolean lifted;
    private double lead;
    private double speed;
    private float damage;

    private record Hover(Vec3 ground, Vec3 position) {
    }

    LightningFanAttack(LightningSheeperEntity boss, LightningTeleportMovement teleports) {
        this.boss = boss;
        this.teleports = teleports;
    }

    /** Finds safe space over the nearby floor before starting the single timed attack. */
    boolean start(ServerLevel level) {
        Vec3 ground = teleports.findAttackGround(level);
        if (ground == null) {
            return false;
        }
        Vec3 position = ground.add(0, 0.65, 0);
        if (!teleports.isSafeFanHoverPosition(position)) {
            position = ground.add(0, 0.25, 0);
            if (!teleports.isSafeFanHoverPosition(position)) {
                return false;
            }
        }
        int minimum = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_FAN_DURATION_MIN.get());
        int maximum = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_FAN_DURATION_MAX.get());
        durationTicks = minimum + boss.getRandom().nextInt(Math.max(0, maximum - minimum) + 1);
        preparationTicks = Math.min(durationTicks / 2,
                SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_FAN_PREPARATION.get()));
        liftTicks = Math.min(4, preparationTicks - 1);
        lead = LightningSheeperConfig.LIGHTNING_FAN_LEAD.get();
        speed = LightningSheeperConfig.LIGHTNING_FAN_SPEED.get() / 20;
        trackingTicks = Math.max(1,
                SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_FAN_TRACKING_RESPONSE.get()));
        damage = LightningSheeperConfig.LIGHTNING_FAN_DAMAGE.get().floatValue();

        hover = new Hover(ground, position);
        startedAt = level.getGameTime();
        boss.setTarget(null);
        boss.startTravelArc(position, liftTicks);
        float volume = LightningSheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
        LightningAttackSounds.play(boss, boss.getEyePosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, volume * 0.8F, 1.35F);
        return true;
    }

    boolean isActive() {
        return hover != null;
    }

    /** Updates all current participants, giving each a separate lagging aim point. */
    void tick(ServerLevel level, List<ServerPlayer> players) {
        Hover site = hover;
        if (site == null) {
            return;
        }
        long elapsed = level.getGameTime() - startedAt;
        if (elapsed >= durationTicks || players.isEmpty() || !teleports.isSafeFanHoverPosition(site.position())) {
            clear();
            return;
        }
        if (!lifted && elapsed >= liftTicks) {
            teleports.teleportTo(site.position());
            boss.startFanArcCharge(preparationTicks - liftTicks, durationTicks - preparationTicks);
            lifted = true;
        }
        if (elapsed < preparationTicks) {
            return;
        }

        Vec3 origin = boss.getBoundingBox().getCenter();
        if (elapsed == preparationTicks) {
            playFiringSound(level, origin);
        }
        focuses.keySet().retainAll(players.stream().map(ServerPlayer::getUUID).toList());
        boolean updateVisual = (elapsed - preparationTicks) % VISUAL_UPDATE_TICKS == 0;
        var ends = new HashMap<Integer, List<Vec3>>();
        for (ServerPlayer player : players) {
            Vec3 focus = track(player.position(), focuses.get(player.getUUID()), origin);
            focuses.put(player.getUUID(), focus);
            List<Vec3> arcs = fireAt(level, players, origin, focus);
            if (updateVisual) {
                ends.put(player.getId(), arcs);
            }
        }
        if (updateVisual) {
            sendVisual(level, origin, ends);
        }
    }

    private void playFiringSound(ServerLevel level, Vec3 origin) {
        float volume = LightningSheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
        if (volume > 0) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.HOSTILE, volume * 0.5F, 1.4F);
        }
    }

    /** Starts short of the target and closes only part of the remaining gap each tick. */
    private Vec3 track(Vec3 feet, @Nullable Vec3 focus, Vec3 origin) {
        if (focus == null) {
            Vec3 forward = horizontal(feet.subtract(origin));
            double gap = Math.min(lead, forward.horizontalDistance() * 0.8);
            return feet.subtract(forward.normalize().scale(gap));
        }
        Vec3 step = feet.subtract(focus).scale(1.0 / trackingTicks);
        double distance = step.horizontalDistance();
        Vec3 horizontalStep = distance <= speed ? step : step.scale(speed / distance);
        return focus.add(horizontalStep.x, Mth.clamp(step.y, -speed, speed), horizontalStep.z);
    }

    /** Traces the central arc and the two staggered side arcs for one player. */
    private List<Vec3> fireAt(ServerLevel level, List<ServerPlayer> players, Vec3 origin, Vec3 focus) {
        double ground = findGround(level, focus);
        Vec3 offset = horizontal(focus.subtract(origin));
        Vec3 forward = offset.normalize();
        if (forward.lengthSqr() == 0) {
            double yaw = Math.toRadians(boss.getYRot());
            forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        Vec3 sideways = new Vec3(-forward.z, 0, forward.x);
        Vec3 setback = forward.scale(Math.min(1, offset.horizontalDistance()));
        double referenceY = Double.isFinite(ground) ? ground : focus.y;
        var ends = new ArrayList<Vec3>(3);
        for (int lane = -1; lane <= 1; lane++) {
            Vec3 point = lane == 0 ? focus : new Vec3(focus.x, referenceY, focus.z)
                    .subtract(setback).add(sideways.scale(lane));
            ends.add(traceArc(level, players, origin, point, lane == 0 ? ground : findGround(level, point)));
        }
        return ends;
    }

    /** Chooses a nearby surface so cliffs and cave ceilings do not pull an impact far above the target. */
    private double findGround(ServerLevel level, Vec3 point) {
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double radius = boss.getCombatRadius();
        if (horizontal(point.subtract(center)).lengthSqr() > radius * radius) {
            return Double.NaN;
        }
        int minY = Math.max(boss.getArenaMinimumY(), Mth.floor(point.y) - 3);
        int maxY = Math.min(boss.getArenaMaximumY() - 1, Mth.floor(point.y) + 2);
        return minY <= maxY ? LightningRingSurface.findForStrike(level, point.x, point.z, point.y, minY, maxY)
                : Double.NaN;
    }

    /** Clips the visible arc to cover and applies damage only when it actually reaches the ground impact. */
    private Vec3 traceArc(ServerLevel level, List<ServerPlayer> players, Vec3 origin, Vec3 point, double floor) {
        if (!Double.isFinite(floor)) {
            return origin;
        }
        var impact = new Vec3(point.x, floor, point.z);
        Vec3 raised = impact.add(0, 0.1, 0);
        if (!LightningAreaDamage.chunksLoaded(level, origin, raised)) {
            return origin;
        }
        LightningStormData storm = LightningStormData.get(level);
        Vec3 hit = storm.clipLightning(origin, raised);
        var obstruction = level.clip(new ClipContext(origin, hit, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, boss));
        if (obstruction.getType() != HitResult.Type.MISS) {
            storm.igniteLeaves(level, boss.getUUID(), obstruction.getBlockPos());
            return obstruction.getLocation();
        }
        if (!hit.equals(raised)) {
            return hit;
        }
        var support = new BlockPos(impact.x, Math.nextDown(impact.y), impact.z);
        storm.igniteLeaves(level, boss.getUUID(), support);
        long now = level.getGameTime();
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            if (now >= nextHitTick.getOrDefault(id, 0L)
                    && LightningAreaDamage.hurtPlayer(boss, player, impact, HIT_RADIUS, HIT_HEIGHT, damage)) {
                nextHitTick.put(id, now + 20);
            }
        }
        return raised;
    }

    /** Shares one snapshot with every observer who can be near any of the arena's impacts. */
    private void sendVisual(ServerLevel level, Vec3 origin, Map<Integer, List<Vec3>> ends) {
        var visual = new LightningFanPacket(level.dimension().location(), boss.getId(), origin, ends);
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double range = boss.getCombatRadius() + 64;
        for (ServerPlayer observer : level.players()) {
            if (horizontal(observer.position().subtract(center)).lengthSqr() <= range * range) {
                ModNetwork.sendLightningFan(observer, visual);
            }
        }
    }

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0, vector.z);
    }

    /** Rechecks the floor before restoring the body and clears any remaining visible arcs. */
    void clear() {
        Hover site = hover;
        if (site == null) {
            return;
        }
        hover = null;
        if (boss.level instanceof ServerLevel level) {
            sendVisual(level, boss.getBoundingBox().getCenter(), Map.of());
            if (lifted && boss.isAlive() && !teleports.landPreferredAttackTravel(site.ground())) {
                Vec3 fallback = teleports.findAttackGround(level);
                if (fallback != null) {
                    teleports.landPreferredAttackTravel(fallback);
                }
            }
        }
        boss.clearTravelArc();
        lifted = false;
        focuses.clear();
        nextHitTick.clear();
    }
}
