package de.codemorra.sheeper.world;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;

/** Makes virtual water navigable to the drowned's vanilla swimming AI. */
public final class DrownedArenaPaths {
    private DrownedArenaPaths() {
    }

    /**
     * Classifies the cells covered by a drowned's swimming node. A path can cross
     * between natural and virtual water when every cell is wet and the mob fits.
     *
     * @return a path type for nodes touching virtual water, or null to defer to vanilla
     */
    @Nullable
    public static BlockPathTypes classify(BlockGetter blocks, int x, int y, int z, Mob mob,
            int width, int height, int depth) {
        if (!(mob instanceof Drowned) || !(mob.level instanceof ServerLevel level)
                || !(blocks instanceof PathNavigationRegion region)) {
            return null;
        }
        var volumes = WaterArenaData.activeVolumes(level);
        if (volumes.isEmpty()) {
            return null;
        }

        boolean virtual = false;
        boolean blocked = false;
        var cell = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < height; dy++) {
                for (int dz = 0; dz < depth; dz++) {
                    cell.set(x + dx, y + dy, z + dz);
                    var fluid = region.getFluidState(cell);
                    if (fluid.is(FluidTags.WATER)) {
                        continue;
                    }
                    if (fluid.isEmpty() && volumes.stream().anyMatch(bounds -> bounds.contains(cell))) {
                        virtual = true;
                        // The navigation snapshot returns air for missing chunks.
                        blocked |= !region.getWorldBorder().isWithinBounds(cell)
                                || region.getChunkForCollisions(SectionPos.blockToSectionCoord(cell.getX()),
                                        SectionPos.blockToSectionCoord(cell.getZ())) instanceof EmptyLevelChunk;
                    } else {
                        blocked = true;
                    }
                }
            }
        }
        if (!virtual) {
            return null;
        }

        // Match Path's node offset and check the real collision shapes at the destination.
        var body = mob.getBoundingBox().move(x + width * 0.5 - mob.getX(), y - mob.getY(),
                z + depth * 0.5 - mob.getZ());
        return !blocked && region.noCollision(mob, body) ? BlockPathTypes.WATER : BlockPathTypes.BLOCKED;
    }

    /** Keeps virtual water from incurring vanilla's surcharge for dry swimming nodes. */
    public static boolean isDryNode(FluidState fluid, Mob mob, BlockPathTypes type) {
        return fluid.isEmpty() && (!(mob instanceof Drowned) || type != BlockPathTypes.WATER);
    }
}
