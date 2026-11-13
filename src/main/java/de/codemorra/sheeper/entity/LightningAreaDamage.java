package de.codemorra.sheeper.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shared cylindrical hit area and solid-cover checks for bolts and electrical
 * zones.
 */
final class LightningAreaDamage {
    private LightningAreaDamage() {
    }

    static boolean contains(LightningSheeperEntity boss, ServerPlayer player, Vec3 pos, double radius, double height) {
        AABB bounds = player.getBoundingBox();
        double dx = Mth.clamp(pos.x, bounds.minX, bounds.maxX) - pos.x;
        double dz = Mth.clamp(pos.z, bounds.minZ, bounds.maxZ) - pos.z;
        if (dx * dx + dz * dz > radius * radius || bounds.maxY <= pos.y || bounds.minY >= pos.y + height) {
            return false;
        }
        ServerLevel level = (ServerLevel) boss.level;
        Vec3 from = pos.add(0.0D, 0.1D, 0.0D);
        Vec3 to = bounds.getCenter();
        AABB rayBounds = new AABB(from, to).inflate(1.0D);
        for (int x = Mth.floor(rayBounds.minX) >> 4; x <= Mth.floor(rayBounds.maxX) >> 4; x++) {
            for (int z = Mth.floor(rayBounds.minZ) >> 4; z <= Mth.floor(rayBounds.maxZ) >> 4; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, boss)).getType() == HitResult.Type.MISS;
    }
}
