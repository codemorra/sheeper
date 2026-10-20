package de.codemorra.sheeper;

import de.codemorra.sheeper.registry.ModEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

@Mod(SheeperMod.MODID)
public class SheeperMod {
    public static final String MODID = "sheeper";

    public static final Logger LOGGER = LogUtils.getLogger();

    public SheeperMod(FMLJavaModLoadingContext context) {
        ModEntities.register(context.getModEventBus());
        context.getModEventBus().addListener(this::registerEntityAttributes);
        LOGGER.info("SheeperMod initialized");
    }

    // Registers the entity attributes for custom entities.
    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SHEEPER.get(), Sheep.createAttributes().build());
    }
}
