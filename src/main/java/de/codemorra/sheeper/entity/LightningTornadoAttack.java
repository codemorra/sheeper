package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.LightningArenaBoundaryDrag;
import de.codemorra.sheeper.world.LightningTornado;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.phys.Vec3;

/** Sends several independently moving funnels across the arena before they gradually dissipate. */
final class LightningTornadoAttack {
    private static final class Funnel {
        private LightningTornado snapshot;
        private double heading;
        private double desiredHeading;
        private long nextTurn;

        private Funnel(LightningTornado snapshot, double heading) {
            this.snapshot = snapshot;
            this.heading = heading;
            desiredHeading = heading;
            nextTurn = snapshot.activeFrom() + 42;
        }
    }

    private final LightningSheeperEntity boss;
    private final Map<UUID, Long> nextHit = new HashMap<>();
    private final List<Funnel> funnels = new ArrayList<>();
    private double radius;
    private double height;
    private double movementRadius;
    private double speed;
    private double strength;
    private float damage;
    private int hitInterval;
    LightningTornadoAttack(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    List<LightningTornado> snapshots() {
        return funnels.stream().map(funnel -> funnel.snapshot).toList();
    }

    boolean isActive() {
        return !funnels.isEmpty();
    }

    /** Scales the number of independently moving funnels with the arena floor area. */
    void start(ServerLevel level, List<ServerPlayer> participants) {
        clear();
        double arenaRadius = boss.getCombatRadius();
        double floorArea = Math.PI * arenaRadius * arenaRadius;
        double density = LightningSheeperConfig.LIGHTNING_TORNADOS_PER_1000_BLOCKS.get();
        int count = Mth.clamp((int) Math.round(floorArea * density / 1_000), 1,
                LightningTornado.MAX_FUNNELS_PER_ATTACK);
        radius = Math.min(LightningSheeperConfig.LIGHTNING_TORNADO_RADIUS.get(), arenaRadius * 0.35);
        // Keep even the wide end of the funnel behind the arena's entry band.
        double combatArea = arenaRadius - LightningArenaBoundaryDrag.entryInset(arenaRadius);
        movementRadius = Math.max(0, combatArea - radius * 1.35 - 0.3);
        height = LightningSheeperConfig.LIGHTNING_TORNADO_HEIGHT.get();
        speed = LightningSheeperConfig.LIGHTNING_TORNADO_SPEED.get() / 20;
        strength = LightningSheeperConfig.LIGHTNING_TORNADO_STRENGTH.get();
        damage = LightningSheeperConfig.LIGHTNING_TORNADO_DAMAGE.get().floatValue();
        hitInterval = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_TORNADO_DAMAGE_INTERVAL.get());

        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        List<ServerPlayer> targets = participants.stream().sorted(Comparator.comparingDouble(boss::distanceToSqr)).toList();
        long now = level.getGameTime();
        long activeFrom = now + SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_TORNADO_WARNING.get());
        long dissolveFrom = activeFrom + SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_TORNADO_DURATION.get());
        long end = dissolveFrom + SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_TORNADO_DISSOLVE.get());
        float volume = LightningSheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
        for (int index = 0; index < count; index++) {
            Vec3 focus = index < targets.size() ? targets.get(index).position() : null;
            Vec3 start = findStart(level, focus, center);
            if (start == null) {
                continue;
            }
            double heading = focus == null ? boss.getRandom().nextDouble() * 2 * Math.PI
                    : Math.atan2(focus.z - start.z, focus.x - start.x);
            var snapshot = buildSnapshot(start, Vec3.ZERO, now, activeFrom, dissolveFrom, end);
            funnels.add(new Funnel(snapshot, heading));
            if (volume > 0) {
                level.playSound(null, start.x, start.y, start.z, SoundEvents.TRIDENT_RIPTIDE_1,
                        SoundSource.HOSTILE, volume, 0.62F);
            }
        }
    }

    @Nullable
    private Vec3 findStart(ServerLevel level, @Nullable Vec3 focus, Vec3 center) {
        for (int attempt = 0; attempt < 40; attempt++) {
            boolean near = focus != null && attempt < 14;
            double angle = boss.getRandom().nextDouble() * 2 * Math.PI;
            double distance = near ? radius + 4 + boss.getRandom().nextDouble() * 4
                    : Math.sqrt(boss.getRandom().nextDouble()) * movementRadius;
            Vec3 point = (near ? focus : center).add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            double spacing = radius * 2.5;
            if (point.subtract(center).horizontalDistanceSqr() > movementRadius * movementRadius
                    || funnels.stream().anyMatch(funnel -> point.subtract(funnel.snapshot.position())
                            .horizontalDistanceSqr() < spacing * spacing)) {
                continue;
            }
            double y = ground(level, point.x, point.z, point.y);
            if (Double.isFinite(y)) {
                return new Vec3(point.x, y, point.z);
            }
        }
        return null;
    }

    void tick(ServerLevel level, List<ServerPlayer> participants) {
        if (funnels.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        if (now >= funnels.get(0).snapshot.endTick()) {
            clear();
            return;
        }
        for (Funnel funnel : funnels) {
            LightningTornado current = funnel.snapshot;
            Vec3 base = current.position();
            if (now >= current.activeFrom()) {
                base = move(level, funnel, now);
            }
            funnel.snapshot = buildSnapshot(base, base.subtract(current.position()), now, current.activeFrom(),
                    current.dissolveFrom(), current.endTick());
            if (current.isDissolving(now)) {
                softenFalls(level, funnel.snapshot, participants, now);
            } else if (now >= current.activeFrom()) {
                damagePlayers(level, funnel.snapshot, participants, now);
                if ((now - current.activeFrom()) % 7 == 0) {
                    showDebris(level, base);
                }
            }
        }
        boss.lookAt(EntityAnchorArgument.Anchor.EYES, funnels.get(0).snapshot.position().add(0, 1.2, 0));
        boss.setYHeadRot(boss.getYRot());
    }

    /** Turns smoothly, follows nearby ground and slows down as the funnel dissolves. */
    private Vec3 move(ServerLevel level, Funnel funnel, long now) {
        LightningTornado current = funnel.snapshot;
        Vec3 base = current.position();
        if (now >= funnel.nextTurn && !current.isDissolving(now)) {
            funnel.desiredHeading += (boss.getRandom().nextDouble() - 0.5) * Math.PI * 0.95;
            funnel.nextTurn = now + 30 + boss.getRandom().nextInt(28);
        }
        double change = Math.toRadians(Mth.wrapDegrees(Math.toDegrees(funnel.desiredHeading - funnel.heading)));
        funnel.heading += Mth.clamp(change, -0.12, 0.12);
        double travel = speed * current.fadeAt(now);
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        Vec3 next = base.add(Math.cos(funnel.heading) * travel, 0, Math.sin(funnel.heading) * travel);
        if (next.subtract(center).horizontalDistanceSqr() > movementRadius * movementRadius) {
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

    /** Lets vanilla count the free fall from the point where controlled descent ends. */
    private void softenFalls(ServerLevel level, LightningTornado funnel, List<ServerPlayer> players, long now) {
        for (ServerPlayer player : players) {
            if (player.isAlive() && !player.isPassenger() && funnel.supportsDescent(player.position(), now)
                    && LightningTornado.hasClearPath(level, player, funnel.position())) {
                player.resetFallDistance();
            }
        }
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
        return new LightningTornado(boss.getArenaCenter(), boss.getCombatRadius(), boss.getArenaMinimumY(),
                boss.getArenaMaximumY(), base, motion, radius, availableHeight, strength, now, activeFrom, dissolveFrom, end);
    }

    private void damagePlayers(ServerLevel level, LightningTornado funnel, List<ServerPlayer> players, long now) {
        if (damage <= 0) {
            return;
        }
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            if (!player.isAlive() || nextHit.getOrDefault(id, 0L) > now || funnel.exposure(player.position(), now) < 0.15
                    || !LightningTornado.hasClearPath(level, player, funnel.position())) {
                continue;
            }
            nextHit.put(id, now + hitInterval);
            Vec3 motion = player.getDeltaMovement();
            if (player.hurt(new EntityDamageSource("lightningBolt", boss), damage)) {
                player.setDeltaMovement(motion);
            }
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
        funnels.clear();
        nextHit.clear();
    }
}
