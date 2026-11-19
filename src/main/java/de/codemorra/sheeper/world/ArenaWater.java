package de.codemorra.sheeper.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import de.codemorra.sheeper.entity.WaterSheeperEntity;
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

    /** Tests the exact cylindrical water volume on either logical side. */
    public static boolean contains(Level level, Vec3 point) {
        return activeVolumes(level).stream().anyMatch(bounds -> bounds.contains(point));
    }

    /**
     * Checks terrain clearance without requiring a fluid block. Non-colliding
     * plants and attachments remain intact; solid shapes and other fluids block
     * fountain columns and water particles.
     */
    public static boolean isOpenCell(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        var fluid = state.getFluidState();
        return (fluid.isEmpty() || fluid.is(FluidTags.WATER))
                && state.getCollisionShape(level, pos).isEmpty();
    }

    /** Tests both active-volume membership and terrain clearance for a visual sample. */
    public static boolean isOpenAt(Level level, Vec3 point) {
        return contains(level, point) && isOpenCell(level, new BlockPos(point));
    }

    /**
     * Gives players and the arena owner vanilla water movement without placing
     * fluid blocks. Other creatures and foreign fluids retain their own physics.
     * Called after Forge has collected the real fluid heights for this tick.
     */
    public static double waterHeight(Entity entity) {
        if (!supportsSwimming(entity) || entity.getFluidTypeHeight(ForgeMod.LAVA_TYPE.get()) > 0) {
            return 0;
        }
        double height = 0;
        var body = entity.getBoundingBox();
        for (var bounds : activeVolumes(entity.level)) {
            if (insideColumn(bounds, entity.getX(), entity.getZ())) {
                height = Math.max(height, Math.min(body.maxY, bounds.maximumY() + 1.0D)
                        - Math.max(body.minY, bounds.minimumY()));
            }
        }
        return height > 0 ? Math.max(height, entity.getFluidTypeHeight(ForgeMod.WATER_TYPE.get())) : 0;
    }

    /** Supplies Forge's eye-fluid lookup while preserving lava and other real fluids. */
    public static boolean isEyeSubmerged(Entity entity) {
        if (!supportsSwimming(entity) || !contains(entity.level, entity.getEyePosition())) {
            return false;
        }
        var fluid = entity.level.getFluidState(new BlockPos(entity.getEyePosition()));
        return fluid.isEmpty() || fluid.is(FluidTags.WATER);
    }

    /** Keeps vanilla look-directed swimming when the virtual water cells contain air. */
    public static boolean isEmptyForSwimming(net.minecraft.world.level.material.FluidState state, Entity entity) {
        return state.isEmpty() && !(supportsSwimming(entity)
                && contains(entity.level, entity.position().add(0, 0.9D, 0)));
    }

    private static boolean supportsSwimming(Entity entity) {
        return entity.isAlive() && (entity instanceof Player player && !player.isSpectator()
                || entity instanceof WaterSheeperEntity);
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
        return contains(entity.level, entity.getEyePosition());
    }

    /**
     * Clips movement against the virtual floor and a ceiling that permits entry
     * from above. Using ordinary collision keeps horizontal sliding and swimming
     * prediction consistent on both sides; lateral exits remain open.
     *
     * @param collisions existing entity collision shapes, retained unchanged
     * @param entity entity whose movement is being resolved
     * @param movement requested displacement before block collision resolution
     * @return original shapes or a copy containing crossed arena boundaries
     */
    public static List<VoxelShape> boundaryCollisions(List<VoxelShape> collisions, Entity entity, Vec3 movement) {
        if (!(entity instanceof Player player) || player.isCreative() || player.isSpectator()
                || !player.isAlive() || movement.y == 0) {
            return collisions;
        }
        List<VoxelShape> result = null;
        for (var bounds : activeVolumes(entity.level)) {
            if (!insideColumn(bounds, entity.getX(), entity.getZ())
                    || !insideColumn(bounds, entity.getX() + movement.x, entity.getZ() + movement.z)) {
                continue;
            }
            double floor = bounds.minimumY() + BOUNDARY_MARGIN;
            double ceiling = bounds.maximumY() + 1.0D - BOUNDARY_MARGIN;
            double feet = entity.getY();
            double head = entity.getBoundingBox().maxY;
            boolean crossesFloor = movement.y < 0 && feet >= floor - 1.0E-5D && feet + movement.y < floor;
            boolean crossesCeiling = movement.y > 0 && head <= ceiling + 1.0E-5D && head + movement.y > ceiling;
            if (!crossesFloor && !crossesCeiling) {
                continue;
            }
            if (result == null) {
                result = new ArrayList<>(collisions);
            }
            var body = entity.getBoundingBox().expandTowards(movement);
            // Align with sub-pixel rounding so the next tick cannot begin inside
            // the artificial shape and slip through it.
            double surface = crossesFloor ? Math.min(floor, feet) : Math.max(ceiling, head);
            result.add(Shapes.box(body.minX - 1, crossesFloor ? surface - 1 : surface, body.minZ - 1,
                    body.maxX + 1, crossesFloor ? surface : surface + 1, body.maxZ + 1));
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
