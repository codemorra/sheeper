package de.codemorra.sheeper;

import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.registry.ModItems;
import de.codemorra.sheeper.registry.ModBlocks;
import de.codemorra.sheeper.config.SheeperConfig;
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
     * Registers the mod's entities, blocks, items, and configuration.
     * 
     * @param context The mod loading context used to register entities, blocks, items, and
     *                configuration
     */
    public SheeperMod(FMLJavaModLoadingContext context) {
        ModEntities.register(context.getModEventBus());
        ModBlocks.register(context.getModEventBus());
        ModItems.register(context.getModEventBus());
        context.getModEventBus().addListener(this::registerEntityAttributes);
        context.registerConfig(ModConfig.Type.COMMON, SheeperConfig.SPEC);
        LOGGER.info("SheeperMod initialized");
    }

    /**
     * Registers the entity attributes for custom entities.
     * 
     * @param event The entity attribute creation event used to register attributes
     */
    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SHEEPER.get(), Sheep.createAttributes().build());
    }
}
