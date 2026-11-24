package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.world.WaterArenaPushProtection;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Each exposed player's timer can trigger a discharge against everyone in reach
 * with a clear path from the boss. Leaving reach or taking cover clears that
 * player's progress.
 */
final class WaterProximityPush {
    private final WaterSheeperEntity boss;
    private final Map<UUID, Integer> remainingTicks = new HashMap<>();

    /**
     * @param boss owner whose position and configured strengths determine each push
     */
    WaterProximityPush(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    /**
     * Advances timers for nearby participants who are exposed to the discharge.
     * The entity pauses calls for NoAI so disabling AI preserves reaction time.
     *
     * @param participants current arena players, excluding dead and outside players
     */
    void tick(List<ServerPlayer> participants) {
        double radius = WaterSheeperConfig.WATER_PROXIMITY_PUSH_RADIUS.get();
        double horizontal = WaterSheeperConfig.WATER_PROXIMITY_PUSH_STRENGTH.get();
        double upward = WaterSheeperConfig.WATER_PROXIMITY_PUSH_UPWARD_STRENGTH.get();
        double damage = WaterSheeperConfig.WATER_PROXIMITY_PUSH_DAMAGE.get();
        if (!(boss.level instanceof ServerLevel level) || radius == 0
                || horizontal == 0 && upward == 0 && damage == 0) {
            remainingTicks.clear();
            return;
        }
        Vec3 origin = boss.getBoundingBox().getCenter();
        Set<UUID> nearby = new HashSet<>();
        List<ServerPlayer> affected = new ArrayList<>();
        boolean discharged = false;
        int delay = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_PROXIMITY_PUSH_DELAY.get());
        for (ServerPlayer player : participants) {
            if (!boss.containsAttackPoint(player.position()) || boss.distanceToSqr(player) > radius * radius) {
                continue;
            }
            // Check cover before collecting targets or advancing their reaction timers.
            var cover = WaterAttackBlocks.clipLoaded(boss, level, origin, player.getBoundingBox().getCenter());
            if (cover == null || cover.getType() != HitResult.Type.MISS) {
                continue;
            }
            UUID id = player.getUUID();
            nearby.add(id);
            affected.add(player);
            int remaining = remainingTicks.computeIfAbsent(id, ignored -> delay) - 1;
            if (remaining <= 0) {
                discharged = true;
            }
            remainingTicks.put(id, remaining);
        }
        remainingTicks.keySet().retainAll(nearby);
        if (discharged) {
            // Resolve the complete group before applying damage or motion so the
            // participant order cannot change who is caught by this discharge.
            remainingTicks.replaceAll((id, ticks) -> delay);
            for (ServerPlayer player : affected) {
                push(player, horizontal, upward, damage);
            }
            showPushBubbles(level, origin);
        }
    }

    /**
     * Applies one damage hit per discharge, then synchronizes the configured
     * launch. Damage immunity does not suppress the existing defensive knockback.
     */
    private void push(ServerPlayer player, double horizontal, double upward, double damage) {
        if (damage > 0) {
            player.hurt(DamageSource.mobAttack(boss), (float) damage);
        }
        if (!player.isAlive()) {
            return;
        }
        Vec3 away = new Vec3(player.getX() - boss.getX(), 0, player.getZ() - boss.getZ());
        if (away.lengthSqr() < 1.0E-8D) {
            away = new Vec3(1, 0, 0);
        }
        Vec3 velocity = away.normalize().scale(horizontal);
        WaterArenaPushProtection.apply(player, new Vec3(velocity.x, upward, velocity.z));
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.hurtMarked = false;
    }

    /**
     * Surrounds the entire boss with one outward bubble burst per discharge,
     * regardless of how many players are caught in it.
     */
    private void showPushBubbles(ServerLevel level, Vec3 origin) {
        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 0.8F, 0.8F);
        double radius = boss.getBbWidth() * 0.5D + 0.25D;
        double halfHeight = boss.getBbHeight() * 0.5D;
        for (int layer = 0; layer < 4; layer++) {
            double y = origin.y + (layer / 3.0D - 0.5D) * halfHeight * 2;
            for (int index = 0; index < 24; index++) {
                double angle = (index + layer * 0.5D) * Math.PI * 2 / 24;
                double x = Math.cos(angle);
                double z = Math.sin(angle);
                // Zero count preserves the outward velocity instead of randomizing it.
                level.sendParticles(ParticleTypes.BUBBLE,
                        origin.x + x * radius, y, origin.z + z * radius,
                        0, x * 1.5D, 0.15D, z * 1.5D, 1.0D);
            }
        }
        level.sendParticles(ParticleTypes.BUBBLE, origin.x, origin.y, origin.z,
                48, radius * 0.7D, halfHeight * 0.7D, radius * 0.7D, 0.35D);
    }

    /** Clears all per-player progress when the encounter fully resets. */
    void reset() {
        remainingTicks.clear();
    }

    /** @return per-player proximity timers for entity persistence */
    ListTag save() {
        ListTag tag = new ListTag();
        remainingTicks.forEach((id, ticks) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Ticks", ticks);
            tag.add(entry);
        });
        return tag;
    }

    /**
     * @param tag saved per-player timers, revalidated against reach and cover
     *            on the next tick
     */
    void load(ListTag tag) {
        remainingTicks.clear();
        for (Tag value : tag) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                remainingTicks.put(entry.getUUID("Player"), Mth.clamp(entry.getInt("Ticks"), 1,
                        SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_PROXIMITY_PUSH_DELAY.get())));
            }
        }
    }
}
