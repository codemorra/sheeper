package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/** Runs the rapid final barrage and checks each player's shelter on every damage pulse. */
final class LightningRodDischarge {
    private static final int DAMAGE_INTERVAL = 10;
    // A positional storm pulse should not knock the player away from the boss on the other side of the arena.
    private static final DamageSource DAMAGE = new DamageSource("lightningBolt").setScalesWithDifficulty();

    private final LightningSheeperEntity boss;
    private final LightningRodShelters shelters;
    private int age;
    private int remaining;
    private float damage;
    @Nullable
    private Vec3 previousImpact;

    LightningRodDischarge(LightningSheeperEntity boss, LightningRodShelters shelters) {
        this.boss = boss;
        this.shelters = shelters;
    }

    void start() {
        age = 0;
        remaining = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ROD_FINALE_DURATION.get());
        damage = LightningSheeperConfig.LIGHTNING_ROD_FINALE_DAMAGE.get().floatValue();
        previousImpact = null;
    }

    /** @return whether the barrage has time left after this tick */
    boolean tick(ServerLevel level, List<ServerPlayer> players) {
        showBolts(level, players);
        if (damage > 0 && age % DAMAGE_INTERVAL == 0) {
            for (ServerPlayer player : players) {
                if (player.isAlive() && !shelters.protects(player)
                        && LightningAreaDamage.hurtPlayer(boss, player, DAMAGE, damage)) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.65,
                            player.getZ(), 7, 0.22, 0.26, 0.22, 0.1);
                }
            }
        }
        age++;
        return --remaining > 0;
    }

    /** Scatters rapid cloud bolts across the arena and deliberately repeats a previous strike periodically. */
    private void showBolts(ServerLevel level, List<ServerPlayer> players) {
        int count = Mth.clamp(Mth.ceil(boss.getCombatRadius() / 9), 3, 12);
        boolean audibleBeat = age % 6 == 0;
        List<Vec3> audibleImpacts = audibleBeat ? new ArrayList<>(count) : List.of();
        for (int index = 0; index < count; index++) {
            boolean repeat = index == 0 && audibleBeat && previousImpact != null;
            Vec3 position = repeat ? resolveSurface(level, previousImpact) : randomImpact(level);
            if (position != null) {
                LightningStrikeEffect.spawnWeatherBolt(level, boss.getUUID(), position, boss.getStormCloudY(position), false);
                if (audibleBeat) {
                    audibleImpacts.add(position);
                }
                previousImpact = position;
            }
        }
        if (audibleBeat) {
            playImpacts(level, players, audibleImpacts);
        }
    }

    /** Plays one real impact direction per listener so the barrage stays audible across the arena. */
    static void playImpacts(ServerLevel level, List<ServerPlayer> participants, List<Vec3> impacts) {
        float volume = LightningSheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
        if (volume <= 0 || impacts.isEmpty()) {
            return;
        }
        var sound = SoundEvents.LIGHTNING_BOLT_IMPACT;
        double range = sound.getRange(volume);
        for (ServerPlayer player : level.players()) {
            Vec3 listener = player.getEyePosition();
            Vec3 nearest = impacts.get(0);
            for (Vec3 impact : impacts) {
                if (listener.distanceToSqr(impact) < listener.distanceToSqr(nearest)) {
                    nearest = impact;
                }
            }
            Vec3 offset = nearest.subtract(listener);
            double distance = offset.length();
            if (participants.contains(player)) {
                // Keep distant hits in the right direction at an audible distance.
                nearest = listener.add(offset.scale(Math.min(1, 6 / Math.max(distance, 0.01))));
            } else if (distance >= range) {
                continue;
            }
            float pitch = 0.9F + level.random.nextFloat() * 0.2F;
            player.connection.send(new ClientboundSoundPacket(sound, SoundSource.HOSTILE,
                    nearest.x, nearest.y, nearest.z, volume, pitch, level.random.nextLong()));
        }
    }

    /** Samples loaded columns uniformly across the storm, avoiding columns without an exposed surface. */
    @Nullable
    private Vec3 randomImpact(ServerLevel level) {
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double radius = boss.getCombatRadius();
        for (int attempt = 0; attempt < 6; attempt++) {
            double direction = boss.getRandom().nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(boss.getRandom().nextDouble()) * radius;
            double x = Mth.floor(center.x + Math.cos(direction) * distance) + 0.5;
            double z = Mth.floor(center.z + Math.sin(direction) * distance) + 0.5;
            var column = new Vec3(x, center.y, z);
            if (column.subtract(center).horizontalDistanceSqr() <= radius * radius) {
                Vec3 position = resolveSurface(level, column);
                if (position != null) {
                    return position;
                }
            }
        }
        return null;
    }

    @Nullable
    private Vec3 resolveSurface(ServerLevel level, Vec3 column) {
        int minY = boss.getArenaMinimumY();
        int maxY = boss.getArenaMaximumY() - 1;
        double y = LightningRingSurface.findWater(level, column.x, column.z, minY, maxY);
        if (!Double.isFinite(y)) {
            y = LightningRingSurface.find(level, column.x, column.z, column.y, minY, maxY);
        }
        return Double.isFinite(y) ? new Vec3(column.x, y, column.z) : null;
    }

    void clear() {
        remaining = 0;
        previousImpact = null;
    }
}
