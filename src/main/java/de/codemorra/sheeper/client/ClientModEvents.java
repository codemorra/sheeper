package de.codemorra.sheeper.client;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.client.renderer.entity.SheepRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side event handling for the Sheeper mod.
 */
@Mod.EventBusSubscriber(
    modid = SheeperMod.MODID,
    bus = Mod.EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT
)
public class ClientModEvents {
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ClientModEvents() {
    }

    /**
     * Registers entity renderers for custom entities.
     * 
     * @param event The event used to register entity renderers
     */
    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SHEEPER.get(), SheepRenderer::new);
    }
}
