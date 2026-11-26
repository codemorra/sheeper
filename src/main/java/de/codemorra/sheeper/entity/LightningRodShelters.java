package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.network.LightningRodShelterPacket;
import de.codemorra.sheeper.network.ModNetwork;
import de.codemorra.sheeper.world.LightningRodShelter;
import de.codemorra.sheeper.world.LightningStormData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Plans dry shelters across the arena and keeps their client snapshots current. */
final class LightningRodShelters {
    private static final int REFRESH_TICKS = 10;
    private final LightningSheeperEntity boss;
    private List<LightningRodShelter> shelters = List.of();
    private Vec3 boltOrigin = Vec3.ZERO;
    private long formationStart = -1;
    private long nextRefresh;
    private long dissolveStart;
    private int dissolveTicks;

    LightningRodShelters(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    /** Prefers open terrain and fills remaining slots with covered spots in the arena. */
    boolean place(ServerLevel level, List<ServerPlayer> players) {
        if (players.isEmpty()) {
            return false;
        }
        double arenaRadius = boss.getCombatRadius();
        double density = LightningSheeperConfig.LIGHTNING_ROD_SHELTER_DENSITY.get();
        int count = Mth.clamp((int) Math.round(Math.PI * arenaRadius * arenaRadius * density / 1_000),
                1, LightningRodShelter.MAX_SHELTERS);
        double radius = Math.min(LightningSheeperConfig.LIGHTNING_ROD_SHELTER_RADIUS.get(), arenaRadius * 0.75);
        var placed = new ArrayList<LightningRodShelter>(count);
        ServerPlayer player = players.get(0);
        sampleShelters(level, player, placed, count, radius, true);
        sampleShelters(level, player, placed, count, radius, false);
        shelters = List.copyOf(placed);
        return !shelters.isEmpty();
    }

    /** Samples loaded columns across the arena without using player positions. */
    private void sampleShelters(ServerLevel level, ServerPlayer player, List<LightningRodShelter> placed,
            int count, double radius, boolean preferSky) {
        if (placed.size() >= count) {
            return;
        }
        double arenaRadius = boss.getCombatRadius();
        double available = Math.max(0, arenaRadius - radius - 0.5);
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        int minY = boss.getArenaMinimumY();
        int maxY = boss.getArenaMaximumY() - 1;
        var candidates = new ArrayList<LightningRodShelter>();
        for (int attempt = 0; attempt < count * 32; attempt++) {
            double angle = boss.getRandom().nextDouble() * 2 * Math.PI;
            double distance = Math.sqrt(boss.getRandom().nextDouble()) * available;
            double x = Mth.floor(center.x + Math.cos(angle) * distance) + 0.5;
            double z = Mth.floor(center.z + Math.sin(angle) * distance) + 0.5;
            Vec3 column = new Vec3(x, center.y, z);
            if (column.subtract(center).horizontalDistance() + radius > arenaRadius
                    || !LightningAreaDamage.chunksLoaded(level, column, column)) {
                continue;
            }
            // In caves, search near the arena's starting level as well as on other floors.
            double referenceY = preferSky ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z))
                    : attempt % 2 == 0 ? center.y : Mth.nextInt(boss.getRandom(), minY, maxY);
            double searchDistance = preferSky ? 1 : 4;
            Vec3 feet = findStand(level, player, new Vec3(x, referenceY, z),
                    preferSky ? 0.25 : searchDistance, searchDistance);
            LightningRodShelter shelter = shelterAt(level, player, feet, radius, preferSky);
            if (shelter != null && hasApproach(level, player, shelter)) {
                candidates.add(shelter);
            }
        }
        spreadShelters(placed, candidates, count);
    }

    /** Places each new sphere where its closest neighbor is furthest away in the arena's horizontal plane. */
    private static void spreadShelters(List<LightningRodShelter> placed, List<LightningRodShelter> candidates, int count) {
        double[] nearest = new double[candidates.size()];
        Arrays.fill(nearest, Double.POSITIVE_INFINITY);
        for (LightningRodShelter shelter : placed) {
            updateDistances(candidates, nearest, shelter);
        }
        while (placed.size() < count) {
            int best = -1;
            double distance = -1;
            for (int index = 0; index < nearest.length; index++) {
                if (nearest[index] > distance) {
                    distance = nearest[index];
                    best = index;
                }
            }
            if (best < 0) {
                return;
            }
            LightningRodShelter shelter = candidates.get(best);
            placed.add(shelter);
            updateDistances(candidates, nearest, shelter);
        }
    }

    /** Excludes intersections and updates the nearest distance without rescanning earlier placements. */
    private static void updateDistances(List<LightningRodShelter> candidates, double[] nearest, LightningRodShelter shelter) {
        for (int index = 0; index < nearest.length; index++) {
            if (nearest[index] < 0) {
                continue;
            }
            LightningRodShelter candidate = candidates.get(index);
            if (overlaps(candidate, shelter)) {
                nearest[index] = -1;
                continue;
            }
            double dx = candidate.center().x - shelter.center().x;
            double dz = candidate.center().z - shelter.center().z;
            nearest[index] = Math.min(nearest[index], dx * dx + dz * dz);
        }
    }

    /** Reveals all reserved positions as bolts depart from the arrived boss. */
    void beginFormation(ServerLevel level, Vec3 origin) {
        boltOrigin = origin;
        formationStart = level.getGameTime();
        nextRefresh = formationStart;
        tick(level);
    }

    /** Refreshes the same fixed positions so entering and reconnecting observers see the shelters. */
    void tick(ServerLevel level) {
        if (formationStart < 0) {
            return;
        }
        long now = level.getGameTime();
        LightningStormData.get(level).updateShelters(boss.getUUID(), activeShelters(now));
        if (now >= nextRefresh) {
            send(level);
            nextRefresh = now + REFRESH_TICKS;
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

    /** Exposes the shell at its current size to strikes and players. */
    List<LightningRodShelter> activeShelters(long now) {
        if (formationStart < 0 || (isDissolving() && hasDissolved(now))) {
            return List.of();
        }
        double scale = LightningRodShelter.formationRadiusScale(now - formationStart);
        if (scale <= 0) {
            return List.of();
        }
        return scale >= 1 ? shelters : shelters.stream()
                .map(shelter -> new LightningRodShelter(shelter.center(), shelter.radius() * scale)).toList();
    }

    /** Removes the shelters on both sides when the attack finishes or is cancelled. */
    void clear() {
        dissolveStart = 0;
        dissolveTicks = 0;
        long previousFormation = formationStart;
        formationStart = -1;
        if (!shelters.isEmpty()) {
            shelters = List.of();
            if (previousFormation >= 0 && boss.level instanceof ServerLevel level) {
                LightningStormData.get(level).updateShelters(boss.getUUID(), List.of());
                send(level);
            }
        }
        nextRefresh = 0;
    }

    private static boolean overlaps(LightningRodShelter first, LightningRodShelter second) {
        double clearance = first.radius() + second.radius();
        return first.center().distanceToSqr(second.center()) < clearance * clearance;
    }

    /** Rejects extra shelters on isolated columns with no stand that leads into the sphere. */
    private boolean hasApproach(ServerLevel level, ServerPlayer player, LightningRodShelter shelter) {
        Vec3 feet = shelter.center().add(0, -player.getDimensions(Pose.STANDING).height * 0.6, 0);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Vec3 sample = feet.add(direction.getStepX(), 0, direction.getStepZ());
            Vec3 neighbor = findStand(level, player, sample, 1, 1.25);
            if (neighbor != null && canStep(level, player, neighbor, feet)) {
                return true;
            }
        }
        return false;
    }

    /** Centers a shelter over a safe stand while leaving room below the arena ceiling. */
    @Nullable
    private LightningRodShelter shelterAt(ServerLevel level, ServerPlayer player, @Nullable Vec3 feet,
            double radius, boolean requireSky) {
        if (feet == null) {
            return null;
        }
        Vec3 center = feet.add(0, player.getDimensions(Pose.STANDING).height * 0.6, 0);
        double fittingRadius = Math.min(radius, boss.getArenaMaximumY() - center.y);
        return fittingRadius >= 0.95 && (!requireSky || level.canSeeSky(new BlockPos(center)))
                ? new LightningRodShelter(center, fittingRadius) : null;
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

    private void send(ServerLevel level) {
        var snapshot = new LightningRodShelterPacket(level.dimension().location(), boss.getUUID(), shelters,
                boltOrigin, Math.max(0, formationStart), dissolveStart, dissolveTicks);
        double range = boss.getCombatRadius() + 64;
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        for (ServerPlayer observer : level.players()) {
            if (observer.position().subtract(center).horizontalDistanceSqr() <= range * range) {
                ModNetwork.sendLightningRodShelters(observer, snapshot);
            }
        }
    }
}
