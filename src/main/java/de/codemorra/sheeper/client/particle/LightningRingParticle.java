package de.codemorra.sheeper.client.particle;

import de.codemorra.sheeper.entity.LightningRingSurface;
import de.codemorra.sheeper.particle.LightningRingOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Dense white cores with cyan edges remain bright even on dark ground. */
public final class LightningRingParticle extends TextureSheetParticle {
    private LightningRingParticle(ClientLevel level, double x, double y, double z, boolean core, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        lifetime = 3;
        quadSize = core ? 0.28F : 0.20F;
        alpha = 0.95F;
        hasPhysics = false;
        setColor(core ? 1.0F : 0.35F, core ? 1.0F : 0.85F, 1.0F);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        alpha = age < 2 ? 0.95F : 0.45F;
    }

    private static final class Emitter extends NoRenderParticle {
        private final LightningRingOptions options;
        private final SpriteSet sprites;

        Emitter(ClientLevel level, double x, double y, double z, LightningRingOptions options, SpriteSet sprites) {
            super(level, x, y, z);
            this.options = options;
            this.sprites = sprites;
        }

        @Override
        public void tick() {
            remove();
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            int samples = Mth.clamp(Mth.ceil(Math.PI * 2.0D * options.radius() / options.spacing()), 24, 4096);
            for (int index = 0; index < samples; index++) {
                for (int stripe = -1; stripe <= 1; stripe++) {
                    double angle = Math.PI * 2.0D * (index + stripe / 3.0D) / samples;
                    double radius = Math.max(0, options.radius() + stripe * options.width() * 0.4D);
                    double px = x + Math.cos(angle) * radius;
                    double pz = z + Math.sin(angle) * radius;
                    double dx = px - options.arenaX();
                    double dz = pz - options.arenaZ();
                    if (dx * dx + dz * dz > options.arenaRadius() * options.arenaRadius()
                            || (camera.x - px) * (camera.x - px) + (camera.z - pz) * (camera.z - pz) > 64.0D * 64.0D) {
                        continue;
                    }
                    double ground = LightningRingSurface.find(level, px, pz, y, options.minY(), options.maxY());
                    if (Double.isFinite(ground) && camera.distanceToSqr(px, ground, pz) <= 64.0D * 64.0D) {
                        Minecraft.getInstance().particleEngine.add(new LightningRingParticle(level,
                                px, ground + 0.08D, pz, stripe == 0, sprites));
                    }
                }
            }
        }
    }

    public static final class Provider implements ParticleProvider<LightningRingOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(LightningRingOptions options, ClientLevel level, double x, double y, double z,
                double dx, double dy, double dz) {
            if (!Float.isFinite(options.radius()) || !Float.isFinite(options.width())
                    || !Float.isFinite(options.spacing()) || options.spacing() <= 0
                    || !Float.isFinite(options.arenaRadius()) || options.arenaRadius() <= 0
                    || !Double.isFinite(options.arenaX()) || !Double.isFinite(options.arenaZ())
                    || options.minY() > options.maxY()) {
                return null;
            }
            return new Emitter(level, x, y, z, options, sprites);
        }
    }
}
