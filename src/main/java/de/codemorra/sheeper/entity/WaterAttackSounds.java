package de.codemorra.sheeper.entity;

import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Positional attack cues sent to arena participants without increasing their
 * volume.
 */
final class WaterAttackSounds {
    private WaterAttackSounds() {
    }

    /**
     * Makes important cues audible throughout the arena while preserving the
     * direction towards their source. Only distant sources are brought closer.
     */
    static void arenaCue(WaterSheeperEntity boss, Vec3 source, SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer player : boss.getCombatParticipants()) {
            Vec3 listener = player.getEyePosition();
            Vec3 offset = source.subtract(listener);
            Vec3 position = offset.lengthSqr() > 36 ? listener.add(offset.normalize().scale(6)) : source;
            send(player, position, sound, volume, pitch);
        }
    }

    /**
     * Plays a long attack at the closest point on its actual segment for each
     * nearby swimmer.
     */
    static void segment(WaterSheeperEntity boss, Vec3 start, Vec3 end,
            SoundEvent sound, float volume, float pitch) {
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        for (ServerPlayer player : boss.getCombatParticipants()) {
            Vec3 listener = player.getEyePosition();
            double fraction = lengthSquared < 1.0E-8D ? 0
                    : Mth.clamp(listener.subtract(start).dot(segment) / lengthSquared, 0, 1);
            nearby(player, start.add(segment.scale(fraction)), sound, volume, pitch);
        }
    }

    /**
     * Sends a local cue only within normal hearing distance of its physical source.
     */
    static void nearby(ServerPlayer player, Vec3 source, SoundEvent sound, float volume, float pitch) {
        double range = sound.getRange(volume);
        if (player.getEyePosition().distanceToSqr(source) < range * range) {
            send(player, source, sound, volume, pitch);
        }
    }

    /**
     * Direct packets bypass the boss-centered broadcast radius without duplicating
     * playback.
     */
    private static void send(ServerPlayer player, Vec3 source, SoundEvent sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.HOSTILE,
                source.x, source.y, source.z, volume, pitch, player.getRandom().nextLong()));
    }
}
