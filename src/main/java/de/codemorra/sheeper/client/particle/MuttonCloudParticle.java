package de.codemorra.sheeper.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A short-lived vapor puff using the vanilla animated smoke sprites.
 * The emitter assigns the tint according to the body outlet.
 * It expands while fading, rather than forming an opaque cloud around the
 * player.
 */
public final class MuttonCloudParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    private MuttonCloudParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        setParticleSpeed(xSpeed, ySpeed, zSpeed);
        friction = 0.94F;
        gravity = -0.02F;
        lifetime = 22 + random.nextInt(10);
        quadSize = 0.10F + random.nextFloat() * 0.05F;
        alpha = 0.55F;
        // Default for command-spawned particles; body emitters set their own tint.
        setColor(0.95F, 0.22F, 0.06F);
        setSpriteFromAge(sprites);
    }

    /** Uses alpha blending so overlapping puffs remain a translucent haze. */
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Expands each puff over its lifetime while preserving per-emitter scaling. */
    @Override
    public float getQuadSize(float partialTick) {
        float progress = Mth.clamp((age + partialTick) / lifetime, 0.0F, 1.0F);
        return quadSize * (0.6F + progress * 1.4F);
    }

    /** Advances smoke sprites and smoothly lowers opacity until removal. */
    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            setSpriteFromAge(sprites);
            float remaining = 1.0F - (float) age / lifetime;
            alpha = 0.55F * remaining * remaining;
        }
    }

    /** Supplies the atlas sprites loaded from the particle description JSON. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        /**
         * Keeps the engine-managed sprite set, which is rebound on resource reloads.
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /** Creates a puff with the emitter's outward motion and a default warm tint. */
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new MuttonCloudParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
