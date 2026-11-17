package de.codemorra.sheeper.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterArenaSearch;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Approaches and circles the selected player through short, collision-aware
 * waypoints. The entity owns target selection; this goal only plans movement.
 * Player approaches reduce the orbit radius so the boss does not retreat to
 * restore its preferred distance. In close-range mode it approaches directly
 * and stops short of the player's body instead of circling.
 */
public final class WaterOrbitGoal extends Goal {
    private final WaterSheeperEntity boss;
    private UUID targetId;
    private Vec3 previousPlayerPosition;
    private double circleRadius;
    private int direction;
    private long nextPlanTick;
    private long nextDirectionChangeTick;
    private long sprintEndTick;
    private long sprintReadyTick;
    private boolean closingOnTarget;

    /**
     * Creates a movement goal without taking control of the boss's head rotation.
     *
     * @param boss owner whose target and discovered arena surfaces guide movement
     */
    public WaterOrbitGoal(WaterSheeperEntity boss) {
        this.boss = boss;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return boss.isAlive() && !boss.isNoAi() && boss.isCombatReady()
                && boss.getTarget() instanceof ServerPlayer && boss.getMovementRadius() > 0
                && SheeperConfig.WATER_MOVEMENT_SPEED.get() > 0;
    }

    @Override
    public boolean canContinueToUse() {
        // A completed path must not stop the goal before it can plan the next leg.
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        // React to approaching players and end sprint boosts without the usual goal
        // tick delay.
        return true;
    }

    @Override
    public void start() {
        nextPlanTick = 0;
        scheduleDirectionChange(boss.level.getGameTime());
    }

    @Override
    public void tick() {
        ServerPlayer player = (ServerPlayer) boss.getTarget();
        long now = boss.level.getGameTime();
        boolean changed = !player.getUUID().equals(targetId);
        boolean modeChanged = closingOnTarget != boss.isClosingOnTarget();
        closingOnTarget = boss.isClosingOnTarget();
        if (closingOnTarget) {
            sprintEndTick = 0;
            if (horizontal(boss.position().subtract(player.position())).lengthSqr() <= closeRangeDistance(player)
                    * closeRangeDistance(player)) {
                // Leave room for melee attacks instead of continuing through the player.
                updateCircle(player, changed);
                stopHorizontalMovement();
                return;
            }
        }
        boolean directionChanged = !closingOnTarget && !changed && now >= nextDirectionChangeTick;
        if (directionChanged) {
            reverseDirection(now);
        }
        boolean approached = previousPlayerPosition != null
                && player.position().distanceToSqr(previousPlayerPosition) > 0.25D
                && horizontal(player.position().subtract(boss.position())).length() < circleRadius - 0.5D;
        boolean targetMoved = closingOnTarget && previousPlayerPosition != null
                && player.position().distanceToSqr(previousPlayerPosition) > 0.25D;
        if (changed || modeChanged || directionChanged || approached || targetMoved || now >= nextPlanTick) {
            updateCircle(player, changed || modeChanged);
            scheduleNextPlan(now);
            Path path = choosePath(player);
            if (path != null) {
                if (!closingOnTarget) {
                    beginSprint(now);
                }
                boss.getNavigation().moveTo(path, speed(now));
            } else if (changed || modeChanged || approached || targetMoved) {
                // A previous route must not turn into an escape route when the player closes
                // in.
                stopHorizontalMovement();
            }
        }
        boss.getNavigation().setSpeedModifier(speed(now));
    }

    /**
     * Adjusts spacing for player movement without treating the boss's own turns as
     * a reason to shrink the orbit.
     *
     * @param player  current movement target
     * @param changed whether this update starts tracking a different player
     */
    private void updateCircle(ServerPlayer player, boolean changed) {
        double preferred = SheeperConfig.WATER_MOVEMENT_ORBIT_DISTANCE.get();
        double distance = horizontal(boss.position().subtract(player.position())).length();
        if (changed || previousPlayerPosition == null) {
            circleRadius = Math.min(preferred, distance);
            direction = boss.getRandom().nextBoolean() ? 1 : -1;
            scheduleDirectionChange(boss.level.getGameTime());
        } else {
            double previousDistance = horizontal(boss.position().subtract(previousPlayerPosition)).length();
            // Only player movement changes the radius; cutting a chord must not shrink it
            // every update.
            if (distance < previousDistance - 0.1D) {
                circleRadius = Math.min(circleRadius, distance);
            } else if (distance > previousDistance + 0.1D) {
                circleRadius += distance - previousDistance;
            }
            circleRadius = Math.min(preferred, circleRadius);
        }
        targetId = player.getUUID();
        previousPlayerPosition = player.position();
    }

