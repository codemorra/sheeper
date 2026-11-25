package de.codemorra.sheeper.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared terrain-relative funnel geometry for player movement, damage, and presentation. */
public record LightningTornado(BlockPos arenaCenter, double arenaRadius, int minimumY, int maximumY,
        Vec3 position, Vec3 velocity, double radius, double height, double strength, long sampleTick,
        long activeFrom, long dissolveFrom, long endTick) {
    public static final int MAX_FUNNELS_PER_ATTACK = 12;

    public Vec3 centerAt(double now) {
        return position.add(velocity.scale(Mth.clamp(now - sampleTick, 0, 3)));
    }

    public double radiusAtHeight(double heightFraction) {
        return radius * Mth.lerp(Mth.clamp(heightFraction, 0, 1), 0.72, 1.35);
    }

    /** Ramps the wind in after the warning and lets the funnel thin out before it dissipates. */
    public double powerAt(double now) {
        return Mth.clamp((now - activeFrom) / 9, 0, 1) * fadeAt(now);
    }

    public double fadeAt(double now) {
        return Mth.clamp((endTick - now) / (endTick - (double) dissolveFrom), 0, 1);
    }

    public boolean isDissolving(double now) {
        return now >= dissolveFrom && now < endTick;
    }

    public boolean isInsideArena(Vec3 point) {
        double dx = point.x - arenaCenter.getX() - 0.5;
        double dz = point.z - arenaCenter.getZ() - 0.5;
        return point.y >= minimumY && point.y < maximumY && dx * dx + dz * dz <= arenaRadius * arenaRadius;
    }

    /** Gives a soft rim that can be crossed even while the core is pulling strongly. */
    public double exposure(Vec3 point, double now) {
        return powerAt(now) * contact(point, now);
    }

    /** Measures contact before the time envelope, so a fading funnel can still guide a descent. */
    double contact(Vec3 point, double now) {
        if (!isInsideArena(point)) {
            return 0;
        }
        Vec3 center = centerAt(now);
        Vec3 offset = point.subtract(center);
        if (offset.y < -0.5 || offset.y >= height) {
            return 0;
        }
        double fraction = offset.horizontalDistance() / radiusAtHeight(offset.y / height);
        double rim = Mth.clamp((1 - fraction) * 2.5, 0, 1);
        return rim * Mth.clamp((height - offset.y) * 0.75, 0, 1);
    }

    /** Identifies the part of a dissolving funnel that is still supporting a descending player. */
    public boolean supportsDescent(Vec3 point, long now) {
        return isDissolving(now) && now - dissolveFrom >= 4
                && point.y > centerAt(now).y + 0.3 && contact(point, now) * strength >= 0.18;
    }

    /** A steep ledge, wall or roof between the funnel and the player stops its local force. */
    public static boolean hasClearPath(Level level, Player player, Vec3 center) {
        Vec3 target = player.getBoundingBox().getCenter();
        Vec3 inside = new Vec3(center.x, Math.max(center.y + 0.15, target.y), center.z);
        int minChunkX = Mth.floor(Math.min(inside.x, target.x)) >> 4;
        int maxChunkX = Mth.floor(Math.max(inside.x, target.x)) >> 4;
        int minChunkZ = Mth.floor(Math.min(inside.z, target.z)) >> 4;
        int maxChunkZ = Mth.floor(Math.max(inside.z, target.z)) >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return level.clip(new ClipContext(inside, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }
}
