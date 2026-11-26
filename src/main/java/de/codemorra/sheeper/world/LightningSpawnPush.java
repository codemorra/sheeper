package de.codemorra.sheeper.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.network.LightningWeatherPacket.Arena;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Carries players outward behind the expanding storm front once per arena startup. */
final class LightningSpawnPush {
    private final Set<UUID> contacts = new HashSet<>();
    private final Map<UUID, Double> lifts = new HashMap<>();

    /** @return whether the persisted contact or transport state changed */
    boolean tick(ServerLevel level, Arena arena, Set<UUID> carriedPlayers) {
        double speed = LightningSheeperConfig.LIGHTNING_SPAWN_PUSH_SPEED.get() / 20;
        long now = level.getGameTime();
        if (!arena.isBuilding(now) || speed <= 0) {
            return clear(level, carriedPlayers);
        }

        Vec3 center = Vec3.atBottomCenterOf(arena.center());
        double edgeDistance = LightningSheeperConfig.LIGHTNING_SPAWN_PUSH_EDGE_DISTANCE.get();
        double liftHeight = LightningSheeperConfig.LIGHTNING_SPAWN_PUSH_LIFT_HEIGHT.get();
        double front = arena.radiusAt(now) - Math.min(2, arena.radius() * 0.2);
        boolean changed = false;
        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            Vec3 radial = new Vec3(player.getX() - center.x, 0, player.getZ() - center.z);
            double distance = radial.length();
            double halfWidth = player.getBbWidth() * 0.5;
            double targetRadius = Math.max(0, arena.radius() - Math.max(edgeDistance, halfWidth + 0.1));
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
                changed |= lifts.remove(id) != null;
                continue;
            }
            if (!arena.containsY(player.getY()) || distance >= targetRadius) {
                if (lifts.remove(id) != null) {
                    stopUnlessCarried(player, carriedPlayers);
                    changed = true;
                }
                continue;
            }
            if (carriedPlayers.contains(id)) {
                continue;
            }

            double transportFront = front - halfWidth;
            if (distance <= transportFront && contacts.add(id)) {
                lifts.put(id, player.getY() + liftHeight);
                player.stopRiding();
                changed = true;
            }
            Double lift = lifts.get(id);
            if (lift == null) {
                continue;
            }
            carriedPlayers.add(id);
            double step = Math.min(speed, Math.max(0, Math.min(transportFront, targetRadius) - distance));
            Vec3 direction = distance > 1.0E-6 ? radial.scale(1 / distance) : new Vec3(1, 0, 0);
            double ceiling = arena.maximumY() - player.getBbHeight() - 0.1;
            // Apply the same lift and outward force again after collisions instead of moving through terrain.
            double upward = Mth.clamp((Math.min(lift, ceiling) - player.getY()) * 0.2, -0.15, 0.2);
            if (liftHeight == 0) {
                upward = player.getDeltaMovement().y;
            }
            setMotion(player, new Vec3(direction.x * step, upward, direction.z * step));
        }
        changed |= lifts.keySet().removeIf(id -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            return player == null || player.level != level;
        });
        return changed;
    }

    /** Stops the transport on startup completion or arena removal. */
    boolean clear(ServerLevel level, Set<UUID> carriedPlayers) {
        boolean changed = !lifts.isEmpty();
        for (UUID id : lifts.keySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level == level && player.isAlive() && !player.isCreative()
                    && !player.isSpectator()) {
                stopUnlessCarried(player, carriedPlayers);
            }
        }
        lifts.clear();
        return changed;
    }

    private static void stopUnlessCarried(ServerPlayer player, Set<UUID> carriedPlayers) {
        if (!carriedPlayers.contains(player.getUUID())) {
            setMotion(player, Vec3.ZERO);
        }
    }

    private static void setMotion(ServerPlayer player, Vec3 motion) {
        player.setDeltaMovement(motion);
        ArenaFallProtection.protect(player);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.hurtMarked = false;
    }

    /** Saves contact identities and the original lift anchors for an interrupted startup. */
    CompoundTag save() {
        var tag = new CompoundTag();
        var entries = new ListTag();
        for (UUID id : contacts) {
            var entry = new CompoundTag();
            entry.putUUID("Player", id);
            if (lifts.containsKey(id)) {
                entry.putDouble("LiftY", lifts.get(id));
            }
            entries.add(entry);
        }
        tag.put("Contacts", entries);
        return tag;
    }

    void load(CompoundTag tag) {
        for (Tag value : tag.getList("Contacts", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                UUID id = entry.getUUID("Player");
                contacts.add(id);
                if (entry.contains("LiftY", Tag.TAG_DOUBLE) && Double.isFinite(entry.getDouble("LiftY"))) {
                    lifts.put(id, entry.getDouble("LiftY"));
                }
            }
        }
    }
}
