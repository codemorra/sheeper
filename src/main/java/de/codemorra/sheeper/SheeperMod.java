package de.codemorra.sheeper;

import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.registry.ModFluids;
import de.codemorra.sheeper.fluid.BossWaterlogging;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fluids.FluidInteractionRegistry;
import net.minecraftforge.common.ForgeMod;
import net.minecraft.world.level.block.Blocks;
import de.codemorra.sheeper.entity.ElementalSheeperEntity;
import de.codemorra.sheeper.registry.ModItems;
import de.codemorra.sheeper.registry.ModBlocks;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.config.SheeperClientConfig;
import de.codemorra.sheeper.network.ModNetwork;
import de.codemorra.sheeper.registry.ModParticles;
import de.codemorra.sheeper.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

/**
 * Main class for the Sheeper mod.
 * Handles the registration of entities, blocks, items, and configuration.
 */
@Mod(SheeperMod.MODID)
public class SheeperMod {
    public static final String MODID = "sheeper";

    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Registers network packets, entities, blocks, items, particles, sounds, and
     * gameplay/client configuration.
     * 
     * @param context The mod loading context used to register entities, blocks,
     *                items, and
     *                configuration
     */
    public SheeperMod(FMLJavaModLoadingContext context) {
        ModNetwork.register();
        ModEntities.register(context.getModEventBus());
        ModFluids.register(context.getModEventBus());
        ModBlocks.register(context.getModEventBus());
        ModItems.register(context.getModEventBus());
        ModParticles.register(context.getModEventBus());
        ModSounds.register(context.getModEventBus());
        context.getModEventBus().addListener(this::registerEntityAttributes);
        context.getModEventBus().addListener(this::commonSetup);
        context.registerConfig(ModConfig.Type.COMMON, SheeperConfig.SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, SheeperClientConfig.SPEC);
        LOGGER.info("SheeperMod initialized");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BossWaterlogging.enable();
            de.codemorra.sheeper.world.ArenaWeather.enable();
            FluidInteractionRegistry.addInteraction(ForgeMod.LAVA_TYPE.get(),
                    new FluidInteractionRegistry.InteractionInformation(ModFluids.BOSS_WATER_TYPE.get(),
                            state -> state.isSource() ? Blocks.OBSIDIAN.defaultBlockState()
                                    : Blocks.COBBLESTONE.defaultBlockState()));
        });
    }

    /**
     * Registers the entity attributes for custom entities.
     * 
     * @param event The entity attribute creation event used to register attributes
     */
    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SHEEPER.get(), Sheep.createAttributes().build());
        event.put(ModEntities.LAVA_SHEEPER.get(), ElementalSheeperEntity.createBossAttributes().build());
        event.put(ModEntities.LIGHTNING_SHEEPER.get(), ElementalSheeperEntity.createBossAttributes().build());
        event.put(ModEntities.WATER_SHEEPER.get(), ElementalSheeperEntity.createBossAttributes().build());
    }
}
