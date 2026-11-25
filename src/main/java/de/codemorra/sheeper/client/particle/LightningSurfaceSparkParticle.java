package de.codemorra.sheeper.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A short, bright spark for the impact flash and lingering ground charge. */
public final class LightningSurfaceSparkParticle extends TextureSheetParticle {
    private final float initialAlpha;

    private LightningSurfaceSparkParticle(ClientLevel level, double x, double y, double z, double rise,
            SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        quadSize = 0.030F + random.nextFloat() * 0.018F;
        initialAlpha = 0.58F + random.nextFloat() * 0.17F;
        alpha = initialAlpha;
        setColor(0.63F, 0.79F, 1);
        lifetime = 3;
        yd = rise;
        hasPhysics = false;
    }

    @Override
    public void tick() {
        super.tick();
        alpha = initialAlpha * Math.max(0, 1 - age / (float) lifetime);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Reuses the vanilla electric spark texture, including resource pack replacements. */
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double motionX, double motionY, double motionZ) {
            return new LightningSurfaceSparkParticle(level, x, y, z, motionY, sprites);
        }
    }
}
