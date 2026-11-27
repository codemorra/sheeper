package de.codemorra.sheeper.entity;

import java.util.List;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Runs the boss's melee and projectile defenses independently of the ordinary teleport schedule. */
final class LightningCombatDefense {
    // A full bow shot can cross the normal 16-block combat spacing in about five ticks.
    private static final int DODGE_WARNING_TICKS = 1;
    private static final int MINIMUM_REACTION_TICKS = DODGE_WARNING_TICKS + 2;

    private final LightningSheeperEntity boss;
    private int ticksUntilCrackle;
    private int crackleTicksRemaining;
    private long nextDodgeAt;

    LightningCombatDefense(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    /** Advances the crackle even while the boss is travelling as a lightning bolt. */
    void tick(ServerLevel level, List<ServerPlayer> participants, LightningTeleportMovement teleports) {
        if (participants.isEmpty()) {
            clearCrackle();
            return;
        }
        tickCrackle();
        if (!LightningSheeperConfig.LIGHTNING_DODGE_ENABLED.get()
                || level.getGameTime() < nextDodgeAt || !boss.isPickable()) {
            return;
        }
        if (IncomingProjectile.find(level, boss.getBoundingBox(), Vec3.ZERO, participants, MINIMUM_REACTION_TICKS) == null) {
            return;
        }
        // A blocked landing search should not run anew every tick as the same shot approaches.
        nextDodgeAt = level.getGameTime() + IncomingProjectile.PREDICTION_TICKS;
        if (teleports.prepareDodge(DODGE_WARNING_TICKS)) {
            nextDodgeAt = level.getGameTime() + SheeperConfig.secondsToTicks(
                    LightningSheeperConfig.LIGHTNING_DODGE_COOLDOWN.get());
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.HOSTILE, 0.6F, 1.5F);
        }
    }

    /** Deals a light shock after an actual melee hit at any point during the crackle. */
    void retaliate(ServerPlayer player) {
        float damage = LightningSheeperConfig.LIGHTNING_CRACKLE_DAMAGE.get().floatValue();
        if (damage <= 0) {
            return;
        }
        // A recent storm hit must not absorb the shock from a successful melee hit.
        int immunity = player.invulnerableTime;
        player.invulnerableTime = 0;
        try {
            LightningAreaDamage.hurtPlayer(boss, player, damage);
        } finally {
            player.invulnerableTime = immunity;
        }
    }

    void reset() {
        clearCrackle();
        nextDodgeAt = 0;
    }

    private void tickCrackle() {
        if (!LightningSheeperConfig.LIGHTNING_CRACKLE_ENABLED.get()) {
            clearCrackle();
            return;
        }
        if (ticksUntilCrackle == 0) {
            ticksUntilCrackle = nextCrackleInterval();
        }
        if (--ticksUntilCrackle == 0) {
            ticksUntilCrackle = nextCrackleInterval();
            crackleTicksRemaining = Math.min(ticksUntilCrackle - 1, SheeperConfig.secondsToTicks(
                    LightningSheeperConfig.LIGHTNING_CRACKLE_DURATION.get()));
        } else if (crackleTicksRemaining > 0) {
            crackleTicksRemaining--;
        }
        boss.setDefenseCrackling(crackleTicksRemaining > 0);
    }

    private int nextCrackleInterval() {
        int minimum = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_CRACKLE_INTERVAL_MIN.get());
        int maximum = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_CRACKLE_INTERVAL_MAX.get());
        return Mth.nextInt(boss.getRandom(), minimum, Math.max(minimum, maximum));
    }

    private void clearCrackle() {
        ticksUntilCrackle = 0;
        crackleTicksRemaining = 0;
        boss.setDefenseCrackling(false);
    }

}
