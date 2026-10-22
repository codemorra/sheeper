package de.codemorra.sheeper.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import de.codemorra.sheeper.entity.SheeperEntity;
import de.codemorra.sheeper.client.renderer.layer.SheeperEyesLayer;
import de.codemorra.sheeper.client.renderer.layer.SheeperFuseLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SheepRenderer;
import net.minecraft.world.entity.animal.Sheep;

/**
 * Custom renderer for the Sheeper entity, extending the vanilla SheepRenderer.
 * Applies a visual fuse scaling effect based on the Sheeper's fuse progress.
 */
public class SheeperRenderer extends SheepRenderer {
    public SheeperRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayer(new SheeperFuseLayer(this, context.getModelSet()));
        addLayer(new SheeperEyesLayer(this));
    }

    /**
     * Scales the Sheeper entity based on its visual fuse progress.
     *
     * @param sheep       the sheep entity to scale
     * @param poseStack   the pose stack for rendering transformations
     * @param partialTick the partial tick time for interpolation
     */
    @Override
    public void scale(Sheep sheep, PoseStack poseStack, float partialTick) {
        super.scale(sheep, poseStack, partialTick);

        if (sheep instanceof SheeperEntity sheeper) {
            float progress = sheeper.getVisualFuseProgress(partialTick);
            float horizontalScale = 1.0f + progress * 0.3f;
            float verticalScale = 1.0f + progress * 0.3f;

            poseStack.scale(horizontalScale, verticalScale, horizontalScale);
        }
    }
}
