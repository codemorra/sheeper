package de.codemorra.sheeper.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Approaches and circles the selected player through short, collision-aware
 * water waypoints. The entity owns target selection; this goal only plans
 * movement.
 * Player approaches reduce the orbit radius so the boss does not retreat to
 * restore its preferred distance. In close-range mode it approaches directly
 * and stops short of the player's body instead of circling.
 */
public final class WaterOrbitGoal extends Goal {
    private static final double MINE_RETREAT_DISTANCE = 3;
    private static final int MINE_RETREAT_TICKS = 20;
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
    private boolean mineAttack;
    private Vec3 mineRetreat;
    private long mineRetreatEnd;

    /**
     * Creates a movement goal without taking control of the boss's head rotation.
     *
     * @param boss owner whose target and captured water volume guide movement
     */
    public WaterOrbitGoal(WaterSheeperEntity boss) {
        this.boss = boss;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return boss.isAlive() && !boss.isNoAi() && boss.isCombatReady()
                && !boss.isPerformingBeam() && !boss.isPerformingMaelstrom()
                && boss.getTarget() instanceof ServerPlayer && boss.getMovementRadius() > 0
                && WaterSheeperConfig.WATER_MOVEMENT_SPEED.get() > 0;
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
        if (boss.isPerformingMaelstrom()) {
            // Special selection runs before goal continuation checks. Do not
            // overwrite the center route during that transition tick.
            return;
        }
        if (!(boss.getTarget() instanceof ServerPlayer player) || !player.isAlive() || player.isRemoved()) {
            // Goal continuation checks do not necessarily run before every AI
            // tick. Arena exits can already have cleared the shared target.
            stopSwimming();
            return;
        }
        long now = boss.level.getGameTime();
        boolean changed = !player.getUUID().equals(targetId);
        boolean mining = boss.isPerformingWaterMines();
        boolean closing = !mining && boss.isClosingOnTarget();
        boolean modeChanged = closingOnTarget != closing || mineAttack != mining;
        if (mining && !mineAttack) {
            Vec3 away = horizontal(boss.position().subtract(player.position())).normalize();
            if (away.lengthSqr() < 1.0E-7D) {
                away = new Vec3(1, 0, 0);
            }
            mineRetreat = boss.clampSwimmingPosition(boss.position().add(away.scale(MINE_RETREAT_DISTANCE)));
            mineRetreatEnd = now + MINE_RETREAT_TICKS;
        } else if (mineRetreat != null && (!mining || now >= mineRetreatEnd
                || boss.position().distanceToSqr(mineRetreat) < 1)) {
            mineRetreat = null;
            modeChanged = true;
        }
        closingOnTarget = closing;
        mineAttack = mining;
        if (closingOnTarget) {
            sprintEndTick = 0;
            if (boss.getBoundingBox().getCenter().distanceToSqr(
                    player.getBoundingBox().getCenter()) <= closeRangeDistance() * closeRangeDistance()) {
                // Leave room for melee attacks instead of continuing through the player.
                updateCircle(player, changed);
                stopSwimming();
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
        boolean targetMoved = previousPlayerPosition != null
                && player.position().distanceToSqr(previousPlayerPosition) > 0.25D
                && (closingOnTarget || Math.abs(player.getY() - previousPlayerPosition.y) > 0.5D);
        if (changed || modeChanged || directionChanged || approached || targetMoved || now >= nextPlanTick) {
            updateCircle(player, changed || modeChanged);
            scheduleNextPlan(now);
            Path path = choosePath(player);
            if (path == null && mineRetreat != null) {
                // Terrain may block the brief retreat; keep circling instead.
                mineRetreat = null;
                path = choosePath(player);
            }
            if (path != null) {
                if (!closingOnTarget) {
                    beginSprint(now);
                }
                boss.getNavigation().moveTo(path, speed(now));
            } else if (changed || modeChanged || approached || targetMoved) {
                // A previous route must not turn into an escape route when the player closes
                // in.
                stopSwimming();
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
        double preferred = WaterSheeperConfig.WATER_MOVEMENT_ORBIT_DISTANCE.get();
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
        int minimum = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_MOVEMENT_INTERVAL_MIN.get());
        int maximum = Math.max(minimum,
                SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_MOVEMENT_INTERVAL_MAX.get()));
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
     * Chooses a spatial approach or an orbit point at the player's swimming height.
     * Requested points are clamped before searching nearby submerged body
     * positions.
     *
     * @param player center of the current orbit and height reference
     * @return desired feet position inside the captured water volume
     */
    private Vec3 desiredPosition(ServerPlayer player) {
        if (!boss.isWithinMovementArea(boss.getX(), boss.getZ())) {
            return boss.clampSwimmingPosition(new Vec3(boss.getArenaCenter().getX() + 0.5D,
                    boss.getY(), boss.getArenaCenter().getZ() + 0.5D));
        }
        Vec3 away = horizontal(boss.position().subtract(player.position()));
        if (closingOnTarget) {
            Vec3 playerCenter = player.getBoundingBox().getCenter();
            Vec3 spacing = boss.getBoundingBox().getCenter().subtract(playerCenter).normalize()
                    .scale(closeRangeDistance());
            return boss.clampSwimmingPosition(playerCenter.add(spacing).add(0, -boss.getBbHeight() * 0.5D, 0));
        }
        double height = player.getBoundingBox().getCenter().y - boss.getBbHeight() * 0.5D
                + WaterSheeperConfig.WATER_MOVEMENT_HEIGHT_OFFSET.get();
        if (mineRetreat != null) {
            return mineRetreat;
        }
        if (away.lengthSqr() < 4.0D) {
            Vec3 basis = away.lengthSqr() < 1.0E-7D ? new Vec3(1, 0, 0) : away.normalize();
            circleRadius = Math.max(circleRadius, Math.min(2, WaterSheeperConfig.WATER_MOVEMENT_ORBIT_DISTANCE.get()));
            return boss.clampSwimmingPosition(new Vec3(boss.getX() - basis.z * direction * 3,
                    height, boss.getZ() + basis.x * direction * 3));
        }
        double angle = Math.atan2(away.z, away.x) + direction * Math.toRadians(40);
        return boss.clampSwimmingPosition(new Vec3(player.getX() + Math.cos(angle) * circleRadius,
                height, player.getZ() + Math.sin(angle) * circleRadius));
    }

    /**
     * Requests short three-dimensional paths around the next desired swim point.
     * Nearby alternatives let water navigation route above or beside obstacles
     * without scanning or requiring the arena's floor cells.
     *
     * @param player target used for approach spacing and the no-retreat restriction
     * @return a progressing underwater path, or null if all local routes are
     *         blocked
     */
    private Path choosePath(ServerPlayer player) {
        double leg = Math.max(1, Math.min(6, boss.getAttributeValue(Attributes.FOLLOW_RANGE) * 0.5D));
        for (int attempt = 0; attempt < (closingOnTarget ? 1 : 2); attempt++) {
            Vec3 desired = desiredPosition(player);
            Vec3 travel = desired.subtract(boss.position());
            Vec3 next = travel.length() > leg ? boss.position().add(travel.normalize().scale(leg)) : desired;
            double offset = (int) (boss.getBbWidth() + 1.0F) * 0.5D;
            BlockPos node = new BlockPos(next.x - offset, next.y, next.z - offset);
            List<BlockPos> candidates = StreamSupport.stream(BlockPos.betweenClosed(
                    node.offset(-2, -3, -2), node.offset(2, 3, 2)).spliterator(), false)
                    .map(BlockPos::immutable)
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
                if (end.distanceToSqr(boss.position()) < 0.25D
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
                return partial;
            }
            if (!closingOnTarget) {
                reverseDirection(boss.level.getGameTime());
            }
        }
        return null;
    }

    /**
     * Filters local water waypoints for the whole body and keeps approaches from
     * crossing into the player or retreating from an approaching target.
     *
     * @param pos    candidate swim node, aligned like Path.getEntityPosAtNode
     * @param player current movement target
     * @param leg    maximum three-dimensional waypoint distance in blocks
     * @return whether the destination may be considered for water navigation
     */
    private boolean usableDestination(BlockPos pos, ServerPlayer player, double leg) {
        Vec3 point = navigationPoint(pos);
        Vec3 travel = point.subtract(boss.position());
        double minimum = Math.min(0.5D, boss.getMovementRadius() / 2);
        if (travel.lengthSqr() < minimum * minimum || travel.lengthSqr() > leg * leg
                || !boss.canSwimAt(point)) {
            return false;
        }
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        if (!boss.isWithinMovementArea(point.x, point.z)
                && point.subtract(center)
                        .horizontalDistanceSqr() >= boss.position().subtract(center).horizontalDistanceSqr() - 0.001D) {
            return false;
        }
        if (closingOnTarget && point.add(0, boss.getBbHeight() * 0.5D, 0)
                .distanceToSqr(player.getBoundingBox().getCenter()) < closeRangeDistance()
                        * closeRangeDistance()) {
            return false;
        }
        Vec3 away = horizontal(boss.position().subtract(player.position()));
        return mineRetreat != null || away.lengthSqr() <= 1.0E-7D
                || away.length() > WaterSheeperConfig.WATER_MOVEMENT_ORBIT_DISTANCE.get()
                || horizontal(travel).dot(away.normalize()) <= 0.5D;
    }

    private Vec3 navigationPoint(BlockPos pos) {
        // Match Path.getEntityPosAtNode for the actual boss width, including scaled
        // bosses.
        double offset = (int) (boss.getBbWidth() + 1.0F) * 0.5D;
        return new Vec3(pos.getX() + offset, pos.getY(), pos.getZ() + offset);
    }

    /**
     * Uses the configured melee stopping distance without adding body-size
     * offsets. Players can close the remaining gap without making the boss retreat.
     *
     * @return three-dimensional center-to-center stopping distance in blocks
     */
    private double closeRangeDistance() {
        return boss.getMeleeStopDistance();
    }

    /**
     * Revalidates every path node against the submerged arena volume. This also
     * catches terrain changes after the evaluator cached its node classifications.
     *
     * @param path proposed water route
     * @return whether every node after the occupied start remains safe to swim
     */
    private boolean pathInsideArena(Path path) {
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double radiusSquared = boss.getMovementRadius() * boss.getMovementRadius();
        double limit = Math.max(radiusSquared, boss.position().subtract(center).horizontalDistanceSqr());
        for (int index = 1; index < path.getNodeCount(); index++) {
            Vec3 point = path.getEntityPosAtNode(boss, index);
            double distance = point.subtract(center).horizontalDistanceSqr();
            if (!boss.canSwimAt(point) || distance > limit + 1.0E-6D) {
                return false;
            }
            limit = Math.max(radiusSquared, distance);
        }
        return true;
    }

    /** Attempts a short speed boost; its cooldown starts when the boost ends. */
    private void beginSprint(long now) {
        if (now >= sprintReadyTick && WaterSheeperConfig.WATER_MOVEMENT_SPRINT_DURATION.get() > 0
                && boss.getRandom().nextDouble() < WaterSheeperConfig.WATER_MOVEMENT_SPRINT_CHANCE.get()) {
            sprintEndTick = now + SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_MOVEMENT_SPRINT_DURATION.get());
            sprintReadyTick = sprintEndTick
                    + SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_MOVEMENT_SPRINT_COOLDOWN.get());
        }
    }

    private double speed(long now) {
        double speed = WaterSheeperConfig.WATER_MOVEMENT_SPEED.get();
        return now < sprintEndTick ? speed * WaterSheeperConfig.WATER_MOVEMENT_SPRINT_MULTIPLIER.get() : speed;
    }

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0, vector.z);
    }

    /**
     * Cancels navigation and residual swimming motion so close-range stops retain
     * their depth.
     */
    private void stopSwimming() {
        boss.getNavigation().stop();
        Vec3 velocity = boss.getDeltaMovement();
        boss.setDeltaMovement(0, boss.isInArenaWater() ? 0 : velocity.y, 0);
        boss.setYya(0);
        boss.setXxa(0);
        boss.setZza(0);
    }

    @Override
    public void stop() {
        sprintEndTick = 0;
        mineAttack = false;
        mineRetreat = null;
        stopSwimming();
    }

    /** Clears orbit history and sprint cooldown after full encounter recovery. */
    public void reset() {
        stop();
        targetId = null;
        previousPlayerPosition = null;
        circleRadius = 0;
        nextPlanTick = 0;
        nextDirectionChangeTick = 0;
        sprintReadyTick = 0;
    }
}
