package de.codemorra.sheeper.client;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.network.LightningWeatherPacket;
import de.codemorra.sheeper.util.ResourceLocations;
import de.codemorra.sheeper.world.LightningArenaBoundaryDrag;
import de.codemorra.sheeper.world.LightningWindField;
import de.codemorra.sheeper.world.LightningWindGust;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    private static final int CLOUD_SEGMENTS = 48;
    private static final int CLOUD_RINGS = 10;
    private static final int RAIN_SPACING = 2;
    private static ClientLevel snapshotLevel;
    private static List<LightningWeatherPacket.Arena> arenas = List.of();
    private static final LightningWindLines WIND_LINES = new LightningWindLines();
    private static List<LightningWindField> winds = List.of();
    private static long lastSnapshot;

    private LightningWeatherVisuals() {
    }

    public static void receive(LightningWeatherPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && packet.dimension().equals(level.dimension().location())) {
            if (snapshotLevel != level) {
                WIND_LINES.clear();
            }
            snapshotLevel = level;
            arenas = packet.arenas();
            winds = packet.winds();
            lastSnapshot = level.getGameTime();
            LightningArenaBoundaryDrag.receive(packet.boundarySlowdownWidth(), packet.boundarySlowdownStrength());
        }
    }

    /** @return current storm snapshots for local movement prediction */
    public static List<LightningWeatherPacket.Arena> visibleArenas(Level level) {
        return hasSnapshot(level) ? arenas : List.of();
    }

    /** @return the wind fields shared by rendering and client movement prediction */
    public static List<LightningWindField> windFields(Level level) {
        return hasSnapshot(level) ? winds : List.of();
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

    private static boolean hasSnapshot(Level level) {
        return level == snapshotLevel && !arenas.isEmpty() && level.getGameTime() - lastSnapshot <= 60;
    }

    /** Reduces the lightmap's skylight for a camera in the storm while leaving block light to vanilla. */
    public static float stormSkylight(float vanilla, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        var viewer = mc.getCameraEntity();
        if (level == null || viewer == null || !hasSnapshot(level) || vanilla <= 0.32F
                || level.getSkyFlashTime() > 0) {
            return vanilla;
        }
        Vec3 eye = viewer.getEyePosition(partialTick);
        double time = level.getGameTime() + (double) partialTick;
        float shade = 0;
        for (var arena : arenas) {
            double verticalInset = Math.min(eye.y - arena.minimumY(), arena.maximumY() - eye.y);
            // Blend at the open floor and ceiling as well as at the moving storm front.
            float verticalBlend = (float) Mth.clamp(verticalInset / 2, 0, 1);
            shade = Math.max(shade, arena.intensityAt(time, eye.x, eye.y, eye.z) * verticalBlend);
        }
        return Mth.lerp(shade, vanilla, 0.32F);
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
                WIND_LINES.drawFront(buffer, level, arena, camera, time);
                WIND_LINES.drawBoundary(buffer, level, arena, camera, time);
            }
        }
        for (LightningWindField field : winds) {
            WIND_LINES.draw(buffer, level, field, camera, time);
            for (LightningWindGust gust : field.gusts()) {
                drawGust(buffer, level, field, gust, camera, time);
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

    /** Draws staggered translucent ribbons above the ground. */
    private static void drawGust(BufferBuilder buffer, ClientLevel level, LightningWindField field,
            LightningWindGust gust, Vec3 camera, double time) {
        float opacity = gust.opacityAt(time);
        Vec3 center = gust.centerAt(field, time);
        if (opacity <= 0 || camera.distanceToSqr(center) > 48 * 48) {
            return;
        }
        int ribbons = switch (gust.size()) {
            case MEDIUM -> 3;
            case LARGE -> 4;
        };
        Vec3 direction = field.directionAt(time);
        float crestOpacity = opacity * (0.22F + (ribbons - 2) * 0.08F);
        double separation = gust.wakeLength(time) * 0.75 / (ribbons - 1);
        for (int ribbon = 0; ribbon < ribbons; ribbon++) {
            Vec3 crest = center.subtract(direction.scale(ribbon * separation));
            drawGustRibbon(buffer, level, field, gust, camera, crest, direction, ribbon, crestOpacity, time);
        }
    }

    /** Breaks the ribbon at terrain changes and fades its edges into the rain. */
    private static void drawGustRibbon(BufferBuilder buffer, ClientLevel level, LightningWindField field,
            LightningWindGust gust, Vec3 camera, Vec3 crest, Vec3 direction, int ribbon, float opacity, double time) {
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        double width = gust.width() * (1 - ribbon * 0.13);
        double halfHeight = 0.058 + width * 0.012;
        double baseHeight = 0.36 + ribbon * 0.40;
        Vec3 previous = null;
        float previousAlpha = 0;
        for (int index = 0; index <= 12; index++) {
            double across = index / 6.0 - 1;
            Vec3 sample = crest.add(side.scale(across * width / 2))
                    .add(direction.scale(gust.bend(across) + ribbon * 0.075 * Math.sin(across * 4)));
            double floor = gust.groundAt(level, field, sample);
            double height = floor + baseHeight + 0.14 * Math.cos(across * 4.2 + ribbon * 0.6);
            float intensity = field.arena().intensityAt(time, sample.x, height + halfHeight, sample.z);
            if (!Double.isFinite(height) || intensity <= 0) {
                previous = null;
                continue;
            }
            BlockPos pos = new BlockPos(sample.x, height, sample.z);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                previous = null;
                continue;
            }
            Vec3 point = new Vec3(sample.x, height, sample.z).subtract(camera);
            float alpha = opacity * intensity * (float) ((1 - across * across) * (1 - ribbon * 0.10));
            if (previous != null && Math.abs(previous.y - point.y) < 0.7) {
                gustVertex(buffer, previous, -halfHeight, 0);
                gustVertex(buffer, point, -halfHeight, 0);
                gustVertex(buffer, point, 0, alpha);
                gustVertex(buffer, previous, 0, previousAlpha);
                gustVertex(buffer, previous, 0, previousAlpha);
                gustVertex(buffer, point, 0, alpha);
                gustVertex(buffer, point, halfHeight, 0);
                gustVertex(buffer, previous, halfHeight, 0);
            }
            previous = point;
            previousAlpha = alpha;
        }
    }

    private static void gustVertex(BufferBuilder buffer, Vec3 point, double height, float alpha) {
        buffer.vertex((float) point.x, (float) (point.y + height), (float) point.z)
                .color(0.76F, 0.84F, 0.91F, alpha).endVertex();
    }

    /**
     * Draws a closed, flattened cloud bank. Rings meet around its curved sides
     * so the underside and top stay connected from every viewing height.
     */
    private static void drawClouds(BufferBuilder buffer, LightningWeatherPacket.Arena arena,
            Vec3 camera, double time) {
        double halfHeight = arena.cloudHalfHeight();
        Vec3 center = new Vec3(arena.center().getX() + 0.5, arena.cloudCenterY(),
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
        double variation = LightningWeatherPacket.Arena.cloudVariation(x, z, time);
        double y = center.y + halfHeight * (variation * 0.5 + height * (1 + variation * 0.2));
        float shade = (float) (0.23 + variation * 0.06 + height * 0.05);
        float opacity = (float) (0.56 + variation * 0.09 + Math.abs(height) * 0.14) * stormOpacity;
        buffer.vertex((float) (center.x + x), (float) y, (float) (center.z + z))
                .color(shade * 0.8F, shade * 0.92F, shade * 1.15F, opacity).endVertex();
    }

    /**
     * Distributes rain between the terrain and the cloud bank. Each cell has a fixed
     * offset and orientation to break up straight sightlines through the rain.
     */
    private static void drawRain(BufferBuilder buffer, ClientLevel level,
            LightningWeatherPacket.Arena arena, Vec3 camera, double time) {
        double radius = arena.radiusAt(time);
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
                float strength = arena.columnIntensityAt(time, x, z);
                int blockX = Mth.floor(x);
                int blockZ = Mth.floor(z);
                if (strength <= 0 || !level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
                    continue;
                }
                double top = arena.cloudStrikeY(x, z, time);
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
            WIND_LINES.clear();
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        WIND_LINES.tick(level, arenas, winds, camera);
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

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        arenas = List.of();
        winds = List.of();
        WIND_LINES.clear();
        snapshotLevel = null;
    }
}
