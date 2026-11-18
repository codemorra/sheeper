package de.codemorra.sheeper.entity;

import java.util.List;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Schedules special attacks separately from default firing and movement.
 * Specials have independent cooldowns and cannot overlap. Fountains preserve
 * default combat; stationary specials own quiet and default-only recovery.
 */
final class WaterSpecialAttacks {
    private final WaterSheeperEntity boss;
    private final WaterBeamAttack beam;
    private final WaterFountainAttack fountains;
    private final WaterMaelstromAttack maelstrom;
    private boolean beamActive;
    private boolean fountainActive;
    private int beamCooldown;
    private int fountainCooldown;
    private int fountainTicks;
    private int recoveryTicks;
    private int defaultOnlyTicks;
    private int maelstromCooldown = -1;

    /**
     * @param boss owner whose default attack and movement are coordinated with
     *             specials
     */
    WaterSpecialAttacks(WaterSheeperEntity boss) {
        this.boss = boss;
        beam = new WaterBeamAttack(boss);
        fountains = new WaterFountainAttack(boss);
        maelstrom = new WaterMaelstromAttack(boss);
    }

    /**
     * Advances timers before default target selection. An empty arena aborts an
     * active specials and freezes cooldowns until participants return or the fight
     * resets.
     *
     * @param participants  currently eligible arena players
     * @param defaultAttack controller to resume when quiet recovery ends
     */
    void tickTimers(List<ServerPlayer> participants, WaterDefaultAttack defaultAttack) {
        if (!beamActive) {
            boss.setBeamEyeIntensity(Mth.approach(boss.getBeamEyeIntensity(1), 0,
                    1.0F / SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_BEAM_EYE_FADE.get())));
        }
        if (participants.isEmpty()) {
            if (maelstrom.isActive()) {
                finishMaelstrom();
            }
            if (beamActive) {
                finishBeam();
            }
            if (fountainActive) {
                finishFountains();
            }
            return;
        }
        if (maelstromCooldown < 0) {
            // Unlike other specials, Maelstrom must first earn its cooldown at
            // encounter entry. Brief arena exits pause, rather than restart, it.
            maelstromCooldown = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_MAELSTROM_COOLDOWN.get());
        } else if (maelstromCooldown > 0) {
            maelstromCooldown--;
        }
        if (beamCooldown > 0) {
            beamCooldown--;
        }
        if (fountainCooldown > 0) {
            fountainCooldown--;
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
     * Selects uniformly among currently eligible specials. Close range excludes
     * beam entry only; fountains may accompany normal combat at any distance.
     * Once started, approaching the boss cannot cancel a beam.
     *
     * @param target        current shared target
     * @param defaultAttack default cycle whose initial entry pause must have
     *                      elapsed
     */
    void tryStart(ServerPlayer target, WaterDefaultAttack defaultAttack) {
        if (beamActive || fountainActive || maelstrom.isActive()
                || recoveryTicks > 0 || defaultOnlyTicks > 0 || !defaultAttack.isFiring()) {
            return;
        }
        boolean beamReady = beamCooldown == 0
                && !defaultAttack.isClosingOnTarget() && boss.hasLineOfSight(target);
        boolean fountainReady = fountainCooldown == 0;
        boolean maelstromReady = maelstromCooldown == 0 && (!defaultAttack.isClosingOnTarget()
                || defaultAttack.getMeleeTicks() >= SheeperConfig
                        .secondsToTicks(WaterSheeperConfig.WATER_MAELSTROM_MELEE_DELAY.get()));
        int choices = (beamReady ? 1 : 0) + (fountainReady ? 1 : 0) + (maelstromReady ? 1 : 0);
        if (choices == 0) {
            return;
        }
        int choice = boss.getRandom().nextInt(choices);
        if (beamReady && choice-- == 0) {
            beam.start();
            beamActive = true;
        } else if (fountainReady && choice-- == 0) {
            fountains.start();
            fountainActive = true;
            int minimum = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_FOUNTAIN_DURATION_MIN.get());
            int maximum = Math.max(minimum,
                    SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_FOUNTAIN_DURATION_MAX.get()));
            fountainTicks = minimum + boss.getRandom().nextInt(maximum - minimum + 1);
        } else if (maelstromReady && !maelstrom.start()) {
            // Failed center searches must not repeat their pathfinding every tick.
            maelstromCooldown = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_MAELSTROM_COOLDOWN.get());
        }
    }

    /**
     * Advances the active hazard after movement. Fountains keep navigation and
     * regular firing active; pending warnings stay at their announced positions.
     */
    void tickAttack() {
        if (maelstrom.isActive() && !maelstrom.tick()) {
            finishMaelstrom();
        }
        if (beamActive && (!(boss.getTarget() instanceof ServerPlayer target) || !beam.tick(target))) {
            finishBeam();
        }
        if (fountainActive) {
            fountains.tick(boss.getCombatParticipants(), fountainTicks);
            fountainTicks = Math.max(0, fountainTicks - 1);
            if (fountainTicks == 0 && !fountains.hasActiveEffects()) {
                finishFountains();
            }
        }
        maelstrom.tickEffects();
    }

    /** @return whether movement is locked for beam warning or firing */
    boolean isBeamActive() {
        return beamActive;
    }

    /**
     * @return whether Maelstrom owns navigation, including its vulnerable recovery
     */
    boolean isMaelstromActive() {
        return maelstrom.isActive();
    }

    /** @return whether Maelstrom still grants immunity before its impact */
    boolean isMaelstromImmune() {
        return maelstrom.isImmune();
    }

    /**
     * @return whether ordinary movement must be suppressed during a stationary
     *         special
     */
    boolean locksMovement() {
        return beamActive || maelstrom.isStationary();
    }

    /** @return whether projectile firing and its target-switch timer must pause */
    boolean suppressesDefault() {
        return beamActive || maelstrom.isActive() || recoveryTicks > 0;
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
        beamCooldown = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_BEAM_COOLDOWN.get());
        beginRecovery();
    }

    private void finishMaelstrom() {
        boolean impacted = maelstrom.hasImpacted();
        maelstrom.finish();
        maelstromCooldown = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_MAELSTROM_COOLDOWN.get());
        beginRecovery();
        if (impacted) {
            // The attack already held its vulnerable recovery at the impact.
            // Resume default target selection on the next timer update.
            recoveryTicks = 1;
        }
    }

    private void beginRecovery() {
        recoveryTicks = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_SPECIAL_RECOVERY.get());
        int minimum = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_SPECIAL_DEFAULT_ONLY_MIN.get());
        int maximum = Math.max(minimum,
                SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_SPECIAL_DEFAULT_ONLY_MAX.get()));
        defaultOnlyTicks = minimum + boss.getRandom().nextInt(maximum - minimum + 1);
    }

    /**
     * Ends all pending hazards without adding a quiet pause to the default attack.
     */
    private void finishFountains() {
        fountainActive = false;
        fountainTicks = 0;
        fountains.finish();
        fountainCooldown = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_FOUNTAIN_COOLDOWN.get());
    }

    /**
     * Resets specials after full recovery, including Maelstrom's initial cooldown.
     */
    void reset() {
        beamActive = false;
        beamCooldown = 0;
        fountainActive = false;
        fountainCooldown = 0;
        fountainTicks = 0;
        fountains.reset();
        recoveryTicks = 0;
        defaultOnlyTicks = 0;
        maelstrom.stop();
        maelstromCooldown = -1;
        beam.stop();
        boss.setBeamEyeIntensity(0);
    }

    /** @return active beam progress and independent recovery/cooldown timers */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("BeamActive", beamActive);
        tag.putInt("BeamCooldown", beamCooldown);
        tag.putBoolean("FountainActive", fountainActive);
        tag.putInt("FountainCooldown", fountainCooldown);
        tag.putInt("FountainTicks", fountainTicks);
        tag.put("Fountains", fountains.save());
        tag.putInt("RecoveryTicks", recoveryTicks);
        tag.putInt("DefaultOnlyTicks", defaultOnlyTicks);
        tag.put("Beam", beam.save());
        tag.putInt("MaelstromCooldown", maelstromCooldown);
        tag.put("Maelstrom", maelstrom.save());
        return tag;
    }

    /**
     * @param tag saved special state; absent Maelstrom data retains its initial
     *            cooldown
     */
    void load(CompoundTag tag) {
        reset();
        beamActive = tag.getBoolean("BeamActive");
        beamCooldown = Mth.clamp(tag.getInt("BeamCooldown"), 0, 2400);
        recoveryTicks = Mth.clamp(tag.getInt("RecoveryTicks"), 0, 100);
        defaultOnlyTicks = Mth.clamp(tag.getInt("DefaultOnlyTicks"), 0, 600);
        beam.load(tag.getCompound("Beam"));
        fountainCooldown = Mth.clamp(tag.getInt("FountainCooldown"), 0, 2400);
        fountainTicks = Mth.clamp(tag.getInt("FountainTicks"), 0, 12000);
        fountains.load(tag.getCompound("Fountains"));
        // The announcement window may have ended while a column still carries a player.
        fountainActive = !beamActive && tag.getBoolean("FountainActive")
                && (fountainTicks > 0 || fountains.hasActiveEffects());
        maelstromCooldown = tag.contains("MaelstromCooldown")
                ? Mth.clamp(tag.getInt("MaelstromCooldown"), -1, 6000)
                : -1;
        maelstrom.load(tag.getCompound("Maelstrom"));
        if (maelstrom.isActive()) {
            // Malformed or old state must never resume two specials together.
            beamActive = false;
            beam.stop();
            fountainActive = false;
            fountains.reset();
        }
    }
}
