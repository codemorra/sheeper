package de.codemorra.sheeper.entity;

import java.util.UUID;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.world.LightningStormData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * Short-lived visual lightning with a synchronized cloud-to-ground height.
 */
public final class LightningStrikeEffect extends LightningBolt {
    static final int VISUAL_DURATION_TICKS = 8;
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData
            .defineId(LightningStrikeEffect.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData
            .defineId(LightningStrikeEffect.class, EntityDataSerializers.FLOAT);
    private int age;

    public LightningStrikeEffect(EntityType<? extends LightningBolt> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_HEIGHT, 1.0F);
        entityData.define(DATA_WIDTH, 1.0F);
    }

    /** @return rendered height above the fixed impact point */
    public float getStrikeHeight() {
        return entityData.get(DATA_HEIGHT);
    }

    /** @return width multiplier for the rendered strike */
    public float getStrikeWidth() {
        return entityData.get(DATA_WIDTH);
    }

    /** Checks leaves along weather bolts and returns the actual impact point. */
    public static Vec3 spawnWeatherBolt(ServerLevel level, UUID owner, Vec3 pos, double cloudY, boolean playImpact) {
        Vec3 hit = spawnStrike(level, owner, pos, cloudY);
        float volume = LightningSheeperConfig.LIGHTNING_ATTACK_IMPACT_VOLUME.get().floatValue();
        if (playImpact && volume > 0) {
            level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.WEATHER, volume, 0.9F + level.random.nextFloat() * 0.2F);
        }
        return hit;
    }

    private static Vec3 spawnStrike(ServerLevel level, UUID owner, Vec3 pos, double cloudY) {
        Vec3 hit = spawnVisualBolt(level, pos, cloudY, 1.0F);
        if (!hit.equals(new Vec3(pos.x, Math.max(cloudY, pos.y + 0.15), pos.z))) {
            LightningStormData.get(level).igniteCloudStrike(level, owner, hit, cloudY);
        }
        return hit;
    }

    /** Sends a bolt for presentation without invoking the arena's ignition logic. */
    public static void spawnVisualBolt(ServerLevel level, Vec3 pos, double cloudY) {
        spawnVisualBolt(level, pos, cloudY, 1.0F);
    }

    /** Allows an attack to emphasize a visual strike with a wider arc. */
    static Vec3 spawnVisualBolt(ServerLevel level, Vec3 pos, double cloudY, float width) {
        Vec3 start = new Vec3(pos.x, Math.max(cloudY, pos.y + 0.15), pos.z);
        Vec3 hit = LightningStormData.get(level).clipLightning(start, pos);
        if (hit.equals(start)) {
            return hit;
        }
        LightningStrikeEffect effect = ModEntities.LIGHTNING_STRIKE.get().create(level);
        effect.setPos(hit);
        effect.entityData.set(DATA_HEIGHT, (float) (start.y - hit.y));
        effect.entityData.set(DATA_WIDTH, width);
        level.addFreshEntity(effect);
        if (!hit.equals(pos)) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.x, hit.y, hit.z, 8, 0.22, 0.08, 0.22, 0.05);
        }
        return hit;
    }

    @Override
    public void tick() {
        // LightningBolt.tick also affects rods, copper and entities even in some
        // visual-only paths.
        // Only ordinary entity housekeeping is appropriate for this short-lived effect.
        baseTick();
        if (++age >= VISUAL_DURATION_TICKS) {
            discard();
        } else if (level.isClientSide && age % 2 == 0) {
            seed = random.nextLong();
        }
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
