package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.network.LightningStrikeMarkerPacket;
import de.codemorra.sheeper.network.LightningWaterChargePacket;
import de.codemorra.sheeper.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/** Runs the arena's random lightning independently of movement and special attacks. */
final class LightningAmbientStrikes {
    private static final int MAX_PENDING = 48;
    private static final int WARNING_REFRESH_TICKS = 5;
    // Ambient bolts have no attacking entity, so normal hurt physics cannot shove players away from the boss.
    private static final DamageSource STRIKE_DAMAGE = new DamageSource("lightningBolt").setScalesWithDifficulty();

    private record Strike(Vec3 position, long impactTick, double centerRadius, double outerRadius, double height,
            boolean water) {
    }

    private record GroundSparks(Strike strike, long endTick, int speedTicks, Set<UUID> inside, Set<UUID> rewarded) {
    }

    private final LightningSheeperEntity boss;
    private final List<Strike> pending = new ArrayList<>();
    private final List<GroundSparks> groundSparks = new ArrayList<>();
    private int intervalTicks;

    LightningAmbientStrikes(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    /** Doubles random strikes during the charge and reserves the calm window for the special attack. */
    void tick(ServerLevel level, List<ServerPlayer> participants, boolean fighting, int intensifiedTicksRemaining) {
        int count = LightningSheeperConfig.LIGHTNING_AMBIENT_STRIKE_COUNT.get();
        if (count == 0) {
            clearEffects();
            intervalTicks = 0;
            return;
        }

        long now = level.getGameTime();
        tickGroundSparks(fighting ? participants : List.of(), now);
        if (!fighting) {
            pending.clear();
            return;
        }
        var iterator = pending.iterator();
        while (iterator.hasNext()) {
            Strike strike = iterator.next();
            long remaining = strike.impactTick() - now;
            if (remaining <= 0) {
                impact(level, strike, participants);
                iterator.remove();
            } else if (remaining % WARNING_REFRESH_TICKS == 0) {
                showWarning(level, strike, remaining);
            }
        }
        int warningTicks = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_AMBIENT_WARNING.get());
        boolean intensified = intensifiedTicksRemaining > 0;
        if (--intervalTicks > 0 || intensified && warningTicks >= intensifiedTicksRemaining) {
            return;
        }
        intervalTicks = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_AMBIENT_INTERVAL.get());
        long impactTick = now + warningTicks;
        double centerRadius = LightningSheeperConfig.LIGHTNING_AMBIENT_CENTER_RADIUS.get();
        double outerRadius = centerRadius + LightningSheeperConfig.LIGHTNING_AMBIENT_EDGE_WIDTH.get();
        double height = LightningSheeperConfig.LIGHTNING_AMBIENT_HIT_HEIGHT.get();
        int multiplier = intensified ? 2 : 1;
        for (int index = 0; index < count * multiplier && pending.size() < MAX_PENDING * multiplier; index++) {
            randomStrike(level, impactTick, centerRadius, outerRadius, height).ifPresent(strike -> {
                pending.add(strike);
                showWarning(level, strike, impactTick - now);
            });
        }
    }

    /** Samples area uniformly with no player position in the selection, and avoids unloaded terrain. */
    private Optional<Strike> randomStrike(ServerLevel level, long impactTick, double centerRadius, double outerRadius,
            double height) {
        BlockPos center = boss.getArenaCenter();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(boss.getRandom().nextDouble()) * boss.getCombatRadius();
            double x = center.getX() + 0.5 + Math.cos(angle) * radius;
            double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
            double waterX = Mth.floor(x) + 0.5;
            double waterZ = Mth.floor(z) + 0.5;
            if (Math.hypot(waterX - center.getX() - 0.5, waterZ - center.getZ() - 0.5) <= boss.getCombatRadius()) {
                double water = waterSurface(level, waterX, waterZ);
                if (Double.isFinite(water)) {
                    return Optional.of(new Strike(new Vec3(waterX, water, waterZ), impactTick, centerRadius, outerRadius,
                            height, true));
                }
            }
            double y = drySurface(level, x, z, center.getY());
            if (Double.isFinite(y)) {
                return Optional.of(new Strike(new Vec3(x, y, z), impactTick, centerRadius, outerRadius, height,
                        false));
            }
        }
        return Optional.empty();
    }

    private double drySurface(ServerLevel level, double x, double z, double referenceY) {
        double surface = LightningRingSurface.find(level, x, z, referenceY,
                boss.getArenaMinimumY(), boss.getArenaMaximumY() - 1);
        if (!Double.isFinite(surface) || !level.getFluidState(new BlockPos(x, surface + 0.05, z)).isEmpty()) {
            return Double.NaN;
        }
        return surface;
    }

    /** Water must be exposed within the arena so the warning and the impact share the same surface. */
    private double waterSurface(ServerLevel level, double x, double z) {
        double water = LightningRingSurface.findWater(level, x, z, boss.getArenaMinimumY(), boss.getArenaMaximumY() - 1);
        return boss.isWithinArenaHeight(water) ? water : Double.NaN;
    }

    /** Refreshes brief client markers to keep a cancelled strike from leaving a stale warning. */
    private void showWarning(ServerLevel level, Strike strike, long remaining) {
        showMarker(level, strike, (int) Math.min(remaining, WARNING_REFRESH_TICKS + 1), false);
    }

    private void showMarker(ServerLevel level, Strike strike, int duration, boolean impact) {
        Vec3 pos = strike.position();
        int minY = Math.max(boss.getArenaMinimumY(), Mth.floor(pos.y) - 1);
        int maxY = Math.min(boss.getArenaMaximumY() - 1,
                Mth.floor(pos.y + (strike.water() ? 1 : strike.height())));
        var marker = new LightningStrikeMarkerPacket(level.dimension().location(), boss.getArenaCenter(),
                boss.getCombatRadius(), pos, strike.centerRadius(), strike.outerRadius(), minY, maxY,
                strike.impactTick(), duration, strike.water(), impact);
        for (ServerPlayer observer : level.players()) {
            if (observer.position().distanceToSqr(pos) <= 48 * 48) {
                ModNetwork.sendLightningStrikeMarker(observer, marker);
            }
        }
    }

    private void impact(ServerLevel level, Strike strike, List<ServerPlayer> participants) {
        Vec3 position = strike.position();
        double surface = strike.water() ? waterSurface(level, position.x, position.z)
                : drySurface(level, position.x, position.z, position.y);
        if (!Double.isFinite(surface) || Math.abs(surface - position.y) > 0.01) {
            // Terrain changes can invalidate a warning; a bolt must not relocate onto another surface.
            return;
        }
        LightningStrikeEffect.spawnWeatherBolt(level, position, boss.getStormCloudY(position), true);
        if (strike.water()) {
            showMarker(level, strike, 4, true);
            BlockPos water = new BlockPos(position.x, Math.nextDown(position.y), position.z);
            dischargeThroughWater(level, water, participants);
            return;
        }
        float centerDamage = LightningSheeperConfig.LIGHTNING_AMBIENT_CENTER_DAMAGE.get().floatValue();
        float edgeDamage = (float) (centerDamage
                * LightningSheeperConfig.LIGHTNING_AMBIENT_EDGE_DAMAGE_PERCENT.get() / 100);
        double speedSeconds = LightningSheeperConfig.LIGHTNING_AMBIENT_SPEED_DURATION.get();
        int speedTicks = SheeperConfig.secondsToTicks(speedSeconds);
        int lingeringTicks = SheeperConfig.secondsToTicks(
                LightningSheeperConfig.LIGHTNING_AMBIENT_GROUND_SPARK_DURATION.get());
        showMarker(level, strike, Math.max(3, lingeringTicks), true);
        var inside = new HashSet<UUID>();
        for (ServerPlayer player : participants) {
            if (!LightningAreaDamage.contains(boss, player, position, strike.outerRadius(), strike.height())) {
                continue;
            }
            inside.add(player.getUUID());
            boolean center = LightningAreaDamage.contains(boss, player, position, strike.centerRadius(), strike.height());
            float damage = center ? centerDamage : edgeDamage;
            if (damage > 0) {
                player.hurt(STRIKE_DAMAGE, damage);
            }
            if (!center && speedTicks > 0 && player.isAlive()) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speedTicks, 0), boss);
            }
        }
        if (lingeringTicks > 0) {
            groundSparks.add(new GroundSparks(strike, level.getGameTime() + lingeringTicks,
                    SheeperConfig.secondsToTicks(speedSeconds * 0.5), inside, new HashSet<>()));
        }
    }

    /** Rewards new entrants once while preserving the impact rules for players already in the area. */
    private void tickGroundSparks(List<ServerPlayer> participants, long now) {
        var iterator = groundSparks.iterator();
        while (iterator.hasNext()) {
            GroundSparks zone = iterator.next();
            if (now >= zone.endTick()) {
                iterator.remove();
                continue;
            }
            Strike strike = zone.strike();
            if (zone.speedTicks() <= 0) {
                continue;
            }
            var currentlyInside = new HashSet<UUID>();
            for (ServerPlayer player : participants) {
                if (!player.isAlive() || !LightningAreaDamage.contains(boss, player, strike.position(),
                        strike.outerRadius(), strike.height())) {
                    continue;
                }
                UUID id = player.getUUID();
                currentlyInside.add(id);
                if (!zone.inside().contains(id) && zone.rewarded().add(id)) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, zone.speedTicks(), 0), boss);
                }
            }
            zone.inside().clear();
            zone.inside().addAll(currentlyInside);
        }
    }

    private void dischargeThroughWater(ServerLevel level, BlockPos strike, List<ServerPlayer> participants) {
        float damage = LightningSheeperConfig.LIGHTNING_AMBIENT_WATER_DAMAGE.get().floatValue();
        if (damage > 0) {
            var conduction = new LightningWaterConduction(level, boss);
            for (ServerPlayer player : participants) {
                if (player.isAlive() && conduction.reaches(strike, player)) {
                    player.hurt(STRIKE_DAMAGE, damage);
                }
            }
        }
        BlockPos center = boss.getArenaCenter();
        double radius = boss.getCombatRadius();
        double visibleRadius = radius + 48;
        var packet = new LightningWaterChargePacket(level.dimension().location(), strike, center, radius,
                boss.getArenaMinimumY(), boss.getArenaMaximumY());
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - center.getX() - 0.5;
            double dz = player.getZ() - center.getZ() - 0.5;
            if (dx * dx + dz * dz <= visibleRadius * visibleRadius) {
                ModNetwork.sendLightningWaterCharge(player, packet);
            }
        }
    }

    private void clearEffects() {
        pending.clear();
        groundSparks.clear();
    }

    void reset() {
        clearEffects();
        intervalTicks = 0;
    }

    /** Only the clocks survive reload so an interrupted warning cannot hit an unprepared player. */
    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("IntervalTicks", intervalTicks);
        return tag;
    }

    void load(CompoundTag tag) {
        reset();
        intervalTicks = Mth.clamp(tag.getInt("IntervalTicks"), 0, 100);
    }
}
