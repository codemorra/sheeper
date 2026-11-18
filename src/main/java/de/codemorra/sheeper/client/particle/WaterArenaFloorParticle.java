package de.codemorra.sheeper.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;

/**
 * Stationary, softly lit bubbles reveal the arena's lower plane in dark water.
 */
public final class WaterArenaFloorParticle extends TextureSheetParticle {
    private WaterArenaFloorParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        lifetime = 14;
        quadSize = 0.045F + random.nextFloat() * 0.015F;
        hasPhysics = false;
        xd = 0;
        yd = 0;
        zd = 0;
        setColor(0.65F, 0.85F, 1.0F);
    }

    /**
     * Fades without rising, keeping the marked plane aligned with the actual
     * boundary.
     */
    @Override
    public void tick() {
        super.tick();
        alpha = 0.65F * (1.0F - (float) age / lifetime);
        if (!level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER)) {
            remove();
        }
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 240;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * Reuses the vanilla bubble sprite so resource packs also affect the marker.
     */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double motionX, double motionY, double motionZ) {
            return new WaterArenaFloorParticle(level, x, y, z, sprites);
        }
    }
}
