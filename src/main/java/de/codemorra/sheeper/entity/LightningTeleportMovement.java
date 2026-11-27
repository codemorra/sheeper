package de.codemorra.sheeper.entity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.ArenaGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Turns, walks toward a warning marker, then teleports to a safe arena position.
 */
final class LightningTeleportMovement {
    private static final double WALK_SPEED = 0.13;
    private static final double MAX_WALK_DISTANCE = 1.6;
    private static final int FAILED_JUMPS_BEFORE_RECOVERY = 2;
    private static final int FAILED_JUMP_RETRY_TICKS = 20;
    private static final int RECOVERY_RETRY_TICKS = 40;

    private final LightningSheeperEntity boss;
    private int cooldown = -1;
    private int warningRemaining;
    private int arcTicks;
    private int failedJumps;
    private boolean recoveringJump;
    private double walkRemaining;
    private boolean walking;
    private boolean idle;
    @Nullable
    private UUID focusId;
    @Nullable
    private Vec3 destination;

    private record Landing(Vec3 position, boolean exposed) {
    }

    LightningTeleportMovement(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    boolean isPreparing() {
        return destination != null;
    }

    /** Gives the next recovery search a turn before another special attack can start. */
    boolean isRecoveryDue() {
        return failedJumps >= FAILED_JUMPS_BEFORE_RECOVERY && cooldown <= 1;
    }

    /** @return whether this tick's vanilla movement may include a short walk before teleporting */
    boolean isWalking() {
        return walking;
    }

    /**
     * Advances ordinary movement without running attack logic.
     *
     * @param allowed whether the arena and boss can currently move
     * @param focus current participant to orbit, or null for untargeted jumps
     * @param untargetedCombat whether untargeted jumps should keep the combat interval
     * @return whether the boss landed at a safe teleport destination this tick
     */
    boolean tick(boolean allowed, @Nullable ServerPlayer focus, boolean untargetedCombat) {
        walking = false;
        if (!allowed || boss.getMovementRadius() <= 0.0D) {
            if (destination != null) {
                finish();
            }
            return false;
        }
        UUID nextFocus = focus == null ? null : focus.getUUID();
        boolean nowIdle = focus == null && !untargetedCombat;
        if (nowIdle != idle || !Objects.equals(focusId, nextFocus)) {
            // Drop the old destination as soon as a player takes or loses focus.
            focusId = nextFocus;
            idle = nowIdle;
            finish();
            if (!idle) {
                cooldown = 0;
            }
        }
        if (arcTicks > 0) {
            if (destination == null || !isSafePlannedLanding(destination)) {
                failJump();
            } else if (--arcTicks == 0) {
                teleportTo(destination);
                failedJumps = 0;
                finish();
                return true;
            }
            return false;
        }
        if (destination != null) {
            if (!isSafePlannedLanding(destination)) {
                failJump();
                return false;
            }
            faceDestination(destination);
            if (--warningRemaining <= 0) {
                beginArc();
            } else {
                if (warningRemaining % 2 == 0) {
                    showWarning();
                }
                walkToward(destination);
            }
            return false;
        }
        if (cooldown < 0) {
            cooldown = nextInterval();
        }
        if (--cooldown > 0) {
            if (focus != null) {
                boss.getLookControl().setLookAt(focus, 30, 20);
            }
            return false;
        }
        prepareJump(focus);
        return false;
    }

    /** Starts a combat jump now, using the same destination search, walk and visible flight as ordinary movement. */
    boolean prepareCombatJump(ServerPlayer focus) {
        if (isPreparing() || boss.getMovementRadius() <= 0) {
            return false;
        }
        idle = false;
        focusId = focus.getUUID();
        return prepareJump(focus);
    }

    /** Replaces a normal warning with a quicker jump, preserving the current movement focus. */
    boolean prepareDodge(int warningTicks) {
        if (arcTicks > 0 || recoveringJump || boss.getMovementRadius() <= 0) {
            return false;
        }
        Vec3 landing = findAttackDestination();
        if (landing == null) {
            return false;
        }
        destination = landing;
        warningRemaining = warningTicks;
        walkRemaining = 0;
        walking = false;
        faceDestination(landing);
        showBurst(boss.position());
        return true;
    }

    private boolean prepareJump(@Nullable ServerPlayer focus) {
        recoveringJump = failedJumps >= FAILED_JUMPS_BEFORE_RECOVERY;
        ServerLevel level = (ServerLevel) boss.level;
        destination = recoveringJump ? findRecoveryDestination(level)
                : focus == null ? findIdleDestination(level, Double.POSITIVE_INFINITY)
                        : findCombatDestination(level, focus);
        if (destination == null) {
            failJump();
            return false;
        }
        faceDestination(destination);
        warningRemaining = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_TELEPORT_WARNING.get());
        walkRemaining = recoveringJump ? 0 : 1 + boss.getRandom().nextDouble() * (MAX_WALK_DISTANCE - 1);
        if (warningRemaining == 0) {
            beginArc();
        } else {
            showWarning();
        }
        return true;
    }

