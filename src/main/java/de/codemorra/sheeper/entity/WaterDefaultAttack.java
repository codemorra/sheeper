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
 * Controls the default projectile rhythm and its shared movement target.
 * Shooting pauses never stop navigation or clear an otherwise valid target.
 * Close-range approaches suppress projectiles without pausing target-switch
 * timers.
 */
final class WaterDefaultAttack {
    private static final int PAUSE_TICKS = 40;

    private final WaterSheeperEntity boss;
    @Nullable
    private UUID targetId;
    private boolean firing;
    private boolean closingOnTarget;
    private int meleeTicks;
    private int phaseTicks = PAUSE_TICKS;
    private int shotTicks;

    /** @param boss owner whose navigation and projectile attacks share a target */
    WaterDefaultAttack(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    /**
     * Advances the firing/pause cycle before navigation updates. Missing targets
     * are replaced immediately, even during a pause; regular reselection happens
     * only after the pause and may choose the same player again.
     *
     * @param participants currently eligible arena players
     * @param advanceCycle whether regular firing and target-switch timers may
     *                     advance
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
        if (target == null) {
            target = selectTarget(participants);
        }
        if (advanceCycle && --phaseTicks <= 0) {
            firing = !firing;
            if (firing) {
                target = selectTarget(participants);
                int minimum = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_PROJECTILE_DURATION_MIN.get());
                int maximum = Math.max(minimum,
                        SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_PROJECTILE_DURATION_MAX.get()));
                phaseTicks = minimum + boss.getRandom().nextInt(maximum - minimum + 1);
                // Fire immediately after the entry or target-switch pause, without a second
                // delay.
                shotTicks = 0;
            } else {
                phaseTicks = PAUSE_TICKS;
            }
        }
        updateCloseRange(target);
        if (closingOnTarget) {
            meleeTicks = Math.min(600, meleeTicks + 1);
        }
        return target;
    }

    /**
     * @return whether the entry pause has elapsed and the firing cycle is active
     */
    boolean isFiring() {
        return firing;
    }

    /** Reselects a random target and resumes firing on the next cycle update. */
    void resumeAfterSpecial() {
        firing = false;
        phaseTicks = 1;
        shotTicks = 0;
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
        if (!firing) {
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
        firing = false;
        closingOnTarget = false;
        meleeTicks = 0;
        phaseTicks = PAUSE_TICKS;
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
        tag.putBoolean("Firing", firing);
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
        firing = tag.getBoolean("Firing");
        closingOnTarget = tag.getBoolean("ClosingOnTarget");
        meleeTicks = closingOnTarget ? Mth.clamp(tag.getInt("MeleeTicks"), 0, 600) : 0;
        if (tag.contains("PhaseTicks", Tag.TAG_INT)) {
            phaseTicks = Mth.clamp(tag.getInt("PhaseTicks"), 1, 1200);
        }
        shotTicks = Mth.clamp(tag.getInt("ShotTicks"), 0, 12000);
    }
}
