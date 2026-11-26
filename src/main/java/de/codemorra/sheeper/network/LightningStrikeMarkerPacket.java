package de.codemorra.sheeper.network;

import java.util.function.Supplier;

import de.codemorra.sheeper.client.LightningStrikeMarkerVisuals;
import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.world.ArenaLimits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Shares the geometry and phase of a warning or a completed arena strike. */
public record LightningStrikeMarkerPacket(ResourceLocation dimension, BlockPos arenaCenter, double arenaRadius,
        Vec3 position, double centerRadius, double outerRadius, int minY, int maxY, long impactTick,
        int durationTicks, boolean water, boolean impact) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeBlockPos(arenaCenter);
        buffer.writeDouble(arenaRadius);
        buffer.writeDouble(position.x);
        buffer.writeDouble(position.y);
        buffer.writeDouble(position.z);
        buffer.writeDouble(centerRadius);
        buffer.writeDouble(outerRadius);
        buffer.writeVarInt(minY);
        buffer.writeVarInt(maxY);
        buffer.writeLong(impactTick);
        buffer.writeVarInt(durationTicks);
        buffer.writeBoolean(water);
        buffer.writeBoolean(impact);
    }

    public static LightningStrikeMarkerPacket decode(FriendlyByteBuf buffer) {
        var packet = new LightningStrikeMarkerPacket(buffer.readResourceLocation(), buffer.readBlockPos(),
                buffer.readDouble(), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readDouble(), buffer.readDouble(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readLong(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
        double dx = packet.position.x - packet.arenaCenter.getX() - 0.5;
        double dz = packet.position.z - packet.arenaCenter.getZ() - 0.5;
        ForgeConfigSpec.ValueSpec durationSpec = LightningSheeperConfig.SPEC.getSpec()
                .get(LightningSheeperConfig.LIGHTNING_AMBIENT_GROUND_SPARK_DURATION.getPath());
        if (!(ArenaLimits.isValidRadius(packet.arenaRadius)
                && dx * dx + dz * dz <= packet.arenaRadius * packet.arenaRadius + 1.0E-6
                && packet.minY >= -2032 && packet.maxY <= 2032 && packet.minY <= packet.maxY
                && packet.position.y >= packet.minY && packet.position.y < packet.maxY + 1
                && packet.centerRadius >= 0.25 && packet.centerRadius <= 4
                && packet.outerRadius > packet.centerRadius && packet.outerRadius <= 9
                && packet.impactTick >= 0 && packet.durationTicks > 0 && durationSpec.test(packet.durationTicks / 20.0))) {
            throw new IllegalArgumentException("Invalid lightning strike marker");
        }
        return packet;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightningStrikeMarkerVisuals.receive(this));
    }
}
