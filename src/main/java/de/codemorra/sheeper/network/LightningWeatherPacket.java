package de.codemorra.sheeper.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import de.codemorra.sheeper.client.LightningWeatherVisuals;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Full dimension snapshot; clients can render an arena without tracking its
 * boss.
 */
public record LightningWeatherPacket(ResourceLocation dimension, List<Arena> arenas) {
    public record Arena(BlockPos center, double radius, int minimumY, int maximumY, long start, int transition) {
        /** Applies the captured floor and exclusive ceiling to all storm effects. */
        public boolean containsY(double y) {
            return y >= minimumY && y < maximumY;
        }

        /** Checks the persisted geometry and the network bounds with the same limits. */
        public boolean isValid() {
            return Double.isFinite(radius) && radius >= 1 && radius <= 128
                    && containsY(center.getY()) && minimumY >= -2032 && maximumY <= 2032
                    && maximumY - minimumY <= 128 && transition >= 0 && transition <= 1200;
        }

        public boolean isBuilding(double time) {
            return time < start + transition;
        }

        /** Uses the same expanding front for rain, clouds, and the visible boundary. */
        public double radiusAt(double time) {
            return radius * (transition == 0 ? 1 : Mth.clamp((time - start) / transition, 0, 1));
        }

        /** Fades the rain in just behind the front to soften the crossing. */
        public float intensityAt(double time, double x, double y, double z) {
            if (!containsY(y)) {
                return 0;
            }
            double distance = Math.hypot(x - center.getX() - 0.5, z - center.getZ() - 0.5);
            double activeRadius = radiusAt(time);
            return (float) Mth.clamp((activeRadius - distance) / Math.min(3, radius), 0, 1);
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeVarInt(arenas.size());
        for (Arena arena : arenas) {
            buffer.writeBlockPos(arena.center());
            buffer.writeDouble(arena.radius());
            buffer.writeVarInt(arena.minimumY());
            buffer.writeVarInt(arena.maximumY());
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
            int minimumY = buffer.readVarInt();
            int maximumY = buffer.readVarInt();
            long start = buffer.readLong();
            int transition = buffer.readVarInt();
            var arena = new Arena(center, radius, minimumY, maximumY, start, transition);
            if (!arena.isValid()) {
                throw new IllegalArgumentException("Invalid arena weather geometry");
            }
            arenas.add(arena);
        }
        return new LightningWeatherPacket(dimension, List.copyOf(arenas));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightningWeatherVisuals.receive(this));
    }
}
