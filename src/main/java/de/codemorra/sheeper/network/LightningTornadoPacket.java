package de.codemorra.sheeper.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import de.codemorra.sheeper.client.LightningTornadoVisuals;
import de.codemorra.sheeper.world.LightningTornado;
import de.codemorra.sheeper.world.ArenaLimits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Refreshes a group of moving funnels or clears them immediately when the attack ends. */
public record LightningTornadoPacket(ResourceLocation dimension, UUID owner, List<LightningTornado> tornados) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeUUID(owner);
        buffer.writeCollection(tornados, LightningTornadoPacket::encodeFunnel);
    }

    private static void encodeFunnel(FriendlyByteBuf buffer, LightningTornado tornado) {
        buffer.writeBlockPos(tornado.arenaCenter());
        buffer.writeDouble(tornado.arenaRadius());
        buffer.writeVarInt(tornado.minimumY());
        buffer.writeVarInt(tornado.maximumY());
        writePoint(buffer, tornado.position());
        writePoint(buffer, tornado.velocity());
        buffer.writeDouble(tornado.radius());
        buffer.writeDouble(tornado.height());
        buffer.writeDouble(tornado.strength());
        buffer.writeLong(tornado.sampleTick());
        buffer.writeLong(tornado.activeFrom());
        buffer.writeLong(tornado.dissolveFrom());
        buffer.writeLong(tornado.endTick());
    }

    public static LightningTornadoPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        UUID owner = buffer.readUUID();
        int count = buffer.readVarInt();
        if (count < 0 || count > LightningTornado.MAX_FUNNELS_PER_ATTACK) {
            throw new IllegalArgumentException("Invalid lightning tornado count");
        }
        var tornados = new ArrayList<LightningTornado>(count);
        for (int index = 0; index < count; index++) {
            tornados.add(decodeFunnel(buffer, owner));
        }
        return new LightningTornadoPacket(dimension, owner, List.copyOf(tornados));
    }

    private static LightningTornado decodeFunnel(FriendlyByteBuf buffer, UUID owner) {
        var tornado = new LightningTornado(owner, buffer.readBlockPos(), buffer.readDouble(), buffer.readVarInt(),
                buffer.readVarInt(), readPoint(buffer), readPoint(buffer), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readLong());
        if (!(ArenaLimits.isValidRadius(tornado.arenaRadius())
                && tornado.minimumY() >= -2032 && tornado.maximumY() <= 2032
                && tornado.isInsideArena(tornado.position()) && tornado.velocity().lengthSqr() <= 2
                && tornado.radius() >= 0.35 && tornado.radius() <= 9 && tornado.height() >= 1 && tornado.height() <= 16
                && tornado.strength() >= 0 && tornado.strength() <= 1.8
                && tornado.sampleTick() >= 0 && tornado.activeFrom() >= 0
                && tornado.endTick() > tornado.sampleTick() && tornado.endTick() > tornado.dissolveFrom()
                && tornado.dissolveFrom() > tornado.activeFrom() && tornado.endTick() - tornado.sampleTick() <= 550)) {
            throw new IllegalArgumentException("Invalid lightning tornado");
        }
        return tornado;
    }

    private static Vec3 readPoint(FriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static void writePoint(FriendlyByteBuf buffer, Vec3 point) {
        buffer.writeDouble(point.x);
        buffer.writeDouble(point.y);
        buffer.writeDouble(point.z);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightningTornadoVisuals.receive(this));
    }
}
