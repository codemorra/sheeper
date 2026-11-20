package de.codemorra.sheeper.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/** Grows the bubble cluster from a small warning to the full blast radius. */
public final class WaterMineParticle extends NoRenderParticle {
    private static final int RING_SAMPLES = 14;
    private final double reach;
    private final int fuse;
    private final int initialAge;
    private final SpriteSet sprites;

    private WaterMineParticle(ClientLevel level, double x, double y, double z, double reach,
            int fuse, int age, SpriteSet sprites) {
        super(level, x, y, z);
        this.reach = reach;
        this.fuse = fuse;
        initialAge = age;
        this.sprites = sprites;
        lifetime = Math.min(4, fuse - age);
    }

    /** Keeps all three rings growing smoothly between server snapshots. */
    @Override
    public void tick() {
        var particles = Minecraft.getInstance().particleEngine;
        Vec3 center = new Vec3(x, y, z);
        double progress = (double) initialAge / fuse;
        for (int index = 0; index < RING_SAMPLES; index++) {
            double angle = (index + 0.5) * Math.PI * 2 / RING_SAMPLES;
            double first = Math.cos(angle) * reach;
            double second = Math.sin(angle) * reach;
            particles.add(new Dot(level, center, new Vec3(first, 0, second),
                    fuse, initialAge, lifetime, false, sprites));
            particles.add(new Dot(level, center, new Vec3(first, second, 0),
                    fuse, initialAge, lifetime, false, sprites));
            particles.add(new Dot(level, center, new Vec3(0, first, second),
                    fuse, initialAge, lifetime, false, sprites));
        }
        double core = reach * 0.8;
        int count = 4 + (int) (progress * 12);
        for (int index = 0; index < count; index++) {
            double yaw = random.nextDouble() * Math.PI * 2;
            double vertical = random.nextDouble() * 2 - 1;
            double horizontal = Math.sqrt(1 - vertical * vertical) * core;
            Vec3 offset = new Vec3(Math.cos(yaw) * horizontal, core * vertical, Math.sin(yaw) * horizontal);
            particles.add(new Dot(level, center, offset, fuse, initialAge, lifetime, true, sprites));
        }
        remove();
    }

    private static final class Dot extends TextureSheetParticle {
        private final Vec3 center;
        private final Vec3 offset;
        private final double initialScale;
        private final double scalePerTick;

        private Dot(ClientLevel level, Vec3 center, Vec3 offset, int fuse, int initialAge,
                int ticks, boolean core, SpriteSet sprites) {
            super(level, center.x, center.y, center.z);
            this.center = center;
            this.offset = offset;
            scalePerTick = 0.78 / fuse;
            initialScale = 0.22 + scalePerTick * initialAge;
            positionAt(initialScale);
            xo = x;
            yo = y;
            zo = z;
            lifetime = ticks;
            hasPhysics = false;
            quadSize = core ? (float) (0.04 + 0.08 * initialAge / fuse) : 0.065F;
            setAlpha(core ? 0.9F : 0.8F);
            setColor(core ? 0.35F : 0.85F, core ? 0.8F : 0.97F, 1.0F);
            pickSprite(sprites);
        }

        @Override
        public void tick() {
            if (++age >= lifetime) {
                remove();
                return;
            }
            xo = x;
            yo = y;
            zo = z;
            positionAt(Math.min(1, initialScale + age * scalePerTick));
        }

        private void positionAt(double scale) {
            setPos(center.x + offset.x * scale, center.y + offset.y * scale, center.z + offset.z * scale);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /** The packet supplies the blast reach, fuse ticks and current mine age. */
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double reach, double fuse, double age) {
            if (!Double.isFinite(reach) || reach < 0.625 || reach > 3.25
                    || !Double.isFinite(fuse) || fuse < 4 || fuse > 100
                    || !Double.isFinite(age) || age < 0 || age >= fuse) {
                return null;
            }
            return new WaterMineParticle(level, x, y, z, reach, (int) fuse, (int) age, sprites);
        }
    }
}
