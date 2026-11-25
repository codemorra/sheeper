package de.codemorra.sheeper.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import de.codemorra.sheeper.client.LightningRodShelterVisuals;
import de.codemorra.sheeper.world.LightningRodShelter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Synchronizes the fixed shelter spheres and their fade time for one lightning rod attack. */
public record LightningRodShelterPacket(ResourceLocation dimension, UUID owner, List<LightningRodShelter> shelters,
        long dissolveStart, int dissolveTicks) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeUUID(owner);
        buffer.writeLong(dissolveStart);
        buffer.writeVarInt(dissolveTicks);
        buffer.writeCollection(shelters, (output, shelter) -> {
            output.writeDouble(shelter.center().x);
            output.writeDouble(shelter.center().y);
            output.writeDouble(shelter.center().z);
            output.writeDouble(shelter.radius());
        });
    }

    public static LightningRodShelterPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        UUID owner = buffer.readUUID();
        long dissolveStart = buffer.readLong();
        int dissolveTicks = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (dissolveStart < 0 || dissolveTicks < 0 || dissolveTicks > 1200) {
            throw new IllegalArgumentException("Invalid lightning rod shelter fade");
        }
        if (count < 0 || count > LightningRodShelter.MAX_SHELTERS) {
            throw new IllegalArgumentException("Invalid lightning rod shelter count");
        }
        var shelters = new ArrayList<LightningRodShelter>(count);
        for (int index = 0; index < count; index++) {
            var center = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            double radius = buffer.readDouble();
            if (!(Math.abs(center.x) <= 30_000_000 && Math.abs(center.y) <= 2032 && Math.abs(center.z) <= 30_000_000
                    && radius >= 0.95 && radius <= 6)) {
                throw new IllegalArgumentException("Invalid lightning rod shelter");
            }
            shelters.add(new LightningRodShelter(center, radius));
        }
        return new LightningRodShelterPacket(dimension, owner, List.copyOf(shelters), dissolveStart, dissolveTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightningRodShelterVisuals.receive(this));
    }
}
