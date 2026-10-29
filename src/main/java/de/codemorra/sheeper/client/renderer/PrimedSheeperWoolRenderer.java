package de.codemorra.sheeper.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import de.codemorra.sheeper.entity.PrimedSheeperWool;
import de.codemorra.sheeper.registry.ModBlocks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Renders primed Sheeper wool with its fleece color and vanilla TNT effects.
 */
public class PrimedSheeperWoolRenderer
        extends EntityRenderer<PrimedSheeperWool> {

    private final BlockRenderDispatcher blockRenderer;

    public PrimedSheeperWoolRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.5F;
        blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(
            PrimedSheeperWool entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // Scale around the block center, matching the vanilla TNT renderer.
        poseStack.translate(0.0, 0.5, 0.0);

        float remainingFuse = entity.getFuse() - partialTick + 1.0F;

        if (remainingFuse < 10.0F) {
            float progress = Mth.clamp(
                    1.0F - remainingFuse / 10.0F, 0.0F, 1.0F);
            // The vanilla fourth-power curve concentrates swelling near detonation.
            progress *= progress;
            progress *= progress;

            float scale = 1.0F + progress * 0.3F;
            poseStack.scale(scale, scale, scale);
        }

        poseStack.translate(-0.5, -0.5, -0.5);

        TntMinecartRenderer.renderWhiteSolidBlock(
                blockRenderer,
                ModBlocks.getWool(entity.getColor()).defaultBlockState(),
                poseStack,
                buffer,
                packedLight,
                entity.getFuse() / 5 % 2 == 0);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PrimedSheeperWool entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
