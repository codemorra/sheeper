package de.codemorra.sheeper.network;

import java.util.function.Supplier;

import de.codemorra.sheeper.client.SheeperSoundEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Carries a positional sound with its relative volume for the listener's local mix. */
public record LightningSoundPacket(ClientboundSoundPacket sound, boolean ambientThunder) {
    public void encode(FriendlyByteBuf buffer) {
        sound.write(buffer);
        buffer.writeBoolean(ambientThunder);
    }

    public static LightningSoundPacket decode(FriendlyByteBuf buffer) {
        return new LightningSoundPacket(new ClientboundSoundPacket(buffer), buffer.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SheeperSoundEvents.playLightningSound(this));
    }
}
