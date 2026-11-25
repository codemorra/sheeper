package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.particle.LightningRingOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
    // Ambient bolts have no attacking entity, so normal hurt physics cannot shove players away from the boss.
    private static final DamageSource STRIKE_DAMAGE = new DamageSource("lightningBolt").setScalesWithDifficulty();

    private record Strike(Vec3 position, long impactTick, double centerRadius, double outerRadius, double height) {
    }

    private final LightningSheeperEntity boss;
    private final List<Strike> pending = new ArrayList<>();
    private int intervalTicks;

    LightningAmbientStrikes(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    /** Advances only while the arena has participants, preserving the encounter clock through short exits. */
    void tick(ServerLevel level, List<ServerPlayer> participants) {
        int count = LightningSheeperConfig.LIGHTNING_AMBIENT_STRIKE_COUNT.get();
        if (count == 0) {
            clearWarnings();
            intervalTicks = 0;
            return;
        }

        long now = level.getGameTime();
        var iterator = pending.iterator();
        while (iterator.hasNext()) {
            Strike strike = iterator.next();
            long remaining = strike.impactTick() - now;
            if (remaining <= 0) {
                impact(level, strike, participants);
                iterator.remove();
            } else if (remaining % 2 == 0) {
                showWarning(level, strike);
            }
        }
        if (--intervalTicks > 0) {
            return;
        }
        intervalTicks = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_AMBIENT_INTERVAL.get());
        long impactTick = now
                + SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_AMBIENT_WARNING.get());
        double centerRadius = LightningSheeperConfig.LIGHTNING_AMBIENT_CENTER_RADIUS.get();
        double outerRadius = centerRadius + LightningSheeperConfig.LIGHTNING_AMBIENT_EDGE_WIDTH.get();
        double height = LightningSheeperConfig.LIGHTNING_AMBIENT_HIT_HEIGHT.get();
        for (int index = 0; index < count && pending.size() < MAX_PENDING; index++) {
            randomSurface(level).ifPresent(position -> {
                var strike = new Strike(position, impactTick, centerRadius, outerRadius, height);
                pending.add(strike);
                showWarning(level, strike);
                level.sendParticles(ParticleTypes.END_ROD, position.x, position.y + 0.12, position.z,
                        4, 0.15, 0.05, 0.15, 0.005);
            });
        }
    }

    /** Samples area uniformly with no player position in the selection, and avoids unloaded terrain. */
    private Optional<Vec3> randomSurface(ServerLevel level) {
        BlockPos center = boss.getArenaCenter();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(boss.getRandom().nextDouble()) * boss.getCombatRadius();
            double x = center.getX() + 0.5 + Math.cos(angle) * radius;
            double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
            double y = drySurface(level, x, z, center.getY());
            if (Double.isFinite(y)) {
                return Optional.of(new Vec3(x, y, z));
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

    /** Keeps the damage ring clearer than the softer warning around the speed zone. */
    private void showWarning(ServerLevel level, Strike strike) {
        showRing(level, strike, strike.centerRadius(), 0.45F, 0.48F);
        showRing(level, strike, strike.outerRadius(), 0.16F, 0.23F);
    }

    private void showRing(ServerLevel level, Strike strike, double radius, float width, float opacity) {
        Vec3 position = strike.position();
        BlockPos center = boss.getArenaCenter();
        int minY = Math.max(boss.getArenaMinimumY(), Mth.floor(position.y));
        int maxY = Math.min(boss.getArenaMaximumY() - 1, Mth.floor(position.y + strike.height()));
        var options = new LightningRingOptions((float) radius, width, 0.4F, minY, maxY,
                center.getX() + 0.5, center.getZ() + 0.5, (float) boss.getCombatRadius(), opacity);
        level.sendParticles(options, position.x, position.y, position.z, 1, 0, 0, 0, 0);
    }

    private void impact(ServerLevel level, Strike strike, List<ServerPlayer> participants) {
        Vec3 position = strike.position();
        double surface = drySurface(level, position.x, position.z, position.y);
        if (!Double.isFinite(surface) || Math.abs(surface - position.y) > 0.01) {
            // Terrain changes can invalidate a warning; a bolt must not relocate onto another surface.
            return;
        }
        LightningStrikeEffect.spawnWeatherBolt(level, position, boss.getStormCloudY(position), true);
        float centerDamage = LightningSheeperConfig.LIGHTNING_AMBIENT_CENTER_DAMAGE.get().floatValue();
        float edgeDamage = (float) (centerDamage
                * LightningSheeperConfig.LIGHTNING_AMBIENT_EDGE_DAMAGE_PERCENT.get() / 100);
        int speedTicks = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_AMBIENT_SPEED_DURATION.get());
        for (ServerPlayer player : participants) {
            if (!LightningAreaDamage.contains(boss, player, position, strike.outerRadius(), strike.height())) {
                continue;
            }
            boolean center = LightningAreaDamage.contains(boss, player, position, strike.centerRadius(), strike.height());
            float damage = center ? centerDamage : edgeDamage;
            if (damage > 0) {
                player.hurt(STRIKE_DAMAGE, damage);
            }
            if (!center && speedTicks > 0 && player.isAlive()) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speedTicks, 0), boss);
            }
        }
    }

    void clearWarnings() {
        pending.clear();
    }

    void reset() {
        clearWarnings();
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
