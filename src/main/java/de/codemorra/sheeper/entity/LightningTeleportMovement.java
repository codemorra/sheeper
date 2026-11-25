package de.codemorra.sheeper.entity;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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

    private final LightningSheeperEntity boss;
    private int cooldown = -1;
    private int warningRemaining;
    private int arcTicks;
    private double walkRemaining;
    private boolean walking;
    private boolean idle;
    @Nullable
    private UUID focusId;
    @Nullable
    private Vec3 destination;

    LightningTeleportMovement(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    boolean isPreparing() {
        return destination != null;
    }

    /** @return whether this tick's vanilla movement may include a short walk before teleporting */
    boolean isWalking() {
        return walking;
    }

    /**
     * Advances ordinary movement without running attack logic.
     *
     * @param allowed whether the arena and boss can currently move
     * @param focus current participant to orbit, or null while the arena is empty
     * @return whether the boss landed at a safe teleport destination this tick
     */
    boolean tick(boolean allowed, @Nullable ServerPlayer focus) {
        walking = false;
        if (!allowed || boss.getMovementRadius() <= 0.0D) {
            if (destination != null) {
                finish();
            }
            return false;
        }
        UUID nextFocus = focus == null ? null : focus.getUUID();
        boolean nowIdle = focus == null;
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
            if (destination == null || !isSafe(destination)) {
                finish();
            } else if (--arcTicks == 0) {
                teleportTo(destination);
                finish();
                return true;
            }
            return false;
        }
        if (destination != null) {
            if (!isSafe(destination)) {
                finish();
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
        destination = findDestination(focus);
        if (destination == null) {
            finish();
            return false;
        }
        faceDestination(destination);
        warningRemaining = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_TELEPORT_WARNING.get());
        walkRemaining = 1 + boss.getRandom().nextDouble() * (MAX_WALK_DISTANCE - 1);
        if (warningRemaining == 0) {
            beginArc();
        } else {
            showWarning();
        }
        return false;
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

    @Nullable
    private Vec3 findDestination(@Nullable ServerPlayer focus) {
        ServerLevel level = (ServerLevel) boss.level;
        return focus == null ? findIdleDestination(level) : findCombatDestination(level, focus);
    }

    /** Longer idle jumps still originate around the boss, with shorter steps as a terrain fallback. */
    @Nullable
    private Vec3 findIdleDestination(ServerLevel level) {
        Vec3 origin = boss.position();
        double movementRadius = boss.getMovementRadius();
        double jumpDistance = Math.min(movementRadius, Math.max(8, movementRadius * 0.45));
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double interiorRadius = movementRadius * 0.85;
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = jumpDistance * (attempt < 12
                    ? 0.7 + boss.getRandom().nextDouble() * 0.6
                    : 0.2 + boss.getRandom().nextDouble() * 0.5);
            Vec3 point = origin.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            if (attempt < 16 && point.subtract(center).horizontalDistanceSqr() > interiorRadius * interiorRadius) {
                continue;
            }
            Vec3 destination = safeDestination(level, point);
            if (destination != null) {
                return destination;
            }
        }
        return null;
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
        // Keep a blocked search close to the player. Search both sides before varying distance.
        for (double distance : new double[] { preferred, preferred * 0.75, preferred * 1.25 }) {
            for (int attempt = 0; attempt < 12; attempt++) {
                int turn = (attempt + 1) / 2 * (attempt % 2 == 0 ? -1 : 1);
                double bearing = angle + turn * Math.PI / 6;
                Vec3 point = anchor.add(Math.cos(bearing) * distance, 0, Math.sin(bearing) * distance);
                Vec3 destination = safeDestination(level, point);
                if (destination != null && (!approaching
                        || destination.subtract(anchor).horizontalDistance() < currentDistance - 0.5)) {
                    return destination;
                }
            }
        }
        return null;
    }

    @Nullable
    private Vec3 safeDestination(ServerLevel level, Vec3 point) {
        int x = Mth.floor(point.x);
        int z = Mth.floor(point.z);
        if (!boss.isWithinMovementArea(x + 0.5, z + 0.5)
                || !isLoaded(level, new AABB(x - 1, 0, z - 1, x + 2, 1, z + 2))) {
            return null;
        }
        Double surface = boss.findArenaSurface(level, x, z, Mth.ceil(point.y));
        if (surface == null) {
            return null;
        }
        Vec3 destination = new Vec3(x + 0.5, surface, z + 0.5);
        return destination.subtract(boss.position()).horizontalDistanceSqr() >= 1 && isSafe(destination)
                ? destination : null;
    }

    private boolean isSafe(Vec3 target) {
        ServerLevel level = (ServerLevel) boss.level;
        if (!boss.isWithinMovementArea(target.x, target.z)) {
            return false;
        }
        AABB bounds = boss.getBoundingBox().move(target.subtract(boss.position()));
        if (!isSafeBody(level, bounds)) {
            return false;
        }
        Double surface = boss.findArenaSurface(level, Mth.floor(target.x), Mth.floor(target.z), Mth.ceil(target.y));
        if (surface == null || Math.abs(surface - target.y) > 1.0E-6D) {
            return false;
        }
        double distance = LightningSheeperConfig.LIGHTNING_TELEPORT_PLAYER_DISTANCE.get();
        return level.players().stream().noneMatch(player -> player.isAlive() && !player.isRemoved()
                && !player.isSpectator() && player.distanceToSqr(target) < distance * distance);
    }

    /** Checks the full scaled body against arena bounds, loaded terrain, and hazards. */
    private boolean isSafeBody(ServerLevel level, AABB bounds) {
        BlockPos center = boss.getArenaCenter();
        double dx = Math.max(Math.abs(bounds.minX - center.getX() - 0.5), Math.abs(bounds.maxX - center.getX() - 0.5));
        double dz = Math.max(Math.abs(bounds.minZ - center.getZ() - 0.5), Math.abs(bounds.maxZ - center.getZ() - 0.5));
        double radius = boss.getCombatRadius();
        if (dx * dx + dz * dz > radius * radius || bounds.minY < boss.getArenaMinimumY()
                || bounds.maxY > boss.getArenaMaximumY() || !level.getWorldBorder().isWithinBounds(bounds)
                || !isLoaded(level, bounds.inflate(1)) || !level.noCollision(boss, bounds)) {
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

    private void faceDestination(Vec3 target) {
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

    /**
     * Performs one safe immediate teleport for an attack's rapid preparation
     * sequence.
     */
    boolean teleportForAttack() {
        Vec3 target = findDestination(null);
        if (target == null || boss.getMovementRadius() <= 0) {
            return false;
        }
        showDeparture();
        teleportTo(target);
        return true;
    }

    /** Keeps the source position until the visible lightning form reaches the destination. */
    private void beginArc() {
        arcTicks = LightningSheeperEntity.ARC_TRAVEL_TICKS;
        boss.startTravelArc(destination);
        showDeparture();
    }

    private void teleportTo(Vec3 target) {
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

    private void clearDestination() {
        destination = null;
        warningRemaining = 0;
        arcTicks = 0;
        boss.clearTravelArc();
        walkRemaining = 0;
    }

    /** Clears the movement plan and its pause after full encounter recovery. */
    void reset() {
        clearDestination();
        cooldown = -1;
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
        boss.clearTravelArc();
        double savedWalk = tag.getDouble("WalkRemaining");
        walkRemaining = Double.isFinite(savedWalk) ? Mth.clamp(savedWalk, 0, MAX_WALK_DISTANCE) : 0;
        destination = null;
        if (tag.contains("X", Tag.TAG_ANY_NUMERIC) && tag.contains("Y", Tag.TAG_ANY_NUMERIC)
                && tag.contains("Z", Tag.TAG_ANY_NUMERIC)) {
            Vec3 saved = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
            if (Double.isFinite(saved.x) && Double.isFinite(saved.y) && Double.isFinite(saved.z)) {
                destination = saved;
            }
        }
    }
}
