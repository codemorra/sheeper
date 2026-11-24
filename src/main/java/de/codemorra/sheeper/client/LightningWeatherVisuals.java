package de.codemorra.sheeper.client;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.LightningRingSurface;
import de.codemorra.sheeper.network.LightningWeatherPacket;
import de.codemorra.sheeper.util.ResourceLocations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Local storm clouds, wind and rain, independent of boss render distance.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LightningWeatherVisuals {
    private static final ResourceLocation RAIN = ResourceLocations.create("minecraft",
            "textures/environment/rain.png");
    private static final double FRONT_VISIBILITY = 48;
    private static final int CLOUD_SEGMENTS = 48;
    private static final int CLOUD_RINGS = 10;
    private static final int RAIN_SPACING = 2;
    private static ClientLevel snapshotLevel;
    private static List<LightningWeatherPacket.Arena> arenas = List.of();
    private static long lastSnapshot;

    private LightningWeatherVisuals() {
    }

    public static void receive(LightningWeatherPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && packet.dimension().equals(level.dimension().location())) {
            snapshotLevel = level;
            arenas = packet.arenas();
            lastSnapshot = level.getGameTime();
        }
    }

    public static float intensity(Level level, BlockPos pos) {
        return intensity(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    private static float intensity(Level level, double x, double y, double z) {
        if (!hasSnapshot(level)) {
            return 0;
        }
        float value = 0;
        for (var arena : arenas) {
            value = Math.max(value, arena.intensityAt(level.getGameTime(), x, y, z));
        }
        return value;
    }

    private static double cloudHalfHeight(LightningWeatherPacket.Arena arena) {
        return Math.min(4.5, (arena.maximumY() - arena.minimumY()) / 5.0);
    }

    private static double cloudCenterY(LightningWeatherPacket.Arena arena, double halfHeight) {
        return arena.maximumY() - halfHeight * 1.7;
    }

    private static boolean hasSnapshot(Level level) {
        return level == snapshotLevel && !arenas.isEmpty() && level.getGameTime() - lastSnapshot <= 60;
    }

    @SubscribeEvent
    public static void renderStorm(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || level == null
                || !hasSnapshot(level)) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        double time = level.getGameTime() + (double) event.getPartialTick();
        double viewDistance = mc.options.getEffectiveRenderDistance() * 16.0;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        // AFTER_WEATHER already has the camera's view matrix on RenderSystem.
        // Submit camera-relative coordinates so turning the camera applies that matrix only once.
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (var arena : arenas) {
            if (visible(arena, camera, time, viewDistance)) {
                drawClouds(buffer, arena, camera, time);
            }
        }
        Tesselator.getInstance().end();

        mc.gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, RAIN);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        for (var arena : arenas) {
            if (visible(arena, camera, time, viewDistance)) {
                drawRain(buffer, level, arena, camera, time);
            }
        }
        Tesselator.getInstance().end();
        mc.gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static boolean visible(LightningWeatherPacket.Arena arena, Vec3 camera, double time, double viewDistance) {
        Vec3 center = Vec3.atBottomCenterOf(arena.center());
        double radius = arena.radiusAt(time);
        return radius >= 0.1
                && Math.hypot(center.x - camera.x, center.z - camera.z) <= radius + viewDistance;
    }

    /**
     * Draws a closed, flattened cloud bank. Rings meet around its curved sides
     * so the underside and top stay connected from every viewing height.
     */
    private static void drawClouds(BufferBuilder buffer, LightningWeatherPacket.Arena arena,
            Vec3 camera, double time) {
        double halfHeight = cloudHalfHeight(arena);
        Vec3 center = new Vec3(arena.center().getX() + 0.5, cloudCenterY(arena, halfHeight),
                arena.center().getZ() + 0.5).subtract(camera);
        double radius = arena.radiusAt(time);
        float stormOpacity = arena.opacityAt(time);
        for (int ring = 0; ring < CLOUD_RINGS; ring++) {
            double upper = Math.PI * (0.5 - (double) ring / CLOUD_RINGS);
            double lower = Math.PI * (0.5 - (double) (ring + 1) / CLOUD_RINGS);
            double upperRadius = radius * Math.cos(upper);
            double lowerRadius = radius * Math.cos(lower);
            double upperHeight = Math.sin(upper);
            double lowerHeight = Math.sin(lower);
            for (int segment = 0; segment < CLOUD_SEGMENTS; segment++) {
                double a = Math.PI * 2 * segment / CLOUD_SEGMENTS;
                double b = Math.PI * 2 * (segment + 1) / CLOUD_SEGMENTS;
                cloudVertex(buffer, center, upperRadius, a, upperHeight, halfHeight, time, stormOpacity);
                cloudVertex(buffer, center, lowerRadius, a, lowerHeight, halfHeight, time, stormOpacity);
                cloudVertex(buffer, center, lowerRadius, b, lowerHeight, halfHeight, time, stormOpacity);
                cloudVertex(buffer, center, upperRadius, b, upperHeight, halfHeight, time, stormOpacity);
            }
        }
    }

    private static void cloudVertex(BufferBuilder buffer, Vec3 center,
            double radius, double angle, double height, double halfHeight, double time, float stormOpacity) {
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;
        double variation = (Math.sin(x * 0.14 + z * 0.11 - time * 0.018)
                + Math.sin(z * 0.27 - x * 0.09 + time * 0.011)) * 0.5;
        double y = center.y + halfHeight * (variation * 0.5 + height * (1 + variation * 0.2));
        float shade = (float) (0.23 + variation * 0.06 + height * 0.05);
        float opacity = (float) (0.56 + variation * 0.09 + Math.abs(height) * 0.14) * stormOpacity;
        buffer.vertex((float) (center.x + x), (float) y, (float) (center.z + z))
                .color(shade * 0.8F, shade * 0.92F, shade * 1.15F, opacity).endVertex();
    }

    /**
     * Distributes rain from the terrain to the cloud base. Each cell has a fixed
     * offset and orientation to break up straight sightlines through the rain.
     */
    private static void drawRain(BufferBuilder buffer, ClientLevel level,
            LightningWeatherPacket.Arena arena, Vec3 camera, double time) {
        double radius = arena.radiusAt(time);
        double top = cloudCenterY(arena, cloudHalfHeight(arena));
        double centerX = arena.center().getX() + 0.5;
        double centerZ = arena.center().getZ() + 0.5;
        int extent = Mth.ceil(radius / RAIN_SPACING + 0.5);
        for (int dx = -extent; dx <= extent; dx++) {
            for (int dz = -extent; dz <= extent; dz++) {
                int cellX = arena.center().getX() + dx * RAIN_SPACING;
                int cellZ = arena.center().getZ() + dz * RAIN_SPACING;
                long pattern = RandomSupport.mixStafford13(BlockPos.asLong(cellX, 0, cellZ));
                double x = cellX + 0.5 + (rainVariation(pattern) - 0.5) * 1.8;
                double z = cellZ + 0.5 + (rainVariation(pattern >>> 8) - 0.5) * 1.8;
                float strength = arena.intensityAt(time, x, top, z);
                int blockX = Mth.floor(x);
                int blockZ = Mth.floor(z);
                if (strength <= 0 || !level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
                    continue;
                }
                int floor = Math.max(arena.minimumY(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ));
                if (floor >= top) {
                    continue;
                }

                float alpha = strength * 0.40F;
                int light = LevelRenderer.getLightColor(level, new BlockPos(blockX, floor, blockZ));
                double edge = radius - Math.hypot(x - centerX, z - centerZ);
                double halfWidth = Math.min(0.85, edge);
                double downwind = Math.min((top - floor) * 0.20, edge - halfWidth);
                float scroll = (float) (time % 800) * 0.12F + rainVariation(pattern >>> 16);
                float bottomV = floor * 0.25F + scroll;
                float topV = (float) top * 0.25F + scroll;
                double angle = rainVariation(pattern >>> 24) * Math.PI / 2;
                double wx = Math.cos(angle) * halfWidth;
                double wz = Math.sin(angle) * halfWidth;
                // Crossed vertical planes are visible from both sides and do not rotate with the camera.
                rainPlane(buffer, x - camera.x, z - camera.z, floor - camera.y, top - camera.y,
                        downwind, wx, wz, bottomV, topV, alpha, light);
                rainPlane(buffer, x - camera.x, z - camera.z, floor - camera.y, top - camera.y,
                        downwind, -wz, wx, bottomV, topV, alpha, light);
            }
        }
    }

    /** Extracts one independent fraction from the cell's stable hash. */
    private static float rainVariation(long bits) {
        return (bits & 255) / 256.0F;
    }

    private static void rainPlane(BufferBuilder buffer, double x, double z, double bottom,
            double top, double slant, double wx, double wz, float bottomV, float topV, float alpha, int light) {
        double topX = x - slant * 0.91;
        double topZ = z - slant * 0.41;
        rainVertex(buffer, topX - wx, top, topZ - wz, 0, topV, alpha, light);
        rainVertex(buffer, topX + wx, top, topZ + wz, 1, topV, alpha, light);
        rainVertex(buffer, x + wx, bottom, z + wz, 1, bottomV, alpha, light);
        rainVertex(buffer, x - wx, bottom, z - wz, 0, bottomV, alpha, light);
    }

    private static void rainVertex(BufferBuilder buffer, double x, double y, double z,
            float u, float v, float alpha, int light) {
        buffer.vertex((float) x, (float) y, (float) z)
                .uv(u, v).color(0.79F, 0.86F, 1, alpha).uv2(light).endVertex();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        ClientLevel level = mc.level;
        if (!hasSnapshot(level)) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        if (level.getGameTime() % 3 == 0) {
            showFront(level, camera);
        }
        float strength = intensity(level, camera.x, camera.y, camera.z);
        // Splashes are also visible from outside, unlike the listener-local rain sound.
        for (int index = 0; index < 20; index++) {
            int x = Mth.floor(camera.x) + level.random.nextInt(25) - 12;
            int z = Mth.floor(camera.z) + level.random.nextInt(25) - 12;
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (Math.abs(y - camera.y) < 12
                    && level.random.nextFloat() < intensity(level, x + 0.5, y + 0.1, z + 0.5)) {
                level.addParticle(ParticleTypes.RAIN, x + level.random.nextDouble(), y + 0.1D,
                        z + level.random.nextDouble(), 0, 0, 0);
            }
        }
        if (strength > 0.1F && level.getGameTime() % 30 == 0) {
            boolean sheltered = !level.canSeeSky(new BlockPos(camera));
            level.playLocalSound(camera.x, camera.y, camera.z,
                    sheltered ? SoundEvents.WEATHER_RAIN_ABOVE : SoundEvents.WEATHER_RAIN,
                    SoundSource.WEATHER, strength * (sheltered ? 0.2F : 0.4F), sheltered ? 0.5F : 1.0F, false);
        }
    }

    /** Samples only the nearby arc of the storm front, even when viewed from outside. */
    private static void showFront(ClientLevel level, Vec3 camera) {
        long now = level.getGameTime();
        int remaining = 80;
        for (var arena : arenas) {
            double radius = arena.radiusAt(now);
            Vec3 center = Vec3.atBottomCenterOf(arena.center());
            double dx = camera.x - center.x;
            double dz = camera.z - center.z;
            double radial = Math.hypot(dx, dz);
            if (radius < 0.25 || Math.abs(radial - radius) > FRONT_VISIBILITY) {
                continue;
            }
            // A short visible arc keeps the marker density consistent on large arenas.
            double arc = radius <= FRONT_VISIBILITY - radial ? Math.PI : Math.min(Math.PI, FRONT_VISIBILITY / radius);
            double bearing = Math.atan2(dz, dx);
            int samples = Math.min(64, Math.max(8, (int) Math.ceil(arc * radius)));
            boolean building = arena.isBuilding(now);
            boolean draining = arena.isDraining();
            float opacity = arena.opacityAt(now);
            for (int index = 0; index < samples && remaining > 0; index++) {
                if (draining && level.random.nextFloat() >= opacity) {
                    continue;
                }
                double angle = bearing + ((index + level.random.nextDouble()) / samples * 2 - 1) * arc;
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;
                double ground = LightningRingSurface.find(level, x, z, center.y, arena.minimumY(),
                        arena.maximumY() - 1);
                double water = findFrontWaterSurface(level, arena, x, z, ground);
                boolean overWater = Double.isFinite(water);
                double y = overWater ? water : ground;
                if (!Double.isFinite(y) || camera.distanceToSqr(x, y, z) > FRONT_VISIBILITY * FRONT_VISIBILITY) {
                    continue;
                }
                double height = building ? level.random.nextDouble() * 3 : level.random.nextDouble() * 1.8;
                if (overWater) {
                    level.addParticle(ParticleTypes.SPLASH, true, x, Math.min(y + 0.05, arena.maximumY() - 0.01),
                            z, 0.15, 0.08, 0.07);
                }
                double windY = Math.min(y + 0.3 + height, arena.maximumY() - 0.01);
                // Wisps follow the retreating ring toward the center during teardown.
                double windX = draining ? -Math.cos(angle) * 0.22 : 0.22;
                double windZ = draining ? -Math.sin(angle) * 0.22 : 0.10;
                level.addParticle(ParticleTypes.CLOUD, true, x, windY, z, windX, 0.02, windZ);
                if (index % 4 == 0) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, true, x,
                            Math.min(y + 0.18, arena.maximumY() - 0.01), z, 0, 0.02, 0);
                }
                remaining--;
            }
        }
    }

    /** Uses exposed fluid height so deep or flowing water can carry the front instead of its seabed. */
    private static double findFrontWaterSurface(ClientLevel level, LightningWeatherPacket.Arena arena,
            double x, double z, double ground) {
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        if (!level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
            return Double.NaN;
        }
        int minY = Double.isFinite(ground) ? Math.max(arena.minimumY(), Mth.floor(ground)) : arena.minimumY();
        int maxY = Math.min(arena.maximumY() - 1, level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ) - 1);
        var pos = new BlockPos.MutableBlockPos(blockX, maxY, blockZ);
        for (int y = maxY; y >= minY; y--) {
            pos.setY(y);
            var fluid = level.getFluidState(pos);
            if (!fluid.is(FluidTags.WATER)) {
                continue;
            }
            BlockPos above = pos.above();
            if (level.getFluidState(above).is(FluidTags.WATER)
                    || level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN)) {
                continue;
            }
            double surface = y + fluid.getHeight(level, pos);
            if (arena.containsY(surface) && (!Double.isFinite(ground) || surface >= ground)) {
                return surface;
            }
        }
        return Double.NaN;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        arenas = List.of();
        snapshotLevel = null;
    }
}
