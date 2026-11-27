package de.codemorra.sheeper.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.client.renderer.LightningStrikeRenderer;
import de.codemorra.sheeper.client.renderer.LightningTravelArcRenderer;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.entity.LightningSheeperEntity.TravelArc;
import de.codemorra.sheeper.entity.LightningStrikeEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
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
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/** Selects the world pass that owns the storm's translucent geometry. */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LightningWorldRender {
    private static ShaderInstance copyDepthShader;
    private static ShaderInstance glowShader;
    private static Frame pendingFrame;
    private static int framebuffer;
    private static int depthBuffer;
    private static int width;
    private static int height;
    private static boolean shaderRainRendered;

    private record Frame(ClientLevel level, Vec3 camera, float partialTick, Matrix4f projection, Matrix4f view,
            int sceneDepth, List<LightningStrikeEffect> strikes, List<BossEffect> bosses, boolean fallbackRain) {
    }

    private record BossEffect(LightningSheeperEntity boss, @Nullable TravelArc arc) {
    }

    private LightningWorldRender() {
    }

    /** Receives the replacement shader after a resource reload. */
    static void setCopyDepthShader(ShaderInstance shader) {
        copyDepthShader = shader;
    }

    /** Receives the additive lightning shader after a resource reload. */
    static void setGlowShader(ShaderInstance shader) {
        glowShader = shader;
    }

    static ShaderInstance glowShader() {
        return glowShader == null ? GameRenderer.getPositionColorShader() : glowShader;
    }

    /** Selects the same rendering route for weather and entity lightning. */
    public static boolean usesOptiFinePass() {
        return copyDepthShader != null && OptiFineShaderDepth.isActive();
    }

    @SubscribeEvent
    public static void renderWeather(RenderLevelStageEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        if (!usesOptiFinePass()) {
            drawEffects(level, camera, partialTick, false, true);
        } else if (!shaderRainRendered) {
            shaderRainRendered = LightningWeatherVisuals.drawShaderRain(level, camera, partialTick);
        }
    }

    /**
     * Saves the scene before OptiFine composites it. Drawing into the weather target
     * here can lose custom geometry when the shaderpack replaces the weather pass.
     */
    @SuppressWarnings("removal")
    @SubscribeEvent
    public static void captureOptiFineFrame(RenderLevelLastEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (!usesOptiFinePass() || level == null) {
            return;
        }
        int sceneDepth = OptiFineShaderDepth.opaqueSceneTexture();
        if (sceneDepth == 0) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float partialTick = event.getPartialTick();
        var strikes = new ArrayList<LightningStrikeEffect>();
        var bosses = new ArrayList<BossEffect>();
        for (var entity : level.entitiesForRendering()) {
            if (entity.isRemoved()) {
                continue;
            }
            if (entity instanceof LightningStrikeEffect strike && strike.shouldRender(camera.x, camera.y, camera.z)) {
                strikes.add(strike);
            } else if (entity instanceof LightningSheeperEntity boss) {
                TravelArc arc = boss.getTravelArc();
                if (LightningTravelArcRenderer.hasEffect(boss, arc, partialTick)) {
                    bosses.add(new BossEffect(boss, arc));
                }
            }
        }
        if (hasEffects(level) || !strikes.isEmpty() || !bosses.isEmpty()) {
            pendingFrame = new Frame(level, camera, partialTick, event.getProjectionMatrix().copy(),
                    event.getPoseStack().last().pose().copy(), sceneDepth, strikes, bosses, !shaderRainRendered);
        }
    }

    /** Draws after the arena fog pass and before HUD overlays. */
    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    public static void beforeHud(RenderGuiEvent.Pre event) {
        drawPendingFrame(Minecraft.getInstance());
    }

    /** Keeps the world pass visible when F1 prevents Forge from posting the HUD event. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            shaderRainRendered = false;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui && minecraft.screen == null) {
            drawPendingFrame(minecraft);
        }
        pendingFrame = null;
        if (minecraft.level == null || !OptiFineShaderDepth.isActive()) {
            releaseFramebuffer();
        }
    }

    private static boolean hasEffects(ClientLevel level) {
        return LightningWeatherVisuals.hasEffects(level) || LightningTornadoVisuals.hasEffects(level)
                || LightningStrikeMarkerVisuals.hasEffects(level) || LightningWaterChargeVisuals.hasEffects(level)
                || LightningFanVisuals.hasEffects(level) || LightningRingVisuals.hasEffects(level)
                || LightningRodShelterVisuals.hasEffects(level);
    }

    /** Draws the same camera-relative geometry in the vanilla or shaderpack pass. */
    private static void drawEffects(ClientLevel level, Vec3 camera, float partialTick, boolean shaderPack,
            boolean includeRain) {
        LightningWeatherVisuals.draw(level, camera, partialTick, includeRain);
        LightningTornadoVisuals.draw(level, camera, partialTick);
        LightningStrikeMarkerVisuals.draw(level, camera, partialTick);
        LightningWaterChargeVisuals.draw(level, camera, partialTick);
        LightningFanVisuals.draw(level, camera, partialTick);
        LightningRingVisuals.draw(level, camera, partialTick, shaderPack);
        LightningRodShelterVisuals.draw(level, camera, partialTick);
    }

    /**
     * Draws directly into Minecraft's final color texture with a private depth
     * attachment, leaving the world and HUD depth buffers untouched.
     */
    private static void drawPendingFrame(Minecraft minecraft) {
        Frame frame = pendingFrame;
        pendingFrame = null;
        if (frame == null || frame.level() != minecraft.level || copyDepthShader == null) {
            return;
        }
        RenderTarget main = minecraft.getMainRenderTarget();
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        var previousShader = RenderSystem.getShader();
        float[] previousColor = RenderSystem.getShaderColor().clone();
        int previousTexture = RenderSystem.getShaderTexture(0);
        int previousLightmap = RenderSystem.getShaderTexture(2);
        var view = RenderSystem.getModelViewStack();
        Matrix4f previousProjection = RenderSystem.getProjectionMatrix();
        view.pushPose();
        try {
            if (!bindFramebuffer(main)) {
                return;
            }
            RenderSystem.viewport(0, 0, main.viewWidth, main.viewHeight);
            copySceneDepth(frame.sceneDepth());
            RenderSystem.setProjectionMatrix(frame.projection());
            view.setIdentity();
            view.mulPoseMatrix(frame.view());
            RenderSystem.applyModelViewMatrix();
            drawEffects(frame.level(), frame.camera(), frame.partialTick(), true, frame.fallbackRain());
            drawEntityLightning(frame);
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection);
            RenderSystem.depthFunc(previousDepthFunc);
            RenderSystem.depthMask(previousDepthMask);
            if (previousDepthTest) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (previousBlend) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (previousCull) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            RenderSystem.setShaderTexture(0, previousTexture);
            RenderSystem.setShaderTexture(2, previousLightmap);
        }
    }

    /** Flushes the entity lightning together using the same geometry as the regular entity renderers. */
    private static void drawEntityLightning(Frame frame) {
        if (frame.strikes().isEmpty() && frame.bosses().isEmpty()) {
            return;
        }
        RenderSystem.setShader(LightningWorldRender::glowShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        // Both renderers emit each ribbon in both directions. Cull the reverse face to avoid blending twice.
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        var tesselator = Tesselator.getInstance();
        var buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var pose = new PoseStack();
        for (var strike : frame.strikes()) {
            Vec3 offset = strike.position().subtract(frame.camera());
            pose.pushPose();
            pose.translate(offset.x, offset.y, offset.z);
            LightningStrikeRenderer.drawStrike(strike, frame.partialTick(), pose.last().pose(), buffer);
            pose.popPose();
        }
        for (BossEffect effect : frame.bosses()) {
            var boss = effect.boss();
            Vec3 offset = boss.getPosition(frame.partialTick()).subtract(frame.camera());
            pose.pushPose();
            pose.translate(offset.x, offset.y, offset.z);
            LightningTravelArcRenderer.draw(boss, effect.arc(), frame.partialTick(), pose.last().pose(), buffer);
            pose.popPose();
        }
        tesselator.end();
        RenderSystem.defaultBlendFunc();
    }

    /** Shares the main color attachment and allocates private depth at the output size. */
    private static boolean bindFramebuffer(RenderTarget main) {
        if (framebuffer == 0) {
            framebuffer = GL30.glGenFramebuffers();
            depthBuffer = GL30.glGenRenderbuffers();
        }
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        if (width != main.width || height != main.height) {
            width = main.width;
            height = main.height;
            int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthBuffer);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width, height);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
            GL30.glFramebufferRenderbuffer(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, depthBuffer);
        }
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                main.getColorTextureId(), 0);
        return GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    /** Samples depth by UV so a shaderpack can use a different internal render resolution. */
    private static void copySceneDepth(int sceneDepth) {
        copyDepthShader.setSampler("SceneDepth", sceneDepth);
        RenderSystem.setShader(() -> copyDepthShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        try {
            var buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.vertex(-1, -1, 0).uv(0, 0).endVertex();
            buffer.vertex(1, -1, 0).uv(1, 0).endVertex();
            buffer.vertex(1, 1, 0).uv(1, 1).endVertex();
            buffer.vertex(-1, 1, 0).uv(0, 1).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
    }

    private static void releaseFramebuffer() {
        if (framebuffer == 0) {
            return;
        }
        GL30.glDeleteFramebuffers(framebuffer);
        GL30.glDeleteRenderbuffers(depthBuffer);
        framebuffer = 0;
        depthBuffer = 0;
        width = 0;
        height = 0;
    }
}