    private void scheduleNextPlan(long now) {
        int minimum = SheeperConfig.secondsToTicks(SheeperConfig.WATER_MOVEMENT_INTERVAL_MIN.get());
        int maximum = Math.max(minimum, SheeperConfig.secondsToTicks(SheeperConfig.WATER_MOVEMENT_INTERVAL_MAX.get()));
        nextPlanTick = now + minimum + boss.getRandom().nextInt(maximum - minimum + 1);
    }

    private void scheduleDirectionChange(long now) {
        // Keep each direction for 5–8 seconds unless an obstacle forces an earlier
        // change.
        nextDirectionChangeTick = now + 100 + boss.getRandom().nextInt(61);
    }

    private void reverseDirection(long now) {
        direction = -direction;
        scheduleDirectionChange(now);
    }

    /**
     * Chooses an orbit or close-range reference point, or an inward destination if
     * displaced.
     * The reference point still needs a reachable surface and a valid path.
     *
     * @param player center of the current orbit
     * @return desired world position before surface and collision checks
     */
    private Vec3 desiredPosition(ServerPlayer player) {
        if (!boss.isWithinMovementArea(boss.getX(), boss.getZ())) {
            return Vec3.atBottomCenterOf(boss.getArenaCenter());
        }
        Vec3 away = horizontal(boss.position().subtract(player.position()));
        if (closingOnTarget) {
            return new Vec3(player.getX(), boss.getY(), player.getZ())
                    .add(away.normalize().scale(closeRangeDistance(player)));
        }
        if (away.lengthSqr() < 4.0D) {
            // Avoid an unstable angle when overlapping the player; move sideways first.
            Vec3 basis = away.lengthSqr() < 1.0E-7D ? new Vec3(1, 0, 0) : away.normalize();
            circleRadius = Math.max(circleRadius, Math.min(2, SheeperConfig.WATER_MOVEMENT_ORBIT_DISTANCE.get()));
            return boss.position().add(-basis.z * direction * 3, 0, basis.x * direction * 3);
        }
        double angle = Math.atan2(away.z, away.x) + direction * Math.toRadians(40);
        return new Vec3(player.getX() + Math.cos(angle) * circleRadius, boss.getY(),
                player.getZ() + Math.sin(angle) * circleRadius);
    }

    /**
     * Searches nearby arena surfaces toward the desired position. While circling,
     * tries the opposite direction if no route advances; close-range approaches
     * keep moving toward the player instead.
     *
     * @param player target used to select the orbit and prevent retreat
     * @return an arena-safe route that makes progress, or {@code null} if blocked
     */
    private Path choosePath(ServerPlayer player) {
        // An arena waypoint can be far outside a sheep's path search range. Keep
        // each leg short instead of repeatedly requesting an unreachable final
        // position.
        double leg = Math.max(1, Math.min(6, boss.getAttributeValue(Attributes.FOLLOW_RANGE) * 0.5D));
        for (int attempt = 0; attempt < (closingOnTarget ? 1 : 2); attempt++) {
            Vec3 desired = desiredPosition(player);
            List<BlockPos> candidates = boss.getArenaSurfacePositions().stream()
                    .filter(pos -> usableDestination(pos, player, leg))
                    .sorted(Comparator.comparingDouble(pos -> navigationPoint(pos).distanceToSqr(desired)))
                    .limit(12).toList();
            Path partial = null;
            for (BlockPos destination : candidates) {
                Path path = boss.getNavigation().createPath(destination, 0);
                if (path == null || path.getNodeCount() < 2 || !pathInsideArena(path)) {
                    continue;
                }
                Vec3 end = path.getEntityPosAtNode(boss, path.getNodeCount() - 1);
                if (end.subtract(boss.position()).horizontalDistanceSqr() < 1.0D
                        || end.distanceToSqr(desired) >= boss.position().distanceToSqr(desired)) {
                    continue;
                }
                if (path.canReach()) {
                    return path;
                }
                if (partial == null) {
                    partial = path;
                }
            }
            if (partial != null) {
                // A safe partial route can advance toward a passage around an obstacle.
                return partial;
            }
            if (!closingOnTarget) {
                reverseDirection(boss.level.getGameTime());
            }
        }
        return null;
    }

