package de.codemorra.sheeper.client;

import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Tracks the defensive crackle until the synchronized charge ends, including during a teleport. */
final class LightningCrackleSound extends AbstractTickableSoundInstance {
    private final LightningSheeperEntity boss;

    LightningCrackleSound(LightningSheeperEntity boss) {
        super(ModSounds.LIGHTNING_FAN_CRACKLE.get(), SoundSource.HOSTILE, boss.level.random);
        this.boss = boss;
        volume = 0.85F;
        pitch = 1.05F;
        looping = true;
        updatePosition();
    }

    boolean isCrackleActive(Minecraft minecraft) {
        return minecraft.level == boss.level && !boss.isRemoved() && !boss.isSilent() && boss.isDefenseCrackling();
    }

    @Override
    public void tick() {
        if (isCrackleActive(Minecraft.getInstance())) {
            updatePosition();
        } else {
            stop();
        }
    }

    private void updatePosition() {
        Vec3 origin = boss.getBoundingBox().getCenter();
        var arc = boss.getTravelArc();
        if (arc != null) {
            origin = arc.start().lerp(arc.end(), arc.progress(boss.level.getGameTime()));
        }
        x = origin.x;
        y = origin.y;
        z = origin.z;
    }
}
