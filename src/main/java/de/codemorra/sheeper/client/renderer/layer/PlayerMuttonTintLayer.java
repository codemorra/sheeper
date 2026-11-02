package de.codemorra.sheeper.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.codemorra.sheeper.client.MuttonEffectVisuals;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Adds an emissive red tint to the existing skin without coloring armor.
 * Skin parts are not hidden based on equipment, preserving transparent armor
 * gaps.
 */
public final class PlayerMuttonTintLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /**
     * Uses the parent's posed regular or slim player model and existing skin parts.
     *
     * @param renderer the player renderer receiving this layer
     */
    public PlayerMuttonTintLayer(PlayerRenderer renderer) {
        super(renderer);
    }

    /**
     * Re-renders visible skin texels with emissive red translucency. Normal depth
     * testing preserves opaque armor occlusion while transparent gaps reveal skin.
     * Emissivity affects the skin's appearance without lighting surrounding blocks.
     */
    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
            AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // Skin and viewport share the same curve and cooked/raw strength ratio.
        float intensity = MuttonEffectVisuals.getIntensity(player, partialTick);
        if (player.isInvisible() || intensity <= 0.001F) {
            return;
        }

        VertexConsumer vertices = buffers.getBuffer(
                RenderType.entityTranslucentEmissive(player.getSkinTextureLocation()));
        getParentModel().renderToBuffer(poseStack, vertices, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, 1.0F, 0.12F, 0.12F, intensity);
    }
}
