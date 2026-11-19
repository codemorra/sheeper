package de.codemorra.sheeper.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;

/**
 * Positional breathing and player-floor collision without changing water blocks
 * or granting potion effects.
 */
public final class ArenaWater {
    public static final double BOUNDARY_MARGIN = 0.15D;
    private static Function<Level, List<WaterArenaData.VolumeBounds>> clientLookup = level -> List.of();

    private ArenaWater() {
    }

    /**
     * Installs the client snapshot lookup without loading client classes on a
     * dedicated server.
     */
    public static void setClientLookup(Function<Level, List<WaterArenaData.VolumeBounds>> lookup) {
        clientLookup = lookup;
    }

    /**
     * Called by the Forge drowning hook before air consumption. Only players in
     * ordinary water inside a live captured arena gain the same protection as boss
     * water.
     */
    public static boolean allowsBreathing(LivingEntity entity, FluidType type) {
        if (!(entity instanceof Player) || type != ForgeMod.WATER_TYPE.get()) {
            return false;
        }
        BlockPos eye = new BlockPos(entity.getEyePosition());
        return activeVolumes(entity.level).stream().anyMatch(bounds -> bounds.contains(eye));
    }

    /**
     * Adds a one-way floor to normal collision resolution on both logical sides.
     * Vertical clipping then precedes horizontal sliding, just as with real blocks;
     * upward movement and lateral arena exits never acquire an artificial wall.
     *
     * @param collisions existing entity collision shapes, retained unchanged
     * @param entity entity whose movement is being resolved
     * @param movement requested displacement before block collision resolution
     * @return original shapes or a copy containing the crossed arena floors
     */
    public static List<VoxelShape> floorCollisions(List<VoxelShape> collisions, Entity entity, Vec3 movement) {
        if (!(entity instanceof Player player) || player.isCreative() || player.isSpectator()
                || !player.isAlive() || movement.y >= 0) {
            return collisions;
        }
        if (!entity.level.getFluidState(new BlockPos(entity.getEyePosition())).is(FluidTags.WATER)
                && !entity.level.getFluidState(entity.blockPosition()).is(FluidTags.WATER)) {
            return collisions;
        }
        List<VoxelShape> result = null;
        for (var bounds : activeVolumes(entity.level)) {
            double floor = bounds.minimumY() + BOUNDARY_MARGIN;
            if (entity.getY() < floor - 1.0E-5D || entity.getY() + movement.y >= floor
                    || !insideColumn(bounds, entity.getX(), entity.getZ())
                    || !insideColumn(bounds, entity.getX() + movement.x, entity.getZ() + movement.z)) {
                continue;
            }
            if (result == null) {
                result = new ArrayList<>(collisions);
            }
            var body = entity.getBoundingBox().expandTowards(movement);
            // Sub-pixel rounding must not start the next collision inside the
            // floor shape, which vanilla would then allow the player to leave downward.
            double surface = Math.min(floor, entity.getY());
            result.add(Shapes.box(body.minX - 1, surface - 1, body.minZ - 1,
                    body.maxX + 1, surface, body.maxZ + 1));
            entity.fallDistance = 0;
        }
        return result == null ? collisions : result;
    }

    private static List<WaterArenaData.VolumeBounds> activeVolumes(Level level) {
        return level instanceof ServerLevel server ? WaterArenaData.activeVolumes(server) : clientLookup.apply(level);
    }

    /** Uses player-center membership, matching the arena's lateral exit rather than block-cell rounding. */
    private static boolean insideColumn(WaterArenaData.VolumeBounds bounds, double x, double z) {
        double dx = x - bounds.center().getX() - 0.5D;
        double dz = z - bounds.center().getZ() - 0.5D;
        return dx * dx + dz * dz <= bounds.radius() * bounds.radius();
    }
}
