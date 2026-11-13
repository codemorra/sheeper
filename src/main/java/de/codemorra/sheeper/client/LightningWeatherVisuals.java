package de.codemorra.sheeper.client;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.network.LightningWeatherPacket;
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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Bounded rain columns and local sky darkening, independent of boss render
 * distance.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LightningWeatherVisuals {
    private static final ResourceLocation RAIN = ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/environment/rain.png");
    private static ClientLevel snapshotLevel;
    private static List<LightningWeatherPacket.Arena> arenas = List.of();
    private static long lastSnapshot;
    private static BlockPos rainColumn = BlockPos.ZERO;

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
        return intensity(level, pos.getX() + 0.5D, pos.getZ() + 0.5D, 0);
    }

    private static float intensity(Level level, double x, double z, float partialTick) {
        if (level != snapshotLevel || level.getGameTime() - lastSnapshot > 60) {
            return 0;
        }
        float value = 0;
        for (var arena : arenas) {
            if (arena.contains(x, z)) {
                value = Math.max(value, arena.intensity(level.getGameTime() + partialTick));
            }
        }
        return value;
    }

    /** Called only at vanilla's client sky/cloud rain and thunder calculations. */
    public static float skyWeather(float vanilla) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return vanilla;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        return Math.max(vanilla, intensity(mc.level, camera.x, camera.z, mc.getFrameTime()));
    }

    /**
     * Vanilla precipitation keeps its normal behavior outside local storm columns.
     */
    public static BlockPos rainColumn(BlockPos pos) {
        rainColumn = pos.immutable();
        return pos;
    }

    public static Biome.Precipitation precipitation(Biome biome) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && intensity(level, rainColumn) > 0.2F
                ? Biome.Precipitation.RAIN
                : biome.getPrecipitation();
    }

    public static boolean warmEnough(Biome biome, BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && intensity(level, pos) > 0.2F || biome.warmEnoughToRain(pos);
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Vec3 camera = event.getCamera().getPosition();
            float factor = 1 - intensity(mc.level, camera.x, camera.z, (float) event.getPartialTick()) * 0.55F;
            event.setRed(event.getRed() * factor);
            event.setGreen(event.getGreen() * factor);
            event.setBlue(event.getBlue() * factor);
        }
    }

    @SubscribeEvent
    public static void rain(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || level == null
                || level != snapshotLevel || arenas.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        int range = Minecraft.useFancyGraphics() ? 20 : 10;
        float natural = level.getRainLevel(event.getPartialTick());
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = event.getPoseStack().last().pose();
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, RAIN);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        for (int x = Mth.floor(camera.x) - range; x <= Mth.floor(camera.x) + range; x++) {
            for (int z = Mth.floor(camera.z) - range; z <= Mth.floor(camera.z) + range; z++) {
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                float strength = intensity(level, x + 0.5D, z + 0.5D, event.getPartialTick());
                if (strength <= 0) {
                    continue;
                }
                BlockPos column = new BlockPos(x, camera.y, z);
                Biome biome = level.getBiome(column).value();
                if (natural > 0 && strength > 0.2F
                        || biome.getPrecipitation() == Biome.Precipitation.RAIN && biome.warmEnoughToRain(column)) {
                    strength = Math.max(0, strength - natural);
                }
                if (strength <= 0) {
                    continue;
                }
                int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                double bottom = Math.max(floor, camera.y - 12);
                double top = camera.y + 14;
                if (bottom >= top) {
                    continue;
                }
                double dx = x + 0.5D - camera.x;
                double dz = z + 0.5D - camera.z;
                double distance = Math.sqrt(dx * dx + dz * dz);
                float alpha = strength * 0.65F * (float) Math.max(0, 1 - distance * distance / (range * range));
                double wx = distance < 0.01D ? 0.5D : -dz / distance * 0.5D;
                double wz = distance < 0.01D ? 0 : dx / distance * 0.5D;
                // With world-height UVs, increasing the V offset moves streaks downward.
                float scroll = (level.getGameTime() + event.getPartialTick() + (x * 31 + z * 17 & 31)) * 0.12F;
                int light = LevelRenderer.getLightColor(level, new BlockPos(x, Math.max(floor, camera.y), z));
                vertex(buffer, matrix, dx - wx, top - camera.y, dz - wz, 0, (float) top * 0.25F + scroll, alpha, light);
                vertex(buffer, matrix, dx + wx, top - camera.y, dz + wz, 1, (float) top * 0.25F + scroll, alpha, light);
                vertex(buffer, matrix, dx + wx, bottom - camera.y, dz + wz, 1, (float) bottom * 0.25F + scroll, alpha,
                        light);
                vertex(buffer, matrix, dx - wx, bottom - camera.y, dz - wz, 0, (float) bottom * 0.25F + scroll, alpha,
                        light);
            }
        }
        Tesselator.getInstance().end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        mc.gameRenderer.lightTexture().turnOffLightLayer();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, double x, double y, double z,
            float u, float v, float alpha, int light) {
        buffer.vertex(matrix, (float) x, (float) y, (float) z).uv(u, v).color(1, 1, 1, alpha).uv2(light).endVertex();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        ClientLevel level = mc.level;
        if (level != snapshotLevel || arenas.isEmpty()) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        float strength = intensity(level, camera.x, camera.z, 0);
        // Splashes are also visible from outside, unlike the listener-local rain sound.
        for (int index = 0; index < 20; index++) {
            int x = Mth.floor(camera.x) + level.random.nextInt(25) - 12;
            int z = Mth.floor(camera.z) + level.random.nextInt(25) - 12;
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)
                    || level.random.nextFloat() >= intensity(level, x + 0.5D, z + 0.5D, 0)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (Math.abs(y - camera.y) < 12) {
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
        snapshotLevel = null;
    }
}
