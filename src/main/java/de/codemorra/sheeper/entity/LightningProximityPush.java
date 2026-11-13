package de.codemorra.sheeper.entity;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.phys.Vec3;

/**
 * One shared proximity charge prevents alternating players from avoiding the
 * discharge.
 */
final class LightningProximityPush {
    private final LightningSheeperEntity boss;
    private int duration;
    private int remaining;

    LightningProximityPush(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    void tick() {
        double radius = SheeperConfig.LIGHTNING_PROXIMITY_PUSH_RADIUS.get();
        List<ServerPlayer> nearby = radius <= 0 ? List.of()
                : boss.getCombatParticipants().stream()
                        .filter(player -> boss.distanceToSqr(player) <= radius * radius).toList();
        if (nearby.isEmpty()) {
            clear();
            return;
        }
        if (boss.isNoAi()) {
            return;
        }
        if (duration == 0) {
            int min = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_PROXIMITY_PUSH_DELAY_MIN.get());
            int max = Math.max(min,
                    SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_PROXIMITY_PUSH_DELAY_MAX.get()));
            duration = min + boss.getRandom().nextInt(max - min + 1);
            remaining = duration;
        }
        if (--remaining <= 0) {
            discharge(nearby);
            clear();
        } else if (remaining % 2 == 0) {
            showCharge();
        }
    }

    private void showCharge() {
        ServerLevel level = (ServerLevel) boss.level;
        double progress = 1.0D - (double) remaining / duration;
        int peak = SheeperConfig.LIGHTNING_PROXIMITY_PUSH_PARTICLE_COUNT.get();
        int count = Math.max(1, (int) (peak * (0.04D + 0.96D * progress * progress)));
        Vec3 center = boss.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, count,
                boss.getBbWidth() * 0.55D, boss.getBbHeight() * 0.45D, boss.getBbWidth() * 0.55D, 0.01D);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, Math.max(1, count / 8),
                boss.getBbWidth() * 0.5D, boss.getBbHeight() * 0.4D, boss.getBbWidth() * 0.5D, 0.002D);
    }

    private void discharge(List<ServerPlayer> nearby) {
        showDischarge();
        float damage = SheeperConfig.LIGHTNING_PROXIMITY_PUSH_DAMAGE.get().floatValue();
        double horizontal = SheeperConfig.LIGHTNING_PROXIMITY_PUSH_STRENGTH.get();
        double upward = SheeperConfig.LIGHTNING_PROXIMITY_PUSH_UPWARD_STRENGTH.get();
        for (ServerPlayer player : nearby) {
            Vec3 motion = player.getDeltaMovement();
            if (damage > 0) {
                player.hurt(new EntityDamageSource("lightningBolt", boss), damage);
            }
            Vec3 direction = new Vec3(player.getX() - boss.getX(), 0, player.getZ() - boss.getZ());
            if (direction.lengthSqr() < 1.0E-8D) {
                direction = new Vec3(1, 0, 0);
            }
            direction = direction.normalize().scale(horizontal);
            // Apply the configured impulse once, independent of vanilla hurt knockback or
            // immunity.
            player.setDeltaMovement(motion.add(direction.x, upward, direction.z));
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.hurtMarked = false;
        }
    }

    private void showDischarge() {
        ServerLevel level = (ServerLevel) boss.level;
        Vec3 center = boss.getBoundingBox().getCenter();
        int count = SheeperConfig.LIGHTNING_PROXIMITY_PUSH_PARTICLE_COUNT.get();
        // A flattened spherical shell sends dense sparks outward as a pressure wave.
        for (int index = 0; index < count; index++) {
            double vertical = 1.0D - 2.0D * (index + 0.5D) / count;
            double angle = index * Math.PI * (3.0D - Math.sqrt(5.0D));
            double horizontal = Math.sqrt(1.0D - vertical * vertical);
            Vec3 direction = new Vec3(Math.cos(angle) * horizontal, vertical * 0.4D, Math.sin(angle) * horizontal);
            Vec3 start = center.add(direction.scale(boss.getBbWidth() * 0.5D));
            // Zero count makes the offsets a directed velocity instead of random spread.
            level.sendParticles(ParticleTypes.END_ROD, start.x, start.y, start.z,
                    0, direction.x, direction.y, direction.z, 0.35D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, start.x, start.y, start.z,
                    0, direction.x, direction.y, direction.z, 0.6D);
        }
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        float thunder = SheeperConfig.LIGHTNING_PROXIMITY_PUSH_THUNDER_VOLUME.get().floatValue();
        float impact = SheeperConfig.LIGHTNING_PROXIMITY_PUSH_IMPACT_VOLUME.get().floatValue();
        if (thunder > 0) {
            level.playSound(null, boss.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.HOSTILE, thunder, 1.0F);
        }
        if (impact > 0) {
            level.playSound(null, boss.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.HOSTILE, impact, 1.0F);
        }
    }

    private void clear() {
        duration = 0;
        remaining = 0;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Duration", duration);
        tag.putInt("Remaining", remaining);
        return tag;
    }

    void load(CompoundTag tag) {
        duration = Mth.clamp(tag.getInt("Duration"), 0, 12000);
        remaining = Mth.clamp(tag.getInt("Remaining"), 0, duration);
        if (remaining == 0) {
            clear();
        }
    }
}
