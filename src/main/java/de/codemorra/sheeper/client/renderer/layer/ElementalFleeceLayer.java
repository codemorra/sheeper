package de.codemorra.sheeper.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.codemorra.sheeper.entity.ElementalSheeperEntity;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.util.ResourceLocations;
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
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;

/** Animated elemental surfaces layered over the tinted sheep fleece. */
public class ElementalFleeceLayer extends SheepFurLayer {
    private static final ResourceLocation SHEEP_FUR = ResourceLocations.create("minecraft",
            "textures/entity/sheep/sheep_fur.png");
    private static final ResourceLocation MAGMA = ResourceLocations.create("minecraft", "block/magma");
    private static final ResourceLocation WATER = ResourceLocations.create("minecraft", "block/water_flow");
    private static final ResourceLocation LIGHTNING = ResourceLocations.create("minecraft",
            "textures/entity/creeper/creeper_armor.png");

    private final SheepFurModel<Sheep> fleece;

    public ElementalFleeceLayer(RenderLayerParent<Sheep, SheepModel<Sheep>> parent, EntityModelSet models) {
        super(parent, models);
        fleece = new SheepFurModel<>(models.bakeLayer(ModelLayers.SHEEP_FUR));
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, Sheep sheep,
            float limbSwing, float limbSwingAmount, float partialTick, float age, float yaw, float pitch) {
        float opacity = sheep instanceof WaterSheeperEntity water ? water.getBubbleBodyOpacity(partialTick) : 1;
        MultiBufferSource baseFleece = opacity < 1 && !sheep.isInvisible() ? fadingFleece(buffers, opacity) : buffers;
        // The translucent surfaces need the dyed fleece underneath.
        super.render(poses, baseFleece, light, sheep, limbSwing, limbSwingAmount, partialTick, age, yaw, pitch);
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
                float shimmer = 0.58F + 0.08F * Mth.sin(age * 0.08F);
                float flow = age * 0.002F % 1.0F;
                fleece.renderToBuffer(poses, blockSurface(buffers, WATER, surfaceOffset, flow), light,
                        OverlayTexture.NO_OVERLAY, 0.3F, 0.8F, 1.0F, shimmer * opacity);
            }
        }
    }

    /** Keeps vanilla dye and hurt colors while blending the base fleece into the scene. */
    private static MultiBufferSource fadingFleece(MultiBufferSource buffers, float opacity) {
        return type -> new VertexConsumerWrapper(buffers.getBuffer(RenderType.entityTranslucentCull(SHEEP_FUR))) {
            @Override
            public void vertex(float x, float y, float z, float red, float green, float blue, float alpha,
                    float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
                parent.vertex(x, y, z, red, green, blue, alpha * opacity, u, v, overlay, light,
                        normalX, normalY, normalZ);
            }
        };
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
