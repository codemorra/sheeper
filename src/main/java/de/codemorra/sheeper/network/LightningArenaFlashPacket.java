package de.codemorra.sheeper.network;

import java.util.function.Supplier;

import de.codemorra.sheeper.client.ArenaFogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Starts a brief environmental flash inside the lightning arena's captured bounds. */
public record LightningArenaFlashPacket(ResourceLocation dimension, BlockPos center, double radius,
        int minimumY, int maximumY) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeBlockPos(center);
        buffer.writeDouble(radius);
        buffer.writeVarInt(minimumY);
        buffer.writeVarInt(maximumY);
    }

    public static LightningArenaFlashPacket decode(FriendlyByteBuf buffer) {
        var packet = new LightningArenaFlashPacket(buffer.readResourceLocation(), buffer.readBlockPos(),
                buffer.readDouble(), buffer.readVarInt(), buffer.readVarInt());
        if (!(packet.radius() >= 1 && packet.radius() <= 128 && packet.minimumY() >= -2032
                && packet.maximumY() <= 2032 && packet.minimumY() <= packet.center().getY()
                && packet.center().getY() < packet.maximumY() && packet.maximumY() - packet.minimumY() <= 128)) {
            throw new IllegalArgumentException("Invalid lightning arena flash bounds");
        }
        return packet;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ArenaFogRenderer.receiveFlash(this));
    }
}
