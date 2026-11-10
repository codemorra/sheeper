package de.codemorra.sheeper.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import de.codemorra.sheeper.entity.WaterProjectile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Small translucent water cube using the animated resource-pack water sprite.
 */
public class WaterProjectileRenderer extends EntityRenderer<WaterProjectile> {
    private static final ResourceLocation WATER = ResourceLocation.fromNamespaceAndPath("minecraft",
            "block/water_still");
    private final ModelPart model;

    public WaterProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("water", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
        model = LayerDefinition.create(mesh, 16, 16).bakeRoot();
    }

    @Override
    public void render(WaterProjectile projectile, float yaw, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int light) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER);
        poses.pushPose();
        poses.translate(0.0D, projectile.getBbHeight() / 2.0D, 0.0D);
        model.render(poses,
                sprite.wrap(buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(projectile)))),
                light, OverlayTexture.NO_OVERLAY, 0.3F, 0.8F, 1.0F, 0.85F);
        poses.popPose();
        super.render(projectile, yaw, partialTick, poses, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(WaterProjectile projectile) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
