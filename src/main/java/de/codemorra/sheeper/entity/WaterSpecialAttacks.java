package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.List;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Schedules one eligible special after each projectile phase, followed by
 * shared recovery before the next phase.
 */
final class WaterSpecialAttacks {
    private static final int BEAM_EYE_FADE_TICKS = 10;

    private enum Special {
        NONE, BEAM, MINES, MAELSTROM
    }

    private final WaterSheeperEntity boss;
    private final WaterBeamAttack beam;
    private final WaterMaelstromAttack maelstrom;
    private final WaterMineAttack mines;
    private boolean beamActive;
    private long beamSightDeadline;
    private int beamCooldown;
    private int recoveryTicks;
    private Special lastSpecial = Special.NONE;
    private int maelstromCooldown = -1;
    private int mineCooldown;

    /**
     * @param boss owner whose default attack and movement are coordinated with
     *             specials
     */
    WaterSpecialAttacks(WaterSheeperEntity boss) {
        this.boss = boss;
        beam = new WaterBeamAttack(boss);
        maelstrom = new WaterMaelstromAttack(boss);
        mines = new WaterMineAttack(boss);
    }

    /**
     * Advances timers before default target selection. An empty arena aborts
     * active specials and freezes cooldowns until participants return or the fight
     * resets.
     *
     * @param participants  currently eligible arena players
     * @param defaultAttack controller to resume when quiet recovery ends
     */
    void tickTimers(List<ServerPlayer> participants, WaterDefaultAttack defaultAttack) {
        stopDisabledAttacks();
        if (!beamActive) {
            boss.setBeamEyeIntensity(Mth.approach(boss.getBeamEyeIntensity(1), 0,
                    1.0F / BEAM_EYE_FADE_TICKS));
        }
        if (participants.isEmpty()) {
            beamSightDeadline = 0;
            if (maelstrom.isActive()) {
                finishMaelstrom();
            }
            if (beamActive) {
                finishBeam();
            }
            if (mines.isActive()) {
                finishMines();
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
        if (mineCooldown > 0) {
            mineCooldown--;
        }
        if (recoveryTicks > 0 && --recoveryTicks == 0) {
            defaultAttack.resumeAfterSpecial();
        }
    }

    /** Releases active attacks and their hazards if their config switches are turned off. */
    private void stopDisabledAttacks() {
        if (!WaterSheeperConfig.WATER_BEAM_ENABLED.get()) {
            beamSightDeadline = 0;
            if (beamActive) {
                finishBeam();
            }
        }
        if (!WaterSheeperConfig.WATER_MINES_ENABLED.get() && mines.isActive()) {
            finishMines();
        }
        if (!WaterSheeperConfig.WATER_MAELSTROM_ENABLED.get()) {
            if (maelstrom.isActive()) {
                finishMaelstrom();
            }
            if (maelstrom.hasExpandingWave()) {
                maelstrom.stop();
            }
        }
    }

    /**
     * Selects uniformly among eligible specials, avoiding repeats if multiple specials are enabled.
     * A selected beam gets one brief chance to find clear sight before the
     * scheduler considers other specials for this opening.
     *
     * @param defaultAttack projectile phase that must have completed both intervals
     * @param target        current shared movement and attack target
     */
    void tryStart(WaterDefaultAttack defaultAttack, ServerPlayer target) {
        if (suppressesDefault() || maelstrom.hasExpandingWave() || !defaultAttack.isComplete()) {
            return;
        }
        boolean beamEnabled = WaterSheeperConfig.WATER_BEAM_ENABLED.get();
        boolean minesEnabled = WaterSheeperConfig.WATER_MINES_ENABLED.get();
        boolean maelstromEnabled = WaterSheeperConfig.WATER_MAELSTROM_ENABLED.get();
        // Isolating one special for playtesting must still allow later casts after cooldown.
        int enabledCount = (beamEnabled ? 1 : 0) + (minesEnabled ? 1 : 0) + (maelstromEnabled ? 1 : 0);
        Special excluded = enabledCount > 1 ? lastSpecial : Special.NONE;
        boolean beamReady = beamEnabled && excluded != Special.BEAM && beamCooldown == 0
                && !defaultAttack.isClosingOnTarget();
        long now = boss.level.getGameTime();
        if (isSeekingBeamSight()) {
            if (beamReady && now < beamSightDeadline) {
                if (boss.hasBeamSightFrom(boss.position(), target)) {
                    startBeam();
                }
                return;
            }
            beamSightDeadline = 0;
            beamReady = false;
        }
        boolean minesReady = minesEnabled && excluded != Special.MINES && mineCooldown == 0;
        boolean maelstromReady = maelstromEnabled && excluded != Special.MAELSTROM && maelstromCooldown == 0
                && (!defaultAttack.isClosingOnTarget()
                || defaultAttack.getMeleeTicks() >= SheeperConfig
                        .secondsToTicks(WaterSheeperConfig.WATER_MAELSTROM_MELEE_DELAY.get()));
        var choices = new ArrayList<Special>(3);
        if (beamReady) {
            choices.add(Special.BEAM);
        }
        if (minesReady) {
            choices.add(Special.MINES);
        }
        if (maelstromReady) {
            choices.add(Special.MAELSTROM);
        }
        while (!choices.isEmpty()) {
            switch (choices.remove(boss.getRandom().nextInt(choices.size()))) {
                case BEAM -> {
                    if (boss.hasBeamSightFrom(boss.position(), target)) {
                        startBeam();
                        return;
                    }
                    int searchTicks = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_BEAM_SIGHT_SEARCH.get());
                    if (searchTicks > 0) {
                        beamSightDeadline = now + searchTicks;
                        return;
                    }
                }
                case MINES -> {
                    mines.start();
                    return;
                }
                case MAELSTROM -> {
                    if (maelstrom.start()) {
                        return;
                    }
                    // A blocked center leaves the other specials eligible this tick.
                    maelstromCooldown = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_MAELSTROM_COOLDOWN.get());
                }
                case NONE -> throw new IllegalStateException("No special attack selected");
            }
        }
    }

