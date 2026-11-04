package de.codemorra.sheeper.util;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Non-destructive carpet blast with independently configurable damage and
 * launch.
 */
public final class SheeperCarpetExplosion {
    private SheeperCarpetExplosion() {
    }

    /**
     * Affects exposed entities within the configured radius, including the trigger.
     * Call after consuming the carpet. No vanilla block explosion is performed:
     * nearby blocks remain intact and cannot be primed by this blast.
     *
     * @param level the carpet's world; clients do nothing
     * @param pos   the consumed carpet's position
     */
    public static void explode(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }

        Vec3 center = new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D / 16.0D, pos.getZ() + 0.5D);
        double radius = SheeperConfig.CARPET_RADIUS.get();
        float damage = SheeperConfig.CARPET_DAMAGE.get().floatValue();
        double horizontal = SheeperConfig.CARPET_KNOCKBACK.get();
        double upward = SheeperConfig.CARPET_UPWARD_PUSH.get();
        DamageSource source = DamageSource.explosion((LivingEntity) null);

        server.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.7F, 1.3F);
        server.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        server.gameEvent(null, GameEvent.EXPLODE, center);

        for (Entity entity : server.getEntities((Entity) null, new AABB(center, center).inflate(radius))) {
            if (entity.isSpectator() || entity.ignoreExplosion() || entity.isInvulnerableTo(source)
                    || entity instanceof Player player && player.isCreative()) {
                continue;
            }

            // Match vanilla explosion range measurement at the entity's feet.
            double distance = entity.position().distanceTo(center);
            if (distance > radius) {
                continue;
            }

            double exposure = Explosion.getSeenPercent(center, entity);
            if (exposure <= 0.0D) {
                continue;
            }

            if (damage > 0.0F) {
                entity.hurt(source, damage * (float) exposure);
            }

            double impulse = (1.0D - distance / radius) * exposure;
            if (entity instanceof LivingEntity living) {
                impulse = ProtectionEnchantment.getExplosionKnockbackAfterDampener(living, impulse);
                impulse *= Math.max(0.0D, 1.0D - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            }
            if (impulse <= 0.0D || entity.isRemoved() || horizontal == 0.0D && upward == 0.0D) {
                continue;
            }

            Vec3 direction = new Vec3(entity.getX() - center.x, 0.0D, entity.getZ() - center.z);
            // An entity exactly above the center still needs a horizontal launch.
            if (direction.lengthSqr() < 1.0E-8D) {
                direction = new Vec3(1.0D, 0.0D, 0.0D);
            }
            direction = direction.normalize().scale(horizontal * impulse);
            entity.push(direction.x, upward * impulse, direction.z);
            if (entity instanceof ServerPlayer player) {
                // Contact can happen inside movement processing. Send the launch now,
                // before subsequent ground collision or drag can weaken its velocity.
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                player.hurtMarked = false;
            } else {
                entity.hurtMarked = true;
            }
        }
    }
}
