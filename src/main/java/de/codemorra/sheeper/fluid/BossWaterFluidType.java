package de.codemorra.sheeper.fluid;

import java.util.function.Consumer;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Vector3f;
import de.codemorra.sheeper.config.WaterSheeperConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;

/**
 * Water physics and textures without vanilla source conversion or hydration.
 */
public final class BossWaterFluidType extends FluidType {
    public BossWaterFluidType() {
        super(Properties.create().descriptionId("block.sheeper.boss_water")
                .fallDistanceModifier(0.0F).canExtinguish(true).supportsBoating(true).canDrown(false));
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_flow");
            }

            @Override
            public ResourceLocation getOverlayTexture() {
                return ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_overlay");
            }

            @Override
            public int getTintColor() {
                return 0xFF5AB9D8;
            }

            @Override
            public int getTintColor(FluidState state, BlockAndTintGetter level, BlockPos pos) {
                return getTintColor();
            }

            /**
             * Keeps arena visibility consistent instead of inheriting dark biome-water fog.
             */
            @Override
            public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level,
                    int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
                if (hasRestrictedVision(camera)) {
                    return fluidFogColor;
                }
                return new Vector3f(0.12F, 0.38F, 0.52F);
            }

            /**
             * Applies the visibility adjustment only while the camera is inside boss water.
             */
            @Override
            public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance,
                    float partialTick, float nearDistance, float farDistance, FogShape shape) {
                if (hasRestrictedVision(camera)) {
                    return;
                }
                RenderSystem.setShaderFogStart(0);
                RenderSystem
                        .setShaderFogEnd(
                                Math.min(renderDistance, WaterSheeperConfig.WATER_FOG_DISTANCE.get().floatValue()));
                RenderSystem.setShaderFogShape(FogShape.SPHERE);
            }

            /**
             * Visibility improvements must not cancel blindness or darkness gameplay
             * effects.
             */
            private boolean hasRestrictedVision(Camera camera) {
                return camera.getEntity() instanceof LivingEntity entity
                        && (entity.hasEffect(MobEffects.BLINDNESS) || entity.hasEffect(MobEffects.DARKNESS));
            }
        });
    }
}
