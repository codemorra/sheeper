package de.codemorra.sheeper.client;

import de.codemorra.sheeper.entity.WaterProjectile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Quiet flight sound whose source follows the projectile and stops with its
 * removal.
 */
final class WaterProjectileSound extends AbstractTickableSoundInstance {
    private final WaterProjectile projectile;

    WaterProjectileSound(WaterProjectile projectile) {
        super(SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.HOSTILE, projectile.level.random);
        this.projectile = projectile;
        volume = 0.3F;
        pitch = 1.4F;
        looping = true;
        delay = 0;
        updatePosition();
    }

    /**
     * Prevents flight audio from persisting after impact, despawn, or a world
     * change.
     */
    @Override
    public void tick() {
        if (projectile.isRemoved() || Minecraft.getInstance().level != projectile.level) {
            stop();
        } else {
            updatePosition();
        }
    }

    @Override
    public boolean canPlaySound() {
        return !projectile.isSilent();
    }

    private void updatePosition() {
        x = projectile.getX();
        y = projectile.getY();
        z = projectile.getZ();
    }
}
