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
 * Local bubble emitter for a fixed arena air fountain.
 */
public final class ArenaWaterParticle extends NoRenderParticle {
    private final double height;
    private final double speed;
    private final boolean distant;
    private final SpriteSet sprites;

    private ArenaWaterParticle(ClientLevel level, double x, double y, double z,
            double height, int ticks, SpriteSet sprites) {
        super(level, x, y, z);
        this.height = Math.min(128, Math.max(0, height));
        speed = 0.24D;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double cameraY = Mth.clamp(camera.y, y, y + this.height);
        distant = camera.distanceToSqr(x, cameraY, z) > 36 * 36;
        this.sprites = sprites;
        lifetime = Mth.clamp(ticks, 1, 160);
    }

    /**
     * Keeps rising bubbles visible along the full height of the fountain.
     */
    @Override
    public void tick() {
        // A blocked base must not stop emission in open sections higher up.
        if (age >= lifetime) {
            remove();
            return;
        }
        if (age == 0) {
            int samples = Math.max(1, Mth.ceil(height * (distant ? 1 : 2)));
            for (int index = 0; index < samples; index++) {
                emit(y + height * (index + random.nextDouble()) / samples);
            }
        }
        if (age % (distant ? 4 : 2) == 0) {
            emit(y + random.nextDouble() * height);
            if (!distant) {
                emit(y + random.nextDouble() * height);
            }
        }
        age++;
    }

    private void emit(double bubbleY) {
        double bubbleX = x + (random.nextDouble() - 0.5D) * 0.8D;
        double bubbleZ = z + (random.nextDouble() - 0.5D) * 0.8D;
        var point = new Vec3(bubbleX, bubbleY, bubbleZ);
        if (ArenaWater.contains(level, point) && ArenaWater.isClearWaterPoint(level, point)) {
            Minecraft.getInstance().particleEngine.add(new Bubble(level, bubbleX, bubbleY, bubbleZ,
                    speed, y + height, sprites));
        }
    }

    private static final class Bubble extends TextureSheetParticle {
        private final double ceiling;

        private Bubble(ClientLevel level, double x, double y, double z, double speed,
                double ceiling, SpriteSet sprites) {
            super(level, x, y, z);
            this.ceiling = ceiling;
            pickSprite(sprites);
            setSize(0.04F, 0.04F);
            quadSize = 0.08F + random.nextFloat() * 0.06F;
            xd = 0;
            yd = speed;
            zd = 0;
            friction = 1;
            lifetime = Mth.clamp((int) Math.ceil((ceiling - y) / speed) + 2, 8, 160);
        }

        @Override
        public void tick() {
            double previousY = y;
            super.tick();
            var point = new Vec3(x, y, z);
            if (y >= ceiling || !ArenaWater.contains(level, point)
                    || !ArenaWater.isClearWaterColumn(level, point, previousY)) {
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

        /** @param sprites shared bubble texture for the fountain */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /**
         * Decodes the column's height and lifetime from the particle packet.
         */
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double height, double speed, double ticks) {
            if (type != ModParticles.WATER_FOUNTAIN.get()
                    || !Double.isFinite(height) || !Double.isFinite(ticks)) {
                return null;
            }
            return new ArenaWaterParticle(level, x, y, z, height, (int) ticks, sprites);
        }
    }
}
