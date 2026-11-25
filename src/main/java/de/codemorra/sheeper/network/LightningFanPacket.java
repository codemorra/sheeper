package de.codemorra.sheeper.network;

import java.util.List;
import java.util.function.Supplier;

import de.codemorra.sheeper.client.LightningFanVisuals;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Synchronizes the mouth and either the charge progress or the three clipped arc endpoints. */
public record LightningFanPacket(ResourceLocation dimension, int bossId, Vec3 origin, float charge, List<Vec3> ends) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeVarInt(bossId);
        writePoint(buffer, origin);
        buffer.writeFloat(charge);
        ends.forEach(point -> writePoint(buffer, point));
    }

    public static LightningFanPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int bossId = buffer.readVarInt();
        Vec3 origin = readPoint(buffer);
        float charge = buffer.readFloat();
        if (!(charge >= 0 && charge <= 1)) {
            throw new IllegalArgumentException("Invalid lightning fan charge");
        }
        List<Vec3> ends = charge > 0 ? List.of() : List.of(readPoint(buffer), readPoint(buffer), readPoint(buffer));
        return new LightningFanPacket(dimension, bossId, origin, charge, ends);
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
