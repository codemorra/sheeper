package de.codemorra.sheeper;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SheeperMod.MODID)
public class SheeperMod {
    public static final String MODID = "sheeper";

    public static final Logger LOGGER = LogUtils.getLogger();

    public SheeperMod(FMLJavaModLoadingContext context) {
        LOGGER.info("SheeperMod initialized");
    }
}
