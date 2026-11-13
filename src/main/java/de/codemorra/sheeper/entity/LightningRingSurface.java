package de.codemorra.sheeper.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Shared local surface sampling for ring rendering and damage, including
 * partial blocks.
 */
public final class LightningRingSurface {
    private LightningRingSurface() {
    }

    /**
     * Returns the nearest exposed floor at this point, or NaN without loading
     * chunks.
     */
    public static double find(Level level, double x, double z, double referenceY, int minY, int maxY) {
        minY = Math.max(minY, level.getMinBuildHeight());
        maxY = Math.min(maxY, level.getMaxBuildHeight() - 1);
        if (minY > maxY) {
            return Double.NaN;
        }
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        if (!level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
            return Double.NaN;
        }
        int reference = Mth.clamp(Mth.floor(referenceY) - 1, minY - 1, maxY);
        int distance = Math.max(reference - minY + 1, maxY - reference);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int offset = 0; offset <= distance; offset++) {
            for (int side = 0; side < (offset == 0 ? 1 : 2); side++) {
                int y = reference + (side == 0 ? -offset : offset);
                if (y < minY - 1 || y > maxY || y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                    continue;
                }
                pos.set(blockX, y, blockZ);
                double top = Double.NEGATIVE_INFINITY;
                for (AABB box : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
                    if (x - blockX >= box.minX && x - blockX <= box.maxX
                            && z - blockZ >= box.minZ && z - blockZ <= box.maxZ) {
                        top = Math.max(top, box.maxY);
                    }
                }
                double surface = y + top;
                if (!Double.isFinite(surface) || surface < minY || surface > maxY) {
                    continue;
                }
                BlockPos above = new BlockPos(x, surface + 0.05D, z);
                boolean covered = false;
                for (AABB box : level.getBlockState(above).getCollisionShape(level, above).toAabbs()) {
                    if (box.contains(x - above.getX(), surface + 0.05D - above.getY(), z - above.getZ())) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    return surface;
                }
            }
        }
        return Double.NaN;
    }
}
