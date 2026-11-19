package de.codemorra.sheeper.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import de.codemorra.sheeper.client.WaterArenaVisuals;
import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
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
public record WaterArenaPacket(ResourceLocation dimension, List<VolumeBounds> arenas) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeVarInt(arenas.size());
        for (VolumeBounds arena : arenas) {
            buffer.writeBlockPos(arena.center());
            buffer.writeDouble(arena.radius());
            buffer.writeInt(arena.minimumY());
            buffer.writeInt(arena.maximumY());
        }
    }

    /** Rejects invalid geometry before it can trigger client chunk rebuilds. */
    public static WaterArenaPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int count = buffer.readVarInt();
        if (count < 0 || count > 4096) {
            throw new IllegalArgumentException("Invalid water arena snapshot size: " + count);
        }
        List<VolumeBounds> arenas = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            BlockPos center = buffer.readBlockPos();
            double radius = buffer.readDouble();
            int minimum = buffer.readInt();
            int maximum = buffer.readInt();
            VolumeBounds bounds = new VolumeBounds(center, radius, minimum, maximum);
            if (!bounds.isValid()) {
                throw new IllegalArgumentException("Invalid water arena geometry");
            }
            arenas.add(bounds);
        }
        return new WaterArenaPacket(dimension, List.copyOf(arenas));
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
