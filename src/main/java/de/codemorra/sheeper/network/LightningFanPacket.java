package de.codemorra.sheeper.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import de.codemorra.sheeper.client.LightningFanVisuals;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Synchronizes the hover position and the three clipped arc endpoints for each tracked player. */
public record LightningFanPacket(ResourceLocation dimension, int bossId, Vec3 origin,
        Map<Integer, List<Vec3>> ends) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeVarInt(bossId);
        writePoint(buffer, origin);
        buffer.writeVarInt(ends.size());
        for (var target : ends.entrySet()) {
            buffer.writeVarInt(target.getKey());
            target.getValue().forEach(point -> writePoint(buffer, point));
        }
    }

    public static LightningFanPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int bossId = buffer.readVarInt();
        Vec3 origin = readPoint(buffer);
        int count = buffer.readVarInt();
        // At least one byte for the target ID and three double coordinates per endpoint.
        if (count < 0 || count > buffer.readableBytes() / (1 + 9 * Double.BYTES)) {
            throw new IllegalArgumentException("Invalid lightning fan target count");
        }
        var ends = new HashMap<Integer, List<Vec3>>();
        for (int index = 0; index < count; index++) {
            int target = buffer.readVarInt();
            var points = List.of(readPoint(buffer), readPoint(buffer), readPoint(buffer));
            if (ends.put(target, points) != null) {
                throw new IllegalArgumentException("Duplicate lightning fan target");
            }
        }
        return new LightningFanPacket(dimension, bossId, origin, Map.copyOf(ends));
    }

    private static void writePoint(FriendlyByteBuf buffer, Vec3 point) {
        buffer.writeDouble(point.x);
        buffer.writeDouble(point.y);
        buffer.writeDouble(point.z);
    }

    private static Vec3 readPoint(FriendlyByteBuf buffer) {
        var point = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        if (!(Math.abs(point.x) <= 30_000_000 && Math.abs(point.y) <= 2048 && Math.abs(point.z) <= 30_000_000)) {
            throw new IllegalArgumentException("Invalid lightning fan point");
        }
        return point;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightningFanVisuals.receive(this));
    }
}
