package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.world.LightningRodShelter;
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
    private static final int GROUND_DAMAGE_INTERVAL = 5;
    private static final int BARRAGE_DAMAGE_INTERVAL = 10;
    private static final double GROUND_REACH = 1.25;
    // A positional storm pulse should not knock the player away from the boss on the other side of the arena.
    private static final DamageSource DAMAGE = new DamageSource("lightningBolt").setScalesWithDifficulty();

    private final LightningSheeperEntity boss;
    private final LightningRodShelters shelters;
    private final Set<UUID> struckMobs = new HashSet<>();
    private int age;
    private int remaining;
    private long nextGroundPulse = -1;
    private float damage;
    @Nullable
    private Vec3 previousImpact;

    LightningRodDischarge(LightningSheeperEntity boss, LightningRodShelters shelters) {
        this.boss = boss;
        this.shelters = shelters;
    }

    void start(int durationTicks) {
        age = 0;
        remaining = durationTicks;
        nextGroundPulse = -1;
        damage = LightningSheeperConfig.LIGHTNING_ROD_FINALE_DAMAGE.get().floatValue();
        previousImpact = null;
        struckMobs.clear();
        float volume = LightningSheeperConfig.LIGHTNING_ATTACK_IMPACT_VOLUME.get().floatValue();
        LightningAttackSounds.play(boss, boss.getEyePosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                volume * 2.4F, 0.9F);
    }

    /** @return whether the barrage has time left after this tick */
    boolean tick(ServerLevel level, List<ServerPlayer> players) {
        if (showBolts(level, players)) {
            energizeGround(level);
        }
        tickGround(level, players);
        if (damage > 0 && age % BARRAGE_DAMAGE_INTERVAL == 0) {
            // Going airborne can avoid the faster ground pulses, but the barrage still reaches exposed players.
            for (ServerPlayer player : players) {
                if (player.isAlive() && !touchesChargedGround(level, player)) {
                    hurtPlayer(level, player);
                }
            }
            for (var target : LightningAreaDamage.arenaMobs(boss)) {
                LightningAreaDamage.hurtMob(target, DAMAGE, damage);
            }
        }
        age++;
        return --remaining > 0;
    }

    /** Starts both the visible charge and damage clock on the first actual impact. */
    void energizeGround(ServerLevel level) {
        if (nextGroundPulse < 0) {
            nextGroundPulse = level.getGameTime();
            shelters.beginGroundCharge(level);
        }
    }

    /** Damages exposed players touching the arena floor, including during the final fade. */
    void tickGround(ServerLevel level, List<ServerPlayer> players) {
        long now = level.getGameTime();
        if (damage <= 0 || nextGroundPulse < 0 || now < nextGroundPulse) {
            return;
        }
        nextGroundPulse = now + GROUND_DAMAGE_INTERVAL;
        for (ServerPlayer player : players) {
            if (!player.isAlive() || LightningAreaDamage.isSheltered(player) || !touchesChargedGround(level, player)) {
                continue;
            }
            // This hazard has its own pulse clock. Keep the existing immunity timer for other damage sources.
            int immunity = player.invulnerableTime;
            player.invulnerableTime = 0;
            try {
                hurtPlayer(level, player);
            } finally {
                player.invulnerableTime = immunity;
            }
        }
    }

    private boolean touchesChargedGround(ServerLevel level, ServerPlayer player) {
        double surface = LightningRingSurface.findForRod(level, player.getX(), player.getZ(),
                boss.getArenaCenter().getY(), boss.getArenaMinimumY(), boss.getArenaMaximumY() - 1);
        double clearance = player.getY() - surface;
        return clearance <= GROUND_REACH && (clearance >= -0.05 || player.isInWater());
    }

    private void hurtPlayer(ServerLevel level, ServerPlayer player) {
        if (LightningAreaDamage.hurtPlayer(boss, player, DAMAGE, damage)) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.65,
                    player.getZ(), 7, 0.22, 0.26, 0.22, 0.1);
        }
    }

    /** Scatters rapid cloud bolts across the arena and deliberately repeats a previous strike periodically. */
    private boolean showBolts(ServerLevel level, List<ServerPlayer> players) {
        int count = Mth.clamp(Mth.ceil(boss.getCombatRadius() / 9), 3, 12);
        boolean audibleBeat = age % 6 == 0;
        boolean impact = false;
        List<Vec3> audibleImpacts = audibleBeat ? new ArrayList<>(count) : List.of();
        for (int index = 0; index < count; index++) {
            boolean repeat = index == 0 && audibleBeat && previousImpact != null;
            Vec3 position = repeat ? resolveSurface(level, previousImpact) : randomImpact(level);
            if (position != null) {
                Vec3 hit = LightningStrikeEffect.spawnWeatherBolt(level, boss.getUUID(), position,
                        boss.getStormCloudY(position), false);
                impact = true;
                if (hit.equals(position) && damage > 0) {
                    strikeMobsAt(position);
                }
                if (audibleBeat) {
                    audibleImpacts.add(hit);
                }
                previousImpact = position;
            }
        }
        if (audibleBeat) {
            List<LightningRodShelter> active = shelters.activeShelters(level.getGameTime());
            int beats = Math.max(1, (age + remaining + 5) / 6);
            int strikesPerBeat = Math.max(1, Mth.ceil(active.size() / (double) beats));
            int first = age / 6 * strikesPerBeat;
            for (int index = 0; !active.isEmpty() && index < strikesPerBeat; index++) {
                LightningRodShelter shelter = active.get((first + index) % active.size());
                audibleImpacts.add(strikeShelter(level, shelter));
                impact = true;
            }
            playImpacts(level, players, audibleImpacts);
            showChargeRelease(level);
        }
        return impact;
    }

    /** Only actual ground impacts can trigger a mob's vanilla lightning reaction during the barrage. */
    private void strikeMobsAt(Vec3 position) {
        for (var target : LightningAreaDamage.mobsAt(boss, position, 0.75, 3)) {
            UUID id = target.getUUID();
            if (!struckMobs.contains(id) && LightningAreaDamage.contains(boss, target, position, 0.75, 3)
                    && LightningAreaDamage.strikeMob(target, position, damage)) {
                struckMobs.add(id);
            }
        }
    }

    /** Targets the inside of the shell so the same interception logic resolves the visible hit. */
    private Vec3 strikeShelter(ServerLevel level, LightningRodShelter shelter) {
        double angle = level.random.nextDouble() * Math.PI * 2;
        double offset = Math.sqrt(level.random.nextDouble()) * shelter.radius() * 0.7;
        Vec3 target = shelter.center().add(Math.cos(angle) * offset, 0, Math.sin(angle) * offset);
        double cloudY = Math.max(boss.getStormCloudY(target), target.y + shelter.radius() + 3);
        return LightningStrikeEffect.spawnWeatherBolt(level, boss.getUUID(), target, cloudY, false);
    }

    /** Throws off sparks in time with the barrage as the suspended charge shrinks. */
    private void showChargeRelease(ServerLevel level) {
        Vec3 center = boss.getBoundingBox().getCenter();
        double energy = remaining / (double) (age + remaining);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 9 + (int) (energy * 26),
                boss.getBbWidth() * (0.25 + energy * 0.5), boss.getBbHeight() * (0.25 + energy * 0.5),
                boss.getBbWidth() * (0.25 + energy * 0.5), 0.12 + energy * 0.20);
    }

    /** Plays one real impact direction per listener so the barrage stays audible across the arena. */
    static void playImpacts(ServerLevel level, List<ServerPlayer> participants, List<Vec3> impacts) {
        float volume = LightningSheeperConfig.LIGHTNING_ATTACK_IMPACT_VOLUME.get().floatValue();
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
        double y = LightningRingSurface.findForRod(level, column.x, column.z, column.y, minY, maxY);
        return Double.isFinite(y) ? new Vec3(column.x, y, column.z) : null;
    }

    void clear() {
        remaining = 0;
        nextGroundPulse = -1;
        previousImpact = null;
        struckMobs.clear();
    }
}
