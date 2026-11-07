package de.codemorra.sheeper.fluid;

import java.util.function.Consumer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;

/**
 * Water physics and textures without vanilla source conversion or hydration.
 */
public final class BossWaterFluidType extends FluidType {
    public BossWaterFluidType() {
        super(Properties.create().descriptionId("block.sheeper.boss_water")
                .fallDistanceModifier(0.0F).canExtinguish(true).supportsBoating(true));
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
                return 0xFF3F76E4;
            }

            @Override
            public int getTintColor(FluidState state, BlockAndTintGetter level, BlockPos pos) {
                return 0xFF000000 | BiomeColors.getAverageWaterColor(level, pos);
            }
        });
    }
}
