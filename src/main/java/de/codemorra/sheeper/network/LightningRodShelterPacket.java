package de.codemorra.sheeper.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import de.codemorra.sheeper.client.LightningRodShelterVisuals;
import de.codemorra.sheeper.world.ArenaLimits;
import de.codemorra.sheeper.world.LightningRodShelter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Synchronizes shelter and ground effects for one Lightning Rod attack. */
public record LightningRodShelterPacket(ResourceLocation dimension, UUID owner, List<LightningRodShelter> shelters,
        Vec3 boltOrigin, long formationStart, long dissolveStart, int dissolveTicks, @Nullable GroundCharge ground) {
    /** Carries the captured arena bounds and the visual ground-charge clock. */
    public record GroundCharge(BlockPos center, double radius, int minY, int maxY, long start,
            long fadeStart, int fadeTicks) {
        /** Returns full intensity during the barrage, then fades to zero after the last strike. */
        public float intensityAt(double time) {
            if (time < start) {
                return 0;
            }
            if (fadeStart < 0) {
                return 1;
            }
            return fadeTicks == 0 ? 0 : 1 - (float) Mth.clamp((time - fadeStart) / fadeTicks, 0, 1);
        }

        private void encode(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(center);
            buffer.writeDouble(radius);
            buffer.writeVarInt(minY);
            buffer.writeVarInt(maxY);
            buffer.writeLong(start);
            buffer.writeLong(fadeStart);
            buffer.writeVarInt(fadeTicks);
        }

        private static GroundCharge decode(FriendlyByteBuf buffer) {
            var charge = new GroundCharge(buffer.readBlockPos(), buffer.readDouble(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readLong(), buffer.readLong(), buffer.readVarInt());
            if (!ArenaLimits.isValidRadius(charge.radius) || charge.minY < -2032 || charge.maxY > 2031
                    || charge.center.getY() < charge.minY || charge.center.getY() > charge.maxY
                    || charge.maxY - charge.minY > 128 || charge.start < 0
                    || charge.fadeStart != -1 && charge.fadeStart < charge.start
                    || charge.fadeTicks < 0 || charge.fadeTicks > 60) {
                throw new IllegalArgumentException("Invalid Lightning Rod ground charge");
            }
            return charge;
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeUUID(owner);
        writePoint(buffer, boltOrigin);
        buffer.writeLong(formationStart);
        buffer.writeLong(dissolveStart);
        buffer.writeVarInt(dissolveTicks);
        buffer.writeCollection(shelters, (output, shelter) -> {
            writePoint(output, shelter.center());
            output.writeDouble(shelter.radius());
        });
        buffer.writeBoolean(ground != null);
        if (ground != null) {
            ground.encode(buffer);
        }
    }

    public static LightningRodShelterPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        UUID owner = buffer.readUUID();
        Vec3 boltOrigin = readPoint(buffer);
        long formationStart = buffer.readLong();
        long dissolveStart = buffer.readLong();
        int dissolveTicks = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (formationStart < 0 || dissolveStart < 0 || dissolveTicks < 0 || dissolveTicks > 1200) {
            throw new IllegalArgumentException("Invalid lightning rod shelter fade");
        }
        if (count < 0 || count > LightningRodShelter.MAX_SHELTERS) {
            throw new IllegalArgumentException("Invalid lightning rod shelter count");
        }
        var shelters = new ArrayList<LightningRodShelter>(count);
        for (int index = 0; index < count; index++) {
            Vec3 center = readPoint(buffer);
            double radius = buffer.readDouble();
            if (!(radius >= 0.95 && radius <= 12)) {
                throw new IllegalArgumentException("Invalid lightning rod shelter");
            }
            shelters.add(new LightningRodShelter(center, radius));
        }
        GroundCharge ground = buffer.readBoolean() ? GroundCharge.decode(buffer) : null;
        return new LightningRodShelterPacket(dimension, owner, List.copyOf(shelters), boltOrigin, formationStart,
                dissolveStart, dissolveTicks, ground);
    }

    private static Vec3 readPoint(FriendlyByteBuf buffer) {
        var point = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        if (!(Math.abs(point.x) <= 30_000_000 && Math.abs(point.y) <= 2032 && Math.abs(point.z) <= 30_000_000)) {
            throw new IllegalArgumentException("Invalid lightning rod shelter position");
        }
        return point;
    }

    private static void writePoint(FriendlyByteBuf buffer, Vec3 point) {
        buffer.writeDouble(point.x);
        buffer.writeDouble(point.y);
        buffer.writeDouble(point.z);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightningRodShelterVisuals.receive(this));
    }
}
