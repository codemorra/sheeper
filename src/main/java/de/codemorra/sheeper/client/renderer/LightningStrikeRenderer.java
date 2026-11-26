package de.codemorra.sheeper.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import de.codemorra.sheeper.entity.LightningStrikeEffect;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Keeps both ends of a jagged strike anchored to the storm cloud and the impact point. */
public final class LightningStrikeRenderer extends EntityRenderer<LightningStrikeEffect> {
    private static final Vec3 CROSS_X = new Vec3(1, 0, 0);
    private static final Vec3 CROSS_Z = new Vec3(0, 0, 1);

    public LightningStrikeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LightningStrikeEffect strike, float yaw, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int light) {
        float height = strike.getStrikeHeight();
        float width = strike.getStrikeWidth();
        int segments = Mth.clamp(Mth.ceil(height), 2, 24);
        double spread = Math.min(1.4, height * 0.12) * width;
        Vec3 across = CROSS_X.scale(width);
        Vec3 depth = CROSS_Z.scale(width);
        RandomSource random = RandomSource.create(strike.seed);
        var vertices = buffers.getBuffer(RenderType.lightning());
        var matrix = poses.last().pose();
        Vec3 from = Vec3.ZERO;
        for (int index = 1; index <= segments; index++) {
            double fraction = (double) index / segments;
            double offset = Math.sin(fraction * Math.PI) * spread;
            Vec3 to = new Vec3((random.nextDouble() * 2 - 1) * offset, height * fraction,
                    (random.nextDouble() * 2 - 1) * offset);
            LightningTravelArcRenderer.drawSegment(vertices, matrix, from, to, across, depth, 0.9F);
            from = to;
        }
    }

    @Override
    public boolean shouldRender(LightningStrikeEffect strike, Frustum frustum, double x, double y, double z) {
        Vec3 bottom = strike.position();
        double width = strike.getStrikeWidth() * 1.7;
        return strike.shouldRender(x, y, z)
                && frustum.isVisible(new AABB(bottom, bottom.add(0, strike.getStrikeHeight(), 0)).inflate(width, 1.7, width));
    }

    @Override
    public ResourceLocation getTextureLocation(LightningStrikeEffect strike) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
