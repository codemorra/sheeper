package de.codemorra.sheeper.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.SheeperEntity;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Sheep;

/**
 * Renders the glowing eyes of the Sheeper entity when it is in a warning state.
 */
public class SheeperEyesLayer extends RenderLayer<Sheep, SheepModel<Sheep>> {

    private static final RenderType EYES = RenderType.eyes(
            ResourceLocation.fromNamespaceAndPath(
                    SheeperMod.MODID, "textures/entity/sheeper_eyes.png"));

    /**
     * Constructs a new SheeperEyesLayer.
     *
     * @param parent the parent render layer
     */
    public SheeperEyesLayer(RenderLayerParent<Sheep, SheepModel<Sheep>> parent) {
        super(parent);
    }

    /**
     * Renders the glowing eyes of the Sheeper entity.
     *
     * @param poseStack       the pose stack for rendering transformations
     * @param buffer          the buffer source for vertex consumers
     * @param packedLight     the packed light value
     * @param sheep           the sheep entity to render
     * @param limbSwing       the limb swing animation parameter
     * @param limbSwingAmount the limb swing amount animation parameter
     * @param partialTicks    the partial tick time for interpolation
     * @param ageInTicks      the age in ticks for animation
     * @param netHeadYaw      the head yaw rotation
     * @param headPitch       the head pitch rotation
     */
    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            Sheep sheep,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {

        // Skip rendering if the entity is not a Sheeper or is invisible.
        if (!(sheep instanceof SheeperEntity sheeper) || sheep.isInvisible()) {
            return;
        }

        float intensity = sheeper.getVisualWarningIntensity(partialTicks);

        if (intensity <= 0.0F) {
            return;
        }

        VertexConsumer vertices = buffer.getBuffer(EYES);

        // Set the color intensity for the glowing eyes based on the warning intensity.
        getParentModel().renderToBuffer(
                poseStack, vertices, 15728640, OverlayTexture.NO_OVERLAY,
                intensity, intensity, intensity, 1.0F);
    }
}
