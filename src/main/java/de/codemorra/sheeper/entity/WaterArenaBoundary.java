package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Marks the combat circle with sparse spray and stronger nearby water droplets.
 * Uses discovered arena surfaces without changing terrain or loading chunks.
 */
final class WaterArenaBoundary {
    private record Point(double x, double z, BlockPos surface) {
    }

    private final WaterSheeperEntity boss;
    private final List<Point> points = new ArrayList<>();
    private int surfaceCount = -1;
    private double cachedRadius;
    private double cachedSpacing;
    private long nextEmissionTick;

    /**
     * @param boss owner whose fixed origin and combat radius define the visible
     *             boundary
     */
    WaterArenaBoundary(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    /**
     * Emits cosmetic effects for nearby viewers, including players just outside the
     * arena.
     */
    void tick() {
        if (!SheeperConfig.WATER_BOUNDARY_ENABLED.get()) {
            return;
        }
        ServerLevel level = (ServerLevel) boss.level;
        long now = level.getGameTime();
        if (now < nextEmissionTick) {
            return;
        }
        nextEmissionTick = now + SheeperConfig.secondsToTicks(SheeperConfig.WATER_BOUNDARY_INTERVAL.get());
        double radius = boss.getCombatRadius();
        double spacing = SheeperConfig.WATER_BOUNDARY_SPACING.get();
        if (surfaceCount != boss.getArenaSurfacePositions().size()
                || cachedRadius != radius || cachedSpacing != spacing) {
            rebuildPoints(radius, spacing);
        }
        double warning = SheeperConfig.WATER_BOUNDARY_WARNING_DISTANCE.get();
        double centerX = boss.getArenaCenter().getX() + 0.5D;
        double centerZ = boss.getArenaCenter().getZ() + 0.5D;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - centerX;
            double dz = player.getZ() - centerZ;
            if (!player.isAlive() || player.isSpectator() || dx * dx + dz * dz > (radius + warning) * (radius + warning)
                    || Math.abs(player.getY() - boss.getArenaCenter().getY()) > SheeperConfig.WATER_COMBAT_HEIGHT_RANGE
                            .get() + warning) {
                continue;
            }
            for (Point point : points) {
                emitPoint(level, player, point, warning);
            }
        }
    }

    /**
     * Caches bounded samples on the exact combat circle. A nearby inside surface
     * supplies the height when rounding places the circle in an undiscovered cell.
     *
     * @param radius  current combat radius in blocks
     * @param spacing requested horizontal particle spacing in blocks
     */
    private void rebuildPoints(double radius, double spacing) {
        points.clear();
        Map<Long, BlockPos> columns = new HashMap<>();
        BlockPos center = boss.getArenaCenter();
        for (BlockPos surface : boss.getArenaSurfacePositions()) {
            long key = BlockPos.asLong(surface.getX(), 0, surface.getZ());
            columns.merge(key, surface,
                    (first, second) -> Math.abs(first.getY() - center.getY()) <= Math.abs(second.getY() - center.getY())
                            ? first
                            : second);
        }
        int count = Mth.clamp((int) Math.ceil(2 * Math.PI * radius / spacing), 12, 512);
        for (int index = 0; index < count; index++) {
            double angle = 2 * Math.PI * index / count;
            double x = center.getX() + 0.5D + Math.cos(angle) * radius;
            double z = center.getZ() + 0.5D + Math.sin(angle) * radius;
            for (double inward = 0; inward <= Math.min(2, radius); inward += 0.5D) {
                int columnX = Mth.floor(x - Math.cos(angle) * inward);
                int columnZ = Mth.floor(z - Math.sin(angle) * inward);
                BlockPos surface = columns.get(BlockPos.asLong(columnX, 0, columnZ));
                if (surface != null) {
                    points.add(new Point(x, z, surface));
                    break;
                }
            }
        }
        surfaceCount = boss.getArenaSurfacePositions().size();
        cachedRadius = radius;
        cachedSpacing = spacing;
    }

    /**
     * Shows spray at surface height and adds droplets near the viewer. Ordinary
     * particle visibility limits apply; walls and unloaded chunks are skipped.
     *
     * @param level   server world containing the arena
     * @param player  viewer receiving this point's effects
     * @param point   cached horizontal boundary sample and its reference surface
     * @param warning distance within which the point becomes more visible
     */
    private void emitPoint(ServerLevel level, ServerPlayer player, Point point, double warning) {
        if (!WaterArenaData.isLoaded(level, point.surface())) {
            return;
        }
        double y = point.surface().getY() + level.getFluidState(point.surface()).getHeight(level, point.surface())
                + 0.15D;
        BlockPos position = new BlockPos(point.x(), y, point.z());
        if (!WaterArenaData.isLoaded(level, position) || player.distanceToSqr(point.x(), y, point.z()) > 32 * 32
                || !level.getBlockState(position).getCollisionShape(level, position).isEmpty()) {
            return;
        }
        boolean nearby = player.distanceToSqr(point.x(), y, point.z()) <= warning * warning;
        level.sendParticles(player, ParticleTypes.SPLASH, false, point.x(), y, point.z(),
                nearby ? 3 : 1, 0.08D, 0.05D, 0.08D, 0.02D);
        if (nearby) {
            level.sendParticles(player, ParticleTypes.FALLING_WATER, false, point.x(), y + 0.4D, point.z(),
                    1, 0.08D, 0.1D, 0.08D, 0);
        }
    }
}
