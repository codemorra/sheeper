package de.codemorra.sheeper.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import de.codemorra.sheeper.client.LightningWorldRender;
import de.codemorra.sheeper.client.renderer.layer.ElementalFleeceLayer;
import de.codemorra.sheeper.client.renderer.layer.WaterBeamEyesLayer;
import de.codemorra.sheeper.entity.ElementalSheeperEntity;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SheepRenderer;
import net.minecraft.client.renderer.entity.layers.SheepFurLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.animal.Sheep;

/**
 * Keeps the sheep body and fleece, adding server-sized elemental presentation.
 */
public class ElementalSheeperRenderer extends SheepRenderer {
    private float bodyOpacity = 1;

    public ElementalSheeperRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new ElementalBodyModel(context.bakeLayer(ModelLayers.SHEEP));
        layers.removeIf(SheepFurLayer.class::isInstance);
        addLayer(new ElementalFleeceLayer(this, context.getModelSet()));
        addLayer(new WaterBeamEyesLayer(this, context.getModelSet()));
    }

    /**
     * Draws the water tube outside the sheep model's rotation and scale transforms.
     */
    @Override
    public void render(Sheep sheep, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers,
            int light) {
        if (sheep instanceof LightningSheeperEntity bolt) {
            var arc = bolt.getTravelArc();
            if (!LightningWorldRender.usesOptiFinePass()
                    && LightningTravelArcRenderer.hasEffect(bolt, arc, partialTick)) {
                LightningTravelArcRenderer.draw(bolt, arc, partialTick, poses.last().pose(),
                        buffers.getBuffer(RenderType.lightning()));
            }
            if (arc != null) {
                shadowRadius = 0;
                return;
            }
        }
        bodyOpacity = elementalBodyOpacity(sheep, partialTick);
        if (bodyOpacity <= 0) {
            shadowRadius = 0;
            return;
        }
        poses.pushPose();
        if (sheep instanceof WaterSheeperEntity boss) {
            poses.translate(0, -boss.getDeathSinkOffset(partialTick), 0);
        }
        super.render(sheep, yaw, partialTick, poses, buffers, light);
        poses.popPose();
        if (sheep instanceof WaterSheeperEntity boss && boss.isAlive() && !boss.isInvisible()) {
            WaterBeamRenderer.render(boss, poses, buffers, light, partialTick);
        }
    }

    /** Selects alpha blending for the model while it dissolves or rematerializes. */
    @Override
    protected RenderType getRenderType(Sheep sheep, boolean visible, boolean translucent, boolean glowing) {
        if (visible && bodyOpacity < 1) {
            return RenderType.entityTranslucentCull(getTextureLocation(sheep));
        }
        return super.getRenderType(sheep, visible, translucent, glowing);
    }

    /**
     * Keeps a visible beam segment rendered even when the boss itself is outside
     * the view.
     */
    @Override
    public boolean shouldRender(Sheep sheep, Frustum frustum, double x, double y, double z) {
        if (sheep instanceof LightningSheeperEntity bolt) {
            var arc = bolt.getTravelArc();
            if (arc != null) {
                AABB bounds = arc.isCharging()
                        ? new AABB(arc.end(), arc.end()).inflate(bolt.getBbWidth() * 5, bolt.getBbHeight() * 4,
                                bolt.getBbWidth() * 5)
                        : new AABB(arc.start(), arc.end()).inflate(1.5);
                if (frustum.isVisible(bounds)) {
                    return true;
                }
            }
        }
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
        float dissolve = sheep instanceof WaterSheeperEntity boss ? boss.getDeathBodyScale(partialTick) : 1;
        poses.scale(size * dissolve, size * dissolve, size * dissolve);
        shadowRadius = 0.7F * size * dissolve * bodyOpacity;
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
            float progress = boss.getDeathAnimationProgress(partialTick);
            float tilt = progress * progress * (3 - 2 * progress);
            poses.mulPose(Vector3f.ZP.rotationDegrees(70 * tilt));
        }
    }

    /** Keeps the arena bosses aligned with their custom death effects. */
    @Override
    protected float getFlipDegrees(Sheep sheep) {
        return sheep instanceof WaterSheeperEntity || sheep instanceof LightningSheeperEntity
                ? 0 : super.getFlipDegrees(sheep);
    }

    private static float elementalBodyOpacity(Sheep sheep, float partialTick) {
        if (sheep instanceof WaterSheeperEntity boss) {
            return boss.getBubbleBodyOpacity(partialTick);
        }
        return sheep instanceof LightningSheeperEntity boss ? boss.getDeathBodyOpacity(partialTick) : 1;
    }

    /**
     * Tints the vanilla body separately from the fleece and its elemental layer.
     */
    private static final class ElementalBodyModel extends SheepModel<Sheep> {
        private float red;
        private float green;
        private float blue;
        private float opacity;

        private ElementalBodyModel(ModelPart root) {
            super(root);
        }

        @Override
        public void prepareMobModel(Sheep sheep, float limbSwing, float limbSwingAmount, float partialTick) {
            super.prepareMobModel(sheep, limbSwing, limbSwingAmount, partialTick);
            opacity = elementalBodyOpacity(sheep, partialTick);
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
                    red * this.red, green * this.green, blue * this.blue, alpha * opacity);
        }
    }
}
