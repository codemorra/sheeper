package de.codemorra.sheeper.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import de.codemorra.sheeper.world.LightningTornado;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** World-space wind loops driven by the same tornado snapshots as the visible funnels. */
final class LightningTornadoSound extends AbstractTickableSoundInstance {
    private static final double HEARING_DISTANCE = 10;
    private static final float MAX_COMBINED_VOLUME = 0.8F;
    private static final Map<TornadoKey, LightningTornadoSound> loops = new HashMap<>();
    private static ClientLevel soundLevel;

    // The owner launches at most one funnel per tick and retains its launch tick in later snapshots.
    private record TornadoKey(UUID owner, long launchTick) {
    }

    private record Sample(Vec3 position, float volume) {
        static Sample at(LightningTornado tornado, Vec3 listener, long now) {
            Vec3 base = tornado.centerAt(now);
            double height = listener.y - base.y;
            double closestHeight = Mth.clamp(height, 0, tornado.height());
            double radius = tornado.radiusAtHeight(closestHeight / tornado.height());
            double horizontal = Math.hypot(listener.x - base.x, listener.z - base.z);
            double vertical = Math.abs(height - closestHeight);
            double distance = Math.hypot(Math.max(0, horizontal - radius), vertical);
            double proximity = Mth.clamp(1 - distance / HEARING_DISTANCE, 0, 1);
            double depth = Mth.clamp(1 - horizontal / radius, 0, 1) * Mth.clamp(1 - vertical / 4, 0, 1);
            double gain = proximity * proximity * (3 - 2 * proximity) * (0.38 + 0.24 * depth);
            double envelope = tornado.formationAt(now) * tornado.fadeAt(now);
            return new Sample(base.add(0, closestHeight, 0), (float) (gain * envelope));
        }
    }

    private float targetVolume;

    private LightningTornadoSound() {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, RandomSource.create());
        // The flight wind sample loops seamlessly. Range comes from the funnel's own geometry.
        looping = true;
        volume = 0;
        pitch = 0.65F;
        attenuation = Attenuation.NONE;
    }

    /** Keeps one emitter per nearby funnel and balances their levels where they overlap. */
    static void updateLoops(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        var soundManager = minecraft.getSoundManager();
        if (soundLevel != level) {
            loops.values().forEach(soundManager::stop);
            loops.clear();
            soundLevel = level;
        }
        if (level == null || minecraft.isPaused()) {
            return;
        }
        Map<TornadoKey, Sample> audible = new HashMap<>();
        float combined = 0;
        if (minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0
                && minecraft.options.getSoundSourceVolume(SoundSource.WEATHER) > 0) {
            Vec3 listener = minecraft.gameRenderer.getMainCamera().getPosition();
            long now = level.getGameTime();
            for (LightningTornado tornado : LightningTornadoVisuals.activeTornados(level)) {
                Sample sample = Sample.at(tornado, listener, now);
                if (sample.volume() > 0.005F) {
                    audible.put(new TornadoKey(tornado.owner(), tornado.activeFrom()), sample);
                    combined += sample.volume();
                }
            }
        }
        loops.values().removeIf(LightningTornadoSound::isStopped);
        loops.values().forEach(sound -> sound.targetVolume = 0);
        float mix = Math.min(1, MAX_COMBINED_VOLUME / Math.max(MAX_COMBINED_VOLUME, combined));
        audible.forEach((key, sample) -> {
            LightningTornadoSound sound = loops.get(key);
            if (sound == null || !soundManager.isActive(sound)) {
                sound = new LightningTornadoSound();
                sound.update(sample, mix);
                loops.put(key, sound);
                soundManager.play(sound);
            } else {
                sound.update(sample, mix);
            }
        });
    }

    private void update(Sample sample, float mix) {
        Vec3 position = sample.position();
        x = position.x;
        y = position.y;
        z = position.z;
        targetVolume = sample.volume() * mix;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        volume = Mth.lerp(0.4F, volume, targetVolume);
        if (targetVolume == 0 && volume < 0.005F) {
            stop();
        }
    }
}
