package de.codemorra.sheeper.client.particle;

import de.codemorra.sheeper.particle.WaterMineWaveOptions;
import de.codemorra.sheeper.world.ArenaWater;
import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Draws a fast, expanding shell of mini bubbles from compact server snapshots. */
public final class WaterMineWaveParticle extends NoRenderParticle {
    private static final int SAMPLES = 64;
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    private final VolumeBounds arena;
    private final double radius;
    private final double speed;
    private final double maximum;
    private final SpriteSet sprites;

    private WaterMineWaveParticle(ClientLevel level, double x, double y, double z, VolumeBounds arena,
            double radius, double speed, double maximum, SpriteSet sprites) {
        super(level, x, y, z);
        this.arena = arena;
        this.radius = radius;
        this.speed = speed;
        this.maximum = maximum;
        this.sprites = sprites;
    }

    /** Uses evenly spaced sphere samples so the wave reads from every viewing direction. */
    @Override
    public void tick() {
        var client = Minecraft.getInstance();
        var eye = client.getCameraEntity();
        Vec3 center = new Vec3(x, y, z);
        for (int index = 0; index < SAMPLES; index++) {
            double up = 1 - 2 * (index + 0.5) / SAMPLES;
            double horizontal = Math.sqrt(1 - up * up);
            double angle = index * GOLDEN_ANGLE;
            Vec3 direction = new Vec3(Math.cos(angle) * horizontal, up, Math.sin(angle) * horizontal);
            Vec3 point = center.add(direction.scale(radius));
            if (arena.contains(point) && ArenaWater.isClearWaterPoint(level, point)
                    && (eye == null || level.clip(new ClipContext(center, point, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, eye)).getType() == HitResult.Type.MISS)) {
                client.particleEngine.add(new Crest(level, center, direction, radius, speed, maximum, arena, sprites));
            }
        }
        remove();
    }

    private static final class Crest extends TextureSheetParticle {
        private final Vec3 center;
        private final Vec3 direction;
        private final VolumeBounds arena;
        private final double speed;
        private final double maximum;
        private double radius;

        private Crest(ClientLevel level, Vec3 center, Vec3 direction, double radius, double speed,
                double maximum, VolumeBounds arena, SpriteSet sprites) {
            super(level, center.x + direction.x * radius, center.y + direction.y * radius,
                    center.z + direction.z * radius);
            this.center = center;
            this.direction = direction;
            this.radius = radius;
            this.speed = speed;
            this.maximum = maximum;
            this.arena = arena;
            lifetime = 2;
            hasPhysics = false;
            quadSize = 0.045F + random.nextFloat() * 0.025F;
            setColor(0.77F, 0.95F, 1.0F);
            pickSprite(sprites);
        }

        @Override
        public void tick() {
            if (++age >= lifetime) {
                remove();
                return;
            }
            radius = Math.min(maximum, radius + speed);
            Vec3 point = center.add(direction.scale(radius));
            var eye = Minecraft.getInstance().getCameraEntity();
            if (!arena.contains(point) || !ArenaWater.isClearWaterPoint(level, point)
                    || eye != null && level.clip(new ClipContext(new Vec3(x, y, z), point,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, eye)).getType() != HitResult.Type.MISS) {
                remove();
                return;
            }
            xo = x;
            yo = y;
            zo = z;
            setPos(point.x, point.y, point.z);
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

    public static final class Provider implements ParticleProvider<WaterMineWaveOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /** The motion fields carry the current radius, radial speed and maximum reach. */
        @Override
        public Particle createParticle(WaterMineWaveOptions options, ClientLevel level, double x, double y, double z,
                double radius, double speed, double maximum) {
            VolumeBounds bounds = options.bounds();
            if (!bounds.isValidFor(level) || !Double.isFinite(maximum) || maximum > 12.25 || maximum <= 0
                    || !Double.isFinite(radius) || radius <= 0 || radius > maximum
                    || !Double.isFinite(speed) || speed <= 0 || speed > 2) {
                return null;
            }
            return new WaterMineWaveParticle(level, x, y, z, bounds, radius, speed, maximum, sprites);
        }
    }
}
