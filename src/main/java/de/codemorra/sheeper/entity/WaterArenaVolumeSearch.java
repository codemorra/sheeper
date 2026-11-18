package de.codemorra.sheeper.entity;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import javax.annotation.Nullable;

import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Discovers connected floodable cells inside a captured cylindrical volume.
 * Works incrementally without loading chunks or changing terrain during search.
 * Separate surface discovery remains available to terrain-bound visual effects
 * and fountains.
 */
final class WaterArenaVolumeSearch {
    private final ServerLevel level;
    private final WaterArenaData.VolumeBounds bounds;
    private final Queue<BlockPos> pending = new ArrayDeque<>();
    private final Map<BlockPos, Integer> distances = new HashMap<>();
    private final Queue<BlockPos> discovered = new ArrayDeque<>();

    /**
     * @param level  server world whose connected open cells are examined
     * @param bounds fixed volume and seed captured when this arena first starts
     *               filling
     */
    WaterArenaVolumeSearch(ServerLevel level, WaterArenaData.VolumeBounds bounds) {
        this.level = level;
        this.bounds = bounds;
        pending.add(bounds.center());
        distances.put(bounds.center(), 0);
    }

    /**
     * Examines a bounded number of cells. Unloaded cells are retried on later
     * ticks;
     * walls, doors and closed block faces prevent water entering isolated cavities.
     *
     * @param budget maximum queued cells examined during this tick
     */
    void advance(int budget) {
        Queue<BlockPos> deferred = new ArrayDeque<>();
        while (budget-- > 0 && !pending.isEmpty()) {
            BlockPos pos = pending.remove();
            if (!WaterArenaData.isLoaded(level, pos)) {
                deferred.add(pos);
                continue;
            }
            if (!bounds.contains(pos) || !WaterArenaSearch.isOpen(level, pos)) {
                continue;
            }
            discovered.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!bounds.contains(next) || distances.containsKey(next)) {
                    continue;
                }
                if (WaterArenaData.isLoaded(level, next)
                        && Shapes.mergedFaceOccludes(level.getBlockState(pos).getCollisionShape(level, pos),
                                level.getBlockState(next).getCollisionShape(level, next), direction)) {
                    continue;
                }
                distances.put(next, distances.get(pos) + 1);
                pending.add(next);
            }
        }
        pending.addAll(deferred);
    }

    /**
     * @return whether all connected cells, including deferred loaded chunks, have
     *         been examined
     */
    boolean isComplete() {
        return pending.isEmpty();
    }

    /**
     * Hands newly discovered cells to incremental placement without waiting for
     * the whole volume search or retaining a second complete cell collection.
     *
     * @return next discovered cell, or null when placement has caught up
     */
    @Nullable
    BlockPos pollDiscoveredCell() {
        return discovered.poll();
    }

    /**
     * @param pos discovered cell whose outward generation/drainage layer is needed
     */
    int getDistance(BlockPos pos) {
        return distances.get(pos);
    }
}
