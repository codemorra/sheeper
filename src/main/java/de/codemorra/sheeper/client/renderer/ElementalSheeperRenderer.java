package de.codemorra.sheeper.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import de.codemorra.sheeper.client.renderer.layer.ElementalFleeceLayer;
import de.codemorra.sheeper.client.renderer.layer.WaterBeamEyesLayer;
import de.codemorra.sheeper.entity.ElementalSheeperEntity;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SheepRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.animal.Sheep;

/**
 * Keeps the sheep body and fleece, adding server-sized elemental presentation.
 */
public class ElementalSheeperRenderer extends SheepRenderer {
    public ElementalSheeperRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new ElementalBodyModel(context.bakeLayer(ModelLayers.SHEEP));
        addLayer(new ElementalFleeceLayer(this, context.getModelSet()));
        addLayer(new WaterBeamEyesLayer(this, context.getModelSet()));
    }

    /**
     * Draws the water tube outside the sheep model's rotation and scale transforms.
     */
    @Override
    public void render(Sheep sheep, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers,
            int light) {
        super.render(sheep, yaw, partialTick, poses, buffers, light);
        if (sheep instanceof WaterSheeperEntity boss && !boss.isInvisible()) {
            WaterBeamRenderer.render(boss, poses, buffers, light, partialTick);
        }
    }

    /**
     * Keeps a visible beam segment rendered even when the boss itself is outside
     * the view.
     */
    @Override
    public boolean shouldRender(Sheep sheep, Frustum frustum, double x, double y, double z) {
        if (sheep instanceof WaterSheeperEntity boss && boss.getBeamOffset().lengthSqr() > 0
                && frustum.isVisible(new AABB(boss.getBeamOrigin(), boss.getBeamOrigin().add(boss.getBeamOffset()))
                        .inflate(WaterSheeperEntity.BEAM_RADIUS))) {
            return true;
        }
        return super.shouldRender(sheep, frustum, x, y, z);
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
     * Spins the complete water boss model without letting head AI counteract the
     * vortex.
     */
    @Override
    protected void setupRotations(Sheep sheep, PoseStack poses, float age, float yaw, float partialTick) {
        super.setupRotations(sheep, poses, age, yaw, partialTick);
        if (sheep instanceof WaterSheeperEntity boss) {
            poses.mulPose(Vector3f.YP.rotationDegrees(boss.getMaelstromRotation(partialTick)));
        }
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
