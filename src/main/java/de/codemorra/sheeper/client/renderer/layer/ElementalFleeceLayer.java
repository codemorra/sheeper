package de.codemorra.sheeper.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.codemorra.sheeper.entity.ElementalSheeperEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SheepFurModel;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.SheepFurLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.inventory.InventoryMenu;

/** Elemental fleece colors and animated surfaces for lava and lightning. */
public class ElementalFleeceLayer extends SheepFurLayer {
    private static final ResourceLocation MAGMA = new ResourceLocation("minecraft", "block/magma");
    private static final ResourceLocation FLEECE = new ResourceLocation("minecraft",
            "textures/entity/sheep/sheep_fur.png");
    private static final ResourceLocation LIGHTNING = new ResourceLocation("minecraft",
            "textures/entity/creeper/creeper_armor.png");

    private final SheepFurModel<Sheep> fleece;

    public ElementalFleeceLayer(RenderLayerParent<Sheep, SheepModel<Sheep>> parent, EntityModelSet models) {
        super(parent, models);
        fleece = new SheepFurModel<>(models.bakeLayer(ModelLayers.SHEEP_FUR));
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, Sheep sheep,
            float limbSwing, float limbSwingAmount, float partialTick, float age, float yaw, float pitch) {
        // Preserve vanilla fleece and glowing outlines for the other elements.
        if (!(sheep instanceof ElementalSheeperEntity boss) || boss.getElement() != ElementalSheeperEntity.Element.WATER
                || sheep.isInvisible()) {
            super.render(poses, buffers, light, sheep, limbSwing, limbSwingAmount, partialTick, age, yaw, pitch);
        }
        if (sheep.isInvisible() || !(sheep instanceof ElementalSheeperEntity boss)) {
            return;
        }
        getParentModel().copyPropertiesTo(fleece);
        fleece.prepareMobModel(sheep, limbSwing, limbSwingAmount, partialTick);
        fleece.setupAnim(sheep, limbSwing, limbSwingAmount, age, yaw, pitch);

        float surfaceOffset = boss.getSizeMultiplier() * 0.002F;
        switch (boss.getElement()) {
            case LAVA -> {
                float pulse = 0.85F + 0.15F * Mth.sin(age * 0.06F);
                fleece.renderToBuffer(poses, blockSurface(buffers, MAGMA, surfaceOffset, 0.0F),
                        LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, pulse, pulse, pulse, 0.7F);
            }
            case LIGHTNING -> {
                float pulse = 0.6F + 0.3F * Mth.sin(age * 0.07F);
                VertexConsumer sparks = new TiledFleeceVertexConsumer(
                        buffers.getBuffer(RenderType.energySwirl(LIGHTNING,
                                age * 0.003F % 1.0F, age * 0.0016F % 1.0F)),
                        surfaceOffset);
                fleece.renderToBuffer(poses, sparks, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, pulse * 0.7F, pulse * 0.85F, pulse, 1.0F);
            }
            case WATER -> {
                fleece.renderToBuffer(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(FLEECE)), light,
                        OverlayTexture.NO_OVERLAY, 0.28F, 0.48F, 0.64F, 1.0F);
            }
        }
    }

    /**
     * Maps model UVs into an atlas sprite. The atlas supplies the current animation
     * frame and resource-pack textures, rather than sampling a full animation
     * strip.
     */
    private static VertexConsumer blockSurface(
            MultiBufferSource buffers, ResourceLocation texture, float surfaceOffset, float flowOffset) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        return new TiledFleeceVertexConsumer(
                sprite.wrap(buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS))),
                surfaceOffset, flowOffset);
    }
}
