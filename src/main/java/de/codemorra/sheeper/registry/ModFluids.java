package de.codemorra.sheeper.registry;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.fluid.BossWaterFluid;
import de.codemorra.sheeper.fluid.BossWaterFluidType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Source and flowing forms share one fluid type, distinct from ordinary water.
 */
public final class ModFluids {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES,
            SheeperMod.MODID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS,
            SheeperMod.MODID);
    public static final RegistryObject<BossWaterFluidType> BOSS_WATER_TYPE = TYPES.register("boss_water",
            BossWaterFluidType::new);
    public static final RegistryObject<BossWaterFluid.Source> BOSS_WATER = FLUIDS.register("boss_water",
            BossWaterFluid.Source::new);
    public static final RegistryObject<BossWaterFluid.Flowing> FLOWING_BOSS_WATER = FLUIDS
            .register("flowing_boss_water", BossWaterFluid.Flowing::new);

    private ModFluids() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
