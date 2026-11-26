package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.network.LightningRingVisualPacket;
import de.codemorra.sheeper.network.ModNetwork;
import de.codemorra.sheeper.world.LightningStormData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Repeats a direct teleport, a short charge and one expanding ground ring per landing. */
final class LightningRingBarrage {
    private static final int TURN_TICKS = 2;

    private static final class Wave {
        private final Vec3 origin;
        private final double limit;
        private final long startTick;
        private final Set<UUID> hit = new HashSet<>();
        private double radius;

        private Wave(Vec3 origin, double limit, long startTick) {
            this.origin = origin;
            this.limit = limit;
            this.startTick = startTick;
        }
    }

    private final LightningSheeperEntity boss;
    private final LightningTeleportMovement teleports;
    private final List<Wave> waves = new ArrayList<>();
    private int remaining;
    private int warningTicks;
    private int warningRemaining;
    private long warningStart;
    private Vec3 warningOrigin = Vec3.ZERO;
    private int chargeTicks;
    private int intervalTicks;
    private int turnRemaining;
    private int travelRemaining;
    private int chargeRemaining;
    private int untilTeleport;
    private long earliestReleaseTick;
    @Nullable
    private Vec3 landing;
    private double speed;
    private double width;
    private double hitHeight;
    private float damage;
    private float spacing;

    LightningRingBarrage(LightningSheeperEntity boss, LightningTeleportMovement teleports) {
        this.boss = boss;
        this.teleports = teleports;
    }

