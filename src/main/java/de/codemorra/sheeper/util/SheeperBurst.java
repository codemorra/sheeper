package de.codemorra.sheeper.util;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.SheeperEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

/**
 * Creates a non-destructive area burst with independently controlled damage and
 * knockback. The source Sheeper is excluded from its own burst.
 */
public final class SheeperBurst {
    private SheeperBurst() {
    }

    /**
     * Plays the burst on nearby clients and affects exposed entities on the server.
     * Damage uses vanilla explosion attribution and protection; its configured base
     * value does not grow with the radius. Blocks and fluids remain untouched.
     *
     * @param sheeper the source and center of the burst
     */
    public static void explode(SheeperEntity sheeper) {
        if (!(sheeper.getLevel() instanceof ServerLevel level)) {
            return;
        }

        double radius = SheeperConfig.BURST_RADIUS.get();
        float damage = SheeperConfig.BURST_DAMAGE.get().floatValue();
        double knockback = SheeperConfig.BURST_KNOCKBACK.get();
        Vec3 center = sheeper.position().add(0.0D, sheeper.getBbHeight() * 0.5D, 0.0D);
        DamageSource source = DamageSource.explosion(sheeper);

        level.playSound(null, sheeper.getX(), sheeper.getY(), sheeper.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.7F, 1.3F);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);

        for (Entity entity : level.getEntities(sheeper, sheeper.getBoundingBox().inflate(radius))) {
            if (entity.isSpectator() || entity.ignoreExplosion() || entity.isInvulnerableTo(source)
                    || entity instanceof Player player && player.isCreative()) {
                continue;
            }

            Vec3 direction = entity.getBoundingBox().getCenter().subtract(center);
            double distance = direction.length();
            if (distance > radius) {
                continue;
            }

            // Use vanilla's exposure sampling so solid walls shield nearby entities.
            double exposure = Explosion.getSeenPercent(center, entity);
            if (exposure <= 0.0D) {
                continue;
            }

            if (damage > 0.0F) {
                entity.hurt(source, damage * (float) exposure);
            }

            double impulse = knockback * (1.0D - distance / radius) * exposure;
            if (impulse <= 0.0D || entity.isRemoved()) {
                continue;
            }

            // Choose a stable horizontal direction when both centers coincide.
            double directionX = direction.x;
            double directionZ = direction.z;
            if (directionX * directionX + directionZ * directionZ < 1.0E-8D) {
                directionX = 1.0D;
            }

            if (entity instanceof LivingEntity living) {
                living.knockback(impulse, -directionX, -directionZ);
            } else {
                Vec3 push = new Vec3(directionX, 0.0D, directionZ).normalize().scale(impulse);
                entity.push(push.x, 0.2D, push.z);
            }

            // Player movement is client-controlled and needs velocity synchronization.
            entity.hurtMarked = true;
        }
    }
}
