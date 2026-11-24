package de.codemorra.sheeper.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Plans and follows exceptional narrow routes without changing normal swimming navigation. */
final class WaterBubbleTravel {
    static final EntityDimensions DIMENSIONS = EntityDimensions.fixed(0.7F, 0.7F);
    private static final int MAX_VISITED = 4_096;
    private static final int MAX_ROUTE_STEPS = 24;
    private static final int RETRY_TICKS = 20;
    private static final int TARGET_CHECK_INTERVAL = 40;
    private static final double MOVEMENT_PER_TICK = 0.18;
    private static final Direction[] DIRECTIONS = Direction.values();

    private final WaterSheeperEntity boss;
    private final ArrayDeque<Vec3> route = new ArrayDeque<>();
    @Nullable
    private Vec3 destination;
    @Nullable
    private Vec3 lastSafe;
    @Nullable
    private Vec3 progressAnchor;
    private long progressTick;
    private long nextAttempt;
    private int plansWithoutProgress;
    @Nullable
    private UUID trackedTarget;
    private long nextTargetCheck;
    private boolean replanned;
    private boolean returning;

    WaterBubbleTravel(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    /** Starts only after a narrow route all the way to a full-size destination is known. */
    @Nullable
    Vec3 tryBegin(List<Vec3> destinations, boolean checkNormalRoute) {
        if (destinations.isEmpty() || boss.isInBubbleForm() || !boss.isInArenaWater()) {
            return null;
        }
        Predicate<Vec3> needsBubbles = checkNormalRoute ? this::isNormalRouteBlocked : point -> true;
        List<Vec3> path = findRoute(destinations, true, false, needsBubbles);
        if (path.isEmpty()) {
            return null;
        }
        Vec3 end = path.get(path.size() - 1);
        destination = end;
        lastSafe = boss.canMaterializeAt(boss.position()) ? boss.position() : null;
        route.addAll(path);
        replanned = false;
        returning = false;
        nextAttempt = 0;
        resetProgress();
        boss.getNavigation().stop();
        boss.setDeltaMovement(Vec3.ZERO);
        boss.setBubbleForm(true);
        return destination;
    }

    /** A regular route to one candidate must not hide other exits behind narrow openings. */
    private boolean isNormalRouteBlocked(Vec3 end) {
        double offset = (int) (boss.getBbWidth() + 1) * 0.5;
        var normal = boss.getNavigation().createPath(new BlockPos(end.x - offset, end.y, end.z - offset), 0);
        return normal == null || !normal.canReach();
    }

    /** Checks reachability independently of how far the boss can move inside an enclosure. */
    boolean tryTargetRecovery(ServerPlayer player) {
        long now = boss.level.getGameTime();
        if (!player.getUUID().equals(trackedTarget)) {
            trackedTarget = player.getUUID();
            nextTargetCheck = now + 60;
        }
        if (now < nextTargetCheck || !boss.isInArenaWater()) {
            return false;
        }
        nextTargetCheck = now + TARGET_CHECK_INTERVAL;

        Vec3 start = boss.position();
        Vec3 target = boss.clampSwimmingPosition(player.position());
        if (start.distanceToSqr(target) <= MAX_ROUTE_STEPS * MAX_ROUTE_STEPS
                && boss.canTravelAtFullSize(start, target)) {
            return false;
        }
        List<Vec3> exits = fullSizeExitsToward(player, start, target);
        return tryBegin(exits, false) != null;
    }

    /**
     * Searches backward from legal swim points beside the target. A bubble route
     * may initially head away from the player, but must end where normal swimming
     * can continue toward them.
     */
    private List<Vec3> fullSizeExitsToward(ServerPlayer player, Vec3 start, Vec3 target) {
        BlockPos center = new BlockPos(target);
        // Keep the probe local to likely exits even when the player is far away.
        var open = new PriorityQueue<BlockPos>(Comparator.comparingDouble(cell -> feet(cell).distanceToSqr(start)));
        Set<BlockPos> reachable = new HashSet<>();
        for (BlockPos cell : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            Vec3 point = feet(cell);
            // A nearby waypoint across a wall is not on the player's side.
            if (boss.canMaterializeAt(point) && boss.level.clip(new ClipContext(player.getEyePosition(),
                    point.add(0, boss.getBbHeight() / 2, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss)).getType() == HitResult.Type.MISS) {
                BlockPos seed = cell.immutable();
                reachable.add(seed);
                open.add(seed);
            }
        }

        var exits = new ArrayList<Vec3>();
        for (int expanded = 0; !open.isEmpty() && expanded < MAX_VISITED; expanded++) {
            BlockPos cell = open.remove();
            Vec3 point = feet(cell);
            double distance = point.distanceToSqr(start);
            if (distance <= 4 && boss.canTravelAtFullSize(start, point)) {
                return List.of();
            }
            if (distance <= MAX_ROUTE_STEPS * MAX_ROUTE_STEPS) {
                exits.add(point);
            }
            for (Direction direction : DIRECTIONS) {
                BlockPos neighbor = cell.relative(direction);
                Vec3 next = feet(neighbor);
                if (!reachable.contains(neighbor) && boss.canTravelAtFullSize(point, next)) {
                    reachable.add(neighbor);
                    open.add(neighbor);
                }
            }
        }
        return exits;
    }

    /** Waits for repeated planning attempts and several seconds without displacement. */
    boolean tryRecovery(boolean hasPath) {
        long now = boss.level.getGameTime();
        Vec3 start = boss.position();
        if (progressAnchor == null || start.distanceToSqr(progressAnchor) >= 0.25) {
            progressAnchor = start;
            progressTick = now;
            plansWithoutProgress = 0;
        }
        if (++plansWithoutProgress < (hasPath ? 3 : 2) || now - progressTick < 60 || now < nextAttempt) {
            return false;
        }
        nextAttempt = now + RETRY_TICKS;
        double offset = (int) (boss.getBbWidth() + 1) * 0.5;
        BlockPos center = new BlockPos(start.x - offset, start.y, start.z - offset);
        var exits = new ArrayList<Vec3>();
        for (BlockPos cell : BlockPos.betweenClosed(center.offset(-6, -4, -6), center.offset(6, 5, 6))) {
            Vec3 point = new Vec3(cell.getX() + offset, cell.getY(), cell.getZ() + offset);
            double distance = point.distanceToSqr(start);
            if (distance >= 2.25 && distance <= 49 && boss.canMaterializeAt(point)) {
                exits.add(point);
            }
        }
        // The only exit can lead away from the target, especially through a cage wall or roof.
        return tryBegin(exits, true) != null;
    }

    void resetProgress() {
        progressAnchor = null;
        plansWithoutProgress = 0;
        trackedTarget = null;
    }

    /**
     * Moves with ordinary block collision and validates each step again.
     *
     * @return whether the original destination had to be abandoned
     */
    boolean tick() {
        long now = boss.level.getGameTime();
        if (now < nextAttempt) {
            return false;
        }
        Vec3 current = boss.position();
        if (boss.canMaterializeAt(current)) {
            lastSafe = current;
            if (returning || destination != null && current.distanceToSqr(destination) < 1.0E-6) {
                finish();
                return false;
            }
        }

        Vec3 next = route.peekFirst();
        boolean abandoned = false;
        if (next == null || !boss.canTravelAsBubbles(current, next)) {
            route.clear();
            if (!returning && !replanned && destination != null) {
                replanned = true;
                route.addAll(findRoute(List.of(destination), false, false, point -> true));
            }
            if (route.isEmpty()) {
                abandoned = !returning;
                returning = true;
                if (boss.canMaterializeAt(current)) {
                    finish();
                    return abandoned;
                }
                if (lastSafe != null) {
                    route.addAll(findRoute(List.of(lastSafe), false, false, point -> true));
                }
                if (route.isEmpty()) {
                    route.addAll(findRoute(List.of(), false, true, point -> true));
                }
                if (route.isEmpty()) {
                    nextAttempt = now + RETRY_TICKS;
                    return abandoned;
                }
            }
            next = route.peekFirst();
        }

        Vec3 motion = next.subtract(current);
        if (motion.lengthSqr() > MOVEMENT_PER_TICK * MOVEMENT_PER_TICK) {
            motion = motion.normalize().scale(MOVEMENT_PER_TICK);
        }
        boss.move(MoverType.SELF, motion);
        if (boss.position().distanceToSqr(next) < 1.0E-6) {
            route.removeFirst();
        } else if (boss.position().distanceToSqr(current.add(motion)) > 1.0E-6) {
            route.clear();
        }
        return abandoned;
    }

    private void finish() {
        route.clear();
        destination = null;
        lastSafe = null;
        nextAttempt = 0;
        boss.setDeltaMovement(Vec3.ZERO);
        boss.setBubbleForm(false);
    }

    /**
     * Searches face-connected cells. Each edge checks the swept bubble box, which
     * prevents diagonal corner cutting and avoids loading terrain.
     */
    private List<Vec3> findRoute(List<Vec3> destinations, boolean requireNarrow, boolean anySafeExit,
            Predicate<Vec3> acceptTarget) {
        Vec3 start = boss.position();
        Map<BlockPos, List<Vec3>> targets = new HashMap<>();
        for (Vec3 point : destinations) {
            if (point.distanceToSqr(start) <= MAX_ROUTE_STEPS * MAX_ROUTE_STEPS && boss.canMaterializeAt(point)) {
                targets.computeIfAbsent(new BlockPos(point), ignored -> new ArrayList<>()).add(point);
            }
        }
        if (targets.isEmpty() && !anySafeExit) {
            return List.of();
        }

        var open = new ArrayDeque<Step>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos first = new BlockPos(start);
        // Trying the adjacent cells too lets a fractional feet position align at an opening.
        addStart(start, first, open, visited);
        for (Direction direction : DIRECTIONS) {
            addStart(start, first.relative(direction), open, visited);
        }
        for (int expanded = 0; !open.isEmpty() && expanded < MAX_VISITED; expanded++) {
            Step step = open.removeFirst();
            Vec3 feet = feet(step.cell());
            if (anySafeExit && boss.canMaterializeAt(feet)) {
                return routeTo(step, feet);
            }
            for (Vec3 target : targets.getOrDefault(step.cell(), List.of())) {
                if (boss.canTravelAsBubbles(feet, target)) {
                    List<Vec3> path = routeTo(step, target);
                    if ((!requireNarrow || path.stream().anyMatch(point -> !boss.canMaterializeAt(point)))
                            && acceptTarget.test(target)) {
                        return path;
                    }
                }
            }
            if (step.distance() >= MAX_ROUTE_STEPS) {
                continue;
            }
            for (Direction direction : DIRECTIONS) {
                BlockPos cell = step.cell().relative(direction);
                if (!visited.contains(cell) && boss.canTravelAsBubbles(feet, feet(cell))) {
                    visited.add(cell);
                    open.addLast(new Step(cell, step, step.distance() + 1));
                }
            }
        }
        return List.of();
    }

    private void addStart(Vec3 start, BlockPos cell, ArrayDeque<Step> open, Set<BlockPos> visited) {
        if (boss.canTravelAsBubbles(start, feet(cell))) {
            visited.add(cell);
            open.addLast(new Step(cell, null, 0));
        }
    }

    private static Vec3 feet(BlockPos cell) {
        return new Vec3(cell.getX() + 0.5, cell.getY() + 0.15, cell.getZ() + 0.5);
    }

    private static List<Vec3> routeTo(Step step, Vec3 target) {
        var result = new ArrayDeque<Vec3>();
        result.add(target);
        for (Step node = step; node != null; node = node.previous()) {
            result.addFirst(feet(node.cell()));
        }
        return List.copyOf(result);
    }

    private record Step(BlockPos cell, @Nullable Step previous, int distance) {
    }

    /** Saves the endpoints rather than keeping a route through potentially changed terrain. */
    CompoundTag save() {
        var tag = new CompoundTag();
        if (boss.isInBubbleForm()) {
            tag.putBoolean("Active", true);
            tag.putBoolean("Returning", returning);
            savePoint(tag, "Destination", destination);
            savePoint(tag, "LastSafe", lastSafe);
        }
        return tag;
    }

    void load(CompoundTag tag) {
        route.clear();
        resetProgress();
        nextAttempt = 0;
        replanned = false;
        returning = tag.getBoolean("Returning");
        destination = loadPoint(tag.getCompound("Destination"));
        lastSafe = loadPoint(tag.getCompound("LastSafe"));
        boss.setBubbleForm(tag.getBoolean("Active"));
    }

    private static void savePoint(CompoundTag state, String key, @Nullable Vec3 point) {
        if (point != null) {
            var tag = new CompoundTag();
            tag.putDouble("X", point.x);
            tag.putDouble("Y", point.y);
            tag.putDouble("Z", point.z);
            state.put(key, tag);
        }
    }

    @Nullable
    private static Vec3 loadPoint(CompoundTag tag) {
        if (!tag.contains("X", Tag.TAG_DOUBLE) || !tag.contains("Y", Tag.TAG_DOUBLE)
                || !tag.contains("Z", Tag.TAG_DOUBLE)) {
            return null;
        }
        Vec3 point = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z) ? point : null;
    }
}
