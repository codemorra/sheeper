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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
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
        if (age >= lifetime || !isClearWater(level, x, y, z)) {
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
        if (isClearWater(level, bubbleX, bubbleY, bubbleZ)) {
            Minecraft.getInstance().particleEngine.add(new Bubble(level, bubbleX, bubbleY, bubbleZ,
                    speed, y + height, sprites));
        }
    }

    /** Tests the actual collision shape so bubbles can start above a partial block. */
    private static boolean isClearWater(ClientLevel level, double x, double y, double z) {
        if (!ArenaWater.contains(level, new Vec3(x, y, z))) {
            return false;
        }
        var pos = new BlockPos(x, y, z);
        var state = level.getBlockState(pos);
        var fluid = state.getFluidState();
        if (!fluid.isEmpty() && !fluid.is(FluidTags.WATER)) {
            return false;
        }
        var shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return true;
        }
        for (var box : shape.toAabbs()) {
            if (box.contains(x - pos.getX(), y - pos.getY(), z - pos.getZ())) {
                return false;
            }
        }
        return true;
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
            super.tick();
            if (y >= ceiling || !isClearWater(level, x, y, z)) {
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
