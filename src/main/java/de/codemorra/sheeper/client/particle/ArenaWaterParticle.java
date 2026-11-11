package de.codemorra.sheeper.client.particle;

import de.codemorra.sheeper.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.WaterDropParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Vertical drops provide arena rain and bright, sustained fountain jets. */
public final class ArenaWaterParticle extends WaterDropParticle {
    private final boolean fountain;

    private ArenaWaterParticle(ClientLevel level, double x, double y, double z, double speed, boolean fountain) {
        super(level, x, y, z);
        this.fountain = fountain;
        xd = 0;
        zd = 0;
        yd = speed;
        lifetime = 80;
        if (fountain) {
            quadSize = 0.18F + random.nextFloat() * 0.08F;
            setColor(0.4F, 0.8F, 1.0F);
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return fountain ? 0xF000F0 : super.getLightColor(partialTick);
    }

    /**
     * Emits locally so a visible water column needs only one server packet per
     * cell.
     */
    private static final class FountainEmitter extends NoRenderParticle {
        private final double speed;
        private final SpriteSet sprites;

        private FountainEmitter(ClientLevel level, double x, double y, double z, double speed, SpriteSet sprites) {
            super(level, x, y, z);
            this.speed = speed;
            this.sprites = sprites;
            lifetime = 12;
        }

        @Override
        public void tick() {
            if (age++ >= lifetime) {
                remove();
                return;
            }
            for (int drop = 0; drop < 3; drop++) {
                ArenaWaterParticle particle = new ArenaWaterParticle(level,
                        x + (random.nextDouble() - 0.5D) * 0.8D, y,
                        z + (random.nextDouble() - 0.5D) * 0.8D, speed, true);
                particle.pickSprite(sprites);
                Minecraft.getInstance().particleEngine.add(particle);
            }
        }
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double dx, double dy, double dz) {
            if (type == ModParticles.WATER_FOUNTAIN.get()) {
                return new FountainEmitter(level, x, y, z, dy, sprites);
            }
            ArenaWaterParticle particle = new ArenaWaterParticle(level, x, y, z, -0.6D, false);
            particle.pickSprite(sprites);
            return particle;
        }
    }
}
