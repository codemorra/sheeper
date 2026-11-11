package de.codemorra.sheeper.entity;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Timing for a fixed-target salvo; phase selection and target validity belong
 * to the boss.
 */
final class WaterProjectileSalvo {
    private final WaterSheeperEntity boss;
    private int preparationTicks;
    private int preparationDuration;
    private int remainingAttackTicks;
    private int shotDelay;
    private int sightLossTicks;

    WaterProjectileSalvo(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    void start() {
        preparationDuration = SheeperConfig.secondsToTicks(SheeperConfig.WATER_SALVO_PREPARATION.get());
        preparationTicks = preparationDuration;
        int minimum = SheeperConfig.secondsToTicks(SheeperConfig.WATER_SALVO_DURATION_MIN.get());
        int maximum = Math.max(minimum, SheeperConfig.secondsToTicks(SheeperConfig.WATER_SALVO_DURATION_MAX.get()));
        remainingAttackTicks = minimum + boss.getRandom().nextInt(maximum - minimum + 1);
        shotDelay = 0;
        sightLossTicks = 0;
        boss.setSalvoEyeIntensity(0.0F);
    }

    /** Returns false when the salvo completes or loses sight for too long. */
    boolean tick(ServerPlayer target) {
        if (remainingAttackTicks == 0) {
            return false;
        }
        boolean visible = boss.hasLineOfSight(target);
        sightLossTicks = visible ? 0 : sightLossTicks + 1;
        if (sightLossTicks >= SheeperConfig.secondsToTicks(SheeperConfig.WATER_SALVO_SIGHT_TIMEOUT.get())) {
            return false;
        }
        if (preparationTicks > 0) {
            preparationTicks--;
            boss.setSalvoEyeIntensity(1.0F - (float) preparationTicks / preparationDuration);
            // Keep the full warning visible before the first shot.
            return true;
        }
        boss.setSalvoEyeIntensity(1.0F);
        if (visible) {
            if (shotDelay > 0) {
                shotDelay--;
            }
            if (shotDelay == 0 && boss.fireWaterProjectile(target)) {
                shotDelay = SheeperConfig.secondsToTicks(SheeperConfig.WATER_SALVO_INTERVAL.get());
            }
        }
        return --remainingAttackTicks > 0;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("PreparationDuration", preparationDuration);
        tag.putInt("PreparationTicks", preparationTicks);
        tag.putInt("RemainingAttackTicks", remainingAttackTicks);
        tag.putInt("ShotDelay", shotDelay);
        tag.putInt("SightLossTicks", sightLossTicks);
        return tag;
    }

    void load(CompoundTag tag) {
        preparationDuration = Math.max(1, Math.min(1200, tag.getInt("PreparationDuration")));
        preparationTicks = Math.max(0, Math.min(preparationDuration, tag.getInt("PreparationTicks")));
        remainingAttackTicks = Math.max(0, Math.min(1200, tag.getInt("RemainingAttackTicks")));
        shotDelay = Math.max(0, Math.min(1200, tag.getInt("ShotDelay")));
        sightLossTicks = Math.max(0, Math.min(1200, tag.getInt("SightLossTicks")));
    }
}
