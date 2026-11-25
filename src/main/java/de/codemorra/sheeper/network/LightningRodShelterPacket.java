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

/** Refreshes or removes the fixed shelter spheres for one lightning rod attack. */
public record LightningRodShelterPacket(ResourceLocation dimension, UUID owner, List<LightningRodShelter> shelters) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeUUID(owner);
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
        int count = buffer.readVarInt();
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
        return new LightningRodShelterPacket(dimension, owner, List.copyOf(shelters));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightningRodShelterVisuals.receive(this));
    }
}
