package de.codemorra.sheeper.event;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.world.LightningStormData;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Updates the local storm independently of arena participation and boss tracking.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LightningStormEvents {
    private LightningStormEvents() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            LightningStormData.get(level).tick(level);
        }
    }
}
