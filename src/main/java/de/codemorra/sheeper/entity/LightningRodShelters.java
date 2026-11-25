package de.codemorra.sheeper.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.network.LightningRodShelterPacket;
import de.codemorra.sheeper.network.ModNetwork;
import de.codemorra.sheeper.world.LightningRodShelter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Places nearby dry shelters for the participants and keeps their client snapshots current. */
final class LightningRodShelters {
    private static final double MAX_APPROACH_DISTANCE = 11;
    private static final int MAX_ROUTE_STEPS = 16;
    private static final int MAX_ROUTE_POSITIONS = 512;
    private static final int REFRESH_TICKS = 10;
    private final LightningSheeperEntity boss;
    private List<LightningRodShelter> shelters = List.of();
    private long nextRefresh;
    private long dissolveStart;
    private int dissolveTicks;

    private record Step(Vec3 feet, int distance) {
    }

    LightningRodShelters(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    /** Shares reachable shelters where possible and rejects an attack with no shelter for a participant. */
    boolean place(ServerLevel level, List<ServerPlayer> players) {
        var placed = new ArrayList<LightningRodShelter>();
        for (ServerPlayer player : players) {
            List<Vec3> reachable = reachableStandingCenters(level, player);
            if (placed.stream().anyMatch(shelter -> canEnter(reachable, shelter))) {
                continue;
            }
            LightningRodShelter shelter = find(level, player, reachable);
            if (shelter == null || placed.size() == LightningRodShelter.MAX_SHELTERS) {
                return false;
            }
            placed.add(shelter);
        }
        shelters = List.copyOf(placed);
        return !shelters.isEmpty();
    }

    /** Refreshes the same fixed positions so entering and reconnecting observers see the shelters. */
    void tick(ServerLevel level) {
        if (level.getGameTime() >= nextRefresh) {
            send(level);
            nextRefresh = level.getGameTime() + REFRESH_TICKS;
        }
    }

    /** Starts one synchronized fade after the last visible bolt has expired. */
    void beginDissolve(ServerLevel level) {
        dissolveStart = level.getGameTime();
        dissolveTicks = SheeperConfig.secondsToTicks(
                LightningSheeperConfig.LIGHTNING_ROD_SHELTER_DISSOLVE_DURATION.get());
        nextRefresh = dissolveStart;
        tick(level);
    }

    boolean isDissolving() {
        return dissolveTicks > 0;
    }

    boolean hasDissolved(long now) {
        return now - dissolveStart >= dissolveTicks;
    }

    /** Uses the same hitbox-center check as the visible shelter indicator. */
    boolean protects(ServerPlayer player) {
        Vec3 point = player.getBoundingBox().getCenter();
        return shelters.stream().anyMatch(shelter -> shelter.contains(point));
    }

    /** Removes the shelters on both sides when the attack finishes or is cancelled. */
    void clear() {
        dissolveStart = 0;
        dissolveTicks = 0;
        if (!shelters.isEmpty()) {
            shelters = List.of();
            if (boss.level instanceof ServerLevel level) {
                send(level);
            }
        }
        nextRefresh = 0;
    }

    @Nullable
    private LightningRodShelter find(ServerLevel level, ServerPlayer player, List<Vec3> reachable) {
        if (reachable.isEmpty()) {
            return null;
        }
        double radius = Math.min(LightningSheeperConfig.LIGHTNING_ROD_SHELTER_RADIUS.get(),
                boss.getCombatRadius() - 0.05);
        // Prefer a sphere the player has to move towards, then look closer on obstructed terrain.
        for (int attempt = 0; attempt < 36; attempt++) {
            double angle = boss.getRandom().nextDouble() * 2 * Math.PI;
            double reach = attempt < 24 ? 4 + boss.getRandom().nextDouble() * 6
                    : 0.5 + boss.getRandom().nextDouble() * 3.5;
            var point = player.position().add(Math.cos(angle) * reach, 0, Math.sin(angle) * reach);
            LightningRodShelter shelter = at(level, player, point, radius, reachable);
            if (shelter != null) {
                return shelter;
            }
        }
        return at(level, player, player.position(), radius, reachable);
    }

    /** Places the sphere at chest height and verifies that a reachable stand fits inside it. */
    @Nullable
    private LightningRodShelter at(ServerLevel level, ServerPlayer player, Vec3 sample, double radius,
            List<Vec3> reachable) {
        Vec3 arenaCenter = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double x = Mth.floor(sample.x) + 0.5;
        double z = Mth.floor(sample.z) + 0.5;
        if (Math.hypot(x - arenaCenter.x, z - arenaCenter.z) + radius > boss.getCombatRadius()) {
            return null;
        }
        Vec3 feet = findStand(level, player, new Vec3(x, sample.y, z), 2.5, 2.5);
        if (feet == null) {
            return null;
        }
        Vec3 center = feet.add(0, player.getDimensions(Pose.STANDING).height * 0.6, 0);
        double fittingRadius = Math.min(radius, boss.getArenaMaximumY() - center.y);
        if (fittingRadius < 0.95) {
            return null;
        }
        var shelter = new LightningRodShelter(center, fittingRadius);
        return canEnter(reachable, shelter) ? shelter : null;
    }

    /** Explores nearby stands through ordinary one-block jumps and short drops. */
    private List<Vec3> reachableStandingCenters(ServerLevel level, ServerPlayer player) {
        Vec3 origin = player.position();
        Vec3 start = findStand(level, player, origin, 0.25, 2.5);
        if (start == null) {
            return List.of();
        }
        double halfHeight = player.getDimensions(Pose.STANDING).height / 2;
        var centers = new ArrayList<Vec3>();
        var visited = new HashSet<Vec3>();
        var open = new ArrayDeque<Step>();
        visited.add(start);
        open.add(new Step(start, 0));
        while (!open.isEmpty() && centers.size() < MAX_ROUTE_POSITIONS) {
            Step step = open.removeFirst();
            Vec3 feet = step.feet();
            centers.add(feet.add(0, halfHeight, 0));
            if (step.distance() >= MAX_ROUTE_STEPS) {
                continue;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                Vec3 sample = new Vec3(Mth.floor(feet.x) + 0.5 + direction.getStepX(), feet.y,
                        Mth.floor(feet.z) + 0.5 + direction.getStepZ());
                if (sample.subtract(origin).horizontalDistanceSqr() > MAX_APPROACH_DISTANCE * MAX_APPROACH_DISTANCE) {
                    continue;
                }
                Vec3 next = findStand(level, player, sample, 1.25, 1.5);
                if (next != null && !visited.contains(next) && canStep(level, player, feet, next)) {
                    visited.add(next);
                    open.addLast(new Step(next, step.distance() + 1));
                }
            }
        }
        return centers;
    }

    /** Checks support and standing clearance without loading chunks or crossing the arena boundary. */
    @Nullable
    private Vec3 findStand(ServerLevel level, ServerPlayer player, Vec3 sample, double rise, double drop) {
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double radius = boss.getCombatRadius();
        if (sample.subtract(center).horizontalDistanceSqr() > radius * radius
                || !LightningAreaDamage.chunksLoaded(level, sample, sample)) {
            return null;
        }
        int minY = Math.max(boss.getArenaMinimumY(), Mth.floor(sample.y - drop));
        int maxY = Math.min(boss.getArenaMaximumY() - 1, Mth.ceil(sample.y + rise));
        double x = sample.x;
        double z = sample.z;
        double floor = LightningRingSurface.find(level, x, z, sample.y, minY, maxY);
        if (!(floor >= sample.y - drop && floor <= sample.y + rise)) {
            return null;
        }
        var position = new Vec3(x, floor, z);
        AABB body = player.getDimensions(Pose.STANDING).makeBoundingBox(position);
        var support = new BlockPos(x, Math.nextDown(floor), z);
        var feet = new BlockPos(position.add(0, 0.05, 0));
        if (body.maxY > boss.getArenaMaximumY() || !level.getFluidState(support).isEmpty()
                || !level.getFluidState(feet).isEmpty() || level.getBlockState(support).is(Blocks.MAGMA_BLOCK)
                || WalkNodeEvaluator.getBlockPathTypeStatic(level, feet.mutable()).getDanger() != null
                || !level.noCollision(player, body)) {
            return null;
        }
        return position;
    }

    /** Sweeps the standing body vertically and across the step to reject walls and low ceilings. */
    private boolean canStep(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
        var dimensions = player.getDimensions(Pose.STANDING);
        double y = Math.max(from.y, to.y) + 0.01;
        AABB start = dimensions.makeBoundingBox(from);
        AABB end = dimensions.makeBoundingBox(to);
        AABB across = start.move(0, y - from.y, 0).expandTowards(to.x - from.x, 0, to.z - from.z);
        return across.maxY <= boss.getArenaMaximumY()
                && level.noCollision(player, start.expandTowards(0, y - from.y, 0))
                && level.noCollision(player, across)
                && level.noCollision(player, end.expandTowards(0, y - to.y, 0));
    }

    private boolean canEnter(List<Vec3> reachable, LightningRodShelter shelter) {
        return reachable.stream().anyMatch(shelter::contains);
    }

    private void send(ServerLevel level) {
        var snapshot = new LightningRodShelterPacket(level.dimension().location(), boss.getUUID(), shelters,
                dissolveStart, dissolveTicks);
        double range = boss.getCombatRadius() + 64;
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        for (ServerPlayer observer : level.players()) {
            if (observer.position().subtract(center).horizontalDistanceSqr() <= range * range) {
                ModNetwork.sendLightningRodShelters(observer, snapshot);
            }
        }
    }
}
