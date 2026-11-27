package de.codemorra.sheeper.entity;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Predicts short projectile paths for elemental bosses that can dodge during combat. */
record IncomingProjectile(Vec3 motion, Vec3 impact) {
    static final int PREDICTION_TICKS = 12;
    private static final double SEARCH_RADIUS = 40;

    /** @return an unobstructed shot from an arena participant with enough reaction time, or null */
    @Nullable
    static IncomingProjectile find(ServerLevel level, AABB body, Vec3 bodyMotion, List<ServerPlayer> participants,
            int minimumTicks) {
        AABB hitbox = body.inflate(0.25);
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, hitbox.inflate(SEARCH_RADIUS),
                shot -> shot.getOwner() instanceof ServerPlayer player && participants.contains(player))) {
            Vec3 impact = predictImpact(level, hitbox, Vec3.ZERO, projectile, minimumTicks);
            if (impact == null && bodyMotion.lengthSqr() > 1.0E-6) {
                impact = predictImpact(level, hitbox, bodyMotion, projectile, minimumTicks);
            }
            if (impact != null) {
                return new IncomingProjectile(projectile.getDeltaMovement(), impact);
            }
        }
        return null;
    }

    /** Applies arrow gravity and drag so descending arrows still register as a threat. */
    @Nullable
    private static Vec3 predictImpact(ServerLevel level, AABB body, Vec3 bodyMotion, Projectile projectile,
            int minimumTicks) {
        Vec3 start = projectile.position();
        Vec3 motion = projectile.getDeltaMovement();
        if (motion.lengthSqr() < 0.0225 || body.contains(start)) {
            return null;
        }
        Vec3 point = start;
        double arrowDrag = projectile.isInWater() ? 0.6 : 0.99;
        for (int tick = 1; tick <= PREDICTION_TICKS; tick++) {
            Vec3 next = point.add(motion);
            // Trace in the target's frame of reference to account for a boss swimming into the shot.
            Vec3 relativeStart = point.subtract(bodyMotion.scale(tick - 1));
            Vec3 relativeEnd = next.subtract(bodyMotion.scale(tick));
            Vec3 hit = body.clip(relativeStart, relativeEnd).orElse(null);
            if (hit != null) {
                if (tick < minimumTicks) {
                    return null;
                }
                double fraction = relativeStart.distanceTo(hit) / relativeStart.distanceTo(relativeEnd);
                Vec3 impact = point.lerp(next, fraction);
                return level.clip(new ClipContext(start, impact, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, projectile)).getType() == HitResult.Type.MISS ? impact : null;
            }
            point = next;
            if (projectile instanceof AbstractArrow) {
                motion = motion.scale(arrowDrag).add(0, projectile.isNoGravity() ? 0 : -0.05, 0);
            }
        }
        return null;
    }
}
