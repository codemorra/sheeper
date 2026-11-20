package de.codemorra.sheeper.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.world.WaterArenaData.AirColumn;
import de.codemorra.sheeper.world.WaterArenaData.AirState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
    private static Function<Level, List<AirColumn>> clientAirColumns = level -> List.of();

    private ArenaWater() {
    }

    /**
     * Installs the client snapshot lookup without loading client classes on a
     * dedicated server.
     */
    public static void setClientLookup(Function<Level, List<WaterArenaData.VolumeBounds>> lookup) {
        clientLookup = lookup;
    }

    /** Supplies nearby fountain geometry to the local swimming controller. */
    public static void setClientAirColumns(Function<Level, List<AirColumn>> lookup) {
        clientAirColumns = lookup;
    }

    /** Tests the exact cylindrical water volume on either logical side. */
    public static boolean contains(Level level, Vec3 point) {
        for (var bounds : activeVolumes(level)) {
            if (bounds.contains(point)) {
                return true;
            }
        }
        return false;
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

    /** Checks the free part of a block at the player's eyes or a bubble spawn point. */
    public static boolean isClearWaterPoint(Level level, Vec3 point) {
        return isClearWaterColumn(level, point, point.y);
    }

    /** Checks vertical travel against collision shapes, including thin blocks between particle ticks. */
    public static boolean isClearWaterColumn(Level level, Vec3 point, double previousY) {
        double low = Math.min(previousY, point.y);
        double high = Math.max(previousY, point.y);
        BlockPos pos = new BlockPos(point.x, low, point.z);
        double x = point.x - pos.getX();
        double z = point.z - pos.getZ();
        for (int y = pos.getY(); y <= Math.floor(high); y++) {
            BlockPos cell = pos.atY(y);
            var state = level.getBlockState(cell);
            var fluid = state.getFluidState();
            if (!fluid.isEmpty() && !fluid.is(FluidTags.WATER)) {
                return false;
            }
            var shape = state.getCollisionShape(level, cell);
            if (shape.isEmpty()) {
                continue;
            }
            for (var box : shape.toAabbs()) {
                if (x >= box.minX && x < box.maxX && z >= box.minZ && z < box.maxZ
                        && high >= y + box.minY && low < y + box.maxY) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Reports virtual water through Forge's normal fluid state so living entities
     * use their own water behavior. This runs after Forge detects physical fluids.
     */
    public static double waterHeight(Entity entity) {
        if (!supportsSwimming(entity)) {
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
        if (height <= 0) {
            return 0;
        }
        FluidType water = ForgeMod.WATER_TYPE.get();
        if (entity.isInFluidType((type, depth) -> type != water && !type.isAir() && depth > 0)) {
            return 0;
        }
        return Math.max(height, entity.getFluidTypeHeight(water));
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
        return entity instanceof LivingEntity && entity.isAlive()
                && (!(entity instanceof Player player) || !player.isCreative() && !player.isSpectator());
    }

    /**
     * Shelters players during setup, drainage and where no usable route to air
     * exists. Bubble columns allow the normal air recovery branch to run.
     */
    public static boolean allowsBreathing(LivingEntity entity, FluidType type) {
        if (!(entity instanceof ServerPlayer player) || !supportsSwimming(player) || type != ForgeMod.WATER_TYPE.get()) {
            return false;
        }
        AirState state = WaterArenaData.airState(player);
        return state == AirState.SHELTERED || state == AirState.REFILLING;
    }

    /** Throttles the vanilla result so Respiration and drowning damage keep their normal behavior. */
    public static int adjustAirLoss(int vanilla, LivingEntity entity) {
        if (!(entity instanceof Player player) || !supportsSwimming(player)
                || player.getEyeInFluidType() != ForgeMod.WATER_TYPE.get()) {
            return vanilla;
        }
        int air = player.getAirSupply();
        if (player.level.isClientSide) {
            // The server synchronizes the air bar. Local water ticks must not run a second countdown.
            return contains(player.level, player.getEyePosition()) ? air : vanilla;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || air <= 0
                || WaterArenaData.airState(serverPlayer) != AirState.CONSUMING) {
            return vanilla;
        }
        int duration = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_AIR_DURATION.get());
        int capacity = player.getMaxAirSupply();
        long tick = Math.floorMod(player.level.getGameTime(), duration);
        return (tick + 1) * capacity / duration > tick * capacity / duration ? vanilla : air;
    }

    /** A short pass through a fountain restores a noticeable part of the air bar. */
    public static int adjustAirRecovery(int vanilla, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && supportsSwimming(player)
                && player.getEyeInFluidType() == ForgeMod.WATER_TYPE.get()
                && WaterArenaData.airState(player) == AirState.REFILLING) {
            int capacity = player.getMaxAirSupply();
            return Math.min(capacity, player.getAirSupply() + Math.max(1, capacity / 10));
        }
        return vanilla;
    }

    /** Adds a small upward acceleration after water drag without replacing input or knockback. */
    public static Vec3 applyAirFountainLift(Vec3 movement, LivingEntity entity) {
        if (!(entity instanceof Player player) || !supportsSwimming(player) || player.getAbilities().flying
                || player.isPassenger() || !player.isInWater() || player.isInLava()
                || player.getEyeInFluidType() != ForgeMod.WATER_TYPE.get() || movement.y >= 0.04) {
            return movement;
        }
        Vec3 eye = player.getEyePosition();
        var columns = player.level instanceof ServerLevel server ? WaterArenaData.airColumns(server, eye)
                : clientAirColumns.apply(player.level);
        for (AirColumn column : columns) {
            if (column.contains(eye) && player.getBoundingBox().maxY + BOUNDARY_MARGIN + 0.04 < column.surface()
                    && isClearWaterPoint(player.level, eye)) {
                return new Vec3(movement.x, Math.min(0.04, movement.y + 0.01), movement.z);
            }
        }
        return movement;
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
        var volumes = activeVolumes(entity.level);
        for (var bounds : volumes) {
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
            // A face covered by another volume is not an outer water boundary.
            double boundaryY = crossesFloor ? floor - BOUNDARY_MARGIN - 1.0E-5D
                    : ceiling + BOUNDARY_MARGIN + 1.0E-5D;
            Vec3 crossing = new Vec3(entity.getX() + movement.x, boundaryY, entity.getZ() + movement.z);
            if ((!crossesFloor && !crossesCeiling)
                    || volumes.stream().anyMatch(other -> other != bounds && other.contains(crossing))) {
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

    /** Current water geometry shared by physics, protection and overlap-aware boundary calculations. */
    public static List<WaterArenaData.VolumeBounds> activeVolumes(Level level) {
        return level instanceof ServerLevel server ? WaterArenaData.activeVolumes(server) : clientLookup.apply(level);
    }

    /** Uses player-center membership, matching the arena's lateral exit rather than block-cell rounding. */
    private static boolean insideColumn(WaterArenaData.VolumeBounds bounds, double x, double z) {
        double dx = x - bounds.center().getX() - 0.5D;
        double dz = z - bounds.center().getZ() - 0.5D;
        return dx * dx + dz * dz <= bounds.radius() * bounds.radius();
    }
}
