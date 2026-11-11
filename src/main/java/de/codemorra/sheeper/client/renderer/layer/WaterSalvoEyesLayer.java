package de.codemorra.sheeper.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Sheep;

/** Uses the existing red eye texture without the elemental body's blue tint. */
public final class WaterSalvoEyesLayer extends RenderLayer<Sheep, SheepModel<Sheep>> {
    private static final RenderType EYES = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(
            SheeperMod.MODID, "textures/entity/sheeper_eyes.png"));
    private final SheepModel<Sheep> eyes;

    public WaterSalvoEyesLayer(RenderLayerParent<Sheep, SheepModel<Sheep>> parent, EntityModelSet models) {
        super(parent);
        eyes = new SheepModel<>(models.bakeLayer(ModelLayers.SHEEP));
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, Sheep sheep,
            float limbSwing, float limbSwingAmount, float partialTick, float age, float yaw, float pitch) {
        if (!(sheep instanceof WaterSheeperEntity boss) || sheep.isInvisible()) {
            return;
        }
        float intensity = boss.getSalvoEyeIntensity(partialTick);
        if (intensity <= 0.0F) {
            return;
        }
        getParentModel().copyPropertiesTo(eyes);
        eyes.prepareMobModel(sheep, limbSwing, limbSwingAmount, partialTick);
        eyes.setupAnim(sheep, limbSwing, limbSwingAmount, age, yaw, pitch);
        eyes.renderToBuffer(poses, buffers.getBuffer(EYES), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, intensity, intensity, intensity, 1.0F);
    }
}
