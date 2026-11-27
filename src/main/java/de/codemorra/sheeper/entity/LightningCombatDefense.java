package de.codemorra.sheeper.entity;

import java.util.List;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Runs the boss's melee and projectile defenses independently of the ordinary teleport schedule. */
final class LightningCombatDefense {
    // A full bow shot can cross the normal 16-block combat spacing in about five ticks.
    private static final int DODGE_WARNING_TICKS = 1;
    private static final int MINIMUM_REACTION_TICKS = DODGE_WARNING_TICKS + 2;
    private static final int PREDICTION_TICKS = 12;
    private static final double PROJECTILE_SEARCH_RADIUS = 40;

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
        if (level.getGameTime() < nextDodgeAt || !boss.isPickable()) {
            return;
        }
        AABB body = boss.getBoundingBox().inflate(0.25);
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, body.inflate(PROJECTILE_SEARCH_RADIUS),
                shot -> shot.getOwner() instanceof ServerPlayer player && participants.contains(player))) {
            if (canReactTo(level, body, projectile)) {
                // A blocked landing search should not run anew every tick as the same shot approaches.
                nextDodgeAt = level.getGameTime() + PREDICTION_TICKS;
                if (teleports.prepareDodge(DODGE_WARNING_TICKS)) {
                    nextDodgeAt = level.getGameTime() + SheeperConfig.secondsToTicks(
                            LightningSheeperConfig.LIGHTNING_DODGE_COOLDOWN.get());
                    level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                            SoundSource.HOSTILE, 0.6F, 1.5F);
                }
                break;
            }
        }
    }

    /** Deals a light shock after an actual melee hit at any point during the crackle. */
    void retaliate(ServerPlayer player) {
        // A recent storm hit must not absorb the shock from a successful melee hit.
        int immunity = player.invulnerableTime;
        player.invulnerableTime = 0;
        try {
            LightningAreaDamage.hurtPlayer(boss, player, LightningSheeperConfig.LIGHTNING_CRACKLE_DAMAGE.get()
                    .floatValue());
        } finally {
            player.invulnerableTime = immunity;
        }
    }

    void reset() {
        clearCrackle();
        nextDodgeAt = 0;
    }

    private void tickCrackle() {
        if (LightningSheeperConfig.LIGHTNING_CRACKLE_DAMAGE.get() <= 0) {
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

    /**
     * Predicts short projectile paths from current motion. Arrows need gravity and drag to avoid
     * missing the threat from a long, descending shot. Other projectiles use their current heading.
     */
    private static boolean canReactTo(ServerLevel level, AABB body, Projectile projectile) {
        Vec3 start = projectile.position();
        Vec3 motion = projectile.getDeltaMovement();
        if (motion.lengthSqr() < 0.0225 || body.contains(start)) {
            return false;
        }
        Vec3 point = start;
        double arrowDrag = projectile.isInWater() ? 0.6 : 0.99;
        for (int tick = 1; tick <= PREDICTION_TICKS; tick++) {
            Vec3 next = point.add(motion);
            Vec3 hit = body.clip(point, next).orElse(null);
            if (hit != null) {
                return tick >= MINIMUM_REACTION_TICKS && level.clip(new ClipContext(start, hit,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile)).getType() == HitResult.Type.MISS;
            }
            point = next;
            if (projectile instanceof AbstractArrow) {
                motion = motion.scale(arrowDrag).add(0, projectile.isNoGravity() ? 0 : -0.05, 0);
            }
        }
        return false;
    }
}
