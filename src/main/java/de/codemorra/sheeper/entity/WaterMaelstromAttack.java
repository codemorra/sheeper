package de.codemorra.sheeper.entity;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.MoverType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Owns Maelstrom's preparation, staged suction, collision-aware slam and
 * vulnerable recovery. Outgoing wave effects continue independently afterward.
 */
final class WaterMaelstromAttack {
    private static final double PREPARATION_RADIUS = 6.0D;
    private static final double PREPARATION_HEIGHT_RANGE = 2.0D;

    private enum Phase {
        IDLE, APPROACH, ROTATION, ASCENT, MEDIUM_PULL, STRONG_PULL, DESCENT, RECOVERY
    }

    private final WaterSheeperEntity boss;
    private final WaterMaelstromSuction suction = new WaterMaelstromSuction();
    private final WaterMaelstromWave wave;
    private Phase phase = Phase.IDLE;
    @Nullable
    private Vec3 destination;
    private int phaseTicks;
    private int replanTicks;
    private double minimumPreparationY = Double.NaN;
    private double ascentStartY;
    private double ascentTargetY;
    private float rotationDegrees;

    /** @param boss owner whose existing swimming navigation handles the approach */
    WaterMaelstromAttack(WaterSheeperEntity boss) {
        this.boss = boss;
        wave = new WaterMaelstromWave(boss);
    }

    /**
     * Chooses a loaded water position within six blocks of the horizontal center.
     * The lowest available level is preferred; at most two blocks above the
     * shared minimum height are allowed, regardless of individual block types.
     *
     * @return whether a safe approach or an immediate rotation could begin
     */
    boolean start() {
        stop();
        return chooseApproach();
    }

    /**
     * Searches target sets together so one blocked center cell cannot hide other
     * reachable positions.
     */
    private boolean chooseApproach() {
        Set<BlockPos> positions = findPreparationPositions();
        Set<BlockPos> floor = new HashSet<>();
        Set<BlockPos> elevated = new HashSet<>();
        for (BlockPos node : positions) {
            double height = point(node).y - minimumPreparationY;
            if (height >= 0 && height <= PREPARATION_HEIGHT_RANGE) {
                (height <= 0.01D ? floor : elevated).add(node);
            }
        }
        boolean currentAllowed = isPreparationPosition(boss.position());
        if (currentAllowed && boss.getY() <= minimumPreparationY + 0.01D) {
            destination = boss.position();
            beginRotation();
            return true;
        }
        Path floorPath = approachPath(floor);
        if (floorPath != null && floorPath.canReach()) {
            followApproach(floorPath);
            return true;
        }
        if (currentAllowed) {
            // If the lowest level cannot be reached, the shared two-block
            // height band still permits preparation at the current position.
            destination = boss.position();
            beginRotation();
            return true;
        }
        Path elevatedPath = approachPath(elevated);
        if (elevatedPath != null && elevatedPath.canReach()) {
            followApproach(elevatedPath);
            return true;
        }
        // Long approaches may exceed the pathfinder's range. Continue along a
        // progressing partial route, then reassess alternatives if it stalls.
        Path partial = floorPath != null ? floorPath : elevatedPath;
        if (partial != null) {
            followApproach(partial);
            return true;
        }
        return false;
    }

    private Vec3 point(BlockPos node) {
        double offset = (int) (boss.getBbWidth() + 1.0F) * 0.5D;
        return new Vec3(node.getX() + offset, node.getY(), node.getZ() + offset);
    }

    private boolean insidePreparationArea(Vec3 point) {
        double dx = point.x - boss.getArenaCenter().getX() - 0.5D;
        double dz = point.z - boss.getArenaCenter().getZ() - 0.5D;
        return dx * dx + dz * dz <= PREPARATION_RADIUS * PREPARATION_RADIUS
                && boss.isWithinMovementArea(point.x, point.z);
    }

    /**
     * Finds a shared minimum rather than measuring above each local obstacle.
     * The saved spawn height anchors the reference; lower free positions within
     * the preparation area can lower it, but placed blocks cannot raise it.
     *
     * @return loaded, full-body swim positions across the preparation cylinder
     */
    private Set<BlockPos> findPreparationPositions() {
        minimumPreparationY = boss.getArenaCenter().getY();
        double offset = (int) (boss.getBbWidth() + 1.0F) * 0.5D;
        BlockPos center = new BlockPos(boss.getArenaCenter().getX() + 0.5D - offset,
                boss.getArenaWaterMinimumY(), boss.getArenaCenter().getZ() + 0.5D - offset);
        int maximumY = Mth.floor(boss.getArenaWaterSurfaceY() - boss.getBbHeight() - 0.15D);
        Set<BlockPos> positions = new HashSet<>();
        if (maximumY < center.getY()) {
            return positions;
        }
        for (BlockPos node : BlockPos.betweenClosed(
                new BlockPos(center.getX() - 6, center.getY(), center.getZ() - 6),
                new BlockPos(center.getX() + 6, maximumY, center.getZ() + 6))) {
            Vec3 point = point(node);
            if (insidePreparationArea(point) && boss.canSwimAt(point)) {
                positions.add(node.immutable());
                minimumPreparationY = Math.min(minimumPreparationY, point.y);
            }
        }
        return positions;
    }

