package de.codemorra.sheeper.entity;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Runs two projectile intervals before the first special check, and one further
 * interval for each check without an available special.
 * Close-range approaches suppress shots while the intervals keep advancing.
 */
final class WaterDefaultAttack {
    private enum Phase {
        ENTRY, FIRST_INTERVAL, SECOND_INTERVAL, COMPLETE
    }

    private final WaterSheeperEntity boss;
    @Nullable
    private UUID targetId;
    private Phase phase = Phase.ENTRY;
    private boolean closingOnTarget;
    private int meleeTicks;
    private int phaseTicks = entryDelayTicks();
    private int shotTicks;

    /** @param boss owner whose navigation and projectile attacks share a target */
    WaterDefaultAttack(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    /**
     * Advances the projectile phase before navigation. At the midpoint, a valid
     * target is replaced if another participant is available.
     *
     * @param participants currently eligible arena players
     * @param advanceCycle whether a special or its recovery is letting the phase advance
     * @return shared movement and firing target, or {@code null} for an empty arena
     */
    @Nullable
    ServerPlayer tickTarget(List<ServerPlayer> participants, boolean advanceCycle) {
        if (participants.isEmpty()) {
            // Keep the firing cycle frozen during a temporary arena exit.
            targetId = null;
            closingOnTarget = false;
            meleeTicks = 0;
            return null;
        }
        ServerPlayer target = participants.stream().filter(player -> player.getUUID().equals(targetId))
                .findFirst().orElse(null);
        if (advanceCycle && phase != Phase.COMPLETE && --phaseTicks <= 0) {
            switch (phase) {
                case ENTRY -> {
                    phase = Phase.FIRST_INTERVAL;
                    phaseTicks = sampleInterval();
                    shotTicks = 0;
                }
                case FIRST_INTERVAL -> {
                    phase = Phase.SECOND_INTERVAL;
                    phaseTicks = sampleInterval();
                    if (target != null && participants.size() > 1) {
                        ServerPlayer previous = target;
                        target = selectTarget(participants.stream().filter(player -> player != previous).toList());
                    }
                }
                case SECOND_INTERVAL -> phase = Phase.COMPLETE;
                case COMPLETE -> {
                    // The scheduler chooses a special or extends the projectile phase.
                }
            }
        }
        if (target == null) {
            target = selectTarget(participants);
        }
        updateCloseRange(target);
        if (closingOnTarget) {
            meleeTicks = Math.min(600, meleeTicks + 1);
        }
        return target;
    }

    /** @return whether either of the two projectile intervals is running */
    boolean isFiring() {
        return phase == Phase.FIRST_INTERVAL || phase == Phase.SECOND_INTERVAL;
    }

    /** @return whether the projectile phase has yielded to the next special */
    boolean isComplete() {
        return phase == Phase.COMPLETE;
    }

    /**
     * Adds one interval before the next special check without resetting shot spacing.
     *
     * @param participants eligible players, including the previous target
     * @return the randomly selected target for the extension
     */
    ServerPlayer extendProjectilePhase(List<ServerPlayer> participants) {
        phase = Phase.SECOND_INTERVAL;
        phaseTicks = sampleInterval();
        ServerPlayer target = selectTarget(participants);
        updateCloseRange(target);
        return target;
    }

    /** Starts a fresh projectile phase on the next cycle update. */
    void resumeAfterSpecial() {
        phase = Phase.ENTRY;
        phaseTicks = 1;
        shotTicks = 0;
    }

    /** Samples the two firing intervals independently. */
    private int sampleInterval() {
        int minimum = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_PROJECTILE_DURATION_MIN.get());
        int maximum = Math.max(minimum,
                SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_PROJECTILE_DURATION_MAX.get()));
        return minimum + boss.getRandom().nextInt(maximum - minimum + 1);
    }

    private ServerPlayer selectTarget(List<ServerPlayer> participants) {
        ServerPlayer target = participants.get(boss.getRandom().nextInt(participants.size()));
        if (!target.getUUID().equals(targetId)) {
            closingOnTarget = false;
        }
        targetId = target.getUUID();
        return target;
    }

    /**
     * Uses one body-center distance for entering and leaving melee mode. Only
     * the selected target triggers the approach; proximity defense still checks
     * every nearby participant independently.
     *
     * @param target current shared movement and attack target
     */
    private void updateCloseRange(ServerPlayer target) {
        double threshold = WaterSheeperConfig.WATER_CLOSE_RANGE_DISTANCE.get();
        closingOnTarget = boss.getBoundingBox().getCenter()
                .distanceToSqr(target.getBoundingBox().getCenter()) <= threshold * threshold;
        if (!closingOnTarget) {
            meleeTicks = 0;
        }
    }

    /**
     * @return continuous melee-mode ticks; switching to another nearby target does
     *         not interrupt the mode
     */
    int getMeleeTicks() {
        return meleeTicks;
    }

    /**
     * @return whether projectiles are suppressed while the boss approaches its
     *         target
     */
    boolean isClosingOnTarget() {
        return closingOnTarget;
    }

    /**
     * Fires after movement without changing the boss's body rotation. Blocked
     * shots wait for clear sight instead of creating projectiles inside walls.
     */
    void tickProjectiles() {
        if (!(boss.getTarget() instanceof ServerPlayer target)) {
            return;
        }
        // Recheck after movement so entering close range cannot produce one final shot.
        updateCloseRange(target);
        if (!isFiring() || !WaterSheeperConfig.WATER_PROJECTILE_ENABLED.get()) {
            return;
        }
        if (shotTicks > 0) {
            shotTicks--;
        }
        if (!closingOnTarget && shotTicks == 0 && WaterAttackBlocks.hasLineOfSight(boss, target)) {
            WaterProjectile projectile = new WaterProjectile(ModEntities.WATER_PROJECTILE.get(), boss.level);
            projectile.setOwner(boss);
            projectile.setPos(boss.getEyePosition());
            Vec3 direction = target.getEyePosition().subtract(projectile.position());
            if (direction.lengthSqr() < 1.0E-8D) {
                direction = boss.getLookAngle();
            }
            projectile.setDeltaMovement(direction.normalize()
                    .scale(WaterSheeperConfig.WATER_PROJECTILE_SPEED_PER_SECOND.get() / 20.0D));
            if (boss.level.addFreshEntity(projectile)) {
                boss.level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                        SoundEvents.DOLPHIN_SPLASH, SoundSource.HOSTILE, 0.4F,
                        1.4F + boss.getRandom().nextFloat() * 0.2F);
                shotTicks = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_PROJECTILE_INTERVAL.get());
            }
        }
    }

    /** Restores the entry delay after the arena encounter has fully recovered. */
    void reset() {
        targetId = null;
        phase = Phase.ENTRY;
        closingOnTarget = false;
        meleeTicks = 0;
        phaseTicks = entryDelayTicks();
        shotTicks = 0;
    }

    /**
     * @return target and remaining timers, independent of world time while unloaded
     */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (targetId != null) {
            tag.putUUID("Target", targetId);
        }
        tag.putInt("ProjectilePhase", phase.ordinal());
        tag.putBoolean("ClosingOnTarget", closingOnTarget);
        tag.putInt("MeleeTicks", meleeTicks);
        tag.putInt("PhaseTicks", phaseTicks);
        tag.putInt("ShotTicks", shotTicks);
        return tag;
    }

    /**
     * Restores a saved cycle; bosses without this data start with the entry delay.
     *
     * @param tag saved default attack state
     */
    void load(CompoundTag tag) {
        reset();
        targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        if (tag.contains("ProjectilePhase", Tag.TAG_INT)) {
            int savedPhase = tag.getInt("ProjectilePhase");
            if (savedPhase >= 0 && savedPhase < Phase.values().length) {
                phase = Phase.values()[savedPhase];
            }
        } else if (tag.getBoolean("Firing")) {
            // An older firing cycle can continue as the first interval.
            phase = Phase.FIRST_INTERVAL;
        }
        closingOnTarget = tag.getBoolean("ClosingOnTarget");
        meleeTicks = closingOnTarget ? Mth.clamp(tag.getInt("MeleeTicks"), 0, 600) : 0;
        if (tag.contains("PhaseTicks", Tag.TAG_INT)) {
            int savedTicks = tag.getInt("PhaseTicks");
            phaseTicks = isComplete() ? 0 : Mth.clamp(savedTicks, phase == Phase.ENTRY ? 0 : 1, 1200);
        }
        shotTicks = Mth.clamp(tag.getInt("ShotTicks"), 0, 12000);
    }

    private static int entryDelayTicks() {
        return SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_COMBAT_ENTRY_DELAY.get());
    }
}
