package de.codemorra.sheeper.network;

import java.util.function.Supplier;

import de.codemorra.sheeper.client.LightningWaterChargeVisuals;
import de.codemorra.sheeper.world.ArenaLimits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Announces a real water impact so observers can trace and render its discharge locally. */
public record LightningWaterChargePacket(ResourceLocation dimension, BlockPos strike, BlockPos arenaCenter,
        double radius, int minimumY, int maximumY) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeBlockPos(strike);
        buffer.writeBlockPos(arenaCenter);
        buffer.writeDouble(radius);
        buffer.writeVarInt(minimumY);
        buffer.writeVarInt(maximumY);
    }

    public static LightningWaterChargePacket decode(FriendlyByteBuf buffer) {
        var packet = new LightningWaterChargePacket(buffer.readResourceLocation(), buffer.readBlockPos(),
                buffer.readBlockPos(), buffer.readDouble(), buffer.readVarInt(), buffer.readVarInt());
        if (!(ArenaLimits.isValidRadius(packet.radius)
                && packet.minimumY >= -2032 && packet.maximumY <= 2032
                && packet.maximumY - packet.minimumY <= 128
                && packet.contains(packet.strike))) {
            throw new IllegalArgumentException("Invalid lightning water discharge");
        }
        return packet;
    }

    private boolean contains(BlockPos cell) {
        double dx = (double) cell.getX() - arenaCenter.getX();
        double dz = (double) cell.getZ() - arenaCenter.getZ();
        return cell.getY() >= minimumY && cell.getY() < maximumY && dx * dx + dz * dz <= radius * radius;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightningWaterChargeVisuals.receive(this));
    }
}