    /** Updates the focus after landing while keeping the pause that just started. */
    void focusAfterLanding(ServerPlayer player) {
        focusId = player.getUUID();
        boss.getLookControl().setLookAt(player, 30, 20);
    }

    private int nextInterval() {
        double minimumSeconds = idle ? LightningSheeperConfig.LIGHTNING_IDLE_INTERVAL_MIN.get()
                : LightningSheeperConfig.LIGHTNING_TELEPORT_INTERVAL_MIN.get();
        double maximumSeconds = idle ? LightningSheeperConfig.LIGHTNING_IDLE_INTERVAL_MAX.get()
                : LightningSheeperConfig.LIGHTNING_TELEPORT_INTERVAL_MAX.get();
        int minimum = SheeperConfig.secondsToTicks(minimumSeconds);
        int maximum = Math.max(minimum, SheeperConfig.secondsToTicks(maximumSeconds));
        return minimum + boss.getRandom().nextInt(maximum - minimum + 1);
    }

    /** Longer idle jumps still originate around the boss, with shorter steps as a terrain fallback. */
    @Nullable
    private Vec3 findIdleDestination(ServerLevel level, double highestLanding) {
        Vec3 origin = boss.position();
        double movementRadius = boss.getMovementRadius();
        double jumpDistance = Math.min(movementRadius, Math.max(8, movementRadius * 0.45));
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double interiorRadius = movementRadius * 0.85;
        Vec3 covered = null;
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = jumpDistance * (attempt < 12
                    ? 0.7 + boss.getRandom().nextDouble() * 0.6
                    : 0.2 + boss.getRandom().nextDouble() * 0.5);
            Vec3 point = origin.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            if (attempt < 16 && point.subtract(center).horizontalDistanceSqr() > interiorRadius * interiorRadius) {
                continue;
            }
            Landing candidate = findLanding(level, point, true, highestLanding);
            if (candidate != null) {
                if (candidate.exposed() || !canPreferSky(level)) {
                    return candidate.position();
                }
                if (covered == null) {
                    covered = candidate.position();
                }
            }
        }
        return covered;
    }

    /** Approaches a distant player first, then searches for lateral jumps at the desired spacing. */
    @Nullable
    private Vec3 findCombatDestination(ServerLevel level, ServerPlayer focus) {
        Vec3 anchor = focus.position();
        Vec3 fromPlayer = boss.position().subtract(anchor);
        double currentDistance = fromPlayer.horizontalDistance();
        double preferred = Math.max(LightningSheeperConfig.LIGHTNING_MOVEMENT_ORBIT_DISTANCE.get(),
                LightningSheeperConfig.LIGHTNING_TELEPORT_PLAYER_DISTANCE.get() + 0.5);
        boolean approaching = currentDistance > preferred + 1.5;
        double angle = Mth.atan2(fromPlayer.z, fromPlayer.x);
        if (!approaching) {
            angle += boss.getRandom().nextBoolean() ? Math.PI / 3 : -Math.PI / 3;
        }
        Vec3 covered = null;
        // Keep a blocked search close to the player. Search both sides before varying distance.
        for (double distance : new double[] { preferred, preferred * 0.75, preferred * 1.25 }) {
            for (int attempt = 0; attempt < 12; attempt++) {
                int turn = (attempt + 1) / 2 * (attempt % 2 == 0 ? -1 : 1);
                double bearing = angle + turn * Math.PI / 6;
                Vec3 point = anchor.add(Math.cos(bearing) * distance, 0, Math.sin(bearing) * distance);
                Landing candidate = findLanding(level, point, true, Double.POSITIVE_INFINITY);
                if (candidate != null && (!approaching
                        || candidate.position().subtract(anchor).horizontalDistance() < currentDistance - 0.5)) {
                    if (candidate.exposed() || !canPreferSky(level)) {
                        return candidate.position();
                    }
                    if (covered == null) {
                        covered = candidate.position();
                    }
                }
            }
        }
        return covered;
    }

    /** Checks the exposed surface first, then a floor around the ordinary search height. */
    @Nullable
    private Landing findLanding(ServerLevel level, Vec3 point, boolean requirePlayerClearance, double highestLanding) {
        int x = Mth.floor(point.x);
        int z = Mth.floor(point.z);
        if (!boss.isWithinMovementArea(x + 0.5, z + 0.5)
                || !isLoaded(level, new AABB(x - 1, 0, z - 1, x + 2, 1, z + 2))) {
            return null;
        }
        if (canPreferSky(level)) {
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (surfaceY >= boss.getArenaMinimumY() && surfaceY < boss.getArenaMaximumY()
                    && surfaceY - 1 <= highestLanding) {
                Vec3 exposed = landingNear(level, x, z, surfaceY, requirePlayerClearance, highestLanding);
                if (exposed != null && Mth.ceil(exposed.y) >= surfaceY
                        && level.canSeeSky(new BlockPos(x, Mth.ceil(exposed.y), z))) {
                    return new Landing(exposed, true);
                }
            }
        }
        int referenceY = Mth.ceil(point.y);
        if (highestLanding < referenceY) {
            referenceY = Mth.floor(highestLanding);
        }
        Vec3 covered = landingNear(level, x, z, referenceY, requirePlayerClearance, highestLanding);
        return covered == null ? null : new Landing(covered, false);
    }

    @Nullable
    private Vec3 landingNear(ServerLevel level, int x, int z, int referenceY, boolean requirePlayerClearance,
            double highestLanding) {
        Double surface = boss.findArenaSurface(level, x, z, referenceY);
        if (surface == null) {
            return null;
        }
        Vec3 destination = new Vec3(x + 0.5, surface, z + 0.5);
        return destination.y <= highestLanding && destination.distanceToSqr(boss.position()) >= 1
                && (!requirePlayerClearance || hasPlayerClearance(level, destination))
                && hasSafeLanding(level, destination)
                ? destination : null;
    }

    /** Widens a failed local search to the whole arena without bypassing collision or chunk checks. */
    @Nullable
    private Vec3 findRecoveryDestination(ServerLevel level) {
        Vec3 origin = boss.position();
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double radius = boss.getMovementRadius();
        double heading = boss.getRandom().nextDouble() * Math.PI * 2;
        Vec3 best = null;
        int bestPriority = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 57; attempt++) {
            Vec3 point = center;
            if (attempt > 0 && attempt <= 24) {
                // Sample three rings around the boss to catch narrow landings near the obstruction.
                double angle = heading + (attempt - 1) % 8 * Math.PI / 4;
                double distance = 2 << ((attempt - 1) / 8);
                point = origin.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            } else if (attempt > 24) {
                double angle = boss.getRandom().nextDouble() * Math.PI * 2;
                double distance = Math.sqrt(boss.getRandom().nextDouble()) * radius;
                // Try other floors too when nearly all of the arena lies underground.
                double y = attempt % 3 == 0 ? Mth.nextInt(boss.getRandom(), boss.getArenaMinimumY(),
                        boss.getArenaMaximumY() - 1) : origin.y;
                point = new Vec3(center.x + Math.cos(angle) * distance, y, center.z + Math.sin(angle) * distance);
            }
            Landing candidate = findLanding(level, point, true, Double.POSITIVE_INFINITY);
            boolean hasClearance = candidate != null && isRecoverySpaceEmpty(level, candidate.position());
            if (!hasClearance) {
                if (bestPriority < 2) {
                    continue;
                }
                candidate = findLanding(level, point, false, Double.POSITIVE_INFINITY);
                if (candidate == null || !isRecoverySpaceEmpty(level, candidate.position())) {
                    continue;
                }
            }
            int priority = landingPriority(hasClearance, candidate);
            if (priority < bestPriority) {
                best = candidate.position();
                bestPriority = priority;
                if (priority == 0 || priority == 1 && !canPreferSky(level)) {
                    break;
                }
            }
        }
        return best;
    }

    private static int landingPriority(boolean hasClearance, Landing landing) {
        return (hasClearance ? 0 : 2) + (landing.exposed() ? 0 : 1);
    }

    private static boolean canPreferSky(ServerLevel level) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
    }

    /** Recovery can use a tighter gap to players while still requiring a safe, empty landing. */
    private boolean isSafePlannedLanding(Vec3 target) {
        ServerLevel level = (ServerLevel) boss.level;
        return recoveringJump ? hasSafeLanding(level, target) && isRecoverySpaceEmpty(level, target) : isSafe(target);
    }

    /** Vanilla block and entity collision checks do not exclude most living hitboxes. */
    private boolean isRecoverySpaceEmpty(ServerLevel level, Vec3 target) {
        AABB bounds = boss.getBoundingBox().move(target.subtract(boss.position()));
        return level.getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> entity != boss && entity.isAlive() && !entity.isSpectator()).isEmpty();
    }

    private boolean isSafe(Vec3 target) {
        ServerLevel level = (ServerLevel) boss.level;
        return hasSafeLanding(level, target) && hasPlayerClearance(level, target);
    }

    private boolean hasSafeLanding(ServerLevel level, Vec3 target) {
        if (!boss.isWithinMovementArea(target.x, target.z)) {
            return false;
        }
        AABB bounds = boss.getBoundingBox().move(target.subtract(boss.position()));
        if (!isSafeBody(level, bounds)) {
            return false;
        }
        Double surface = boss.findArenaSurface(level, Mth.floor(target.x), Mth.floor(target.z), Mth.ceil(target.y));
        return surface != null && Math.abs(surface - target.y) <= 1.0E-6;
    }

    private boolean hasPlayerClearance(ServerLevel level, Vec3 target) {
        double distance = LightningSheeperConfig.LIGHTNING_TELEPORT_PLAYER_DISTANCE.get();
        return level.players().stream().noneMatch(player -> player.isAlive() && !player.isRemoved()
                && !player.isSpectator() && player.distanceToSqr(target) < distance * distance);
    }

    /** Finds an attack destination using player distance as a preference and terrain as a requirement. */
    @Nullable
    Vec3 findPreferredAttackDestination(List<ServerPlayer> players, double preferredDistance) {
        return findPreferredAttackDestination(players, preferredDistance, boss.getMovementRadius());
    }

    /** Prefers the requested spacing and an exposed landing within the attack's allowed radius. */
    @Nullable
    Vec3 findPreferredAttackDestination(List<ServerPlayer> players, double preferredDistance, double centerRadius) {
        ServerLevel level = (ServerLevel) boss.level;
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter()).add(0, boss.getY() - boss.getArenaCenter().getY(), 0);
        double radius = Math.min(centerRadius, boss.getMovementRadius());
        boolean limitedToCenter = radius < boss.getMovementRadius();
        Vec3 destination = null;
        int bestPriority = Integer.MAX_VALUE;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int attempt = 0; attempt < 48; attempt++) {
            Vec3 anchor;
            double distance;
            if (attempt < 24 && !players.isEmpty()) {
                anchor = players.get(boss.getRandom().nextInt(players.size())).position();
                distance = preferredDistance + boss.getRandom().nextDouble() * 2;
            } else if (attempt < 36 && !limitedToCenter) {
                anchor = boss.position();
                distance = 1 + boss.getRandom().nextDouble() * 5;
            } else {
                anchor = center;
                // Also try the exact center when looking for a launch position in a small area.
                distance = limitedToCenter && attempt == 24 ? 0 : Math.sqrt(boss.getRandom().nextDouble()) * radius;
            }
            double angle = boss.getRandom().nextDouble() * Math.PI * 2;
            Vec3 point = anchor.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            double dx = Mth.floor(point.x) + 0.5 - center.x;
            double dz = Mth.floor(point.z) + 0.5 - center.z;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            Landing landing = findLanding(level, point, false, Double.POSITIVE_INFINITY);
            if (landing == null) {
                continue;
            }
            Vec3 candidate = landing.position();
            double nearest = players.stream().mapToDouble(player -> player.position().subtract(candidate)
                    .horizontalDistance()).min().orElse(preferredDistance);
            int priority = landingPriority(nearest >= preferredDistance, landing);
            double score = Math.abs(nearest - preferredDistance);
            if (priority < bestPriority || priority == bestPriority && score < bestScore) {
                bestPriority = priority;
                bestScore = score;
                destination = candidate;
                if (score <= 0.5 && (priority == 0 || priority == 1 && !canPreferSky(level))) {
                    break;
                }
            }
        }
        return destination;
    }

    /**
     * Samples the whole movement area for a lightning row salvo. When a distance
     * preference cannot be met, the previous position takes priority over players.
     */
    @Nullable
    Vec3 findLineAttackDestination(List<ServerPlayer> players, double minimumJump, double minimumPlayerDistance) {
        ServerLevel level = (ServerLevel) boss.level;
        Vec3 origin = boss.position();
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter()).add(0, origin.y - boss.getArenaCenter().getY(), 0);
        double radius = boss.getMovementRadius();
        Landing best = null;
        double bestJumpShortfall = Double.POSITIVE_INFINITY;
        double bestPlayerShortfall = Double.POSITIVE_INFINITY;
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(boss.getRandom().nextDouble()) * radius;
            Vec3 point = center.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            Landing landing = findLanding(level, point, false, Double.POSITIVE_INFINITY);
            if (landing == null) {
                continue;
            }
            Vec3 destination = landing.position();
            double jumpShortfall = Math.max(0, minimumJump - destination.subtract(origin).horizontalDistance());
            if (jumpShortfall > bestJumpShortfall) {
                continue;
            }
            double nearest = players.stream().mapToDouble(player -> destination.subtract(player.position())
                    .horizontalDistance()).min().orElse(minimumPlayerDistance);
            double playerShortfall = Math.max(0, minimumPlayerDistance - nearest);
            if (best == null || jumpShortfall < bestJumpShortfall || playerShortfall < bestPlayerShortfall
                    || playerShortfall == bestPlayerShortfall && landing.exposed() && !best.exposed()) {
                best = landing;
                bestJumpShortfall = jumpShortfall;
                bestPlayerShortfall = playerShortfall;
                if (jumpShortfall == 0 && playerShortfall == 0 && (landing.exposed() || !canPreferSky(level))) {
                    break;
                }
            }
        }
        return best == null ? null : best.position();
    }

    /** Uses the normal flight duration without imposing the normal teleport's minimum player distance. */
    boolean beginPreferredAttackTravel(Vec3 destination) {
        if (!hasSafeLanding((ServerLevel) boss.level, destination)) {
            return false;
        }
        startTravel(destination, LightningSheeperEntity.ARC_TRAVEL_TICKS);
        return true;
    }

    /** Rechecks the terrain after a flight whose spacing was only a preference. */
    boolean landPreferredAttackTravel(Vec3 destination) {
        return finishTravel(destination, hasSafeLanding((ServerLevel) boss.level, destination));
    }

    /** Keeps a low attack near its takeoff point, even if the boss is already above the floor. */
    @Nullable
    Vec3 findAttackGround(ServerLevel level) {
        Vec3 origin = boss.position();
        return hasSafeLanding(level, origin) ? origin : findGroundBelow(level, null);
    }

    /** Searches below the hover point first and uses the original takeoff point if nearby ground is unsafe. */
    @Nullable
    Vec3 findGroundBelow(ServerLevel level, Vec3 departure) {
        Vec3 origin = boss.position();
        int bottom = boss.getArenaMinimumY();
        int highest = Math.min(boss.getArenaMaximumY() - 1, Mth.floor(origin.y));
        for (int ring = 0; ring <= 4; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    double x = origin.x + dx;
                    double z = origin.z + dz;
                    double floor = LightningRingSurface.find(level, x, z, origin.y, bottom, highest);
                    if (Double.isFinite(floor) && floor < origin.y) {
                        Vec3 landing = new Vec3(x, floor, z);
                        if (hasSafeLanding(level, landing)) {
                            return landing;
                        }
                    }
                }
            }
        }
        if (departure != null && departure.y < origin.y && hasSafeLanding(level, departure)) {
            return departure;
        }
        return findIdleDestination(level, Math.nextDown(origin.y));
    }

    /** Checks the full scaled body against arena bounds, loaded terrain, and hazards. */
    private boolean isSafeBody(ServerLevel level, AABB bounds) {
        return isSafeBody(level, bounds, false);
    }

    private boolean isSafeBody(ServerLevel level, AABB bounds, boolean allowEntityOverlap) {
        double radius = boss.getCombatRadius();
        if (ArenaGeometry.farthestCornerDistanceSquared(boss.getArenaCenter(), bounds) > radius * radius
                || bounds.minY < boss.getArenaMinimumY()
                || bounds.maxY > boss.getArenaMaximumY() || !level.getWorldBorder().isWithinBounds(bounds)
                || !isLoaded(level, bounds.inflate(1))) {
            return false;
        }
        if (allowEntityOverlap ? level.getBlockCollisions(boss, bounds).iterator().hasNext()
                : !level.noCollision(boss, bounds)) {
            return false;
        }
        // Collision-free space can still contain fluids, fire, or harmful plants.
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(bounds.minX, bounds.minY, bounds.minZ),
                new BlockPos(bounds.maxX - 1.0E-7D, bounds.maxY - 1.0E-7D, bounds.maxZ - 1.0E-7D))) {
            BlockPathTypes path = WalkNodeEvaluator.getBlockPathTypeStatic(level, pos.mutable());
            if (!level.getFluidState(pos).isEmpty() || path.getDanger() != null
                    || path == BlockPathTypes.POWDER_SNOW || path == BlockPathTypes.DANGER_POWDER_SNOW) {
                return false;
            }
        }
        return true;
    }

    /** Turns the boss toward a planned destination without moving it. */
    void faceDestination(Vec3 target) {
        Vec3 direction = target.subtract(boss.position());
        if (direction.horizontalDistanceSqr() > 1.0E-8) {
            float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90;
            boss.setYRot(yaw);
            boss.setYBodyRot(yaw);
            boss.setYHeadRot(yaw);
        }
    }

    /** Lets vanilla movement animate the short approach, stopping before walls and ledges. */
    private void walkToward(Vec3 target) {
        if (walkRemaining <= 0 || !boss.isOnGround()) {
            return;
        }
        Vec3 direction = new Vec3(target.x - boss.getX(), 0, target.z - boss.getZ());
        double distance = Math.min(Math.min(walkRemaining, WALK_SPEED), direction.horizontalDistance() - 0.5);
        if (distance <= 0) {
            return;
        }
        Vec3 step = direction.normalize().scale(distance);
        AABB next = boss.getBoundingBox().move(step);
        ServerLevel level = (ServerLevel) boss.level;
        if (!isSafeBody(level, next)
                || !level.getBlockCollisions(boss, next.deflate(0.02).move(0, -0.25, 0)).iterator().hasNext()) {
            return;
        }
        walking = true;
        walkRemaining -= distance;
        boss.setDeltaMovement(step.x, boss.getDeltaMovement().y, step.z);
    }

    /**
     * Includes the collision and path-classification neighborhood before either may
     * read blocks.
     */
    private boolean isLoaded(ServerLevel level, AABB bounds) {
        for (int x = Mth.floor(bounds.minX) >> 4; x <= Mth.floor(bounds.maxX) >> 4; x++) {
            for (int z = Mth.floor(bounds.minZ) >> 4; z <= Mth.floor(bounds.maxZ) >> 4; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void showWarning() {
        ((ServerLevel) boss.level).sendParticles(ParticleTypes.ELECTRIC_SPARK,
                destination.x, destination.y + 0.2D, destination.z, 8, 0.35D, 0.15D, 0.35D, 0.02D);
    }

    /** Picks a safe destination anywhere within the attack's allowed movement area. */
    @Nullable
    Vec3 findAttackDestination() {
        ServerLevel level = (ServerLevel) boss.level;
        double radius = boss.getMovementRadius();
        if (radius <= 0) {
            return null;
        }
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter()).add(0, boss.getY() - boss.getArenaCenter().getY(), 0);
        Vec3 covered = null;
        // Sample the whole allowed area independently of the current target or position.
        for (int attempt = 0; attempt < 36; attempt++) {
            double angle = boss.getRandom().nextDouble() * 2 * Math.PI;
            double distance = Math.sqrt(boss.getRandom().nextDouble()) * radius;
            Vec3 point = center.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            Landing candidate = findLanding(level, point, true, Double.POSITIVE_INFINITY);
            if (candidate != null) {
                if (candidate.exposed() || !canPreferSky(level)) {
                    return candidate.position();
                }
                if (covered == null) {
                    covered = candidate.position();
                }
            }
        }
        return covered;
    }

    /** Gives distant jumps a longer visible path without prolonging short jumps unnecessarily. */
    int attackTravelTicks(Vec3 target) {
        return Mth.clamp(Mth.ceil(boss.position().distanceTo(target) / 3.0), 12, 32);
    }

    /** Starts the same lightning form used for normal movement, with no approach walk. */
    int beginAttackTravel(Vec3 target) {
        if (!isSafe(target)) {
            return 0;
        }
        return startAttackTravel(target);
    }

    /** Starts a visible flight to an attack position already checked by its caller. */
    int startAttackTravel(Vec3 target) {
        int ticks = attackTravelTicks(target);
        startTravel(target, ticks);
        return ticks;
    }

    private void startTravel(Vec3 target, int ticks) {
        faceDestination(target);
        boss.startTravelArc(target, ticks);
        showDeparture();
    }

    /** Arrives when the visible bolt reaches its destination and rechecks the landing conditions. */
    boolean landAttackTeleport(Vec3 target) {
        return finishTravel(target, isSafe(target));
    }

    private boolean finishTravel(Vec3 target, boolean safe) {
        if (safe) {
            teleportTo(target);
        }
        boss.clearTravelArc();
        return safe;
    }

    /** Checks the scaled body and the arena bounds without requiring a floor below the destination. */
    boolean isSafeHoverPosition(Vec3 target) {
        AABB bounds = boss.getBoundingBox().move(target.subtract(boss.position()));
        return isSafeBody((ServerLevel) boss.level, bounds);
    }

    /** Rechecks the fan's space without treating players entering its incorporeal form as terrain. */
    boolean isSafeFanHoverPosition(Vec3 target) {
        AABB bounds = boss.getBoundingBox().move(target.subtract(boss.position()));
        return isSafeBody((ServerLevel) boss.level, bounds, true);
    }

    /** Keeps the source position until the visible lightning form reaches the destination. */
    private void beginArc() {
        arcTicks = LightningSheeperEntity.ARC_TRAVEL_TICKS;
        startTravel(destination, arcTicks);
    }

    /** Moves the boss to a destination that the caller has already checked. */
    void teleportTo(Vec3 target) {
        boss.getNavigation().stop();
        boss.teleportTo(target.x, target.y, target.z);
        boss.setDeltaMovement(Vec3.ZERO);
        boss.fallDistance = 0.0F;
        boss.hasImpulse = true;
        showBurst(boss.position());
    }

    private void showDeparture() {
        Vec3 departure = boss.position();
        ((ServerLevel) boss.level).playSound(null, departure.x, departure.y, departure.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 0.45F, 1.35F);
        showBurst(departure);
    }

    private void showBurst(Vec3 point) {
        ((ServerLevel) boss.level).sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x,
                point.y + boss.getBbHeight() * 0.5, point.z, 24, boss.getBbWidth() * 0.5,
                boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.5, 0.05);
    }

    private void finish() {
        clearDestination();
        cooldown = nextInterval();
    }

    /** Counts only failed searches or invalidated landings, including a blocked recovery. */
    private void failJump() {
        clearDestination();
        failedJumps = Math.min(failedJumps + 1, FAILED_JUMPS_BEFORE_RECOVERY);
        cooldown = failedJumps == FAILED_JUMPS_BEFORE_RECOVERY ? RECOVERY_RETRY_TICKS : FAILED_JUMP_RETRY_TICKS;
    }

    private void clearDestination() {
        destination = null;
        warningRemaining = 0;
        arcTicks = 0;
        recoveringJump = false;
        boss.clearTravelArc();
        walkRemaining = 0;
    }

    /** Clears the movement plan and its pause after full encounter recovery. */
    void reset() {
        clearDestination();
        cooldown = -1;
        failedJumps = 0;
        walking = false;
        idle = false;
        focusId = null;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Cooldown", cooldown);
        tag.putBoolean("Idle", idle);
        if (focusId != null) {
            tag.putUUID("Focus", focusId);
        }
        if (destination != null) {
            tag.putDouble("X", destination.x);
            tag.putDouble("Y", destination.y);
            tag.putDouble("Z", destination.z);
            tag.putInt("WarningRemaining", warningRemaining);
            tag.putDouble("WalkRemaining", walkRemaining);
            tag.putBoolean("Recovering", recoveringJump);
        }
        return tag;
    }

    void load(CompoundTag tag) {
        cooldown = tag.contains("Cooldown", Tag.TAG_INT) ? Mth.clamp(tag.getInt("Cooldown"), -1, 12000) : -1;
        warningRemaining = Mth.clamp(tag.getInt("WarningRemaining"), 0, 1200);
        idle = tag.getBoolean("Idle");
        focusId = tag.hasUUID("Focus") ? tag.getUUID("Focus") : null;
        walking = false;
        arcTicks = 0;
        failedJumps = 0;
        recoveringJump = false;
        boss.clearTravelArc();
        double savedWalk = tag.getDouble("WalkRemaining");
        walkRemaining = Double.isFinite(savedWalk) ? Mth.clamp(savedWalk, 0, MAX_WALK_DISTANCE) : 0;
        destination = null;
        if (tag.contains("X", Tag.TAG_ANY_NUMERIC) && tag.contains("Y", Tag.TAG_ANY_NUMERIC)
                && tag.contains("Z", Tag.TAG_ANY_NUMERIC)) {
            Vec3 saved = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
            if (Double.isFinite(saved.x) && Double.isFinite(saved.y) && Double.isFinite(saved.z)) {
                destination = saved;
                recoveringJump = tag.getBoolean("Recovering");
                if (recoveringJump) {
                    failedJumps = FAILED_JUMPS_BEFORE_RECOVERY;
                }
            }
        }
    }
}
