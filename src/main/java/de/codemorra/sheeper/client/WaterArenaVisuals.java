package de.codemorra.sheeper.client;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.shaders.FogShape;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.fluid.BossWaterFluid;
import de.codemorra.sheeper.network.WaterArenaPacket;
import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only water appearance; no block state or fluid simulation is changed.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WaterArenaVisuals {
    // Chunk workers only read immutable snapshots published by the client thread.
    private record Snapshot(ClientLevel level, List<VolumeBounds> arenas, long receivedAt) {
    }

    private static volatile Snapshot snapshot = new Snapshot(null, List.of(), 0);

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
        Snapshot previous = snapshot;
        snapshot = new Snapshot(level, packet.arenas(), level.getGameTime());
        if (previous.level() == level) {
            for (VolumeBounds bounds : previous.arenas()) {
                if (!packet.arenas().contains(bounds)) {
                    rebuild(level, bounds);
                }
            }
        }
        for (VolumeBounds bounds : packet.arenas()) {
            if (previous.level() != level || !previous.arenas().contains(bounds)) {
                rebuild(level, bounds);
            }
        }
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
        ClientLevel level = Minecraft.getInstance().level;
        if (current.level() != null && (current.level() != level
                || level.getGameTime() - current.receivedAt() > 60)) {
            snapshot = new Snapshot(null, List.of(), 0);
            if (current.level() == level) {
                current.arenas().forEach(bounds -> rebuild(level, bounds));
            }
        }
    }

    /** Shared client lookup for the breathing hook and chunk tinting. */
    public static boolean contains(Level level, BlockPos pos) {
        return activeVolumes(level).stream().anyMatch(bounds -> bounds.contains(pos));
    }

    /** Supplies the same captured floor geometry used by the server for local movement prediction. */
    public static List<VolumeBounds> activeVolumes(Level level) {
        Snapshot current = snapshot;
        return current.level() == level ? current.arenas() : List.of();
    }

    /**
     * Called only from the liquid renderer; natural water elsewhere keeps its biome
     * tint.
     */
    public static int tintColor(int vanilla, BlockAndTintGetter getter, BlockPos pos, FluidState fluid) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && fluid.is(FluidTags.WATER) && contains(level, pos) ? 0xFF5AB9D8 : vanilla;
    }

    /**
     * Render-only fluid equivalence removes inner faces and fixes shared corner
     * heights. Boss water is bounded by its arena; ordinary water versus air is
     * unchanged.
     */
    public static boolean sameFluid(Fluid first, Fluid second) {
        return first.isSame(second)
                || first instanceof BossWaterFluid && second.isSame(Fluids.WATER)
                || second instanceof BossWaterFluid && first.isSame(Fluids.WATER);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (usesArenaFog(event.getCamera())) {
            event.setRed(0.12F);
            event.setGreen(0.38F);
            event.setBlue(0.52F);
        }
    }

    @SubscribeEvent
    public static void onFogRender(ViewportEvent.RenderFog event) {
        if (usesArenaFog(event.getCamera())) {
            event.setNearPlaneDistance(0);
            event.setFarPlaneDistance(Math.min(Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F,
                    WaterSheeperConfig.WATER_FOG_DISTANCE.get().floatValue()));
            event.setFogShape(FogShape.SPHERE);
            event.setCanceled(true);
        }
    }

    /**
     * Keeps vision effects intact while using the same water classification as the
     * camera.
     */
    private static boolean usesArenaFog(Camera camera) {
        if (camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return false;
        }
        return cameraFluid(camera) == FogType.WATER;
    }

    /**
     * Camera hook: treats the virtual seam as water for fog and underwater FOV.
     * Null delegates to vanilla, preserving real air surfaces and other fluids.
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
        // The visual interface is full-height when any water continues above,
        // including the narrow seam between two different physical fluid types.
        double height = level.getFluidState(pos.above()).is(FluidTags.WATER) ? 1 : fluid.getHeight(level, pos);
        return contains(level, pos) && fluid.is(FluidTags.WATER)
                && point.y < pos.getY() + height ? FogType.WATER : null;
    }

    /**
     * Marks sections once per geometry change rather than rebuilding each block or
     * each snapshot.
     */
    private static void rebuild(ClientLevel level, VolumeBounds bounds) {
        int minimumX = Mth.floor(bounds.center().getX() - bounds.radius() - 1) >> 4;
        int maximumX = Mth.ceil(bounds.center().getX() + bounds.radius() + 1) >> 4;
        int minimumZ = Mth.floor(bounds.center().getZ() - bounds.radius() - 1) >> 4;
        int maximumZ = Mth.ceil(bounds.center().getZ() + bounds.radius() + 1) >> 4;
        int minimumY = Math.max(level.getMinSection(), (bounds.minimumY() - 1) >> 4);
        int maximumY = Math.min(level.getMaxSection() - 1, (bounds.maximumY() + 1) >> 4);
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                for (int y = minimumY; y <= maximumY; y++) {
                    Minecraft.getInstance().levelRenderer.setSectionDirty(x, y, z);
                }
            }
        }
    }
}
