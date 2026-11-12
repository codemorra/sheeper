package de.codemorra.sheeper.entity;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Announces and rechecks safe, loaded destinations for ordinary arena movement.
 */
final class LightningTeleportMovement {
    private final LightningSheeperEntity boss;
    private int cooldown = -1;
    private int warningRemaining;
    @Nullable
    private Vec3 destination;

    LightningTeleportMovement(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    boolean isPreparing() {
        return destination != null;
    }

    /**
     * Disallowed movement pauses the interval and cancels any announced arrival.
     */
    void tick(boolean allowed) {
        if (!allowed || boss.getMovementRadius() <= 0.0D) {
            if (destination != null) {
                finish();
            }
            return;
        }
        if (destination != null) {
            if (!isSafe(destination)) {
                finish();
                return;
            }
            if (--warningRemaining <= 0) {
                teleport();
            } else if (warningRemaining % 2 == 0) {
                showWarning();
            }
            return;
        }
        if (cooldown < 0) {
            cooldown = nextInterval();
        }
        if (--cooldown > 0) {
            return;
        }
        destination = findDestination();
        if (destination == null) {
            finish();
            return;
        }
        warningRemaining = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_TELEPORT_WARNING.get());
        if (warningRemaining == 0) {
            teleport();
        } else {
            showWarning();
        }
    }

    private int nextInterval() {
        int minimum = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_TELEPORT_INTERVAL_MIN.get());
        int maximum = Math.max(minimum,
                SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_TELEPORT_INTERVAL_MAX.get()));
        return minimum + boss.getRandom().nextInt(maximum - minimum + 1);
    }

    @Nullable
    private Vec3 findDestination() {
        ServerLevel level = (ServerLevel) boss.level;
        BlockPos center = boss.getArenaCenter();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = Math.sqrt(boss.getRandom().nextDouble()) * boss.getMovementRadius();
            int x = Mth.floor(center.getX() + 0.5D + Math.cos(angle) * radius);
            int z = Mth.floor(center.getZ() + 0.5D + Math.sin(angle) * radius);
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            Double surface = boss.findArenaSurface(level, x, z);
            if (surface != null) {
                Vec3 candidate = new Vec3(x + 0.5D, surface, z + 0.5D);
                if (candidate.distanceToSqr(boss.position()) >= 1.0D && isSafe(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean isSafe(Vec3 target) {
        ServerLevel level = (ServerLevel) boss.level;
        if (!boss.isWithinMovementArea(target.x, target.z)
                || Math.abs(target.y - boss.getArenaCenter().getY()) > SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE
                        .get()) {
            return false;
        }
        AABB bounds = boss.getBoundingBox().move(target.subtract(boss.position()));
        if (bounds.minY <= level.getMinBuildHeight() || bounds.maxY >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(bounds) || !isLoaded(level, bounds.inflate(1.0D))) {
            return false;
        }
        Double surface = boss.findArenaSurface(level, Mth.floor(target.x), Mth.floor(target.z));
        if (surface == null || Math.abs(surface - target.y) > 1.0E-6D || !level.noCollision(boss, bounds)) {
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
        double distance = SheeperConfig.LIGHTNING_TELEPORT_PLAYER_DISTANCE.get();
        return level.players().stream().noneMatch(player -> player.isAlive() && !player.isRemoved()
                && !player.isSpectator() && player.distanceToSqr(target) < distance * distance);
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

    private void teleport() {
        ServerLevel level = (ServerLevel) boss.level;
        Vec3 departure = boss.position();
        boss.getNavigation().stop();
        boss.teleportTo(destination.x, destination.y, destination.z);
        boss.setDeltaMovement(Vec3.ZERO);
        boss.fallDistance = 0.0F;
        boss.hasImpulse = true;
        for (Vec3 point : new Vec3[] { departure, boss.position() }) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y + boss.getBbHeight() * 0.5D,
                    point.z, 24, boss.getBbWidth() * 0.5D, boss.getBbHeight() * 0.4D,
                    boss.getBbWidth() * 0.5D, 0.05D);
        }
        finish();
    }

    private void finish() {
        destination = null;
        warningRemaining = 0;
        cooldown = nextInterval();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Cooldown", cooldown);
        if (destination != null) {
            tag.putDouble("X", destination.x);
            tag.putDouble("Y", destination.y);
            tag.putDouble("Z", destination.z);
            tag.putInt("WarningRemaining", warningRemaining);
        }
        return tag;
    }

    void load(CompoundTag tag) {
        cooldown = tag.contains("Cooldown", Tag.TAG_INT) ? Mth.clamp(tag.getInt("Cooldown"), -1, 12000) : -1;
        warningRemaining = Mth.clamp(tag.getInt("WarningRemaining"), 0, 1200);
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
