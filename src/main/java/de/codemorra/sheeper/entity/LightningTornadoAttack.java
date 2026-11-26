package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.ArenaFallProtection;
import de.codemorra.sheeper.world.LightningArenaBoundaryDrag;
import de.codemorra.sheeper.world.LightningStormData;
import de.codemorra.sheeper.world.LightningTornado;
import de.codemorra.sheeper.world.LightningTornadoMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Launches a volley of funnels from one teleport destination and advances each until it dissipates. */
final class LightningTornadoAttack {
    private static final int TURN_BEFORE_TELEPORT_TICKS = 10;
    private static final double LAUNCH_SPEED_MULTIPLIER = 2;

    private static final class Funnel {
        private final Vec3 launchOrigin;
        private final double launchDistance;
        private LightningTornado snapshot;
        private double heading;
        private double desiredHeading;
        private long nextTurn;
        private boolean accelerating;

        private Funnel(LightningTornado snapshot, double heading, double launchDistance) {
            launchOrigin = snapshot.position();
            this.launchDistance = launchDistance;
            this.snapshot = snapshot;
            this.heading = heading;
            desiredHeading = heading;
            accelerating = launchDistance > 0;
            nextTurn = snapshot.activeFrom();
        }
    }

    private final LightningSheeperEntity boss;
    private final LightningTeleportMovement teleports;
    private final Map<UUID, Long> nextHit = new HashMap<>();
    private final List<Funnel> funnels = new ArrayList<>();
    private double radius;
    private double height;
    private double movementRadius;
    private double speed;
    private double launchDistance;
    private double strength;
    private float damage;
    private int hitInterval;
    private int launchIntervalTicks;
    private int durationTicks;
    private int dissolveTicks;
    private int launchesRemaining;
    private long nextLaunchAt;
    private long landingAt = -1;
    @Nullable
    private Vec3 destination;

    LightningTornadoAttack(LightningSheeperEntity boss, LightningTeleportMovement teleports) {
        this.boss = boss;
        this.teleports = teleports;
    }

    List<LightningTornado> snapshots() {
        return funnels.stream().map(funnel -> funnel.snapshot).toList();
    }

    boolean isActive() {
        return isLaunching() || !funnels.isEmpty();
    }

    /** @return whether the boss is travelling to the launch position or still has funnels to launch */
    boolean isLaunching() {
        return launchesRemaining > 0;
    }

