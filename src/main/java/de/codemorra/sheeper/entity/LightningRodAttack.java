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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Moves the boss into the air above the arena's center and builds an electrical charge. */
final class LightningRodAttack {
    private static final int CENTER_SEARCH_RADIUS = 4;
    private static final double PREFERRED_HOVER_HEIGHT = 6;
    private final LightningSheeperEntity boss;
    private final LightningTeleportMovement teleports;
    private final LightningRodShelters shelters;
    @Nullable
    private Vec3 hoverPosition;
    private int travelTicks;
    private int chargeTicks;
    private int chargeDuration;
    private int releaseTicks;

    LightningRodAttack(LightningSheeperEntity boss, LightningTeleportMovement teleports) {
        this.boss = boss;
        this.teleports = teleports;
        shelters = new LightningRodShelters(boss);
    }

    /** @return whether the fight has both a safe hover point and nearby shelters */
    boolean start(ServerLevel level, List<ServerPlayer> players) {
        Vec3 position = findHoverPosition(level);
        if (position == null || !shelters.place(level, players)) {
            return false;
        }
        hoverPosition = position;
        boss.setTarget(null);
        chargeDuration = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ROD_CHARGE_DURATION.get());
        chargeTicks = chargeDuration;
        travelTicks = teleports.startAttackTravel(hoverPosition);
        shelters.tick(level);
        return true;
    }

    boolean isActive() {
        return hoverPosition != null;
    }

    boolean isHovering() {
        return isActive() && travelTicks == 0;
    }

    /** Retains lightning form through travel, charge and the final visible strike. */
    void tick(ServerLevel level) {
        Vec3 position = hoverPosition;
        if (position == null) {
            return;
        }
        shelters.tick(level);
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
        if (releaseTicks > 0) {
            if (--releaseTicks == 0) {
                clear();
            }
            return;
        }
        if (chargeTicks % 4 == 0) {
            if (!teleports.isSafeHoverPosition(position)) {
                clear();
                return;
            }
            showCharge(level);
        }
        if (--chargeTicks == 0) {
            boss.setTravelArcCharge(1);
            LightningStrikeEffect.spawnWeatherBolt(level, boss.getEyePosition(), boss.getStormCloudY(position), true);
            releaseTicks = LightningStrikeEffect.VISUAL_DURATION_TICKS;
        }
    }

    /** Tries the central column first, then the nearest rings if roof or terrain obstructs it. */
    @Nullable
    private Vec3 findHoverPosition(ServerLevel level) {
        BlockPos center = boss.getArenaCenter();
        for (int ring = 0; ring <= CENTER_SEARCH_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    Vec3 position = hoverInColumn(level, center, center.getX() + dx, center.getZ() + dz);
                    if (position != null) {
                        return position;
                    }
                }
            }
        }
        return null;
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
        double preferred = Math.min(floor + PREFERRED_HOVER_HEIGHT, ceiling);
        double minimum = floor + 0.5;
        for (double y = preferred; y >= minimum; y = y == minimum ? minimum - 1 : Math.max(minimum, y - 1)) {
            Vec3 position = new Vec3(column.x, y, column.z);
            if (teleports.isSafeHoverPosition(position)) {
                return position;
            }
        }
        return null;
    }

    private void showChargeStart(ServerLevel level) {
        Vec3 point = boss.getEyePosition();
        float volume = LightningSheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
        if (volume > 0) {
            level.playSound(null, point.x, point.y, point.z, SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.HOSTILE, volume, 0.8F);
        }
        showCharge(level);
    }

    /** Intensifies the sparks around the suspended boss as the charge approaches completion. */
    private void showCharge(ServerLevel level) {
        Vec3 point = boss.position().add(0, boss.getBbHeight() * 0.6, 0);
        float progress = 1 - chargeTicks / (float) chargeDuration;
        boss.setTravelArcCharge(progress);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z,
                9 + (int) (progress * 24), boss.getBbWidth() * 0.45, boss.getBbHeight() * 0.4,
                boss.getBbWidth() * 0.45, 0.035 + progress * 0.09);
    }

    /** Restores the body and clears the arc only if this attack owns it. */
    void clear() {
        if (isActive()) {
            boss.clearTravelArc();
            shelters.clear();
        }
        hoverPosition = null;
        travelTicks = 0;
        chargeTicks = 0;
        releaseTicks = 0;
    }
}
