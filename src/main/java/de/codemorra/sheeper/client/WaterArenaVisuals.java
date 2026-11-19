package de.codemorra.sheeper.client;

import java.util.List;

import javax.annotation.Nullable;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.network.WaterArenaPacket;
import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
import de.codemorra.sheeper.world.WaterArenaData.ExpandingVolume;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only water appearance; no block state or fluid simulation is changed.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WaterArenaVisuals {
    // Physics and rendering share immutable snapshots published by the client thread.
    private record Snapshot(ClientLevel level, List<ExpandingVolume> arenas, long receivedAt) {
    }

    private static volatile Snapshot snapshot = new Snapshot(null, List.of(), 0);
    private static final double TRANSITION_WIDTH = 1.0D;
    private static ClientLevel fogLevel;
    private static float previousFogBlend;
    private static float fogBlend;

    private WaterArenaVisuals() {
    }

    /**
     * Accepts whole-dimension bounds even when the corresponding boss is not
     * tracked.
     */
    public static void receive(WaterArenaPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(packet.dimension())) {
            return;
        }
        snapshot = new Snapshot(level, packet.arenas(), level.getGameTime());
    }

    /**
     * Restores normal rendering after disconnects, dimension changes or expired
     * snapshots.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Snapshot current = snapshot;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (current.level() != null && (current.level() != level
                || level.getGameTime() - current.receivedAt() > 60)) {
            snapshot = new Snapshot(null, List.of(), 0);
        }
        if (fogLevel != level) {
            fogLevel = level;
            previousFogBlend = 0;
            fogBlend = 0;
        }
        if (level != null && !minecraft.isPaused()) {
            previousFogBlend = fogBlend;
            float target = transitionAt(level, minecraft.gameRenderer.getMainCamera().getPosition());
            // FOV smoothing also softens a newly received arena snapshot,
            // teleport or encounter removal.
            fogBlend = Mth.lerp(0.5F, fogBlend, target);
        }
    }

    /** Blends the underwater FOV over a short entry band, retaining it at every interior boundary. */
    private static float transitionAt(ClientLevel level, Vec3 point) {
        double intensity = 0;
        for (VolumeBounds bounds : activeVolumes(level)) {
            double width = Math.min(TRANSITION_WIDTH,
                    Math.min(bounds.radius() * 0.5D, (bounds.maximumY() + 1.0D - bounds.minimumY()) * 0.25D));
            double progress = Mth.clamp(1 + bounds.interiorDistance(point) / width, 0, 1);
            intensity = Math.max(intensity, progress * progress * (3 - 2 * progress));
        }
        return (float) intensity;
    }

    private static float fogBlend(double partialTick) {
        return Mth.lerp(Mth.clamp((float) partialTick, 0, 1), previousFogBlend, fogBlend);
    }

    /** Supplies the same captured floor geometry used by the server for local movement prediction. */
    public static List<VolumeBounds> activeVolumes(Level level) {
        Snapshot current = snapshot;
        return current.level() == level ? current.arenas().stream()
                .map(volume -> volume.at(level.getGameTime())).toList() : List.of();
    }

    /** Interpolates only the visual front; collision prediction remains aligned with server ticks. */
    static List<VolumeBounds> renderVolumes(Level level, float partialTick) {
        return expandingVolumes(level).stream()
                .map(volume -> volume.at(level.getGameTime() + partialTick)).toList();
    }

    /** Startup clocks are immutable and shared by fog, local physics and the bubble front. */
    public static List<ExpandingVolume> expandingVolumes(Level level) {
        Snapshot current = snapshot;
        return current.level() == level ? current.arenas() : List.of();
    }

    /** Virtual water supplies its own depth-based fog, without the coarse vanilla camera texture. */
    @SubscribeEvent
    public static void onWaterOverlay(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.WATER
                && de.codemorra.sheeper.world.ArenaWater.contains(event.getPlayer().level,
                        event.getPlayer().getEyePosition())) {
            event.setCanceled(true);
        }
    }

    /** Matches vanilla's underwater FOV gradually without forcing an instant water camera state. */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (canBlendFog(event.getCamera()) && event.getCamera().getFluidInCamera() == FogType.NONE) {
            double scale = Minecraft.getInstance().options.fovEffectScale().get() * fogBlend(event.getPartialTick());
            event.setFOV(event.getFOV() * Mth.lerp(scale, 1.0D, (double) 0.85714287F));
        }
    }

    /** Preserves vision restrictions and the appearance of non-water fluids. */
    static boolean canBlendFog(Camera camera) {
        if (camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return false;
        }
        FogType type = camera.getFluidInCamera();
        return type == FogType.NONE || type == FogType.WATER;
    }

    /**
     * Lets vanilla bubbles survive in virtual water. Only the particle's fluid
     * lookup is substituted; world blocks and actual fluid simulation are intact.
     */
    public static FluidState bubbleFluid(FluidState physical, Particle particle) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && physical.isEmpty()
                && de.codemorra.sheeper.world.ArenaWater.contains(level, particle.getBoundingBox().getCenter())) {
            return Fluids.WATER.defaultFluidState();
        }
        return physical;
    }

    /**
     * Keeps virtual and physical water on one vanilla fog baseline inside the
     * arena. The bounded fog renderer supplies underwater color and attenuation;
     * null delegates to vanilla outside the volume and for non-water fluids.
     */
    @Nullable
    public static FogType cameraFluid(Camera camera) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || camera.getEntity() == null) {
            return null;
        }
        Vec3 point = camera.getPosition();
        BlockPos pos = new BlockPos(point);
        FluidState fluid = level.getFluidState(pos);
        // Keep a common air baseline for virtual and physical water inside the
        // arena. The spatial fog pass adds only the submerged part of each ray.
        if ((fluid.isEmpty() || fluid.is(FluidTags.WATER))
                && activeVolumes(level).stream().anyMatch(bounds -> bounds.contains(point))) {
            return FogType.NONE;
        }
        return null;
    }

}
