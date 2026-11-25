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
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Fires a series of three-arc fans, each chasing a player along the ground. */
final class LightningFanAttack {
    private static final double HIT_RADIUS = 0.65;
    private static final double HIT_HEIGHT = 0.8;
    private static final int VISUAL_UPDATE_TICKS = 2;
    private final LightningSheeperEntity boss;
    private final Map<UUID, Long> nextHitTick = new HashMap<>();
    @Nullable
    private ServerPlayer target;
    @Nullable
    private Vec3 focus;
    private int shotsRemaining;
    private int remainingTicks;
    private int preparationDuration;
    private int preparationTicks;
    private int pauseTicks;
    private int shotPause;
    private int minimumDuration;
    private int maximumDuration;
    private int visualTicks;
    private int trackingTicks;
    private double lead;
    private double speed;
    private float damage;

    LightningFanAttack(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    /** Samples the shot count and captures settings for the full series. */
    void start() {
        clear();
        int minimum = LightningSheeperConfig.LIGHTNING_FAN_SHOT_COUNT_MIN.get();
        int maximum = LightningSheeperConfig.LIGHTNING_FAN_SHOT_COUNT_MAX.get();
        shotsRemaining = minimum + boss.getRandom().nextInt(Math.max(0, maximum - minimum) + 1);
        preparationDuration = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_FAN_PREPARATION.get());
        preparationTicks = preparationDuration;
        shotPause = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_FAN_SHOT_PAUSE.get());
        minimumDuration = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_FAN_DURATION_MIN.get());
        maximumDuration = Math.max(minimumDuration,
                SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_FAN_DURATION_MAX.get()));
        lead = LightningSheeperConfig.LIGHTNING_FAN_LEAD.get();
        speed = LightningSheeperConfig.LIGHTNING_FAN_SPEED.get() / 20;
        trackingTicks = Math.max(1,
                SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_FAN_TRACKING_RESPONSE.get()));
        damage = LightningSheeperConfig.LIGHTNING_FAN_DAMAGE.get().floatValue();
    }

    boolean isActive() {
        return shotsRemaining > 0;
    }

    /** Starts each fan after its pause and advances the impact points while it fires. */
    void tick(ServerLevel level, List<ServerPlayer> players) {
        if (pauseTicks > 0) {
            pauseTicks--;
            return;
        }
        if (remainingTicks == 0) {
            target = chooseNextTarget(players);
            remainingTicks = minimumDuration + boss.getRandom().nextInt(maximumDuration - minimumDuration + 1);
        } else if (!players.contains(target)) {
            // Keep the current impact position when an active fan needs a replacement.
            target = chooseNextTarget(players);
        }
        if (preparationTicks > 0) {
            tickPreparation(level, target);
            return;
        }
        Vec3 feet = target.position();
        Vec3 origin = boss.getBeamOrigin();
        boolean firstTick = focus == null;
        if (firstTick) {
            Vec3 forward = horizontal(feet.subtract(origin));
            double gap = Math.min(lead, forward.horizontalDistance() * 0.8);
            focus = feet.subtract(forward.normalize().scale(gap));
            float volume = LightningSheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
            if (volume > 0) {
                level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                        SoundSource.HOSTILE, volume * 0.5F, 1.4F);
            }
        } else {
            // As with the water beam, only close part of the remaining aim gap each tick.
            Vec3 step = feet.subtract(focus).scale(1.0 / trackingTicks);
            double distance = step.horizontalDistance();
            Vec3 horizontalStep = distance <= speed ? step : step.scale(speed / distance);
            focus = focus.add(horizontalStep.x, Mth.clamp(step.y, -speed, speed), horizontalStep.z);
        }

        double ground = findGround(level, focus);
        Vec3 aim = Double.isFinite(ground) ? new Vec3(focus.x, ground, focus.z) : focus;
        lookAt(target, aim);

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
            Vec3 point = lane == 0 ? aim : new Vec3(focus.x, referenceY, focus.z)
                    .subtract(setback).add(sideways.scale(lane));
            ends.add(traceArc(level, players, origin, point, lane == 0 ? ground : findGround(level, point)));
        }
        if (visualTicks-- <= 0) {
            visualTicks = VISUAL_UPDATE_TICKS - 1;
            sendVisual(level, origin, focus, 0, ends);
        }
        if (--remainingTicks == 0) {
            if (--shotsRemaining == 0) {
                clear();
            } else {
                pauseTicks = shotPause;
                focus = null;
                visualTicks = 0;
            }
        }
    }

    /** Telegraphs the series once, keeping the firing time and the gaps between shots separate. */
    private void tickPreparation(ServerLevel level, ServerPlayer player) {
        lookAt(player, player.getEyePosition());
        Vec3 origin = boss.getBeamOrigin();
        if (preparationTicks == preparationDuration) {
            float volume = LightningSheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
            if (volume > 0) {
                level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.RESPAWN_ANCHOR_CHARGE,
                        SoundSource.HOSTILE, volume * 0.8F, 1.35F);
            }
        }
        preparationTicks--;
        if (preparationTicks % VISUAL_UPDATE_TICKS == 0 || preparationTicks == preparationDuration - 1) {
            float charge = 1 - (float) preparationTicks / preparationDuration;
            sendVisual(level, origin, player.position(), charge, List.of());
        }
    }

    private void lookAt(ServerPlayer player, Vec3 aim) {
        boss.setTarget(player);
        boss.lookAt(EntityAnchorArgument.Anchor.EYES, aim);
        boss.setYHeadRot(boss.getYRot());
    }

    /** Excludes the last target whenever another combat participant is available. */
    private ServerPlayer chooseNextTarget(List<ServerPlayer> players) {
        if (target == null || players.size() == 1) {
            return players.get(boss.getRandom().nextInt(players.size()));
        }
        List<ServerPlayer> alternatives = players.stream()
                .filter(player -> !player.getUUID().equals(target.getUUID()))
                .toList();
        return alternatives.get(boss.getRandom().nextInt(alternatives.size()));
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
        var obstruction = level.clip(new ClipContext(origin, raised, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, boss));
        if (obstruction.getType() != HitResult.Type.MISS) {
            return obstruction.getLocation();
        }
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

    /** Sends the effect to observers near the source or the area the boss is aiming at. */
    private void sendVisual(ServerLevel level, Vec3 origin, Vec3 aim, float charge, List<Vec3> ends) {
        var visual = new LightningFanPacket(level.dimension().location(), boss.getId(), origin, charge, ends);
        for (ServerPlayer observer : level.players()) {
            if (observer.distanceToSqr(origin) <= 64 * 64 || observer.distanceToSqr(aim) <= 64 * 64) {
                ModNetwork.sendLightningFan(observer, visual);
            }
        }
    }

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0, vector.z);
    }

    void clear() {
        target = null;
        focus = null;
        shotsRemaining = 0;
        remainingTicks = 0;
        preparationTicks = 0;
        pauseTicks = 0;
        visualTicks = 0;
        nextHitTick.clear();
    }
}
