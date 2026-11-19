package de.codemorra.sheeper.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import de.codemorra.sheeper.client.WaterArenaVisuals;
import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
import de.codemorra.sheeper.world.WaterArenaData.AnimatedVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Full dimension snapshot, independent of whether clients currently track the
 * boss.
 */
public record WaterArenaPacket(ResourceLocation dimension, List<AnimatedVolume> arenas, long pushProtectedUntil) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeVarInt(arenas.size());
        for (AnimatedVolume volume : arenas) {
            VolumeBounds arena = volume.bounds();
            buffer.writeBlockPos(arena.center());
            buffer.writeDouble(arena.radius());
            buffer.writeInt(arena.minimumY());
            buffer.writeDouble(arena.maximumY());
            buffer.writeLong(volume.startTick());
            buffer.writeVarInt(volume.durationTicks());
            buffer.writeLong(volume.drainStartTick());
            buffer.writeVarInt(volume.drainDurationTicks());
        }
        buffer.writeLong(pushProtectedUntil);
    }

    /** Rejects invalid geometry and clocks before client physics or rendering uses them. */
    public static WaterArenaPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int count = buffer.readVarInt();
        if (count < 0 || count > 4096) {
            throw new IllegalArgumentException("Invalid water arena snapshot size: " + count);
        }
        List<AnimatedVolume> arenas = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            BlockPos center = buffer.readBlockPos();
            double radius = buffer.readDouble();
            int minimum = buffer.readInt();
            double maximum = buffer.readDouble();
            VolumeBounds bounds = new VolumeBounds(center, radius, minimum, maximum);
            long startTick = buffer.readLong();
            int duration = buffer.readVarInt();
            long drainTick = buffer.readLong();
            int drainDuration = buffer.readVarInt();
            if (!bounds.isValid() || startTick < 0 || duration < 0 || duration > 600
                    || drainTick < 0 || drainDuration < 0 || drainDuration > 600) {
                throw new IllegalArgumentException("Invalid water arena geometry");
            }
            arenas.add(new AnimatedVolume(bounds, startTick, duration, drainTick, drainDuration));
        }
        long protectedUntil = buffer.readLong();
        if (protectedUntil < 0) {
            throw new IllegalArgumentException("Invalid water push protection clock");
        }
        return new WaterArenaPacket(dimension, List.copyOf(arenas), protectedUntil);
    }

    /**
     * The registered channel schedules this distribution-guarded handler on the
     * client thread.
     */
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WaterArenaVisuals.receive(this));
    }
}
