package de.codemorra.sheeper.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Draws a visible red viewport tint with stronger edges, underneath the HUD.
 * Gradients are generated as geometry, so no extra bitmap or shader is needed.
 */
public final class MuttonScreenOverlay {
    private MuttonScreenOverlay() {
    }

    /**
     * Renders only for the affected player's own camera, beneath later HUD layers.
     * The shared visual intensity supplies both raw and cooked pulse timing.
     *
     * @param gui         the Forge HUD invoking this overlay
     * @param poseStack   the current GUI transformations
     * @param partialTick interpolation between client ticks
     * @param width       the scaled viewport width
     * @param height      the scaled viewport height
     */
    public static void render(ForgeGui gui, PoseStack poseStack, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getCameraEntity() != minecraft.player) {
            return;
        }
        float intensity = MuttonEffectVisuals.getScreenIntensity(minecraft.player, partialTick);
        if (intensity <= 0.001F) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float base = intensity * 0.30F;
        float edge = intensity * 0.40F;
        // A full-screen wash keeps the center visibly red. Overlapping edge
        // gradients strengthen the corners without covering HUD text or icons.
        quad(buffer, matrix, 0, 0, width, height, base, base, base, base);
        quad(buffer, matrix, 0, 0, width, height * 0.3F, edge, 0, 0, edge);
        quad(buffer, matrix, 0, height * 0.7F, width, height, 0, edge, edge, 0);
        quad(buffer, matrix, 0, 0, width * 0.2F, height, edge, edge, 0, 0);
        quad(buffer, matrix, width * 0.8F, 0, width, height, 0, 0, edge, edge);
        BufferUploader.drawWithShader(buffer.end());

        // Restore GUI rendering defaults for the overlays that follow this one.
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
    }

    /**
     * Appends a red quad with independently interpolated corner opacity.
     * Alpha arguments follow top-left, bottom-left, bottom-right, top-right order.
     */
    private static void quad(BufferBuilder buffer, Matrix4f matrix,
            float left, float top, float right, float bottom,
            float topLeft, float bottomLeft, float bottomRight, float topRight) {
        buffer.vertex(matrix, left, top, -90.0F).color(0.8F, 0.0F, 0.0F, topLeft).endVertex();
        buffer.vertex(matrix, left, bottom, -90.0F).color(0.8F, 0.0F, 0.0F, bottomLeft).endVertex();
        buffer.vertex(matrix, right, bottom, -90.0F).color(0.8F, 0.0F, 0.0F, bottomRight).endVertex();
        buffer.vertex(matrix, right, top, -90.0F).color(0.8F, 0.0F, 0.0F, topRight).endVertex();
    }
}
