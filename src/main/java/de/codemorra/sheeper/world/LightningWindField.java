package de.codemorra.sheeper.world;

import java.util.List;

import de.codemorra.sheeper.network.LightningWeatherPacket.Arena;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared wind state for one arena, including the continuous turn and its local gusts. */
public record LightningWindField(Arena arena, double fromAngle, double turnAngle, long turnStart,
        double strength, List<LightningWindGust> gusts) {
    static final int TURN_TICKS = 20;
    private static final double[] EXPOSURE_HEIGHTS = { 0.24, 0.61, 0.88 };

    public double angleAt(double time) {
        return fromAngle + turnAngle * Mth.clamp((time - turnStart) / TURN_TICKS, 0, 1);
    }

    public Vec3 directionAt(double time) {
        return direction(angleAt(time));
    }

    /** Integrates the changing heading so lines and gusts keep moving through a turn. */
    public Vec3 travelBetween(double start, double end) {
        if (end <= start) {
            return Vec3.ZERO;
        }
        double turnEnd = turnStart + (double) TURN_TICKS;
        double before = Math.max(0, Math.min(end, turnStart) - start);
        double after = Math.max(0, end - Math.max(start, turnEnd));
        Vec3 displacement = direction(fromAngle).scale(before).add(direction(fromAngle + turnAngle).scale(after));
        double arcStart = Math.max(start, turnStart);
        double arcEnd = Math.min(end, turnEnd);
        if (arcEnd <= arcStart) {
            return displacement;
        }
        double halfTurn = (angleAt(arcEnd) - angleAt(arcStart)) / 2;
        double scale = Math.abs(halfTurn) < 1.0E-6 ? 1 : Math.sin(halfTurn) / halfTurn;
        return displacement.add(direction(angleAt((arcStart + arcEnd) / 2)).scale((arcEnd - arcStart) * scale));
    }

    private static Vec3 direction(double angle) {
        return new Vec3(Math.cos(angle), 0, Math.sin(angle));
    }

    /** Samples legs, chest and head so a low obstacle only shields part of the player. */
    static double exposure(Level level, Player player, Vec3 direction) {
        var bounds = player.getBoundingBox();
        Vec3 center = bounds.getCenter();
        Vec3 upwind = center.subtract(direction.scale(1.3));
        int minChunkX = Mth.floor(Math.min(center.x, upwind.x)) >> 4;
        int maxChunkX = Mth.floor(Math.max(center.x, upwind.x)) >> 4;
        int minChunkZ = Mth.floor(Math.min(center.z, upwind.z)) >> 4;
        int maxChunkZ = Mth.floor(Math.max(center.z, upwind.z)) >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) {
                    return 0;
                }
            }
        }

        int exposed = 0;
        for (double height : EXPOSURE_HEIGHTS) {
            double y = Mth.lerp(height, bounds.minY, bounds.maxY);
            Vec3 source = new Vec3(upwind.x, y, upwind.z);
            Vec3 target = new Vec3(center.x, y, center.z);
            if (level.clip(new ClipContext(source, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                    .getType() == HitResult.Type.MISS) {
                exposed++;
            }
        }
        return (double) exposed / EXPOSURE_HEIGHTS.length;
    }
}
