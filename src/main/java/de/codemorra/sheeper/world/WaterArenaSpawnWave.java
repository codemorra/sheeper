package de.codemorra.sheeper.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.world.WaterArenaData.AnimatedVolume;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** One-time, damage-free transport carried by the arena's expanding startup front. */
public final class WaterArenaSpawnWave {
    private static final double FRONT_CLEARANCE = 2.0D;
    private final Set<UUID> contacts = new HashSet<>();
    private final Map<UUID, Double> lifts = new HashMap<>();

    /**
     * Continues pushing against real collisions until the front ends or the player
     * reaches the inset edge. World ticking also works while the boss is unloaded.
     *
     * @return whether persistent contact or transport state changed
     */
    boolean tick(ServerLevel level, AnimatedVolume volume) {
        double speed = WaterSheeperConfig.WATER_SPAWN_PUSH_SPEED.get() / 20.0D;
        if (!volume.isExpanding(level.getGameTime()) || speed <= 0) {
            boolean changed = !lifts.isEmpty();
            clear(level);
            return changed;
        }
        var bounds = volume.bounds();
        double front = volume.at(level.getGameTime()).radius();
        double centerX = bounds.center().getX() + 0.5D;
        double centerZ = bounds.center().getZ() + 0.5D;
        boolean changed = false;
        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            if (player.isCreative()) {
                // A mode change also releases active transport without overriding flight.
                changed |= lifts.remove(id) != null;
                continue;
            }
            Vec3 radial = new Vec3(player.getX() - centerX, 0, player.getZ() - centerZ);
            double distance = radial.length();
            // Keep the whole player inside the water before contact and during transport.
            double transportFront = front - FRONT_CLEARANCE - player.getBbWidth() * 0.5D;
            double inset = Math.max(player.getBbWidth() * 0.5D + ArenaWater.BOUNDARY_MARGIN,
                    WaterSheeperConfig.WATER_SPAWN_PUSH_EDGE_DISTANCE.get());
            double targetRadius = Math.max(0, bounds.radius() - inset);
            boolean eligible = player.isAlive() && !player.isSpectator() && bounds.contains(player.position());
            if (!eligible || distance >= targetRadius) {
                if (lifts.remove(id) != null) {
                    stop(player);
                    changed = true;
                }
                continue;
            }
            if (!contacts.contains(id) && distance <= transportFront) {
                contacts.add(id);
                lifts.put(id, player.getY() + WaterSheeperConfig.WATER_SPAWN_PUSH_LIFT_HEIGHT.get());
                player.stopRiding();
                changed = true;
            }
            Double lift = lifts.get(id);
            if (lift == null) {
                continue;
            }
            // Follow behind the water front; blocked swimmers can catch up without reaching its outer face.
            double step = Math.min(speed, Math.max(0, Math.min(transportFront, targetRadius) - distance));
            Vec3 direction = distance > 1.0E-6D ? radial.scale(1 / distance)
                    : new Vec3(1, 0, 0);
            double ceiling = bounds.maximumY() + 1 - player.getBbHeight() - ArenaWater.BOUNDARY_MARGIN;
            double upward = Mth.clamp((Math.min(lift, ceiling) - player.getY()) * 0.2D, 0, 0.15D);
            // Send the intended motion again even after a wall clipped the previous tick.
            // Vanilla collision remains responsible for preventing movement through blocks.
            WaterArenaPushProtection.apply(player, new Vec3(direction.x * step, upward, direction.z * step));
            ArenaFallProtection.protect(player);
            sync(player);
        }
        var iterator = lifts.keySet().iterator();
        while (iterator.hasNext()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(iterator.next());
            if (player == null || player.level != level) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static void sync(ServerPlayer player) {
        player.fallDistance = 0;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.hurtMarked = false;
    }

    private static void stop(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        WaterArenaPushProtection.stop(player);
        sync(player);
    }

    /** Releases remaining velocity when startup completes or the arena is removed. */
    void clear(ServerLevel level) {
        for (UUID id : lifts.keySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level == level && player.isAlive() && !player.isCreative()) {
                stop(player);
            }
        }
        lifts.clear();
    }

    /** Saves contact identity and lift anchors without restarting transport after a reload. */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        for (UUID id : contacts) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            if (lifts.containsKey(id)) {
                entry.putDouble("LiftY", lifts.get(id));
            }
            entries.add(entry);
        }
        tag.put("Contacts", entries);
        return tag;
    }

    /** Restores only finite lift anchors; the current arena and player positions are rechecked. */
    void load(CompoundTag tag) {
        contacts.clear();
        lifts.clear();
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
