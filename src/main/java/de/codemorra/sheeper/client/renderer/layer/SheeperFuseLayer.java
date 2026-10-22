package de.codemorra.sheeper.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.codemorra.sheeper.entity.SheeperEntity;
import net.minecraft.client.model.SheepFurModel;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Sheep;

/**
 * Renders the fuse effect on the Sheeper entity.
 */
public class SheeperFuseLayer extends RenderLayer<Sheep, SheepModel<Sheep>> {

    private static final ResourceLocation FUR_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/entity/sheep/sheep_fur.png");

    private final SheepFurModel<Sheep> furModel;

    /**
     * Constructs a new SheeperFuseLayer.
     *
     * @param parent   The parent render layer.
     * @param modelSet The model set used to bake the sheep fur model.
     */
    public SheeperFuseLayer(RenderLayerParent<Sheep, SheepModel<Sheep>> parent, EntityModelSet modelSet) {
        super(parent);
        furModel = new SheepFurModel<>(modelSet.bakeLayer(ModelLayers.SHEEP_FUR));
    }

    /**
     * Renders the fuse effect on the Sheeper entity.
     *
     * @param poseStack       The pose stack for rendering transformations.
     * @param buffer          The buffer source for vertex consumers.
     * @param packedLight     The packed light value.
     * @param sheep           The sheep entity being rendered.
     * @param limbSwing       The limb swing animation parameter.
     * @param limbSwingAmount The limb swing amount animation parameter.
     * @param partialTicks    The partial tick time.
     * @param ageInTicks      The age in ticks.
     * @param netHeadYaw      The head yaw rotation.
     * @param headPitch       The head pitch rotation.
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

        // Skip rendering if the sheep is not a Sheeper or is invisible.
        if (!(sheep instanceof SheeperEntity sheeper) || sheep.isInvisible()) {
            return;
        }

        float opacity = sheeper.getVisualFuseProgress(partialTicks) * 0.65F;

        // Skip rendering if the fuse is not visible.
        if (opacity <= 0.0F) {
            return;
        }

        VertexConsumer bodyVertices = buffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(sheep)));

        // Render the main body with the fuse effect.
        getParentModel().renderToBuffer(
                poseStack, bodyVertices, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 0.15F, 0.15F, opacity);

        // Render the fur layer with the fuse effect if the sheep is not sheared.
        if (!sheep.isSheared()) {
            getParentModel().copyPropertiesTo(furModel);
            furModel.prepareMobModel(
                    sheep, limbSwing, limbSwingAmount, partialTicks);
            furModel.setupAnim(sheep, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

            VertexConsumer furVertices = buffer.getBuffer(RenderType.entityTranslucent(FUR_TEXTURE));

            furModel.renderToBuffer(
                    poseStack, furVertices, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 0.15F, 0.15F, opacity);
        }
    }
}
