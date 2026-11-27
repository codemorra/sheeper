package de.codemorra.sheeper.entity;

import java.util.List;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Moves the boss over the arena, then holds its lightning form through the final barrage. */
final class LightningRodAttack {
    private static final int CENTER_SEARCH_RADIUS = 4;
    private static final int RETURN_DELAY_TICKS = 10;
    private final LightningSheeperEntity boss;
    private final LightningTeleportMovement teleports;
    private final LightningRodShelters shelters;
    private final LightningRodDischarge discharge;
    @Nullable
    private Vec3 hoverPosition;
    private Vec3 departurePosition;
    @Nullable
    private Vec3 landingPosition;
    private int landingTicks;
    private int landingAttempts;
    private int openingTicks;
    private int travelTicks;
    private int chargeTicks;
    private int chargeDuration;
    private int calmTicks;
    private int calmDuration;
    private int finaleDuration;
    private long finalStrikeAt = -1;
    private int groundFadeTicks;
    private boolean landed;

    LightningRodAttack(LightningSheeperEntity boss, LightningTeleportMovement teleports) {
        this.boss = boss;
        this.teleports = teleports;
        shelters = new LightningRodShelters(boss);
        discharge = new LightningRodDischarge(boss, shelters);
    }

    /** @return whether the arena has both a safe hover point and a shelter site */
    boolean start(ServerLevel level, List<ServerPlayer> players) {
        Vec3 position = findHoverPosition(level);
        if (position == null || !shelters.place(level, players)) {
            return false;
        }
        hoverPosition = position;
        departurePosition = boss.position();
        boss.setTarget(null);
        chargeDuration = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ROD_CHARGE_DURATION.get());
        calmDuration = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ROD_CALM_DURATION.get());
        finaleDuration = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ROD_FINALE_DURATION.get());
        groundFadeTicks = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ROD_GROUND_FADE_DURATION.get());
        chargeTicks = chargeDuration;
        showOpening(level);
        openingTicks = 4;
        return true;
    }

    boolean isActive() {
        return hoverPosition != null;
    }

    /** @return whether the opening bolt is charging the boss before its flight to the center */
    boolean isChargingOnGround() {
        return openingTicks > 0;
    }

    boolean isHovering() {
        return isActive() && travelTicks == 0 && !landed;
    }

    boolean isQuiet() {
        return calmTicks > 0 || finalStrikeAt >= 0;
    }

    /** @return the fade duration during the calm before the discharge */
    int quietFadeTicks() {
        return calmTicks > 0 ? calmDuration : 0;
    }

    /** @return ticks before damaging random strikes stop, including the flight to the center */
    int chargeStormTicksRemaining() {
        return openingTicks + travelTicks + chargeTicks;
    }

    /** Advances travel, charge and discharge before the overlapping landing and arena fade. */
    void tick(ServerLevel level, List<ServerPlayer> players) {
        Vec3 position = hoverPosition;
        if (position == null) {
            return;
        }
        if (openingTicks > 0) {
            if (--openingTicks == 0) {
                travelTicks = teleports.startAttackTravel(position);
            }
            return;
        }
        shelters.tick(level);
        if (finalStrikeAt >= 0) {
            tickRelease(level, players);
            return;
        }
        if (travelTicks > 0) {
            if (--travelTicks == 0) {
                if (teleports.isSafeHoverPosition(position)) {
                    teleports.teleportTo(position);
                    showChargeStart(level);
                } else {
                    clear();
                }
            }
            return;
        }
        if (chargeTicks > 0) {
            if (chargeTicks % 4 == 0) {
                if (!teleports.isSafeHoverPosition(position)) {
                    clear();
                    return;
                }
                showCharge(level);
            }
            if (--chargeTicks == 0) {
                calmTicks = calmDuration;
            }
            return;
        }
        if (calmTicks > 0) {
            if (calmTicks % 4 == 0 && !teleports.isSafeHoverPosition(position)) {
                clear();
                return;
            }
            if (--calmTicks > 0) {
                return;
            }
            if (!teleports.isSafeHoverPosition(position)) {
                clear();
                return;
            }
            discharge.start(finaleDuration);
        }
        if (!discharge.tick(level, players)) {
            Vec3 impact = boss.getEyePosition();
            Vec3 hit = LightningStrikeEffect.spawnWeatherBolt(level, boss.getUUID(), impact,
                    boss.getStormCloudY(position), false);
            LightningRodDischarge.playImpacts(level, players, List.of(hit));
            discharge.energizeGround(level);
            discharge.tickGround(level, players);
            finalStrikeAt = level.getGameTime();
            shelters.beginDissolve(level, groundFadeTicks);
        }
    }

    /** Keeps the shelter and ground clocks independent of movement back to the floor. */
    private void tickRelease(ServerLevel level, List<ServerPlayer> players) {
        long now = level.getGameTime();
        long elapsed = now - finalStrikeAt;
        if (elapsed < groundFadeTicks) {
            discharge.tickGround(level, players);
        }
        if (!landed) {
            if (landingPosition != null) {
                if (--landingTicks == 0) {
                    if (teleports.landPreferredAttackTravel(landingPosition)) {
                        landed = true;
                    } else {
                        beginLanding(level);
                    }
                }
            } else if (elapsed >= RETURN_DELAY_TICKS) {
                beginLanding(level);
            }
        }
        if (landed && shelters.hasDissolved(now)) {
            clear();
        }
    }

    /** Keeps the bolt visible until the boss can materialize at a checked ground position. */
    private void beginLanding(ServerLevel level) {
        if (landingAttempts++ == 2) {
            finishWithoutLanding();
            return;
        }
        landingPosition = teleports.findGroundBelow(level, departurePosition);
        if (landingPosition == null) {
            finishWithoutLanding();
            return;
        }
        landingTicks = teleports.startAttackTravel(landingPosition);
    }

    /** Lets gravity restore the body when terrain no longer offers a checked landing. */
    private void finishWithoutLanding() {
        boss.clearTravelArc();
        landed = true;
    }

    /** Prefers high positions near the center before settling for a lower column. */
    @Nullable
    private Vec3 findHoverPosition(ServerLevel level) {
        BlockPos center = boss.getArenaCenter();
        double desiredHeight = Mth.lerp(0.8, center.getY(), boss.getArenaMaximumY() - boss.getBbHeight() - 0.1);
        Vec3 best = null;
        for (int ring = 0; ring <= CENTER_SEARCH_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    Vec3 position = hoverInColumn(level, center, center.getX() + dx, center.getZ() + dz);
                    if (position != null) {
                        if (position.y >= desiredHeight - 1) {
                            return position;
                        }
                        if (best == null || position.y > best.y) {
                            best = position;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Looks for full-body clearance, lowering the hover point if the arena ceiling is close. */
    @Nullable
    private Vec3 hoverInColumn(ServerLevel level, BlockPos center, int x, int z) {
        Vec3 column = new Vec3(x + 0.5, center.getY(), z + 0.5);
        if (!LightningAreaDamage.chunksLoaded(level, column, column)) {
            return null;
        }
        Double ground = boss.findArenaSurface(level, x, z);
        double floor = ground == null ? center.getY() : ground;
        double ceiling = boss.getArenaMaximumY() - boss.getBbHeight() - 0.1;
        double minimum = floor + 0.5;
        double preferred = Mth.lerp(0.8, floor, ceiling);
        if (ceiling < minimum) {
            return null;
        }
        preferred = Math.max(preferred, minimum);
        for (double y = preferred; y >= minimum; y = y == minimum ? minimum - 1 : Math.max(minimum, y - 1)) {
            Vec3 position = new Vec3(column.x, y, column.z);
            if (teleports.isSafeHoverPosition(position)) {
                return position;
            }
        }
        return null;
    }

    /** Strikes the boss before its body becomes the travelling bolt. */
    private void showOpening(ServerLevel level) {
        Vec3 point = boss.getBoundingBox().getCenter();
        double originY = Math.max(point.y + 16, boss.getStormCloudY(point));
        LightningStrikeEffect.spawnVisualBolt(level, point, originY, 2.8F);
        LightningAttackSounds.play(boss, point, SoundEvents.GENERIC_EXPLODE, 2, 1.0F);
    }

    private void showChargeStart(ServerLevel level) {
        Vec3 point = boss.getEyePosition();
        boss.startTravelArcCharge(chargeDuration, calmDuration, finaleDuration);
        shelters.beginFormation(level, boss.getBoundingBox().getCenter());
        LightningAttackSounds.play(boss, point, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.7F, 0.5F);
        showCharge(level);
    }

    /** Intensifies the sparks around the suspended boss as the charge approaches completion. */
    private void showCharge(ServerLevel level) {
        Vec3 point = boss.position().add(0, boss.getBbHeight() * 0.6, 0);
        float progress = 1 - chargeTicks / (float) chargeDuration;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z,
                9 + (int) (progress * 24), boss.getBbWidth() * 0.45, boss.getBbHeight() * 0.4,
                boss.getBbWidth() * 0.45, 0.035 + progress * 0.09);
    }

    /** Restores the body and clears the arc only if this attack owns it. */
    void clear() {
        if (isActive()) {
            boss.clearTravelArc();
            shelters.clear();
            discharge.clear();
        }
        hoverPosition = null;
        departurePosition = null;
        landingPosition = null;
        landingTicks = 0;
        landingAttempts = 0;
        openingTicks = 0;
        travelTicks = 0;
        chargeTicks = 0;
        calmTicks = 0;
        finalStrikeAt = -1;
        groundFadeTicks = 0;
        landed = false;
    }
}