    /** Captures one wave count and the timing and dimensions of the full attack. */
    void start(ServerLevel level) {
        clear();
        int min = LightningSheeperConfig.LIGHTNING_RING_COUNT_MIN.get();
        int max = LightningSheeperConfig.LIGHTNING_RING_COUNT_MAX.get();
        remaining = min + boss.getRandom().nextInt(Math.max(0, max - min) + 1);
        chargeTicks = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_RING_CHARGE.get());
        intervalTicks = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_RING_INTERVAL.get());
        speed = LightningSheeperConfig.LIGHTNING_RING_SPEED.get() / 20;
        width = LightningSheeperConfig.LIGHTNING_RING_WIDTH.get();
        hitHeight = LightningSheeperConfig.LIGHTNING_RING_HIT_HEIGHT.get();
        damage = LightningSheeperConfig.LIGHTNING_RING_DAMAGE.get().floatValue();
        spacing = LightningSheeperConfig.LIGHTNING_RING_PARTICLE_SPACING.get().floatValue();
        warningTicks = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_RING_WARNING.get());
        warningRemaining = warningTicks;
        if (warningTicks > 0) {
            warningOrigin = boss.position();
            warningStart = level.getGameTime();
            showWarning(level);
            float volume = LightningSheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
            LightningAttackSounds.play(boss, warningOrigin, SoundEvents.RESPAWN_ANCHOR_CHARGE, volume * 0.8F, 1.35F);
        }
    }

    boolean isActive() {
        return remaining > 0 || !waves.isEmpty();
    }

    void tick(ServerLevel level, List<ServerPlayer> players) {
        if (warningRemaining > 0) {
            long age = level.getGameTime() - warningStart;
            if (age > 0 && age % 4 == 0) {
                showWarning(level);
            }
            warningRemaining--;
            return;
        }
        var iterator = waves.iterator();
        while (iterator.hasNext()) {
            Wave wave = iterator.next();
            double previous = wave.radius;
            wave.radius = Math.min(wave.limit, previous + speed);
            igniteAlongWave(level, wave, previous);
            hitPlayers(level, wave, previous, players);
            if ((level.getGameTime() - wave.startTick) % 3 == 0) {
                showWave(level, wave);
            }
            if (wave.radius >= wave.limit) {
                iterator.remove();
            }
        }
        if (remaining <= 0) {
            return;
        }
        if (chargeRemaining > 0) {
            if (--chargeRemaining == 0) {
                release(level);
                if (--remaining > 0) {
                    planNextJump(level.getGameTime());
                }
            } else if (chargeRemaining % 3 == 0) {
                showCharge(level);
            }
        } else if (travelRemaining > 0) {
            if (--travelRemaining == 0 && landing != null) {
                teleports.landAttackTeleport(landing);
                beginCharge(level);
            }
        } else if (turnRemaining > 0) {
            if (--turnRemaining == 0) {
                // An unsafe destination leaves the boss in place for this wave.
                travelRemaining = landing == null ? 0 : teleports.beginAttackTravel(landing);
                if (travelRemaining == 0) {
                    beginCharge(level);
                }
            }
        } else if (--untilTeleport <= 0) {
            if (landing == null) {
                landing = teleports.findAttackDestination();
            }
            if (landing != null) {
                teleports.faceDestination(landing);
            }
            turnRemaining = TURN_TICKS;
        }
    }

    /** Samples the moving crest against the same terrain surface used for ring damage. */
    private void igniteAlongWave(ServerLevel level, Wave wave, double previous) {
        var storm = LightningStormData.get(level);
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double arenaRadius = boss.getCombatRadius();
        int passes = Math.max(1, Mth.ceil(wave.radius - previous));
        for (int pass = 0; pass < passes; pass++) {
            double radius = Mth.lerp((pass + 0.5) / passes, previous, wave.radius);
            int samples = Mth.clamp(Mth.ceil(2 * Math.PI * radius), 8, 512);
            double step = 2 * Math.PI / samples;
            double turn = (level.getGameTime() % 11) * step / 11;
            for (int index = 0; index < samples; index++) {
                double angle = turn + index * step;
                double x = wave.origin.x + radius * Math.cos(angle);
                double z = wave.origin.z + radius * Math.sin(angle);
                double dx = x - center.x;
                double dz = z - center.z;
                if (dx * dx + dz * dz > arenaRadius * arenaRadius) {
                    continue;
                }
                double floor = LightningRingSurface.find(level, x, z, wave.origin.y,
                        boss.getArenaMinimumY(), boss.getArenaMaximumY() - 1);
                if (Double.isFinite(floor)) {
                    storm.igniteLeaves(level, boss.getUUID(), new BlockPos(x, Math.nextDown(floor), z));
                }
            }
        }
    }

    /** Reserves time for a tracked flight and the full charge within the minimum wave interval. */
    private void planNextJump(long now) {
        landing = teleports.findAttackDestination();
        int travelTicks = landing == null ? 0 : teleports.attackTravelTicks(landing);
        earliestReleaseTick = now + intervalTicks;
        untilTeleport = Math.max(1, intervalTicks - chargeTicks - TURN_TICKS - travelTicks);
    }

    private void beginCharge(ServerLevel level) {
        landing = null;
        chargeRemaining = (int) Math.max(chargeTicks, earliestReleaseTick - level.getGameTime());
        showCharge(level);
    }

    private void showCharge(ServerLevel level) {
        double progress = Math.max(0, 1 - chargeRemaining / (double) chargeTicks);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + boss.getBbHeight() * 0.55,
                boss.getZ(), 10 + (int) (progress * 22), boss.getBbWidth() * 0.52,
                boss.getBbHeight() * 0.37, boss.getBbWidth() * 0.52, 0.035 + progress * 0.07);
    }

    /** Stores the landing origin on the wave so it keeps travelling through later teleports. */
    private void release(ServerLevel level) {
        Vec3 origin = boss.position();
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double limit = boss.getCombatRadius() + origin.subtract(center).horizontalDistance() + width * 0.5;
        Wave wave = new Wave(origin, limit, level.getGameTime());
        waves.add(wave);
        showWave(level, wave);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, origin.x, origin.y + boss.getBbHeight() * 0.52,
                origin.z, 28, boss.getBbWidth() * 0.40, boss.getBbHeight() * 0.20, boss.getBbWidth() * 0.40, 0.13);
        float volume = LightningSheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
        LightningAttackSounds.play(boss, origin, SoundEvents.LIGHTNING_BOLT_IMPACT, volume, 1.4F);
    }

    private void hitPlayers(ServerLevel level, Wave wave, double previous, List<ServerPlayer> players) {
        if (damage <= 0) {
            return;
        }
        for (ServerPlayer player : players) {
            if (!player.isAlive() || wave.hit.contains(player.getUUID())) {
                continue;
            }
            AABB bounds = player.getBoundingBox();
            double near = Math.hypot(Mth.clamp(wave.origin.x, bounds.minX, bounds.maxX) - wave.origin.x,
                    Mth.clamp(wave.origin.z, bounds.minZ, bounds.maxZ) - wave.origin.z);
            double far = Math.hypot(Math.max(Math.abs(bounds.minX - wave.origin.x), Math.abs(bounds.maxX - wave.origin.x)),
                    Math.max(Math.abs(bounds.minZ - wave.origin.z), Math.abs(bounds.maxZ - wave.origin.z)));
            // Test the full swept band so a fast wave still hits a player between two visible frames.
            if (near > wave.radius + width * 0.5 || far < previous - width * 0.5) {
                continue;
            }
            double floor = LightningRingSurface.find(level, player.getX(), player.getZ(), wave.origin.y,
                    boss.getArenaMinimumY(), boss.getArenaMaximumY() - 1);
            if (!Double.isFinite(floor) || bounds.minY < floor - 0.1 || bounds.minY >= floor + hitHeight) {
                continue;
            }
            Vec3 motion = player.getDeltaMovement();
            if (LightningAreaDamage.hurtPlayer(boss, player, damage)) {
                wave.hit.add(player.getUUID());
                player.setDeltaMovement(motion);
            }
        }
    }

    private void showWarning(ServerLevel level) {
        sendVisual(level, warningOrigin, warningStart, warningTicks, 56);
    }

    private void showWave(ServerLevel level, Wave wave) {
        sendVisual(level, wave.origin, wave.startTick, 0, wave.radius + 64);
    }

    private void sendVisual(ServerLevel level, Vec3 origin, long startTick, int warningDuration, double range) {
        var packet = new LightningRingVisualPacket(level.dimension().location(), boss.getArenaCenter(),
                boss.getCombatRadius(), boss.getArenaMinimumY(), boss.getArenaMaximumY() - 1, origin, startTick,
                warningDuration, speed, (float) width, spacing, boss.getBbHeight());
        if (!boss.isWithinArenaHeight(origin.y) || !packet.isWithinArena(origin.x, origin.z)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(origin) <= range * range) {
                ModNetwork.sendLightningRingVisual(player, packet);
            }
        }
    }

    void clear() {
        if (travelRemaining > 0) {
            boss.clearTravelArc();
        }
        waves.clear();
        remaining = 0;
        warningRemaining = 0;
        turnRemaining = 0;
        travelRemaining = 0;
        chargeRemaining = 0;
        untilTeleport = 0;
        earliestReleaseTick = 0;
        landing = null;
    }
}
