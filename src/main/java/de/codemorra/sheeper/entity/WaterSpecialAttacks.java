package de.codemorra.sheeper.entity;

import java.util.List;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Schedules special attacks separately from default firing and movement.
 * Beam cooldown, quiet recovery and default-only recovery use independent
 * timers.
 */
final class WaterSpecialAttacks {
    private final WaterSheeperEntity boss;
    private final WaterBeamAttack beam;
    private boolean beamActive;
    private int beamCooldown;
    private int recoveryTicks;
    private int defaultOnlyTicks;

    /**
     * @param boss owner whose default attack and movement are coordinated with
     *             specials
     */
    WaterSpecialAttacks(WaterSheeperEntity boss) {
        this.boss = boss;
        beam = new WaterBeamAttack(boss);
    }

    /**
     * Advances timers before default target selection. An empty arena aborts an
     * active beam and freezes cooldowns until participants return or the fight
     * resets.
     *
     * @param participants  currently eligible arena players
     * @param defaultAttack controller to resume when quiet recovery ends
     */
    void tickTimers(List<ServerPlayer> participants, WaterDefaultAttack defaultAttack) {
        if (!beamActive) {
            boss.setBeamEyeIntensity(Mth.approach(boss.getBeamEyeIntensity(1), 0,
                    1.0F / SheeperConfig.secondsToTicks(SheeperConfig.WATER_BEAM_EYE_FADE.get())));
        }
        if (participants.isEmpty()) {
            if (beamActive) {
                finishBeam();
            }
            return;
        }
        if (beamCooldown > 0) {
            beamCooldown--;
        }
        if (recoveryTicks > 0) {
            if (--recoveryTicks == 0) {
                defaultAttack.resumeAfterSpecial();
            }
        } else if (defaultOnlyTicks > 0) {
            defaultOnlyTicks--;
        }
    }

    /**
     * Checks close range only at entry. Once started, approaching the boss cannot
     * cancel a beam or cause ordinary firing to resume.
     *
     * @param target        current shared target
     * @param defaultAttack default cycle whose initial entry pause must have
     *                      elapsed
     */
    void tryStart(ServerPlayer target, WaterDefaultAttack defaultAttack) {
        if (!beamActive && beamCooldown == 0 && recoveryTicks == 0 && defaultOnlyTicks == 0
                && defaultAttack.isFiring() && !defaultAttack.isClosingOnTarget()
                && boss.hasLineOfSight(target)) {
            beam.start();
            beamActive = true;
        }
    }

    /**
     * Advances contact damage after movement; navigation is locked while the beam
     * is active.
     */
    void tickAttack() {
        if (beamActive && (!(boss.getTarget() instanceof ServerPlayer target) || !beam.tick(target))) {
            finishBeam();
        }
    }

    /** @return whether movement is locked for beam warning or firing */
    boolean isBeamActive() {
        return beamActive;
    }

    /** @return whether projectile firing and its target-switch timer must pause */
    boolean suppressesDefault() {
        return beamActive || recoveryTicks > 0;
    }

    /**
     * @return the gradually tracked world position used by the boss's head control
     */
    Vec3 getAimPosition() {
        return beam.getAimPosition();
    }

    private void finishBeam() {
        beamActive = false;
        beam.stop();
        beamCooldown = SheeperConfig.secondsToTicks(SheeperConfig.WATER_BEAM_COOLDOWN.get());
        recoveryTicks = SheeperConfig.secondsToTicks(SheeperConfig.WATER_SPECIAL_RECOVERY.get());
        int minimum = SheeperConfig.secondsToTicks(SheeperConfig.WATER_SPECIAL_DEFAULT_ONLY_MIN.get());
        int maximum = Math.max(minimum,
                SheeperConfig.secondsToTicks(SheeperConfig.WATER_SPECIAL_DEFAULT_ONLY_MAX.get()));
        defaultOnlyTicks = minimum + boss.getRandom().nextInt(maximum - minimum + 1);
    }

    /** Makes all implemented special cooldowns ready after full arena recovery. */
    void reset() {
        beamActive = false;
        beamCooldown = 0;
        recoveryTicks = 0;
        defaultOnlyTicks = 0;
        beam.stop();
        boss.setBeamEyeIntensity(0);
    }

    /** @return active beam progress and independent recovery/cooldown timers */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("BeamActive", beamActive);
        tag.putInt("BeamCooldown", beamCooldown);
        tag.putInt("RecoveryTicks", recoveryTicks);
        tag.putInt("DefaultOnlyTicks", defaultOnlyTicks);
        tag.put("Beam", beam.save());
        return tag;
    }

    /** @param tag saved special state; absent data starts with ready cooldowns */
    void load(CompoundTag tag) {
        reset();
        beamActive = tag.getBoolean("BeamActive");
        beamCooldown = Mth.clamp(tag.getInt("BeamCooldown"), 0, 2400);
        recoveryTicks = Mth.clamp(tag.getInt("RecoveryTicks"), 0, 100);
        defaultOnlyTicks = Mth.clamp(tag.getInt("DefaultOnlyTicks"), 0, 600);
        beam.load(tag.getCompound("Beam"));
    }
}
