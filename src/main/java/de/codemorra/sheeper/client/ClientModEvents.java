package de.codemorra.sheeper.client;

import java.io.IOException;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import de.codemorra.sheeper.client.renderer.ElementalSheeperRenderer;
import de.codemorra.sheeper.client.renderer.LightningStrikeRenderer;
import de.codemorra.sheeper.client.renderer.SheeperRenderer;
import de.codemorra.sheeper.client.renderer.WaterProjectileRenderer;
import de.codemorra.sheeper.client.renderer.PrimedSheeperWoolRenderer;
import de.codemorra.sheeper.client.renderer.layer.PlayerMuttonTintLayer;
import de.codemorra.sheeper.client.particle.MuttonCloudParticle;
import de.codemorra.sheeper.client.particle.ArenaWaterParticle;
import de.codemorra.sheeper.client.particle.MaelstromWaveParticle;
import de.codemorra.sheeper.client.particle.WaterArenaBoundaryParticle;
import de.codemorra.sheeper.client.particle.WaterMineParticle;
import de.codemorra.sheeper.client.particle.WaterMineWaveParticle;
import de.codemorra.sheeper.client.particle.LightningSurfaceSparkParticle;
import de.codemorra.sheeper.registry.ModParticles;
import de.codemorra.sheeper.util.ResourceLocations;
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
    public static void setupArenaLookups(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            de.codemorra.sheeper.world.ArenaWeather.setClientLookup(LightningWeatherVisuals::intensity);
            de.codemorra.sheeper.world.LightningArenaBoundaryDrag.setClientLookup(LightningWeatherVisuals::visibleArenas);
            de.codemorra.sheeper.world.LightningArenaWind.setClientLookup(LightningWeatherVisuals::windFields);
            de.codemorra.sheeper.world.LightningTornadoMotion.setClientLookup(LightningTornadoVisuals::activeTornados);
            de.codemorra.sheeper.world.ArenaWater.setClientLookup(WaterArenaVisuals::activeVolumes);
            de.codemorra.sheeper.world.ArenaWater.setClientAirColumns(WaterArenaVisuals::airColumns);
        });
    }

    /** Registers the arena shaders through the resource-pack reload lifecycle. */
    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceManager(),
                ResourceLocations.create(SheeperMod.MODID, "arena_fog"),
                DefaultVertexFormat.POSITION_TEX), ArenaFogRenderer::setShader);
        event.registerShader(new ShaderInstance(event.getResourceManager(),
                ResourceLocations.create(SheeperMod.MODID, "lightning_scene_depth"),
                DefaultVertexFormat.POSITION_TEX), LightningWorldRender::setCopyDepthShader);
        event.registerShader(new ShaderInstance(event.getResourceManager(),
                ResourceLocations.create(SheeperMod.MODID, "lightning_shelter_glow"),
                DefaultVertexFormat.POSITION_COLOR), LightningWorldRender::setGlowShader);
    }

    /** Binds the vapor provider to the shared vanilla smoke sprite animation. */
    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.register(ModParticles.MUTTON_CLOUD.get(), MuttonCloudParticle.Provider::new);
        event.register(ModParticles.ARENA_RAIN.get(), ArenaWaterParticle.Provider::new);
        event.register(ModParticles.WATER_FOUNTAIN.get(), ArenaWaterParticle.Provider::new);
        event.register(ModParticles.WATER_MINE.get(), WaterMineParticle.Provider::new);
        event.register(ModParticles.WATER_MINE_WAVE.get(), WaterMineWaveParticle.Provider::new);
        event.register(ModParticles.MAELSTROM_WAVE.get(), MaelstromWaveParticle.Provider::new);
        event.register(ModParticles.WATER_ARENA_FLOOR.get(), WaterArenaBoundaryParticle.Provider::new);
        event.register(ModParticles.WATER_ARENA_SIDE.get(), WaterArenaBoundaryParticle.Provider::new);
        event.register(ModParticles.LIGHTNING_SURFACE_SPARK.get(), LightningSurfaceSparkParticle.Provider::new);
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
        event.registerEntityRenderer(ModEntities.LIGHTNING_STRIKE.get(), LightningStrikeRenderer::new);
        event.registerEntityRenderer(ModEntities.WATER_SHEEPER.get(), ElementalSheeperRenderer::new);
        event.registerEntityRenderer(ModEntities.WATER_PROJECTILE.get(), WaterProjectileRenderer::new);

        event.registerEntityRenderer(
                ModEntities.SHEEPER.get(), SheeperRenderer::new);

        event.registerEntityRenderer(
                ModEntities.PRIMED_SHEEPER_WOOL.get(),
                PrimedSheeperWoolRenderer::new);
    }
}
