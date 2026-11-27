package de.codemorra.sheeper.entity;

import java.util.List;

import de.codemorra.sheeper.network.LightningSoundPacket;
import de.codemorra.sheeper.network.ModNetwork;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Plays positional attack cues throughout the storm arena. */
final class LightningAttackSounds {
    private LightningAttackSounds() {
    }

    /** Keeps cues audible inside the arena and uses ordinary sound range for nearby observers. */
    static void play(LightningSheeperEntity boss, Vec3 origin, SoundEvent sound, float volume, float pitch) {
        playNearest(boss, List.of(origin), sound, volume, pitch);
    }

    /** Plays one cue per listener from the nearest source so overlapping effects do not stack. */
    static void playNearest(LightningSheeperEntity boss, List<Vec3> origins, SoundEvent sound, float volume,
            float pitch) {
        if (volume <= 0 || origins.isEmpty()) {
            return;
        }
        ServerLevel level = (ServerLevel) boss.level;
        double range = deliveryRange(sound, volume);
        long seed = level.random.nextLong();
        for (var player : level.players()) {
            Vec3 listener = player.getEyePosition();
            Vec3 source = origins.get(0);
            for (Vec3 origin : origins) {
                if (listener.distanceToSqr(origin) < listener.distanceToSqr(source)) {
                    source = origin;
                }
            }
            if (boss.isInsideArena(player)) {
                source = audibleSource(listener, source);
            } else if (player.position().distanceToSqr(source) >= range * range) {
                continue;
            }
            send(player, source, sound, SoundSource.HOSTILE, volume, pitch, seed);
        }
    }

    /** Delivers nearby impacts without moving their origin toward arena participants. */
    static void playNearby(ServerLevel level, Vec3 origin, SoundEvent sound, SoundSource category, float pitch) {
        double range = deliveryRange(sound, 1);
        long seed = level.random.nextLong();
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(origin) < range * range) {
                send(player, origin, sound, category, 1, pitch, seed);
            }
        }
    }

    /** Covers the full supported local mix. The client attenuates sounds at its chosen volume. */
    static double deliveryRange(SoundEvent sound, float volume) {
        return sound.getRange(volume * 4);
    }

    static void send(ServerPlayer player, Vec3 origin, SoundEvent sound, SoundSource category,
            float volume, float pitch, long seed) {
        var cue = new ClientboundSoundPacket(sound, category, origin.x, origin.y, origin.z, volume, pitch, seed);
        ModNetwork.sendLightningSound(player, new LightningSoundPacket(cue, false));
    }

    /** Brings distant sounds closer along the line to the origin, preserving their direction. */
    private static Vec3 audibleSource(Vec3 listener, Vec3 origin) {
        Vec3 offset = origin.subtract(listener);
        return offset.lengthSqr() > 36 ? listener.add(offset.normalize().scale(6)) : origin;
    }
}
