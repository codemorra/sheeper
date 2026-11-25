package de.codemorra.sheeper.entity;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.registry.ModEntities;
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
    private int age;

    public LightningStrikeEffect(EntityType<? extends LightningBolt> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_HEIGHT, 1.0F);
    }

    /** @return rendered height above the fixed impact point */
    public float getStrikeHeight() {
        return entityData.get(DATA_HEIGHT);
    }

    /** Shared warning marker for both stationary storm centers and moving lanes. */
    public static void showWarning(ServerLevel level, Vec3 pos, double radius) {
        for (int index = 0; index < 24; index++) {
            double angle = Math.PI * 2.0D * index / 24;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.x + Math.cos(angle) * radius, pos.y + 0.15D,
                    pos.z + Math.sin(angle) * radius, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.15D, pos.z,
                4, 0.1D, 0.1D, 0.1D, 0.005D);
    }

    /**
     * Spawns only the visual bolt and shared audio; attacks apply damage
     * separately.
     */
    static void spawn(LightningSheeperEntity boss, Vec3 pos) {
        ServerLevel level = (ServerLevel) boss.level;
        spawnVisual(level, pos, boss.getStormCloudY(pos));
        float thunder = LightningSheeperConfig.LIGHTNING_STRIKE_THUNDER_VOLUME.get().floatValue();
        float impact = LightningSheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
        if (thunder > 0) {
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.HOSTILE, thunder, 0.9F + boss.getRandom().nextFloat() * 0.2F);
        }
        if (impact > 0) {
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.HOSTILE, impact, 0.9F + boss.getRandom().nextFloat() * 0.2F);
        }
    }

    /** Spawns a cosmetic weather bolt. Combat strikes may also play the positional impact cue. */
    public static void spawnWeatherBolt(ServerLevel level, Vec3 pos, double cloudY, boolean playImpact) {
        spawnVisual(level, pos, cloudY);
        float volume = LightningSheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
        if (playImpact && volume > 0) {
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.WEATHER, volume, 0.9F + level.random.nextFloat() * 0.2F);
        }
    }

    private static void spawnVisual(ServerLevel level, Vec3 pos, double cloudY) {
        LightningStrikeEffect effect = ModEntities.LIGHTNING_STRIKE.get().create(level);
        effect.setPos(pos);
        effect.entityData.set(DATA_HEIGHT, (float) Math.max(0.15, cloudY - pos.y));
        level.addFreshEntity(effect);
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