    /** Scales the number of independently moving funnels with the arena floor area. */
    void start(ServerLevel level, List<ServerPlayer> participants) {
        clear();
        double arenaRadius = boss.getCombatRadius();
        double floorArea = Math.PI * arenaRadius * arenaRadius;
        double density = LightningSheeperConfig.LIGHTNING_TORNADOS_PER_1000_BLOCKS.get();
        launchesRemaining = Mth.clamp((int) Math.round(floorArea * density / 1_000), 1,
                LightningTornado.MAX_FUNNELS_PER_ATTACK);
        radius = Math.min(LightningSheeperConfig.LIGHTNING_TORNADO_RADIUS.get(), arenaRadius * 0.35);
        // Keep even the wide end of the funnel behind the arena's entry band.
        double combatArea = arenaRadius - LightningArenaBoundaryDrag.entryInset(arenaRadius);
        movementRadius = Math.max(0, combatArea - radius * 1.35 - 0.3);
        height = LightningSheeperConfig.LIGHTNING_TORNADO_HEIGHT.get();
        speed = LightningSheeperConfig.LIGHTNING_TORNADO_SPEED.get() / 20;
        launchDistance = LightningSheeperConfig.LIGHTNING_TORNADO_LAUNCH_DISTANCE.get();
        strength = LightningSheeperConfig.LIGHTNING_TORNADO_STRENGTH.get();
        damage = LightningSheeperConfig.LIGHTNING_TORNADO_DAMAGE.get().floatValue();
        hitInterval = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_TORNADO_DAMAGE_INTERVAL.get());
        launchIntervalTicks = SheeperConfig.secondsToTicks(
                LightningSheeperConfig.LIGHTNING_TORNADO_LAUNCH_INTERVAL.get());
        durationTicks = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_TORNADO_DURATION.get());
        dissolveTicks = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_TORNADO_DISSOLVE.get());

        double preferredDistance = LightningSheeperConfig.LIGHTNING_TORNADO_TELEPORT_DISTANCE.get();
        double centerRadius = LightningSheeperConfig.LIGHTNING_TORNADO_TELEPORT_CENTER_RADIUS.get();
        destination = teleports.findPreferredAttackDestination(participants, preferredDistance, centerRadius);
        if (destination == null && centerRadius < boss.getMovementRadius()) {
            destination = teleports.findPreferredAttackDestination(participants, preferredDistance);
        }
        nextLaunchAt = level.getGameTime() + TURN_BEFORE_TELEPORT_TICKS;
        if (destination != null) {
            teleports.faceDestination(destination);
        }
    }

    /** Launches from the landing position once per interval, including a separate lifetime for each funnel. */
    private void tickLaunches(ServerLevel level, long now) {
        if (destination != null) {
            if (landingAt >= 0) {
                if (now < landingAt) {
                    return;
                }
                teleports.landPreferredAttackTravel(destination);
                landingAt = -1;
                nextLaunchAt = now;
            } else {
                teleports.faceDestination(destination);
                if (now < nextLaunchAt) {
                    return;
                }
                if (teleports.beginPreferredAttackTravel(destination)) {
                    landingAt = now + LightningSheeperEntity.ARC_TRAVEL_TICKS;
                    return;
                }
            }
            destination = null;
        }
        if (now >= nextLaunchAt) {
            launch(level, now);
            launchesRemaining--;
            nextLaunchAt = now + launchIntervalTicks;
        }
    }

    private void launch(ServerLevel level, long now) {
        Vec3 position = boss.position();
        double y = ground(level, position.x, position.z, position.y);
        double baseY = Double.isFinite(y) ? y
                : Mth.clamp(position.y, boss.getArenaMinimumY(), boss.getArenaMaximumY() - 1);
        Vec3 origin = new Vec3(position.x, baseY, position.z);
        long dissolveFrom = now + durationTicks;
        var snapshot = buildSnapshot(origin, Vec3.ZERO, now, now, dissolveFrom, dissolveFrom + dissolveTicks);
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double availableDistance = movementRadius + origin.subtract(center).horizontalDistance();
        double heading = boss.getRandom().nextDouble() * 2 * Math.PI;
        funnels.add(new Funnel(snapshot, heading, Math.min(launchDistance, availableDistance)));
        float volume = LightningSheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
        if (volume > 0) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.TRIDENT_RIPTIDE_1,
                    SoundSource.HOSTILE, volume, 0.62F);
        }
    }

    void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (isLaunching()) {
            tickLaunches(level, now);
        }
        funnels.removeIf(funnel -> now >= funnel.snapshot.endTick());
        for (Funnel funnel : funnels) {
            LightningTornado current = funnel.snapshot;
            Vec3 base = current.position();
            if (now > current.activeFrom()) {
                base = move(level, funnel, now);
            }
            funnel.snapshot = buildSnapshot(base, base.subtract(current.position()), now, current.activeFrom(),
                    current.dissolveFrom(), current.endTick());
            if (now > current.activeFrom() && !current.isDissolving(now) && (now - current.activeFrom()) % 7 == 0) {
                showDebris(level, base);
                igniteStrands(level, funnel.snapshot, now);
            }
        }
    }

    /** Checks the electrical strands of the tornado rather than burning the whole wind volume. */
    private void igniteStrands(ServerLevel level, LightningTornado tornado, long now) {
        var storm = LightningStormData.get(level);
        for (int strand = 0; strand < LightningTornado.STRANDS; strand += 3) {
            Vec3 previous = tornado.strandPoint(tornado.position(), now, strand, 0);
            for (int step = 1; step <= LightningTornado.STRAND_STEPS; step++) {
                Vec3 point = tornado.strandPoint(tornado.position(), now, strand, step);
                if (tornado.hasLightning(strand, step, now)) {
                    storm.igniteLeaves(level, boss.getUUID(), new BlockPos(point));
                    storm.igniteLeaves(level, boss.getUUID(), new BlockPos(previous.lerp(point, 0.5)));
                }
                previous = point;
            }
        }
    }

    /** Turns smoothly, follows nearby ground and slows down as the funnel dissolves. */
    private Vec3 move(ServerLevel level, Funnel funnel, long now) {
        LightningTornado current = funnel.snapshot;
        Vec3 base = current.position();
        if (funnel.accelerating && base.subtract(funnel.launchOrigin).horizontalDistanceSqr()
                >= funnel.launchDistance * funnel.launchDistance) {
            funnel.accelerating = false;
        }
        if (!funnel.accelerating && now >= funnel.nextTurn && !current.isDissolving(now)) {
            funnel.desiredHeading += (boss.getRandom().nextDouble() - 0.5) * Math.PI * 0.95;
            funnel.nextTurn = now + 30 + boss.getRandom().nextInt(28);
        }
        double change = Math.toRadians(Mth.wrapDegrees(Math.toDegrees(funnel.desiredHeading - funnel.heading)));
        funnel.heading += Mth.clamp(change, -0.12, 0.12);
        double travel = speed * current.fadeAt(now);
        if (funnel.accelerating && !current.isDissolving(now)) {
            travel *= LAUNCH_SPEED_MULTIPLIER;
        }
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        Vec3 next = base.add(Math.cos(funnel.heading) * travel, 0, Math.sin(funnel.heading) * travel);
        double nextRadius = next.subtract(center).horizontalDistanceSqr();
        if (nextRadius > movementRadius * movementRadius && nextRadius >= base.subtract(center).horizontalDistanceSqr()) {
            funnel.desiredHeading = Math.atan2(center.z - base.z, center.x - base.x);
            return base;
        }
        double ground = ground(level, next.x, next.z, base.y);
        if (!Double.isFinite(ground) || Math.abs(ground - base.y) > 3.5) {
            funnel.desiredHeading = funnel.heading + 1.4;
            return base;
        }
        return new Vec3(next.x, base.y + Mth.clamp(ground - base.y, -0.24, 0.24), next.z);
    }

    private double ground(ServerLevel level, double x, double z, double reference) {
        int min = boss.getArenaMinimumY();
        int max = boss.getArenaMaximumY() - 2;
        double water = LightningRingSurface.findWater(level, x, z, min, max);
        if (Double.isFinite(water) && Math.abs(water - reference) <= 2.5) {
            return water;
        }
        return LightningRingSurface.find(level, x, z, reference, min, max);
    }

    private LightningTornado buildSnapshot(Vec3 base, Vec3 motion, long now, long activeFrom, long dissolveFrom, long end) {
        double availableHeight = Math.min(height, boss.getArenaMaximumY() - base.y);
        return new LightningTornado(boss.getUUID(), boss.getArenaCenter(), boss.getCombatRadius(), boss.getArenaMinimumY(),
                boss.getArenaMaximumY(), base, motion, radius, availableHeight, strength, now, activeFrom, dissolveFrom, end);
    }

    /** Uses the published funnel snapshot so overlap cannot add another funnel's effect. */
    void applyPlayerEffects(ServerLevel level, List<ServerPlayer> players) {
        if (funnels.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (ServerPlayer player : players) {
            if (!player.isAlive() || player.isPassenger()) {
                continue;
            }
            LightningTornado funnel = LightningTornadoMotion.controllingFunnel(player);
            if (funnel == null && strength == 0 && damage > 0) {
                // Disabling wind strength still allows contact damage from a funnel.
                funnel = funnels.stream().map(candidate -> candidate.snapshot)
                        .filter(candidate -> !candidate.isDissolving(now)
                                && candidate.exposure(player.position(), now) >= 0.15
                                && LightningTornado.hasClearPath(level, player, candidate.position()))
                        .findFirst().orElse(null);
            }
            if (funnel == null || !boss.getUUID().equals(funnel.owner())) {
                continue;
            }
            if (funnel.isDissolving(now)) {
                if (funnel.supportsDescent(player.position(), now)) {
                    ArenaFallProtection.protect(player);
                }
            } else if (damage > 0 && funnel.exposure(player.position(), now) >= 0.15) {
                damagePlayer(player, now);
            }
        }
    }

    private void damagePlayer(ServerPlayer player, long now) {
        UUID id = player.getUUID();
        if (nextHit.getOrDefault(id, 0L) > now) {
            return;
        }
        nextHit.put(id, now + hitInterval);
        Vec3 motion = player.getDeltaMovement();
        if (LightningAreaDamage.hurtPlayer(boss, player, damage)) {
            player.setDeltaMovement(motion);
        }
    }

    /** Uses the local terrain texture for debris without modifying blocks. */
    private void showDebris(ServerLevel level, Vec3 base) {
        BlockPos below = new BlockPos(base.x, base.y - 0.12, base.z);
        var state = level.getBlockState(below);
        if (!state.isAir() && state.getFluidState().isEmpty()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    base.x, base.y + 0.8, base.z, 9, radius * 0.40, 0.45, radius * 0.40, 0.11);
        }
    }

    void clear() {
        if (landingAt >= 0) {
            boss.clearTravelArc();
        }
        launchesRemaining = 0;
        landingAt = -1;
        destination = null;
        funnels.clear();
        nextHit.clear();
    }
}
