package de.codemorra.sheeper.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import de.codemorra.sheeper.client.LightningWeatherVisuals;
import de.codemorra.sheeper.config.LightningSheeperConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Full dimension snapshot; clients can render an arena without tracking its
 * boss.
 */
public record LightningWeatherPacket(ResourceLocation dimension, List<Arena> arenas, double boundarySlowdownWidth,
        double boundarySlowdownStrength) {
    public record Arena(BlockPos center, double radius, int minimumY, int maximumY, long start, int transition,
            long drainStart, int drainDuration) {
        /** Applies the captured floor and exclusive ceiling to all storm effects. */
        public boolean containsY(double y) {
            return y >= minimumY && y < maximumY;
        }

        /** Keeps the cloud bank above the spawn point even in a shallow arena. */
        public double cloudHalfHeight() {
            return cloudHalfHeight(center.getY(), maximumY);
        }

        private static double cloudHalfHeight(int originY, int ceilingY) {
            return Math.min(4.5, (ceilingY - originY) / 3.0);
        }

        /** Rain and lightning originate inside the same cloud geometry drawn by the client. */
        public double cloudCenterY() {
            return cloudCenterY(center.getY(), maximumY);
        }

        public static double cloudCenterY(int originY, int ceilingY) {
            return ceilingY - 1.7 * cloudHalfHeight(originY, ceilingY);
        }

        /** Follows the cloud bank's animated midline at the bolt's horizontal position. */
        public double cloudStrikeY(double x, double z, double time) {
            double variation = cloudVariation(x - center.getX() - 0.5, z - center.getZ() - 0.5, time);
            return cloudCenterY() + cloudHalfHeight() * variation * 0.5;
        }

        public static double cloudVariation(double x, double z, double time) {
            return (Math.sin(x * 0.14 + z * 0.11 - time * 0.018)
                    + Math.sin(z * 0.27 - x * 0.09 + time * 0.011)) * 0.5;
        }

        /** Checks the persisted geometry and the network bounds with the same limits. */
        public boolean isValid() {
            return Double.isFinite(radius) && radius >= 1 && radius <= 128
                    && containsY(center.getY()) && minimumY >= -2032 && maximumY <= 2032
                    && maximumY - minimumY <= 128 && transition >= 0 && transition <= 1200
                    && drainDuration >= 0 && drainDuration <= 600 && (!isDraining() || drainStart >= start);
        }

        public boolean isBuilding(double time) {
            return !isDraining() && time - start < transition;
        }

        public boolean isDraining() {
            return drainDuration > 0;
        }

        public boolean hasFinishedDraining(double time) {
            return isDraining() && time - drainStart >= drainDuration;
        }

        /** Uses the server's clock so observers and reloaded worlds resume the same retreat. */
        public Arena beginDrain(long time, int duration) {
            return new Arena(center, radius, minimumY, maximumY, start, transition, time, duration);
        }

        /** @return the fraction of storm opacity remaining during teardown */
        public float opacityAt(double time) {
            return isDraining() ? (float) Mth.clamp(1 - (time - drainStart) / drainDuration, 0, 1) : 1;
        }

        /** Uses the same expanding front for rain, clouds, and the visible boundary. */
        public double radiusAt(double time) {
            // A storm removed during buildup begins its retreat at the radius it already reached.
            double buildTime = isDraining() ? Math.min(time, drainStart) : time;
            double built = transition == 0 ? 1 : Mth.clamp((buildTime - start) / transition, 0, 1);
            return radius * built * opacityAt(time);
        }

        /** Fades the rain in just behind the front to soften the crossing. */
        public float intensityAt(double time, double x, double y, double z) {
            if (!containsY(y)) {
                return 0;
            }
            double distance = Math.hypot(x - center.getX() - 0.5, z - center.getZ() - 0.5);
            double activeRadius = radiusAt(time);
            return (float) Mth.clamp((activeRadius - distance) / Math.min(3, radius), 0, 1) * opacityAt(time);
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension);
        buffer.writeVarInt(arenas.size());
        for (Arena arena : arenas) {
            buffer.writeBlockPos(arena.center());
            buffer.writeDouble(arena.radius());
            buffer.writeVarInt(arena.minimumY());
            buffer.writeVarInt(arena.maximumY());
            buffer.writeLong(arena.start());
            buffer.writeVarInt(arena.transition());
            buffer.writeLong(arena.drainStart());
            buffer.writeVarInt(arena.drainDuration());
        }
        buffer.writeDouble(boundarySlowdownWidth);
        buffer.writeDouble(boundarySlowdownStrength);
    }

    public static LightningWeatherPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int count = buffer.readVarInt();
        if (count < 0 || count > 4096) {
            throw new IllegalArgumentException("Invalid arena weather snapshot size: " + count);
        }
        List<Arena> arenas = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            BlockPos center = buffer.readBlockPos();
            double radius = buffer.readDouble();
            int minimumY = buffer.readVarInt();
            int maximumY = buffer.readVarInt();
            long start = buffer.readLong();
            int transition = buffer.readVarInt();
            long drainStart = buffer.readLong();
            int drainDuration = buffer.readVarInt();
            var arena = new Arena(center, radius, minimumY, maximumY, start, transition, drainStart, drainDuration);
            if (!arena.isValid()) {
                throw new IllegalArgumentException("Invalid arena weather geometry");
            }
            arenas.add(arena);
        }
        double width = buffer.readDouble();
        double strength = buffer.readDouble();
        ForgeConfigSpec.ValueSpec widthSpec = LightningSheeperConfig.SPEC.getSpec()
                .get(LightningSheeperConfig.LIGHTNING_BOUNDARY_SLOWDOWN_WIDTH.getPath());
        ForgeConfigSpec.ValueSpec strengthSpec = LightningSheeperConfig.SPEC.getSpec()
                .get(LightningSheeperConfig.LIGHTNING_BOUNDARY_SLOWDOWN_STRENGTH.getPath());
        if (!widthSpec.test(width) || !strengthSpec.test(strength)) {
            throw new IllegalArgumentException("Invalid storm boundary slowdown settings");
        }
        return new LightningWeatherPacket(dimension, List.copyOf(arenas), width, strength);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightningWeatherVisuals.receive(this));
    }
}
