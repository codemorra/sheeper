package de.codemorra.sheeper.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.codemorra.sheeper.client.renderer.layer.ElementalFleeceLayer;
import de.codemorra.sheeper.client.renderer.layer.WaterSalvoEyesLayer;
import de.codemorra.sheeper.entity.ElementalSheeperEntity;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SheepRenderer;
import net.minecraft.world.entity.animal.Sheep;

/**
 * Keeps the sheep body and fleece, adding server-sized elemental presentation.
 */
public class ElementalSheeperRenderer extends SheepRenderer {
    public ElementalSheeperRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new ElementalBodyModel(context.bakeLayer(ModelLayers.SHEEP));
        addLayer(new ElementalFleeceLayer(this, context.getModelSet()));
        addLayer(new WaterSalvoEyesLayer(this, context.getModelSet()));
    }

    /**
     * Uses the synchronized scale that also defines the boss's collision bounds.
     */
    @Override
    protected void scale(Sheep sheep, PoseStack poses, float partialTick) {
        super.scale(sheep, poses, partialTick);
        float size = ((ElementalSheeperEntity) sheep).getSizeMultiplier();
        poses.scale(size, size, size);
        shadowRadius = 0.7F * size;
    }

    /**
     * Tints the vanilla body separately from the fleece and its elemental layer.
     */
    private static final class ElementalBodyModel extends SheepModel<Sheep> {
        private float red;
        private float green;
        private float blue;

        private ElementalBodyModel(ModelPart root) {
            super(root);
        }

        @Override
        public void prepareMobModel(Sheep sheep, float limbSwing, float limbSwingAmount, float partialTick) {
            super.prepareMobModel(sheep, limbSwing, limbSwingAmount, partialTick);
            switch (((ElementalSheeperEntity) sheep).getElement()) {
                case LAVA -> {
                    red = 0.65F;
                    green = 0.34F;
                    blue = 0.24F;
                }
                case LIGHTNING -> {
                    red = 0.45F;
                    green = 0.55F;
                    blue = 0.7F;
                }
                case WATER -> {
                    red = 0.4F;
                    green = 0.8F;
                    blue = 0.85F;
                }
            }
        }

        @Override
        public void renderToBuffer(PoseStack poses, VertexConsumer vertices, int light, int overlay,
                float red, float green, float blue, float alpha) {
            super.renderToBuffer(poses, vertices, light, overlay,
                    red * this.red, green * this.green, blue * this.blue, alpha);
        }
    }
}