    /**
     * Filters nearby surfaces before pathfinding, including space for the full
     * scaled boss and the restriction against moving away from a nearby player.
     *
     * @param pos    candidate arena surface
     * @param player current movement target
     * @param leg    maximum horizontal route destination distance in blocks
     * @return whether the candidate can be considered for navigation
     */
    private boolean usableDestination(BlockPos pos, ServerPlayer player, double leg) {
        Vec3 point = navigationPoint(pos);
        Vec3 travel = horizontal(point.subtract(boss.position()));
        double minimum = Math.min(1.5D, boss.getMovementRadius() / 2);
        if (travel.lengthSqr() < minimum * minimum || travel.lengthSqr() > leg * leg
                || !boss.isWithinMovementArea(point.x, point.z)) {
            return false;
        }
        Vec3 away = horizontal(boss.position().subtract(player.position()));
        if (closingOnTarget && horizontal(point.subtract(player.position())).lengthSqr() < closeRangeDistance(player)
                * closeRangeDistance(player)) {
            return false;
        }
        if (away.lengthSqr() > 1.0E-7D
                && away.length() <= SheeperConfig.WATER_MOVEMENT_ORBIT_DISTANCE.get()
                && travel.dot(away.normalize()) > 0.5D) {
            return false;
        }
        return WaterArenaData.isLoaded((ServerLevel) boss.level, pos) && WaterArenaSearch.isSurface(boss.level, pos)
                && boss.level.noCollision(boss, boss.getBoundingBox().move(point.subtract(boss.position())));
    }

    private Vec3 navigationPoint(BlockPos pos) {
        // Match Path.getEntityPosAtNode for the actual boss width, including scaled
        // bosses.
        double offset = (int) (boss.getBbWidth() + 1.0F) * 0.5D;
        return new Vec3(pos.getX() + offset, pos.getY(), pos.getZ() + offset);
    }

    /**
     * Leaves a small gap between both bodies so approaching does not become a
     * continuous collision push. The minimum keeps small bosses within melee reach.
     *
     * @param player target whose width contributes to the stopping distance
     * @return horizontal center-to-center stopping distance in blocks
     */
    private double closeRangeDistance(ServerPlayer player) {
        return Math.max(1.5D, (boss.getBbWidth() + player.getBbWidth()) * 0.5D + 0.5D);
    }

    /**
     * Checks route nodes against the movement circle. A displaced boss may return
     * from outside, but each outside node must be no farther out than the previous
     * one.
     *
     * @param path proposed navigation route
     * @return whether all nodes after the occupied start respect the boundary
     */
    private boolean pathInsideArena(Path path) {
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double radiusSquared = boss.getMovementRadius() * boss.getMovementRadius();
        double limit = Math.max(radiusSquared, boss.position().subtract(center).horizontalDistanceSqr());
        for (int index = 1; index < path.getNodeCount(); index++) {
            double distance = path.getEntityPosAtNode(boss, index).subtract(center).horizontalDistanceSqr();
            if (distance > limit + 1.0E-7D) {
                return false;
            }
            limit = Math.max(radiusSquared, distance);
        }
        return true;
    }

    /** Attempts a short speed boost; its cooldown starts when the boost ends. */
    private void beginSprint(long now) {
        if (now >= sprintReadyTick && SheeperConfig.WATER_MOVEMENT_SPRINT_DURATION.get() > 0
                && boss.getRandom().nextDouble() < SheeperConfig.WATER_MOVEMENT_SPRINT_CHANCE.get()) {
            sprintEndTick = now + SheeperConfig.secondsToTicks(SheeperConfig.WATER_MOVEMENT_SPRINT_DURATION.get());
            sprintReadyTick = sprintEndTick
                    + SheeperConfig.secondsToTicks(SheeperConfig.WATER_MOVEMENT_SPRINT_COOLDOWN.get());
        }
    }

    private double speed(long now) {
        double speed = SheeperConfig.WATER_MOVEMENT_SPEED.get();
        return now < sprintEndTick ? speed * SheeperConfig.WATER_MOVEMENT_SPRINT_MULTIPLIER.get() : speed;
    }

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0, vector.z);
    }

    /**
     * Cancels navigation and residual horizontal motion while preserving gravity.
     */
    private void stopHorizontalMovement() {
        boss.getNavigation().stop();
        Vec3 velocity = boss.getDeltaMovement();
        boss.setDeltaMovement(0, velocity.y, 0);
        boss.setXxa(0);
        boss.setZza(0);
    }

    @Override
    public void stop() {
        sprintEndTick = 0;
        stopHorizontalMovement();
    }
}
