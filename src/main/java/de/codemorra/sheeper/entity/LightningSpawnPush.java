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
import net.minecraft.world.phys.Vec3;

/**
 * The expanding preparation wave schedules one flat, damage-free push per
 * player.
 */
final class LightningSpawnPush {
    private final LightningSheeperEntity boss;
    private final Set<UUID> contacts = new HashSet<>();
    private final Map<UUID, Integer> delays = new HashMap<>();

    LightningSpawnPush(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    void tick(double waveRadius, boolean preparing) {
        double radius = SheeperConfig.LIGHTNING_SPAWN_PUSH_RADIUS.get();
        if (radius <= 0 || SheeperConfig.LIGHTNING_SPAWN_PUSH_STRENGTH.get() == 0
                && SheeperConfig.LIGHTNING_SPAWN_PUSH_UPWARD_STRENGTH.get() == 0) {
            delays.clear();
            return;
        }
        List<ServerPlayer> nearby = boss.getCombatParticipants().stream()
                .filter(player -> boss.distanceToSqr(player) <= radius * radius).toList();
        var pending = delays.entrySet().iterator();
        while (pending.hasNext()) {
            var entry = pending.next();
            ServerPlayer player = nearby.stream().filter(candidate -> candidate.getUUID().equals(entry.getKey()))
                    .findFirst().orElse(null);
            if (player == null) {
                pending.remove();
            } else if (entry.getValue() <= 1) {
                push(player);
                pending.remove();
            } else {
                entry.setValue(entry.getValue() - 1);
            }
        }
        if (!preparing) {
            return;
        }
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        for (ServerPlayer player : nearby) {
            if (player.position().subtract(center).horizontalDistanceSqr() > waveRadius * waveRadius
                    || !contacts.add(player.getUUID())) {
                continue;
            }
            int delay = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_SPAWN_PUSH_DELAY.get());
            if (delay == 0) {
                push(player);
            } else {
                delays.put(player.getUUID(), delay);
            }
        }
    }

    private void push(ServerPlayer player) {
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        Vec3 direction = new Vec3(player.getX() - center.x, 0, player.getZ() - center.z);
        if (direction.lengthSqr() < 1.0E-8D) {
            direction = new Vec3(1, 0, 0);
        }
        direction = direction.normalize().scale(SheeperConfig.LIGHTNING_SPAWN_PUSH_STRENGTH.get());
        player.setDeltaMovement(direction.x, SheeperConfig.LIGHTNING_SPAWN_PUSH_UPWARD_STRENGTH.get(), direction.z);
        player.fallDistance = 0;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.hurtMarked = false;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag players = new ListTag();
        for (UUID id : contacts) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Delay", delays.getOrDefault(id, 0));
            players.add(entry);
        }
        tag.put("Contacts", players);
        return tag;
    }

    void load(CompoundTag tag) {
        contacts.clear();
        delays.clear();
        for (Tag value : tag.getList("Contacts", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                UUID id = entry.getUUID("Player");
                contacts.add(id);
                int delay = Mth.clamp(entry.getInt("Delay"), 0, 1200);
                if (delay > 0) {
                    delays.put(id, delay);
                }
            }
        }
    }
}
