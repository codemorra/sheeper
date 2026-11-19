package de.codemorra.sheeper.client.particle;

import de.codemorra.sheeper.world.ArenaWater;
import de.codemorra.sheeper.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Local full-height bubble emitter; the former arena-rain packet produces no
 * particles.
 */
public final class ArenaWaterParticle extends NoRenderParticle {
    private final double height;
    private final double speed;
    private final boolean warning;
    private final SpriteSet sprites;

    private ArenaWaterParticle(ClientLevel level, double x, double y, double z,
            double height, double speed, int ticks, SpriteSet sprites) {
        super(level, x, y, z);
        this.height = Math.min(128, Math.max(0, height));
        warning = speed <= 0;
        this.speed = warning ? 0.025D : Mth.clamp(speed * 2, 0.65D, 1.2D);
        this.sprites = sprites;
        lifetime = Mth.clamp(ticks, 1, 160);
    }

    /**
     * Distributes both phases over the full column, with a denser continuous stream
     * during eruption.
     */
    @Override
    public void tick() {
        if (age >= lifetime || !ArenaWater.isOpenAt(level, new Vec3(x, y, z))) {
            remove();
            return;
        }
        if (age == 0) {
            int samples = Math.max(1, (int) Math.ceil(warning ? height / 8 : height * 1.5D));
            for (int index = 0; index < samples; index++) {
                emit(y + height * (index + random.nextDouble()) / samples);
            }
        }
        if (!warning || age % 8 == 0) {
            int count = warning ? 1 : Mth.clamp((int) Math.ceil(height / 8), 2, 8);
            for (int index = 0; index < count; index++) {
                emit(y + random.nextDouble() * height);
            }
        }
        age++;
    }

    private void emit(double bubbleY) {
        double bubbleX = x + (random.nextDouble() - 0.5D) * 0.8D;
        double bubbleZ = z + (random.nextDouble() - 0.5D) * 0.8D;
        if (ArenaWater.isOpenAt(level, new Vec3(bubbleX, bubbleY, bubbleZ))) {
            Minecraft.getInstance().particleEngine.add(new Bubble(level, bubbleX, bubbleY, bubbleZ,
                    speed, y + height, warning, sprites));
        }
    }

    /**
     * Maintains the eruption's upward speed instead of vanilla bubbles' strong
     * velocity damping.
     */
    private static final class Bubble extends TextureSheetParticle {
        private final double ceiling;

        private Bubble(ClientLevel level, double x, double y, double z, double speed,
                double ceiling, boolean warning, SpriteSet sprites) {
            super(level, x, y, z);
            this.ceiling = ceiling;
            pickSprite(sprites);
            setSize(0.04F, 0.04F);
            quadSize = warning ? 0.045F : 0.055F + random.nextFloat() * 0.035F;
            xd = 0;
            yd = speed;
            zd = 0;
            friction = 1;
            lifetime = warning ? 12 : Mth.clamp((int) Math.ceil((ceiling - y) / speed) + 2, 8, 160);
        }

        @Override
        public void tick() {
            super.tick();
            if (y >= ceiling || !ArenaWater.isOpenAt(level, new Vec3(x, y, z))) {
                remove();
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        /** @param sprites shared bubble texture for the warning and eruption phases */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /**
         * Decodes the column's height, ascent speed and lifetime from the three
         * motion fields. Legacy rain packets are ignored without removing a registry
         * ID.
         */
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double height, double speed, double ticks) {
            if (type != ModParticles.WATER_FOUNTAIN.get()
                    || !Double.isFinite(height) || !Double.isFinite(speed) || !Double.isFinite(ticks)) {
                return null;
            }
            return new ArenaWaterParticle(level, x, y, z, height, speed, (int) ticks, sprites);
        }
    }
}
