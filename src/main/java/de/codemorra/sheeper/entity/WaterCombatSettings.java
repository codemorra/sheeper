package de.codemorra.sheeper.entity;

import java.util.HashSet;
import java.util.Set;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.world.ArenaWater;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Resolves dependent combat settings without changing user configuration
 * values.
 */
final class WaterCombatSettings {
    private final WaterSheeperEntity boss;
    private final Set<String> warnings = new HashSet<>();

    WaterCombatSettings(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    /** Checks the candidate against the captured water bounds and nearby terrain. */
    boolean fitsSpawn(ServerLevel level, Vec3 position) {
        BlockPos center = new BlockPos(position);
        var bounds = WaterArenaData.VolumeBounds.capture(level, center, boss.getCombatRadius(),
                WaterSheeperConfig.WATER_ARENA_DEPTH.get(), WaterSheeperConfig.WATER_ARENA_HEIGHT.get());
        AABB body = boss.getBoundingBox().move(position.subtract(boss.position()));
        return !level.isOutsideBuildHeight(center) && bounds.isValidFor(level) && body.minY >= bounds.minimumY()
                && body.maxY + ArenaWater.BOUNDARY_MARGIN <= bounds.maximumY() + 1
                && ArenaSpawnPlacement.fitsTerrain(level, boss, body) && hasArenaRoom(bounds);
    }

    /**
     * Keeps the stopping point inside melee entry and any enabled proximity
     * defense.
     */
    double meleeStopDistance() {
        double configured = WaterSheeperConfig.WATER_CLOSE_RANGE_STOP_DISTANCE.get();
        double entry = WaterSheeperConfig.WATER_CLOSE_RANGE_DISTANCE.get();
        // Very small custom entry distances still need a positive stopping point.
        double maximum = entry - Math.min(0.5D, entry * 0.5D);
        double pushRadius = WaterSheeperConfig.WATER_PROXIMITY_PUSH_RADIUS.get();
        boolean pushEnabled = pushRadius > 0 && (WaterSheeperConfig.WATER_PROXIMITY_PUSH_DAMAGE.get() > 0
                || WaterSheeperConfig.WATER_PROXIMITY_PUSH_STRENGTH.get() > 0
                || WaterSheeperConfig.WATER_PROXIMITY_PUSH_UPWARD_STRENGTH.get() > 0);
        if (pushEnabled) {
            maximum = Math.min(maximum, pushRadius);
        }
        double effective = Math.min(configured, maximum);
        if (effective < configured) {
            warnOnce("Melee stop distance " + configured + " exceeds the usable limit " + effective
                    + " (closeRange.distance=" + entry + ", proximityPush.radius=" + pushRadius
                    + "). Using " + effective + " without modifying the config.");
        }
        return effective;
    }

    /** Preserves a central danger region even when the arena radius is reduced. */
    double safeOuterWidth() {
        double configured = WaterSheeperConfig.WATER_MAELSTROM_SAFE_OUTER_WIDTH.get();
        double effective = Math.min(configured, boss.getArenaRadius() * 0.5D);
        if (effective < configured) {
            warnOnce("Maelstrom safeOuterWidth=" + configured + " exceeds half of the arena radius="
                    + boss.getArenaRadius() + ". Using " + effective + " without modifying the config.");
        }
        return effective;
    }

    /**
     * Checks captured geometry against the current body and movement clearance.
     * This validates configuration space, not whether terrain blocks a particular
     * path.
     */
    boolean hasArenaRoom(WaterArenaData.VolumeBounds bounds) {
        double radius = Math.min(bounds.radius(), boss.getCombatRadius());
        double requiredRadius = boss.getBbWidth() * Math.sqrt(0.5D) + 0.25D
                + WaterSheeperConfig.WATER_MOVEMENT_EDGE_DISTANCE.get();
        double height = bounds.maximumY() + 1.0D - bounds.minimumY();
        double requiredHeight = boss.getBbHeight() + 0.15D;
        if (radius <= requiredRadius || height < requiredHeight) {
            warnOnce("Arena configuration cannot fit the boss: usable radius=" + radius
                    + " must exceed " + requiredRadius + " for its size and movement.edgeDistance; captured height="
                    + height + " must be at least " + requiredHeight
                    + ". Combat is disabled; check size, combatRadius, movement.edgeDistance and captured water bounds.");
            return false;
        }
        return true;
    }

    /**
     * Logs each distinct conflicting combination once per loaded boss, avoiding
     * tick spam.
     */
    private void warnOnce(String message) {
        if (!boss.level.isClientSide && warnings.add(message)) {
            SheeperMod.LOGGER.warn("Water Sheeper {} at {}: {}", boss.getUUID(), boss.getArenaCenter(), message);
        }
    }
}
