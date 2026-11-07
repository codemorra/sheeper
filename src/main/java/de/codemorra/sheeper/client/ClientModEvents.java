package de.codemorra.sheeper.client;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.registry.ModEntities;
import de.codemorra.sheeper.registry.ModFluids;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import de.codemorra.sheeper.client.renderer.ElementalSheeperRenderer;
import de.codemorra.sheeper.client.renderer.SheeperRenderer;
import de.codemorra.sheeper.client.renderer.PrimedSheeperWoolRenderer;
import de.codemorra.sheeper.client.renderer.layer.PlayerMuttonTintLayer;
import de.codemorra.sheeper.client.particle.MuttonCloudParticle;
import de.codemorra.sheeper.registry.ModParticles;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side event handling for the Sheeper mod.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void setupFluids(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModFluids.BOSS_WATER.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_BOSS_WATER.get(), RenderType.translucent());
        });
    }

    /** Binds the vapor provider to the shared vanilla smoke sprite animation. */
    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.register(ModParticles.MUTTON_CLOUD.get(), MuttonCloudParticle.Provider::new);
    }

    /**
     * Adds the mutton tint to every registered vanilla player skin model,
     * including the regular and slim variants.
     */
    @SubscribeEvent
    public static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new PlayerMuttonTintLayer(renderer));
            }
        }
    }

    /**
     * Registers the screen tint after the world vignette and before HUD content.
     */
    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "mutton_tint", MuttonScreenOverlay::render);
    }

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
        event.registerEntityRenderer(ModEntities.LAVA_SHEEPER.get(), ElementalSheeperRenderer::new);
        event.registerEntityRenderer(ModEntities.LIGHTNING_SHEEPER.get(), ElementalSheeperRenderer::new);
        event.registerEntityRenderer(ModEntities.WATER_SHEEPER.get(), ElementalSheeperRenderer::new);

        event.registerEntityRenderer(
                ModEntities.SHEEPER.get(), SheeperRenderer::new);

        event.registerEntityRenderer(
                ModEntities.PRIMED_SHEEPER_WOOL.get(),
                PrimedSheeperWoolRenderer::new);
    }
}
