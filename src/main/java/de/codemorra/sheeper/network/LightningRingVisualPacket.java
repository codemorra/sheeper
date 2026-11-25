package de.codemorra.sheeper.network;

import java.util.function.Supplier;

import de.codemorra.sheeper.client.LightningRingVisuals;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Synchronizes the origin and clock of a ring wave or the charge preceding the first teleport. */
public record LightningRingVisualPacket(ResourceLocation dimension, BlockPos arenaCenter, double arenaRadius,
        int minY, int maxY, Vec3 origin, long startTick, int warningTicks, double speed, float width,
        float spacing, float bodyHeight) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeBlockPos(arenaCenter);
        buffer.writeDouble(arenaRadius);
        buffer.writeVarInt(minY);
        buffer.writeVarInt(maxY);
        buffer.writeDouble(origin.x);
        buffer.writeDouble(origin.y);
        buffer.writeDouble(origin.z);
        buffer.writeLong(startTick);
        buffer.writeVarInt(warningTicks);
        buffer.writeDouble(speed);
        buffer.writeFloat(width);
        buffer.writeFloat(spacing);
        buffer.writeFloat(bodyHeight);
    }

    public static LightningRingVisualPacket decode(FriendlyByteBuf buffer) {
        var packet = new LightningRingVisualPacket(buffer.readResourceLocation(), buffer.readBlockPos(),
                buffer.readDouble(), buffer.readVarInt(), buffer.readVarInt(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readLong(),
                buffer.readVarInt(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        if (!(packet.arenaRadius >= 1 && packet.arenaRadius <= 128 && packet.minY >= -2032 && packet.maxY <= 2032
                && packet.minY <= packet.maxY && packet.origin.y >= packet.minY && packet.origin.y < packet.maxY + 1
                && packet.isWithinArena(packet.origin.x, packet.origin.z)
                && packet.startTick >= 0 && packet.warningTicks >= 0 && packet.warningTicks <= 40
                && packet.speed >= 0.4 && packet.speed <= 1.8 && packet.width >= 0.1F && packet.width <= 2
                && packet.spacing >= 0.2F && packet.spacing <= 1 && packet.bodyHeight > 0 && packet.bodyHeight <= 32)) {
            throw new IllegalArgumentException("Invalid lightning ring visual");
        }
        return packet;
    }

    /** Checks whether a sample lies inside the arena's horizontal boundary. */
    public boolean isWithinArena(double x, double z) {
        double dx = x - arenaCenter.getX() - 0.5;
        double dz = z - arenaCenter.getZ() - 0.5;
        return dx * dx + dz * dz <= arenaRadius * arenaRadius;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightningRingVisuals.receive(this));
    }
}