    private void startBeam() {
        beamSightDeadline = 0;
        beam.start();
        beamActive = true;
    }

    /** @return whether navigation has a brief window to find an unobstructed beam position */
    boolean isSeekingBeamSight() {
        return beamSightDeadline != 0;
    }

    /**
     * Advances the active special and its visual effects after movement.
     */
    void tickAttack() {
        if (boss.isInBubbleForm()) {
            maelstrom.tickEffects();
            return;
        }
        if (maelstrom.isActive() && !maelstrom.tick()) {
            finishMaelstrom();
        }
        if (beamActive && (!(boss.getTarget() instanceof ServerPlayer target) || !beam.tick(target))) {
            finishBeam();
        }
        if (mines.isActive()) {
            if (boss.getTarget() instanceof ServerPlayer) {
                mines.tick(boss.getCombatParticipants());
                if (!mines.isActive()) {
                    finishMines();
                }
            } else {
                finishMines();
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

    /** Releases preparation and immunity when a blocked bubble route must be abandoned. */
    void cancelMaelstromApproach() {
        if (maelstrom.isApproaching()) {
            finishMaelstrom();
        }
    }

    /** Mines pause ordinary firing while allowing the boss to reorient and swim. */
    boolean isMineAttackActive() {
        return mines.isActive();
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
        return beamActive || maelstrom.isActive() || mines.isActive() || recoveryTicks > 0;
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
        beginRecovery(Special.BEAM);
    }

    private void finishMaelstrom() {
        boolean impacted = maelstrom.hasImpacted();
        maelstrom.finish();
        maelstromCooldown = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_MAELSTROM_COOLDOWN.get());
        beginRecovery(Special.MAELSTROM);
        if (impacted) {
            // The attack already held its vulnerable recovery at the impact.
            // Begin the next projectile phase on the next timer update.
            recoveryTicks = 1;
        }
    }

    private void beginRecovery(Special completed) {
        lastSpecial = completed;
        recoveryTicks = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_SPECIAL_RECOVERY.get());
    }

    private void finishMines() {
        mines.clear();
        mineCooldown = mines.cooldown();
        beginRecovery(Special.MINES);
    }

    /**
     * Resets specials after full recovery, including Maelstrom's initial cooldown.
     */
    void reset() {
        beamActive = false;
        beamSightDeadline = 0;
        beamCooldown = 0;
        recoveryTicks = 0;
        lastSpecial = Special.NONE;
        maelstrom.stop();
        maelstromCooldown = -1;
        mines.clear();
        mineCooldown = 0;
        beam.stop();
        boss.setBeamEyeIntensity(0);
    }

    /** @return active beam progress and independent recovery/cooldown timers */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("BeamActive", beamActive);
        tag.putInt("BeamCooldown", beamCooldown);
        tag.putInt("RecoveryTicks", recoveryTicks);
        tag.putInt("LastSpecial", lastSpecial.ordinal());
        tag.put("Beam", beam.save());
        tag.putInt("MaelstromCooldown", maelstromCooldown);
        tag.put("Maelstrom", maelstrom.save());
        tag.putInt("MineCooldown", mineCooldown);
        tag.put("Mines", mines.save());
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
        int savedSpecial = tag.getInt("LastSpecial");
        if (savedSpecial >= 0 && savedSpecial < Special.values().length) {
            lastSpecial = Special.values()[savedSpecial];
        }
        beam.load(tag.getCompound("Beam"));
        maelstromCooldown = tag.contains("MaelstromCooldown")
                ? Mth.clamp(tag.getInt("MaelstromCooldown"), -1, 6000)
                : -1;
        maelstrom.load(tag.getCompound("Maelstrom"));
        mineCooldown = Mth.clamp(tag.getInt("MineCooldown"), 0, 2400);
        if (!beamActive && !maelstrom.isActive()) {
            mines.load(tag.getCompound("Mines"));
        }
        if (maelstrom.isActive()) {
            // Malformed or old state must never resume two specials together.
            beamActive = false;
            beam.stop();
        }
    }
}
