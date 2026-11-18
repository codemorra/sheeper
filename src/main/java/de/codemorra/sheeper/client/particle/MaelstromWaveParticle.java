package de.codemorra.sheeper.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Short-lived bubble crests make the pressure wave visible at the viewer's
 * swimming height.
 */
public final class MaelstromWaveParticle extends TextureSheetParticle {
    private MaelstromWaveParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        lifetime = 6;
        quadSize = 0.10F + random.nextFloat() * 0.05F;
        hasPhysics = false;
        xd = 0;
        yd = 0.025D;
        zd = 0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    private static final class Emitter extends NoRenderParticle {
        private final double radius;
        private final double height;
        private final SpriteSet sprites;

        private Emitter(ClientLevel level, double x, double y, double z,
                double radius, double height, SpriteSet sprites) {
            super(level, x, y, z);
            this.radius = radius;
            this.height = height;
            this.sprites = sprites;
        }

        /**
         * Expands one compact packet into a bounded nearby arc, leaving distant
         * particles unallocated.
         */
        @Override
        public void tick() {
            remove();
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            int samples = Mth.clamp(Mth.ceil(Math.PI * 2 * radius / 1.5D), 24, 512);
            double bottom = Math.max(y + 0.2D, camera.y - 4);
            double top = Math.min(y + height - 0.2D, camera.y + 4);
            for (int index = 0; index < samples; index++) {
                double angle = index * Math.PI * 2 / samples;
                double px = x + Math.cos(angle) * radius;
                double pz = z + Math.sin(angle) * radius;
                if ((px - camera.x) * (px - camera.x) + (pz - camera.z) * (pz - camera.z) > 24 * 24) {
                    continue;
                }
                for (double py = bottom; py <= top; py += 1.5D) {
                    if (level.getFluidState(new BlockPos(px, py, pz)).is(FluidTags.WATER)) {
                        Minecraft.getInstance().particleEngine.add(new MaelstromWaveParticle(level,
                                px, py, pz, sprites));
                    }
                }
            }
        }
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        /** @param sprites vanilla bubble sprite shared by every crest sample */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /**
         * Packet motion fields carry ring radius and flooded height rather than
         * particle velocity.
         */
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double radius, double height, double unused) {
            if (!Double.isFinite(radius) || !Double.isFinite(height) || radius < 0 || radius > 256
                    || height <= 0 || height > 128) {
                return null;
            }
            return new Emitter(level, x, y, z, radius, height, sprites);
        }
    }
}
