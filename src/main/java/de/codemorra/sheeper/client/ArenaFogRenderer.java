package de.codemorra.sheeper.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector4f;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.WaterSheeperConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelLastEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Integrates fog along each visible ray's intersection with an arena.
 * Scene depth keeps foreground terrain in front of the fog, including when
 * viewing the volume from outside or along its upper boundary.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ArenaFogRenderer {
    private static ShaderInstance shader;
    private static RenderTarget depthCopy;
    private static boolean capturedDepth;
    private static FogFrame pendingOptiFineFrame;

    private record FogFrame(ClientLevel level, int depthTexture, Matrix4f inverse,
            Vector4f eye, Vec3 camera, float partialTick) {
    }

    private ArenaFogRenderer() {
    }

    /** The resource manager owns shader disposal and supplies a replacement after reloads. */
    static void setShader(ShaderInstance loaded) {
        shader = loaded;
    }

    /**
     * Copies opaque scene depth before Fabulous transparency processing can clear
     * the main depth attachment. The copy also avoids reading an attached texture
     * while drawing back into the same framebuffer.
     */
    @SubscribeEvent
    public static void captureDepth(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        capturedDepth = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (!canRender(minecraft, event.getCamera()) || OptiFineShaderDepth.isActive()) {
            return;
        }
        RenderTarget main = minecraft.getMainRenderTarget();
        if (!main.useDepth) {
            return;
        }
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            if (depthCopy == null || depthCopy.width != main.width || depthCopy.height != main.height
                    || depthCopy.isStencilEnabled() != main.isStencilEnabled()) {
                releaseDepth();
                depthCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
                if (main.isStencilEnabled()) {
                    depthCopy.enableStencil();
                }
            }
            depthCopy.copyDepthFrom(main);
            capturedDepth = true;
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }

    /**
     * Runs after the vanilla world layers and captures the world camera for
     * OptiFine, whose final composite runs later in GameRenderer.
     */
    @SuppressWarnings("removal")
    @SubscribeEvent
    public static void render(RenderLevelLastEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (shader == null || minecraft.level == null) {
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        boolean optiFine = OptiFineShaderDepth.isActive();
        if (optiFine ? !canRender(minecraft, camera) : !capturedDepth) {
            return;
        }
        capturedDepth = false;
        int depthTexture = optiFine ? OptiFineShaderDepth.sceneTexture() : depthCopy.getDepthTextureId();
        if (depthTexture == 0) {
            return;
        }
        Matrix4f inverse = event.getProjectionMatrix().copy();
        inverse.multiply(event.getPoseStack().last().pose());
        if (!inverse.invert()) {
            return;
        }
        // The perspective center maps to clip (0, 0, z, 0). Inverting it
        // includes view bobbing and hurt transforms folded into the projection.
        Vector4f eye = new Vector4f(0, 0, 1, 0);
        eye.transform(inverse);
        if (Math.abs(eye.w()) < 1.0E-6F) {
            return;
        }
        eye.mul(1.0F / eye.w());
        var frame = new FogFrame(minecraft.level, depthTexture, inverse, eye, camera.getPosition(),
                event.getPartialTick());
        if (optiFine) {
            pendingOptiFineFrame = frame;
        } else {
            draw(minecraft, frame);
        }
    }

    /** Blends over OptiFine's final composite while the HUD is still clear. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void beforeHud(RenderGuiEvent.Pre event) {
        drawPendingOptiFineFrame(Minecraft.getInstance());
    }

    /** With F1 active Forge skips the HUD event, but the world is still ready before the final screen blit. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && minecraft.options.hideGui && minecraft.screen == null) {
            drawPendingOptiFineFrame(minecraft);
        }
        pendingOptiFineFrame = null;
    }

    private static void drawPendingOptiFineFrame(Minecraft minecraft) {
        FogFrame frame = pendingOptiFineFrame;
        pendingOptiFineFrame = null;
        if (frame != null && shader != null && minecraft.level == frame.level()) {
            draw(minecraft, frame);
        }
    }

    private static boolean canRender(Minecraft minecraft, Camera camera) {
        ClientLevel level = minecraft.level;
        return level != null && shader != null && WaterArenaVisuals.canBlendFog(camera)
                && (LightningSheeperConfig.LIGHTNING_FOG_INTENSITY.get() > 0
                        && !LightningWeatherVisuals.visibleArenas(level).isEmpty()
                        || WaterSheeperConfig.WATER_FOG_INTENSITY.get() > 0
                                && !WaterArenaVisuals.activeVolumes(level).isEmpty());
    }

    private static void draw(Minecraft minecraft, FogFrame frame) {
        shader.getUniform("InverseViewProjection").set(frame.inverse());
        shader.getUniform("RayOrigin").set(frame.eye().x(), frame.eye().y(), frame.eye().z());
        shader.setSampler("SceneDepth", frame.depthTexture());
        ShaderInstance previous = RenderSystem.getShader();
        minecraft.getMainRenderTarget().bindWrite(false);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);
        try {
            double renderDistance = minecraft.options.getEffectiveRenderDistance() * 16.0D;
            double time = frame.level().getGameTime() + (double) frame.partialTick();
            double stormIntensity = LightningSheeperConfig.LIGHTNING_FOG_INTENSITY.get();
            if (stormIntensity > 0) {
                shader.getUniform("FogDensity").set((float) (0.031 * stormIntensity));
                for (var arena : LightningWeatherVisuals.visibleArenas(frame.level())) {
                    // Keep distant lightning readable through the storm, and fade as the arena drains.
                    float tint = arena.rodDarknessAt(time);
                    float rodColor = LightningWeatherVisuals.ROD_FOG_COLOR;
                    shader.getUniform("FogColor").set(Mth.lerp(tint, 0.08F, rodColor),
                            Mth.lerp(tint, 0.10F, rodColor), Mth.lerp(tint, 0.14F, rodColor),
                            0.72F * arena.opacityAt(time));
                    drawVolume(frame.camera(), renderDistance, arena.center(), arena.radiusAt(time), arena.minimumY(),
                            arena.maximumY());
                }
            }
            double waterIntensity = WaterSheeperConfig.WATER_FOG_INTENSITY.get();
            if (waterIntensity > 0) {
                shader.getUniform("FogColor").set(0.12F, 0.38F, 0.52F, 1.0F);
                shader.getUniform("FogDensity").set((float) (Math.log(20) * waterIntensity
                        / WaterSheeperConfig.WATER_FOG_DISTANCE.get()));
                for (var bounds : WaterArenaVisuals.renderVolumes(frame.level(), frame.partialTick())) {
                    drawVolume(frame.camera(), renderDistance, bounds.center(), bounds.radius(), bounds.minimumY(),
                            bounds.maximumY() + 1);
                }
            }
        } finally {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.setShader(() -> previous);
        }
    }

    /** Projects one bounded fog cylinder using camera-relative coordinates to retain world precision. */
    private static void drawVolume(Vec3 camera, double renderDistance, BlockPos center, double radius,
            double minimumY, double ceilingY) {
        double x = center.getX() + 0.5D - camera.x;
        double z = center.getZ() + 0.5D - camera.z;
        if (radius <= 0 || Math.hypot(x, z) - radius > renderDistance) {
            return;
        }
        shader.getUniform("FogCylinder").set((float) x, (float) z, (float) radius);
        shader.getUniform("FogHeights").set((float) (minimumY - camera.y), (float) (ceilingY - camera.y));
        var vertices = Tesselator.getInstance().getBuilder();
        vertices.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        vertices.vertex(-1, -1, 0).uv(0, 0).endVertex();
        vertices.vertex(1, -1, 0).uv(1, 0).endVertex();
        vertices.vertex(1, 1, 0).uv(1, 1).endVertex();
        vertices.vertex(-1, 1, 0).uv(0, 1).endVertex();
        BufferUploader.drawWithShader(vertices.end());
    }

    /** Releases the temporary GPU target when leaving a world. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level == null && depthCopy != null) {
            releaseDepth();
        }
    }

    private static void releaseDepth() {
        if (depthCopy != null) {
            depthCopy.destroyBuffers();
            depthCopy = null;
        }
        capturedDepth = false;
    }
}
