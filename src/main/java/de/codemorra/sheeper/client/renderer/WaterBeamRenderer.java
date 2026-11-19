package de.codemorra.sheeper.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.util.ResourceLocations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Animated water tube with an opaque-looking core and a gently rippling outer
 * surface.
 */
final class WaterBeamRenderer {
    private static final ResourceLocation WATER = ResourceLocations.create("minecraft",
            "block/water_flow");

    private WaterBeamRenderer() {
    }

    /**
     * Renders in entity-local world axes after the sheep model restores its pose.
     * Repeated short texture sections avoid stretching one water sprite over the
     * full range; geometry uses the server's clipped collision segment.
     *
     * @param boss        owner supplying the synchronized segment and mouth height
     * @param poses       entity-local pose stack
     * @param buffers     shared render buffers
     * @param light       world lighting at the boss
     * @param partialTick interpolation fraction for the cosmetic surface ripples
     */
    static void render(WaterSheeperEntity boss, PoseStack poses, MultiBufferSource buffers, int light,
            float partialTick) {
        Vec3 offset = boss.getBeamOffset();
        double length = offset.length();
        if (length < 1.0E-4D) {
            return;
        }
        Vec3 direction = offset.normalize();
        Vec3 reference = Math.abs(direction.y) > 0.99D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = direction.cross(reference).normalize();
        Vec3 up = direction.cross(side).normalize();
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        poses.pushPose();
        poses.translate(0, boss.getBeamOrigin().y - boss.getY(), 0);
        for (int layer = 0; layer < 2; layer++) {
            double radius = layer == 0 ? 0.15D : 0.22D;
            float alpha = layer == 0 ? 0.98F : 0.8F;
            for (double start = 0; start < length; start += 1.0D) {
                double end = Math.min(length, start + 1);
                double ripple = layer == 0 ? 0 : 0.02D * Math.sin((boss.tickCount + partialTick) * 0.6D - start * 2);
                for (int face = 0; face < 8; face++) {
                    double angle = face * Math.PI / 4;
                    double nextAngle = (face + 1) * Math.PI / 4;
                    Vec3 first = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                    Vec3 second = side.scale(Math.cos(nextAngle)).add(up.scale(Math.sin(nextAngle)));
                    Vec3 normal = first.add(second).normalize();
                    Vec3 a = first.scale(radius + ripple);
                    Vec3 b = second.scale(radius + ripple);
                    vertex(vertices, poses.last(), a.add(direction.scale(start)), normal, sprite.getU0(),
                            sprite.getV0(), alpha, light);
                    vertex(vertices, poses.last(), a.add(direction.scale(end)), normal, sprite.getU0(), sprite.getV1(),
                            alpha, light);
                    vertex(vertices, poses.last(), b.add(direction.scale(end)), normal, sprite.getU1(), sprite.getV1(),
                            alpha, light);
                    vertex(vertices, poses.last(), b.add(direction.scale(start)), normal, sprite.getU1(),
                            sprite.getV0(), alpha, light);
                }
            }
        }
        poses.popPose();
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, Vec3 point, Vec3 normal,
            float u, float v, float alpha, int light) {
        vertices.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .color(0.3F, 0.75F, 1.0F, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }
}
