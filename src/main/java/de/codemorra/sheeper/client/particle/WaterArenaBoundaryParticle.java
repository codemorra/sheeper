package de.codemorra.sheeper.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import de.codemorra.sheeper.client.WaterArenaVisuals;
import net.minecraft.world.phys.Vec3;

/**
 * Stationary, lit bubbles reveal nearby arena bounds without imitating an attack.
 */
public final class WaterArenaBoundaryParticle extends TextureSheetParticle {
    private final float opacity;

    private WaterArenaBoundaryParticle(ClientLevel level, double x, double y, double z,
            SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        lifetime = 24 + random.nextInt(9);
        opacity = 0.85F;
        alpha = opacity;
        quadSize = 0.10F + random.nextFloat() * 0.05F;
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
        Vec3 point = new Vec3(x, y, z);
        if (WaterArenaVisuals.activeVolumes(level).stream().noneMatch(bounds ->
                Math.abs(bounds.interiorDistance(point)) < 0.4D)) {
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
            return new WaterArenaBoundaryParticle(level, x, y, z, sprites);
        }
    }
}
