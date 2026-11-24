package de.codemorra.sheeper.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.WaterArenaData;
import de.codemorra.sheeper.world.ArenaWater;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.MoverType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Owns Maelstrom's preparation, staged suction, collision-aware slam and
 * vulnerable recovery. Outgoing wave effects continue independently afterward.
 */
final class WaterMaelstromAttack {
    private static final float INITIAL_ROTATION_DEGREES_PER_SECOND = 180.0F;
    private static final double PREPARATION_RADIUS = 6.0D;
    private static final double PREFERRED_FLOOR_CLEARANCE = 1;
    private static final double MAX_FLOOR_CLEARANCE = 2;
    private static final double COLLISION_EPSILON = 0.001;

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
    private double ascentStartY;
    private double ascentTargetY;
    private float rotationDegrees;

    /** @param boss owner whose existing swimming navigation handles the approach */
    WaterMaelstromAttack(WaterSheeperEntity boss) {
        this.boss = boss;
        wave = new WaterMaelstromWave(boss);
    }

    /**
     * Chooses a loaded water position near the saved arena center and above its
     * local floor.
     *
     * @return whether a safe approach or an immediate rotation could begin
     */
    boolean start() {
        stop();
        return chooseApproach();
    }

    /**
     * Prioritizes reachable starts over the center, then nearby starts and
     * finally the lower positions needed in shallow arenas.
     */
    private boolean chooseApproach() {
        Vec3 current = boss.position();
        double clearance = preparationClearance(current);
        if (Double.isFinite(clearance) && clearance >= PREFERRED_FLOOR_CLEARANCE && isOverCenter(current)) {
            beginRotation();
            return true;
        }
        Path partial = null;
        List<Set<BlockPos>> groups = findPreparationPositions();
        for (Set<BlockPos> candidates : groups) {
            Path path = approachPath(candidates);
            if (path != null && path.canReach()) {
                followApproach(path);
                return true;
            }
            if (partial == null) {
                partial = path;
            }
        }
        if (Double.isFinite(clearance)) {
            beginRotation();
            return true;
        }
        Vec3 bubbleDestination = boss.bubbleTravel().tryBegin(
                groups.stream().flatMap(Set::stream).map(this::point).toList(), false);
        if (bubbleDestination != null) {
            destination = bubbleDestination;
            phase = Phase.APPROACH;
            return true;
        }
        // Beyond the pathfinder's range, keep a progressing route and retry at the next waypoint.
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

    /** Includes the closest aligned nodes for narrow and wide boss bodies. */
    private boolean isOverCenter(Vec3 point) {
        double dx = point.x - boss.getArenaCenter().getX() - 0.5;
        double dz = point.z - boss.getArenaCenter().getZ() - 0.5;
        return Math.abs(dx) <= 0.5 && Math.abs(dz) <= 0.5;
    }

    /**
     * @return loaded swim nodes grouped by preference for the approach
     */
    private List<Set<BlockPos>> findPreparationPositions() {
        Set<BlockPos> central = new HashSet<>();
        Set<BlockPos> nearby = new HashSet<>();
        Set<BlockPos> low = new HashSet<>();
        List<Set<BlockPos>> groups = List.of(central, nearby, low);
        double offset = (int) (boss.getBbWidth() + 1.0F) * 0.5D;
        BlockPos center = new BlockPos(boss.getArenaCenter().getX() + 0.5D - offset,
                boss.getArenaWaterMinimumY(), boss.getArenaCenter().getZ() + 0.5D - offset);
        int maximumY = Mth.floor(boss.getArenaWaterSurfaceY() - boss.getBbHeight() - 0.15D);
        if (maximumY < center.getY()) {
            return groups;
        }
        for (BlockPos node : BlockPos.betweenClosed(
                new BlockPos(center.getX() - 6, center.getY(), center.getZ() - 6),
                new BlockPos(center.getX() + 6, maximumY, center.getZ() + 6))) {
            Vec3 point = point(node);
            double clearance = preparationClearance(point);
            if (Double.isFinite(clearance)) {
                Set<BlockPos> group = clearance < PREFERRED_FLOOR_CLEARANCE ? low
                        : isOverCenter(point) ? central : nearby;
                group.add(node.immutable());
            }
        }
        return groups;
    }

    /**
     * Probes below the whole boss body. The virtual floor limits the probe in
     * open terrain, and solid collision shapes provide the local floor elsewhere.
     *
     * @return the usable drop below this start, or infinity for an unsuitable start
     */
    private double preparationClearance(Vec3 point) {
        if (!insidePreparationArea(point) || !boss.canSwimAt(point)) {
            return Double.POSITIVE_INFINITY;
        }
        AABB body = boss.getBoundingBox().move(point.subtract(boss.position()));
        double probe = Math.min(Math.max(0, point.y - boss.getArenaWaterMinimumY()),
                MAX_FLOOR_CLEARANCE + COLLISION_EPSILON);
        double drop = -clipDescent(body, probe);
        return drop <= MAX_FLOOR_CLEARANCE && (drop == 0 || boss.canSwimAt(point.add(0, -drop, 0)))
                ? drop : Double.POSITIVE_INFINITY;
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
        if (phase == Phase.APPROACH && !Double.isFinite(preparationClearance(destination)) && !chooseApproach()) {
            return false;
        }
        // Once rotation begins, lost support only changes the eventual landing height.
        if (phase == Phase.ROTATION && (!insidePreparationArea(boss.position()) || !boss.canSwimAt(boss.position()))) {
            return false;
        }
        if (phase == Phase.APPROACH) {
            if (boss.position().distanceToSqr(destination) <= 0.36D
                    && Double.isFinite(preparationClearance(boss.position()))) {
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
        rotationDegrees += INITIAL_ROTATION_DEGREES_PER_SECOND
                * rotationMultiplier / 20;
        boss.setMaelstromRotation(rotationDegrees);
        if (phaseTicks % 4 == 0) {
            showVortex();
        }
        if (phaseTicks % 40 == 1) {
            playVortexSound();
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
        double distance = Math.max(0, boss.getY() - boss.getArenaWaterMinimumY());
        double step = Math.min(distance, WaterSheeperConfig.WATER_MAELSTROM_SLAM_SPEED.get() / 20.0D);
        double probe = clipDescent(boss.getBoundingBox(), Math.min(distance, step + COLLISION_EPSILON));
        double movement = Math.max(-step, probe);
        if (!boss.canSwimAt(boss.position().add(0, movement, 0))) {
            return false;
        }
        if (movement < 0) {
            boss.move(MoverType.SELF, new Vec3(0, movement, 0));
        }
        if (distance <= step || probe >= -step) {
            // Immunity and suction end immediately at impact; recovery holds the
            // boss still while the independent wave carries and damages players.
            showImpact();
            wave.start(boss.position());
            phase = Phase.RECOVERY;
            phaseTicks = 0;
            holdPosition();
        }
        return phase == Phase.RECOVERY || ++phaseTicks < 200;
    }

    /** Returns the vertical displacement permitted by the terrain under the body. */
    private double clipDescent(AABB body, double distance) {
        return Shapes.collide(Direction.Axis.Y, body,
                boss.level.getBlockCollisions(boss, body.expandTowards(0, -distance, 0)), -distance);
    }

    /**
     * Advances outgoing wave effects after the active attack has entered or
     * finished recovery.
     */
    void tickEffects() {
        wave.tick(boss.getCombatParticipants());
    }

    /** @return whether the released front is still traveling through the arena */
    boolean hasExpandingWave() {
        return wave.isExpanding();
    }

    /** @return whether rotation is still waiting for the boss to reach its start */
    boolean isApproaching() {
        return phase == Phase.APPROACH;
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
        suction.tick(boss.getCombatParticipants().stream()
                .filter(player -> boss.containsAttackPoint(player.position())).toList(), destination, strength);
    }

    private void beginRotation() {
        destination = boss.position();
        phase = Phase.ROTATION;
        phaseTicks = 0;
        holdPosition();
        WaterAttackSounds.arenaCue(boss, boss.getBoundingBox().getCenter(),
                SoundEvents.CONDUIT_ACTIVATE, 1.2F, 0.65F);
    }

    /** Increasing volume and pitch distinguish the three suction intensities. */
    private void playVortexSound() {
        int intensity = phase == Phase.STRONG_PULL ? 3 : phase == Phase.MEDIUM_PULL ? 2 : 1;
        WaterAttackSounds.arenaCue(boss, boss.getBoundingBox().getCenter(),
                SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,
                0.4F + intensity * 0.25F, 0.55F + intensity * 0.15F);
    }

    private void holdPosition() {
        boss.getNavigation().stop();
        boss.setDeltaMovement(Vec3.ZERO);
        boss.setXxa(0);
        boss.setYya(0);
        boss.setZza(0);
    }

    /**
     * Inward-moving spiral arms grow denser and wider with the suction phases.
     * Particle motion is cosmetic and never changes the player's pull strength.
     */
    private void showVortex() {
        int intensity = phase == Phase.STRONG_PULL ? 3 : phase == Phase.MEDIUM_PULL ? 2 : 1;
        int arms = isPulling() ? 3 : 1;
        int samples = 8 + intensity * 4;
        Vec3 center = boss.getBoundingBox().getCenter();
        double innerRadius = boss.getBbWidth() * 0.5D + 0.4D;
        double reach = isPulling() ? 2 + intensity * 1.5D : 1;
        double angle = rotationDegrees * Math.PI / 180;
        for (int arm = 0; arm < arms; arm++) {
            for (int index = 0; index < samples; index++) {
                double progress = (double) index / (samples - 1);
                double radius = innerRadius + progress * reach;
                double theta = angle + arm * Math.PI * 2 / arms + progress * Math.PI * 1.5D;
                double x = Math.cos(theta);
                double z = Math.sin(theta);
                Vec3 point = center.add(x * radius, (progress - 0.5D) * boss.getBbHeight(), z * radius);
                double inward = isPulling() ? 0.8D + intensity * 0.4D : 0;
                double tangent = 0.5D + intensity * 0.3D;
                sendBubble(point, new Vec3(-x * inward - z * tangent, 0.1D,
                        -z * inward + x * tangent));
            }
        }
    }

    /**
     * Releases a brief, dense outward burst at the exact start of the pressure
     * wave.
     */
    private void showImpact() {
        Vec3 center = boss.getBoundingBox().getCenter();
        WaterAttackSounds.arenaCue(boss, center, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2.0F, 0.6F);
        WaterAttackSounds.arenaCue(boss, center, SoundEvents.CONDUIT_ATTACK_TARGET, 1.4F, 0.6F);
        double radius = boss.getBbWidth() * 0.5D + 0.3D;
        for (int layer = 0; layer < 3; layer++) {
            for (int index = 0; index < 24; index++) {
                double angle = (index + layer * 0.5D) * Math.PI * 2 / 24;
                double x = Math.cos(angle);
                double z = Math.sin(angle);
                Vec3 point = center.add(x * radius, (layer - 1) * boss.getBbHeight() * 0.35D, z * radius);
                sendBubble(point, new Vec3(x * 3, 0.4D, z * 3));
            }
        }
    }

    /**
     * Emits directed bubbles in loaded, unobstructed virtual water cells.
     */
    private void sendBubble(Vec3 point, Vec3 motion) {
        BlockPos cell = new BlockPos(point);
        if (boss.containsAttackPoint(point)
                && WaterArenaData.isLoaded((ServerLevel) boss.level, cell)
                && ArenaWater.isOpenCell(boss.level, cell)) {
            // Zero count preserves the velocity; positive counts randomize it.
            ((ServerLevel) boss.level).sendParticles(ParticleTypes.BUBBLE,
                    point.x, point.y, point.z, 0, motion.x, motion.y, motion.z, 1);
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
        phaseTicks = Mth.clamp(tag.getInt("Ticks"), 0, phase == Phase.APPROACH ? 600 : 200);
        ascentStartY = tag.getDouble("AscentStartY");
        ascentTargetY = tag.getDouble("AscentTargetY");
        if (!Double.isFinite(ascentStartY) || !Double.isFinite(ascentTargetY)
                || ascentTargetY < ascentStartY || ascentTargetY - ascentStartY > 8) {
            stop();
            return;
        }
        float savedRotation = tag.contains("RotationDegrees", Tag.TAG_FLOAT) ? tag.getFloat("RotationDegrees")
                : phaseTicks * INITIAL_ROTATION_DEGREES_PER_SECOND / 20;
        rotationDegrees = Float.isFinite(savedRotation) ? Mth.clamp(savedRotation, 0, 1000000) : 0;
        boss.setMaelstromRotation(rotationDegrees);
    }
}
