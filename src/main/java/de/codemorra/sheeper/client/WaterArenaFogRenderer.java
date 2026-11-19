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
import de.codemorra.sheeper.config.WaterSheeperConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelLastEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Integrates water fog along each visible ray's intersection with the arena.
 * Scene depth keeps foreground terrain in front of the fog, including when
 * viewing the water volume from outside or along its upper boundary.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WaterArenaFogRenderer {
    private static ShaderInstance shader;
    private static RenderTarget depthCopy;
    private static boolean capturedDepth;

    private WaterArenaFogRenderer() {
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
        if (minecraft.level == null || shader == null
                || WaterSheeperConfig.WATER_FOG_INTENSITY.get() <= 0
                || WaterArenaVisuals.activeVolumes(minecraft.level).isEmpty()
                || !WaterArenaVisuals.canBlendFog(event.getCamera())) {
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
     * The legacy event is intentional for this Minecraft version: it runs after
     * Fabulous has composited every world layer, and before the hand and HUD.
     */
    @SuppressWarnings("removal")
    @SubscribeEvent
    public static void render(RenderLevelLastEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!capturedDepth || shader == null || minecraft.level == null) {
            return;
        }
        capturedDepth = false;
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
        float originScale = 1.0F / eye.w();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        shader.getUniform("InverseViewProjection").set(inverse);
        shader.getUniform("RayOrigin").set(eye.x() * originScale, eye.y() * originScale, eye.z() * originScale);
        shader.getUniform("WaterColor").set(0.12F, 0.38F, 0.52F);
        // Intensity scales attenuation while preserving the existing base-distance tuning.
        shader.getUniform("WaterDensity").set((float) (Math.log(20) * WaterSheeperConfig.WATER_FOG_INTENSITY.get()
                / WaterSheeperConfig.WATER_FOG_DISTANCE.get()));
        shader.setSampler("SceneDepth", depthCopy.getDepthTextureId());
        ShaderInstance previous = RenderSystem.getShader();
        minecraft.getMainRenderTarget().bindWrite(false);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);
        try {
            double renderDistance = minecraft.options.getEffectiveRenderDistance() * 16.0D;
            for (var bounds : WaterArenaVisuals.activeVolumes(minecraft.level)) {
                // Camera-relative coordinates retain precision far from world origin.
                double x = bounds.center().getX() + 0.5D - camera.x;
                double z = bounds.center().getZ() + 0.5D - camera.z;
                if (Math.hypot(x, z) - bounds.radius() > renderDistance) {
                    continue;
                }
                shader.getUniform("WaterCylinder").set((float) x, (float) z, (float) bounds.radius());
                shader.getUniform("WaterHeights").set((float) (bounds.minimumY() - camera.y),
                        (float) (bounds.maximumY() + 1.0D - camera.y));
                var vertices = Tesselator.getInstance().getBuilder();
                vertices.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                vertices.vertex(-1, -1, 0).uv(0, 0).endVertex();
                vertices.vertex(1, -1, 0).uv(1, 0).endVertex();
                vertices.vertex(1, 1, 0).uv(1, 1).endVertex();
                vertices.vertex(-1, 1, 0).uv(0, 1).endVertex();
                BufferUploader.drawWithShader(vertices.end());
            }
        } finally {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.setShader(() -> previous);
        }
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
