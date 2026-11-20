package de.codemorra.sheeper.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;

import de.codemorra.sheeper.world.ArenaWater;
import de.codemorra.sheeper.registry.ModParticles;
import net.minecraft.client.Camera;
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
     * Builds the column from the floor with a steady stream of rising bubbles.
     */
    @Override
    public void tick() {
        if (age >= lifetime) {
            remove();
            return;
        }
        if (!distant || age % 2 == 0) {
            emit();
        }
        age++;
    }

    private void emit() {
        double bubbleX = x + (random.nextDouble() - 0.5D) * 0.8D;
        double bubbleZ = z + (random.nextDouble() - 0.5D) * 0.8D;
        if (ArenaWater.contains(level, new Vec3(bubbleX, y, bubbleZ))) {
            Minecraft.getInstance().particleEngine.add(new Bubble(level, bubbleX, y, bubbleZ,
                    speed, y + height, sprites));
        }
    }

    private static final class Bubble extends TextureSheetParticle {
        private final double ceiling;
        private float previousOpacity;
        private float opacity;

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
            // Keep the stream rising through blocked sections and draw only its open portions.
            hasPhysics = false;
            lifetime = Mth.ceil((ceiling - y) / speed) + 2;
        }

        @Override
        public void tick() {
            super.tick();
            var point = new Vec3(x, y, z);
            if (y >= ceiling || !ArenaWater.contains(level, point)) {
                remove();
                return;
            }
            if (ArenaWater.isClearWaterPoint(level, point)) {
                previousOpacity = opacity;
                opacity = Math.min(1, opacity + 0.25F);
            } else {
                previousOpacity = 0;
                opacity = 0;
            }
        }

        /** Fades in across render frames when a bubble starts or reaches open water. */
        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            if (opacity > 0) {
                alpha = Mth.lerp(partialTick, previousOpacity, opacity);
                super.render(buffer, camera, partialTick);
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
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
