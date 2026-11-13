package de.codemorra.sheeper.entity;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * Uses the vanilla bolt renderer without running lightning's world
 * interactions.
 */
public final class LightningStrikeEffect extends LightningBolt {
    private int age;

    public LightningStrikeEffect(EntityType<? extends LightningBolt> type, Level level) {
        super(type, level);
    }

    /** Shared warning marker for both stationary storm centers and moving lanes. */
    static void showWarning(ServerLevel level, Vec3 pos, double radius) {
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
        LightningStrikeEffect effect = ModEntities.LIGHTNING_STRIKE.get().create(level);
        effect.setPos(pos);
        level.addFreshEntity(effect);
        float thunder = SheeperConfig.LIGHTNING_STRIKE_THUNDER_VOLUME.get().floatValue();
        float impact = SheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
        if (thunder > 0) {
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.HOSTILE, thunder, 0.9F + boss.getRandom().nextFloat() * 0.2F);
        }
        if (impact > 0) {
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.HOSTILE, impact, 0.9F + boss.getRandom().nextFloat() * 0.2F);
        }
    }

    @Override
    public void tick() {
        // LightningBolt.tick also affects rods, copper and entities even in some
        // visual-only paths.
        // Only ordinary entity housekeeping is appropriate for this short-lived effect.
        baseTick();
        if (++age >= 8) {
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
