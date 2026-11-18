package de.codemorra.sheeper.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/**
 * Independent configurable close-range timers for every nearby arena player,
 * including those behind the boss. Leaving the boss's reach resets the timer.
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
     * Advances only nearby participants' timers. The entity pauses calls for NoAI
     * so disabling AI does not consume a player's remaining reaction time.
     *
     * @param participants current arena players, excluding dead and outside players
     */
    void tick(List<ServerPlayer> participants) {
        double radius = SheeperConfig.WATER_PROXIMITY_PUSH_RADIUS.get();
        double horizontal = SheeperConfig.WATER_PROXIMITY_PUSH_STRENGTH.get();
        double upward = SheeperConfig.WATER_PROXIMITY_PUSH_UPWARD_STRENGTH.get();
        double damage = SheeperConfig.WATER_PROXIMITY_PUSH_DAMAGE.get();
        if (radius == 0 || horizontal == 0 && upward == 0 && damage == 0) {
            remainingTicks.clear();
            return;
        }
        Set<UUID> nearby = new HashSet<>();
        int delay = SheeperConfig.secondsToTicks(SheeperConfig.WATER_PROXIMITY_PUSH_DELAY.get());
        for (ServerPlayer player : participants) {
            if (boss.distanceToSqr(player) > radius * radius) {
                continue;
            }
            UUID id = player.getUUID();
            nearby.add(id);
            int remaining = remainingTicks.computeIfAbsent(id, ignored -> delay) - 1;
            if (remaining <= 0) {
                push(player, horizontal, upward, damage);
                remaining = delay;
            }
            remainingTicks.put(id, remaining);
        }
        remainingTicks.keySet().retainAll(nearby);
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
        player.setDeltaMovement(velocity.x, upward, velocity.z);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.hurtMarked = false;
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
     * @param tag saved per-player timers, revalidated against arena membership on
     *            the next tick
     */
    void load(ListTag tag) {
        remainingTicks.clear();
        for (Tag value : tag) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                remainingTicks.put(entry.getUUID("Player"), Mth.clamp(entry.getInt("Ticks"), 1,
                        SheeperConfig.secondsToTicks(SheeperConfig.WATER_PROXIMITY_PUSH_DELAY.get())));
            }
        }
    }
}
