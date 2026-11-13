package de.codemorra.sheeper.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import de.codemorra.sheeper.client.LightningWeatherVisuals;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Full dimension snapshot; clients can render an arena without tracking its
 * boss.
 */
public record LightningWeatherPacket(ResourceLocation dimension, List<Arena> arenas) {
    public record Arena(BlockPos center, double radius, long start, int transition) {
        public float intensity(double time) {
            return (float) Math.max(0, Math.min(1, (time - start) / Math.max(1, transition)));
        }

        public boolean contains(double x, double z) {
            double dx = x - center.getX() - 0.5D;
            double dz = z - center.getZ() - 0.5D;
            return dx * dx + dz * dz <= radius * radius;
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeVarInt(arenas.size());
        for (Arena arena : arenas) {
            buffer.writeBlockPos(arena.center());
            buffer.writeDouble(arena.radius());
            buffer.writeLong(arena.start());
            buffer.writeVarInt(arena.transition());
        }
    }

    public static LightningWeatherPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int count = buffer.readVarInt();
        if (count < 0 || count > 4096) {
            throw new IllegalArgumentException("Invalid arena weather snapshot size: " + count);
        }
        List<Arena> arenas = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            BlockPos center = buffer.readBlockPos();
            double radius = buffer.readDouble();
            long start = buffer.readLong();
            int transition = buffer.readVarInt();
            if (!Double.isFinite(radius) || radius <= 0 || radius > 128 || transition < 0 || transition > 1200) {
                throw new IllegalArgumentException("Invalid arena weather geometry");
            }
            arenas.add(new Arena(center, radius, start, transition));
        }
        return new LightningWeatherPacket(dimension, List.copyOf(arenas));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightningWeatherVisuals.receive(this));
    }
}