    private boolean isPreparationPosition(Vec3 point) {
        return insidePreparationArea(point) && point.y >= minimumPreparationY - 0.01D
                && point.y <= minimumPreparationY + PREPARATION_HEIGHT_RANGE
                && boss.canSwimAt(point);
    }

    /**
     * Accepts progressing partial paths while keeping every node inside the flooded
     * movement area.
     */
    @Nullable
    private Path approachPath(Set<BlockPos> targets) {
        if (targets.isEmpty()) {
            return null;
        }
        Path path = boss.getNavigation().createPath(targets, 0);
        if (path == null || path.getNodeCount() < 2) {
            return null;
        }
        for (int index = 1; index < path.getNodeCount(); index++) {
            Vec3 point = path.getEntityPosAtNode(boss, index);
            if (!boss.isWithinMovementArea(point.x, point.z) || !boss.canSwimAt(point)) {
                return null;
            }
        }
        Vec3 target = point(path.getTarget());
        Vec3 end = path.getEntityPosAtNode(boss, path.getNodeCount() - 1);
        return end.distanceToSqr(target) < boss.position().distanceToSqr(target) - 0.25D ? path : null;
    }

    private void followApproach(Path path) {
        destination = point(path.getTarget());
        phase = Phase.APPROACH;
        boss.getNavigation().moveTo(path, WaterSheeperConfig.WATER_MAELSTROM_CENTER_SPEED.get());
        replanTicks = 20;
    }

