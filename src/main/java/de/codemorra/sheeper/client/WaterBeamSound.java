package de.codemorra.sheeper.client;

import de.codemorra.sheeper.entity.WaterSheeperEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Layers brief bubble cues over water ambience at the closest point on an
 * active beam.
 */
final class WaterBeamSound {
    private final WaterSheeperEntity boss;
    private int ticks;

    WaterBeamSound(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    /**
     * Includes world changes and boss removal so detached emitters are discarded.
     */
    boolean isBeamActive() {
        return Minecraft.getInstance().level == boss.level && boss.isAlive() && !boss.isSilent()
                && boss.getBeamOffset().lengthSqr() > 1.0E-8D;
    }

    /** Short bubble samples fill the ambient clip's built-in swells and pauses. */
    void tick() {
        if (!isBeamActive()) {
            return;
        }
        Vec3 point = closestPoint();
        var level = Minecraft.getInstance().level;
        if (ticks % 40 == 0) {
            level.playLocalSound(point.x, point.y, point.z, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                    SoundSource.HOSTILE, 0.65F, 0.85F, false);
        }
        if (ticks % 4 == 0) {
            level.playLocalSound(point.x, point.y, point.z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP,
                    SoundSource.HOSTILE, 0.3F, 0.95F + level.random.nextFloat() * 0.2F, false);
        }
        ticks = (ticks + 1) % 40;
    }

    /**
     * Uses the camera rather than the boss position to preserve spatial audio along
     * the full tube.
     */
    private Vec3 closestPoint() {
        Vec3 listener = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 start = boss.getBeamOrigin();
        Vec3 offset = boss.getBeamOffset();
        double lengthSquared = offset.lengthSqr();
        double fraction = lengthSquared < 1.0E-8D ? 0
                : Mth.clamp(listener.subtract(start).dot(offset) / lengthSquared, 0, 1);
        return start.add(offset.scale(fraction));
    }
}
