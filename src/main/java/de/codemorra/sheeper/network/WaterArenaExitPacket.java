package de.codemorra.sheeper.network;

import java.util.function.Supplier;

import de.codemorra.sheeper.world.WaterArenaPushProtection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Requests an intentional lateral exit; the server rechecks the live outer boundary. */
public record WaterArenaExitPacket(double x, double z) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(x);
        buffer.writeDouble(z);
    }

    public static WaterArenaExitPacket decode(FriendlyByteBuf buffer) {
        double x = buffer.readDouble();
        double z = buffer.readDouble();
        if (!Double.isFinite(x) || !Double.isFinite(z)
                || x * x + z * z < 1.0E-8D || x * x + z * z > 1.001D) {
            throw new IllegalArgumentException("Invalid water arena exit direction");
        }
        return new WaterArenaExitPacket(x, z);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        var sender = context.get().getSender();
        if (sender != null) {
            WaterArenaPushProtection.requestExit(sender, new Vec3(x, 0, z));
        }
        context.get().setPacketHandled(true);
    }
}
