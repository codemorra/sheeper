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
import net.minecraft.world.level.material.Fluids;
import de.codemorra.sheeper.registry.ModParticles;

/**
 * Stationary, lit bubbles reveal nearby arena bounds without imitating an attack.
 */
public final class WaterArenaBoundaryParticle extends TextureSheetParticle {
    private final float opacity;
    private final boolean side;

    private WaterArenaBoundaryParticle(ClientLevel level, double x, double y, double z,
            SpriteSet sprites, boolean side) {
        super(level, x, y, z);
        this.side = side;
        pickSprite(sprites);
        lifetime = side ? 20 : 14;
        opacity = side ? 0.85F : 0.65F;
        alpha = opacity;
        quadSize = side ? 0.10F + random.nextFloat() * 0.03F : 0.045F + random.nextFloat() * 0.015F;
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
        alpha = opacity * (1.0F - (float) age / lifetime);
        var fluid = level.getFluidState(new BlockPos(x, y, z));
        if (!fluid.is(FluidTags.WATER) || side && !fluid.getType().isSame(Fluids.WATER)) {
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
            return new WaterArenaBoundaryParticle(level, x, y, z, sprites,
                    type == ModParticles.WATER_ARENA_SIDE.get());
        }
    }
}
