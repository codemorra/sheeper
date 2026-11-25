package de.codemorra.sheeper.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;

/** Uses the same water connections for lightning damage and its visible discharge. */
public final class LightningWaterVolume {
    private final Level level;
    private final double centerX;
    private final double centerZ;
    private final double radiusSquared;
    private final int minimumY;
    private final int maximumY;

    public LightningWaterVolume(Level level, BlockPos center, double radius, int minimumY, int maximumY) {
        this.level = level;
        centerX = center.getX() + 0.5;
        centerZ = center.getZ() + 0.5;
        radiusSquared = radius * radius;
        this.minimumY = minimumY;
        this.maximumY = maximumY;
    }

    /** Checks the arena bounds and fluid without loading chunks. */
    public boolean hasWater(BlockPos position) {
        double dx = position.getX() + 0.5 - centerX;
        double dz = position.getZ() + 0.5 - centerZ;
        return position.getY() >= minimumY && position.getY() < maximumY && dx * dx + dz * dz <= radiusSquared
                && level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)
                && level.getFluidState(position).is(FluidTags.WATER);
    }

    /** Tests whether two adjacent water cells, already checked by hasWater, touch along their shared face. */
    public boolean connects(BlockPos from, BlockPos to, Direction direction) {
        var fromCollision = level.getBlockState(from).getCollisionShape(level, from);
        var toCollision = level.getBlockState(to).getCollisionShape(level, to);
        if (direction.getAxis().isVertical()) {
            BlockPos lower = direction == Direction.UP ? from : to;
            return level.getFluidState(lower).getHeight(level, lower) >= 1
                    && !Shapes.mergedFaceOccludes(fromCollision, toCollision, direction);
        }
        if (fromCollision.isEmpty() && toCollision.isEmpty()) {
            return true;
        }
        // A low flow cannot conduct through the solid part of an adjacent waterlogged block.
        double height = Math.min(level.getFluidState(from).getHeight(level, from),
                level.getFluidState(to).getHeight(level, to));
        var waterFace = Shapes.getFaceShape(Shapes.box(0, 0, 0, 1, height, 1), direction);
        var blocked = Shapes.or(Shapes.getFaceShape(fromCollision, direction),
                Shapes.getFaceShape(toCollision, direction.getOpposite()));
        return Shapes.joinIsNotEmpty(waterFace, blocked, BooleanOp.ONLY_FIRST);
    }
}
