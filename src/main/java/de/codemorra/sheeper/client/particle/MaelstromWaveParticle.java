package de.codemorra.sheeper.client.particle;

import de.codemorra.sheeper.particle.MaelstromWaveOptions;
import de.codemorra.sheeper.world.ArenaWater;
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

/**
 * Short-lived bubble crests make the pressure wave visible at the viewer's
 * swimming height.
 */
public final class MaelstromWaveParticle extends TextureSheetParticle {
    private final MaelstromWaveOptions arena;
    private final double bottom;
    private final double top;

    private MaelstromWaveParticle(ClientLevel level, double x, double y, double z,
            double motionX, double motionZ, SpriteSet sprites, MaelstromWaveOptions arena, double bottom, double top) {
        super(level, x, y, z);
        this.arena = arena;
        this.bottom = bottom;
        this.top = top;
        pickSprite(sprites);
        lifetime = 4;
        quadSize = 0.13F + random.nextFloat() * 0.05F;
        hasPhysics = false;
        friction = 1;
        xd = motionX;
        yd = 0.025D;
        zd = motionZ;
    }

    /**
     * Keeps the crest moving with the wave instead of leaving stationary rings
     * behind.
     */
    @Override
    public void tick() {
        super.tick();
        quadSize *= 0.88F;
        if (!arena.contains(x, z) || y < bottom || y > top || !ArenaWater.isOpenAt(level, new Vec3(x, y, z))) {
            remove();
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    private static final class Emitter extends NoRenderParticle {
        private final double radius;
        private final double height;
        private final double speed;
        private final SpriteSet sprites;
        private final MaelstromWaveOptions arena;

        private Emitter(ClientLevel level, double x, double y, double z,
                double radius, double height, double speed, SpriteSet sprites, MaelstromWaveOptions arena) {
            super(level, x, y, z);
            this.radius = radius;
            this.height = height;
            this.speed = speed;
            this.sprites = sprites;
            this.arena = arena;
        }

        /**
         * Expands one compact packet into a bounded nearby arc, leaving distant
         * particles unallocated.
         */
        @Override
        public void tick() {
            remove();
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            int samples = Mth.clamp(Mth.ceil(Math.PI * 2 * radius), 24, 512);
            double bottom = Math.max(y + 0.2D, camera.y - 4);
            double top = Math.min(y + height - 0.2D, camera.y + 4);
            for (int index = 0; index < samples; index++) {
                double angle = index * Math.PI * 2 / samples;
                double px = x + Math.cos(angle) * radius;
                double pz = z + Math.sin(angle) * radius;
                if (!arena.contains(px, pz)
                        || (px - camera.x) * (px - camera.x) + (pz - camera.z) * (pz - camera.z) > 24 * 24) {
                    continue;
                }
                for (double py = bottom; py <= top; py += 1.0D) {
                    if (ArenaWater.isOpenAt(level, new Vec3(px, py, pz))) {
                        Minecraft.getInstance().particleEngine.add(new MaelstromWaveParticle(level,
                                px, py, pz, Math.cos(angle) * speed, Math.sin(angle) * speed, sprites,
                                arena, y, y + height));
                    }
                }
            }
        }
    }

    public static final class Provider implements ParticleProvider<MaelstromWaveOptions> {
        private final SpriteSet sprites;

        /** @param sprites vanilla bubble sprite shared by every crest sample */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        /**
         * Packet motion fields carry ring radius, flooded height and outward
         * speed per tick; nearby crest particles follow that speed locally.
         */
        @Override
        public Particle createParticle(MaelstromWaveOptions arena, ClientLevel level, double x, double y, double z,
                double radius, double height, double speed) {
            if (!Double.isFinite(radius) || !Double.isFinite(height) || radius < 0 || radius > 256
                    || height <= 0 || height > 129 || !Double.isFinite(speed) || speed < 0 || speed > 3
                    || !Double.isFinite(arena.arenaX()) || !Double.isFinite(arena.arenaZ())
                    || !Double.isFinite(arena.arenaRadius()) || arena.arenaRadius() < 1 || arena.arenaRadius() > 128) {
                return null;
            }
            return new Emitter(level, x, y, z, radius, height, speed, sprites, arena);
        }
    }
}
