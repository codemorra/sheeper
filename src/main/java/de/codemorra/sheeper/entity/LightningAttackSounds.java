package de.codemorra.sheeper.entity;

import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Plays positional attack cues throughout the storm arena. */
final class LightningAttackSounds {
    private LightningAttackSounds() {
    }

    /** Keeps cues audible inside the arena and uses ordinary sound range for nearby observers. */
    static void play(LightningSheeperEntity boss, Vec3 origin, SoundEvent sound, float volume, float pitch) {
        if (volume <= 0) {
            return;
        }
        ServerLevel level = (ServerLevel) boss.level;
        double range = sound.getRange(volume);
        long seed = level.random.nextLong();
        for (var player : level.players()) {
            Vec3 source = origin;
            if (boss.isInsideArena(player)) {
                source = audibleSource(player.getEyePosition(), origin);
            } else if (player.position().distanceToSqr(origin) >= range * range) {
                continue;
            }
            player.connection.send(new ClientboundSoundPacket(sound, SoundSource.HOSTILE,
                    source.x, source.y, source.z, volume, pitch, seed));
        }
    }

    /** Brings distant sounds closer along the line to the origin, preserving their direction. */
    private static Vec3 audibleSource(Vec3 listener, Vec3 origin) {
        Vec3 offset = origin.subtract(listener);
        return offset.lengthSqr() > 36 ? listener.add(offset.normalize().scale(6)) : origin;
    }
}