    /**
     * Advances only while combat and AI are active. Obstructed or stalled
     * approaches terminate instead of leaving an immune boss trapped in terrain.
     *
     * @return whether preparation still owns movement and damage immunity
     */
    boolean tick() {
        if (phase == Phase.IDLE || destination == null) {
            return false;
        }
        if (!Double.isFinite(minimumPreparationY)) {
            findPreparationPositions();
        }
        if ((phase == Phase.APPROACH || phase == Phase.ROTATION) && !isPreparationPosition(destination)
                && (phase != Phase.APPROACH || !chooseApproach())) {
            return false;
        }
        if (phase == Phase.APPROACH) {
            if (boss.position().distanceToSqr(destination) <= 0.36D && isPreparationPosition(boss.position())) {
                beginRotation();
            } else if (++phaseTicks >= SheeperConfig
                    .secondsToTicks(WaterSheeperConfig.WATER_MAELSTROM_CENTER_TIMEOUT.get())) {
                return false;
            } else {
                // Stopping the combat goal can cancel the initial route during
                // the AI tick; immediately establish Maelstrom's own route again.
                if (phaseTicks == 1 || --replanTicks <= 0) {
                    double offset = (int) (boss.getBbWidth() + 1.0F) * 0.5D;
                    Path path = approachPath(Set.of(new BlockPos(destination.x - offset, destination.y,
                            destination.z - offset)));
                    if (path != null) {
                        boss.getNavigation().moveTo(path, WaterSheeperConfig.WATER_MAELSTROM_CENTER_SPEED.get());
                    } else if (!chooseApproach()) {
                        return false;
                    }
                    replanTicks = 20;
                }
                boss.getLookControl().setLookAt(destination.x, destination.y + boss.getBbHeight() * 0.5D,
                        destination.z);
                return true;
            }
        }
        if (phase == Phase.DESCENT) {
            return descend();
        }
        if (phase == Phase.RECOVERY) {
            holdPosition();
            return ++phaseTicks < SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_SPECIAL_RECOVERY.get());
        }
        holdPosition();
        phaseTicks++;
        if (phase == Phase.ASCENT) {
            ascend();
        }
        if (isPulling()) {
            pullPlayers();
        }
        int rotationMultiplier = phase == Phase.STRONG_PULL ? 4 : phase == Phase.MEDIUM_PULL ? 2 : 1;
        rotationDegrees += WaterSheeperConfig.WATER_MAELSTROM_ROTATION_SPEED.get().floatValue()
                * rotationMultiplier / 20;
        boss.setMaelstromRotation(rotationDegrees);
        if (phaseTicks % 4 == 0) {
            showVortex();
        }
        if (phaseTicks >= phaseDuration()) {
            switch (phase) {
                case ROTATION -> {
                    phase = Phase.ASCENT;
                    ascentStartY = boss.getY();
                    ascentTargetY = Math.min(boss.getArenaWaterSurfaceY() - boss.getBbHeight() - 0.15D,
                            ascentStartY + WaterSheeperConfig.WATER_MAELSTROM_ASCENT_HEIGHT.get());
                }
                case ASCENT -> phase = Phase.MEDIUM_PULL;
                case MEDIUM_PULL -> phase = Phase.STRONG_PULL;
                case STRONG_PULL -> {
                    phase = Phase.DESCENT;
                    suction.clear();
                    rotationDegrees = 0;
                    boss.setMaelstromRotation(0);
                }
                default -> {
                    return false;
                }
            }
            phaseTicks = 0;
        }
        return true;
    }

    /**
     * Drops through normal collision until ground or the captured lower bound is
     * reached.
     */
    private boolean descend() {
        holdPosition();
        double bottom = Math.max(boss.getArenaWaterMinimumY(), minimumPreparationY);
        double distance = Math.max(0, boss.getY() - bottom);
        double step = Math.min(distance, WaterSheeperConfig.WATER_MAELSTROM_SLAM_SPEED.get() / 20.0D);
        double movement = Shapes.collide(Direction.Axis.Y, boss.getBoundingBox(),
                boss.level.getBlockCollisions(boss, boss.getBoundingBox().expandTowards(0, -step, 0)), -step);
        if (movement < 0) {
            boss.move(MoverType.SELF, new Vec3(0, movement, 0));
        }
        if (distance <= step + 0.01D || Math.abs(movement) < step - 0.001D || ++phaseTicks >= 200) {
            // Immunity and suction end immediately at impact; recovery holds the
            // boss still while the independent wave carries and damages players.
            wave.start(boss.position());
            phase = Phase.RECOVERY;
            phaseTicks = 0;
            holdPosition();
        }
        return true;
    }

    /**
     * Advances outgoing wave effects after the active attack has entered or
     * finished recovery.
     */
    void tickEffects() {
        wave.tick(boss.getCombatParticipants());
    }

    /** @return whether impact already granted the vulnerable recovery window */
    boolean hasImpacted() {
        return phase == Phase.RECOVERY;
    }

    /** @return whether the current phase grants immunity, ending at impact */
    boolean isImmune() {
        return isActive() && phase != Phase.RECOVERY;
    }

    private int phaseDuration() {
        double seconds = switch (phase) {
            case ROTATION -> WaterSheeperConfig.WATER_MAELSTROM_ROTATION_DURATION.get();
            case ASCENT -> WaterSheeperConfig.WATER_MAELSTROM_ASCENT_DURATION.get();
            case MEDIUM_PULL -> WaterSheeperConfig.WATER_MAELSTROM_MEDIUM_DURATION.get();
            case STRONG_PULL -> WaterSheeperConfig.WATER_MAELSTROM_STRONG_DURATION.get();
            default -> 0.05D;
        };
        return SheeperConfig.secondsToTicks(seconds);
    }

    /**
     * Moves through ordinary collision handling and stays submerged even under a
     * low ceiling.
     */
    private void ascend() {
        double progress = Math.min(1.0D, (double) phaseTicks / phaseDuration());
        double nextY = Mth.lerp(progress, ascentStartY, ascentTargetY);
        // Keep each step slow even when a formerly obstructed column opens again.
        double step = Math.min(Math.max(0, nextY - boss.getY()),
                Math.max(0, ascentTargetY - ascentStartY) / phaseDuration());
        Vec3 next = boss.position().add(0, step, 0);
        if (step > 0 && boss.canSwimAt(next)) {
            boss.move(MoverType.SELF, new Vec3(0, step, 0));
        }
    }

    private boolean isPulling() {
        return phase == Phase.ASCENT || phase == Phase.MEDIUM_PULL || phase == Phase.STRONG_PULL;
    }

    /**
     * Adds an inward impulse without locking player controls or changing height.
     * Collision remains ordinary player movement; no player is moved through
     * blocks.
     */
    private void pullPlayers() {
        double weak = WaterSheeperConfig.WATER_MAELSTROM_WEAK_PULL.get();
        double medium = Math.max(weak, WaterSheeperConfig.WATER_MAELSTROM_MEDIUM_PULL.get());
        double strength = phase == Phase.STRONG_PULL
                ? Math.max(medium, WaterSheeperConfig.WATER_MAELSTROM_STRONG_PULL.get())
                : phase == Phase.MEDIUM_PULL ? medium : weak;
        suction.tick(boss.getCombatParticipants(), destination, strength);
    }

    private void beginRotation() {
        phase = Phase.ROTATION;
        phaseTicks = 0;
        holdPosition();
    }

    private void holdPosition() {
        boss.getNavigation().stop();
        boss.setDeltaMovement(Vec3.ZERO);
        boss.setXxa(0);
        boss.setYya(0);
        boss.setZza(0);
    }

    /**
     * Small rotating bubble arcs introduce the vortex without creating water
     * blocks.
     */
    private void showVortex() {
        int intensity = phase == Phase.STRONG_PULL ? 3 : phase == Phase.MEDIUM_PULL ? 2 : 1;
        int layers = isPulling() ? 3 : 1;
        double angle = rotationDegrees * Math.PI / 180;
        for (int layer = 0; layer < layers; layer++) {
            double radius = boss.getBbWidth() * 0.5D + 0.5D + layer * intensity;
            for (int index = 0; index < 8 * intensity; index++) {
                double theta = angle + index * Math.PI * 2 / (8 * intensity) + layer * 0.5D;
                Vec3 point = boss.getBoundingBox().getCenter().add(Math.cos(theta) * radius,
                        Math.sin(theta * 2) * boss.getBbHeight() * 0.25D - layer * 0.3D,
                        Math.sin(theta) * radius);
                BlockPos cell = new BlockPos(point);
                if (boss.isArenaWaterCell(cell)
                        && WaterArenaData.isLoaded((ServerLevel) boss.level, cell)
                        && boss.level.getFluidState(cell).is(FluidTags.WATER)) {
                    ((ServerLevel) boss.level).sendParticles(ParticleTypes.BUBBLE_COLUMN_UP,
                            point.x, point.y, point.z, 1, 0.05D, 0.05D, 0.05D, 0.02D);
                }
            }
        }
    }

    /**
     * @return whether this preparation suppresses all other attacks and normal
     *         navigation
     */
    boolean isActive() {
        return phase != Phase.IDLE;
    }

    /** @return whether the current phase must remain stationary */
    boolean isStationary() {
        return phase == Phase.ROTATION || phase == Phase.MEDIUM_PULL || phase == Phase.STRONG_PULL
                || phase == Phase.RECOVERY;
    }

    /**
     * Releases immunity through the phase state and clears motion and visual
     * rotation.
     */
    void stop() {
        finish();
        wave.clear();
    }

    /**
     * Releases the active boss phase while allowing already released waves to
     * complete.
     */
    void finish() {
        if (isActive()) {
            holdPosition();
        }
        phase = Phase.IDLE;
        destination = null;
        phaseTicks = 0;
        replanTicks = 0;
        minimumPreparationY = Double.NaN;
        ascentStartY = 0;
        ascentTargetY = 0;
        rotationDegrees = 0;
        suction.clear();
        boss.setMaelstromRotation(0);
    }

    /**
     * @return bounded preparation state; unloaded time does not advance the attack
     */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Wave", wave.save());
        tag.putInt("Phase", phase.ordinal());
        tag.putInt("Ticks", phaseTicks);
        tag.putFloat("RotationDegrees", rotationDegrees);
        tag.putDouble("AscentStartY", ascentStartY);
        tag.putDouble("AscentTargetY", ascentTargetY);
        if (Double.isFinite(minimumPreparationY)) {
            tag.putDouble("MinimumY", minimumPreparationY);
        }
        if (destination != null) {
            tag.putDouble("X", destination.x);
            tag.putDouble("Y", destination.y);
            tag.putDouble("Z", destination.z);
        }
        return tag;
    }

    /**
     * @param tag saved preparation, revalidated against current water and terrain
     *            on its next tick
     */
    void load(CompoundTag tag) {
        stop();
        wave.load(tag.getCompound("Wave"));
        int savedPhase = tag.getInt("Phase");
        Vec3 point = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        if (savedPhase <= 0 || savedPhase >= Phase.values().length
                || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
            return;
        }
        phase = Phase.values()[savedPhase];
        destination = point;
        if (tag.contains("MinimumY", Tag.TAG_DOUBLE)
                && Double.isFinite(tag.getDouble("MinimumY"))) {
            minimumPreparationY = tag.getDouble("MinimumY");
        }
        phaseTicks = Mth.clamp(tag.getInt("Ticks"), 0, phase == Phase.APPROACH ? 600 : 200);
        ascentStartY = tag.getDouble("AscentStartY");
        ascentTargetY = tag.getDouble("AscentTargetY");
        if (!Double.isFinite(ascentStartY) || !Double.isFinite(ascentTargetY)
                || ascentTargetY < ascentStartY || ascentTargetY - ascentStartY > 8) {
            stop();
            return;
        }
        float savedRotation = tag.contains("RotationDegrees", Tag.TAG_FLOAT) ? tag.getFloat("RotationDegrees")
                : phaseTicks * WaterSheeperConfig.WATER_MAELSTROM_ROTATION_SPEED.get().floatValue() / 20;
        rotationDegrees = Float.isFinite(savedRotation) ? Mth.clamp(savedRotation, 0, 1000000) : 0;
        boss.setMaelstromRotation(rotationDegrees);
    }
}
