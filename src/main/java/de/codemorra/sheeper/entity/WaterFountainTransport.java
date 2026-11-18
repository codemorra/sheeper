package de.codemorra.sheeper.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Carries fountain victims upward with repeated synchronized velocity updates.
 * Damage belongs to the fountain's one-time hit, not this ongoing transport.
 */
final class WaterFountainTransport {
    private record Lift(double x, double z, double ceiling, int remainingTicks) {
    }

    private final WaterSheeperEntity boss;
    private final Map<UUID, Lift> lifts = new HashMap<>();

    /** @param boss owner whose captured water surface limits transport height */
    WaterFountainTransport(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    /** @return whether this player is already carried by a fountain */
    boolean contains(ServerPlayer player) {
        return lifts.containsKey(player.getUUID());
    }

    /**
     * Captures horizontal position so swimming input cannot carry a victim out of
     * the rising column.
     */
    void start(ServerPlayer player, double ceiling) {
        double rise = Math.max(0, ceiling - player.getBbHeight() - 0.15D - player.getY());
        double speed = SheeperConfig.WATER_FOUNTAIN_LIFT_SPEED.get() / 20.0D;
        int duration = Mth.clamp((int) Math.ceil(rise / speed * 2) + 40, 40, 3600);
        lifts.put(player.getUUID(), new Lift(player.getX(), player.getZ(), ceiling, duration));
    }

    /**
     * Advances transports even after the originating column expires. Membership,
     * chunk availability and full-body vertical clearance are rechecked each tick.
     *
     * @param participants current eligible arena players
     */
    void tick(List<ServerPlayer> participants) {
        var iterator = lifts.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = participants.stream().filter(candidate -> candidate.getUUID().equals(entry.getKey()))
                    .findFirst().orElse(null);
            Lift lift = entry.getValue();
            if (player == null || lift.remainingTicks() <= 0) {
                release(entry.getKey());
                iterator.remove();
                continue;
            }
            double ceiling = Math.min(lift.ceiling(), boss.getArenaWaterSurfaceY());
            double remaining = ceiling - player.getBbHeight() - 0.15D - player.getY();
            if (remaining <= 0.1D) {
                stop(player);
                iterator.remove();
                continue;
            }
            double rise = Math.min(remaining, SheeperConfig.WATER_FOUNTAIN_LIFT_SPEED.get() / 20.0D);
            AABB swept = player.getBoundingBox().expandTowards(0, rise, 0);
            if (!loaded(swept)) {
                stop(player);
                iterator.remove();
                continue;
            }
            rise = Shapes.collide(Direction.Axis.Y, player.getBoundingBox(),
                    boss.level.getBlockCollisions(player, swept), rise);
            if (rise <= 0.01D) {
                // Keep real ceilings solid; never teleport a player through terrain.
                stop(player);
                iterator.remove();
                continue;
            }
            double dx = Mth.clamp((lift.x() - player.getX()) * 0.3D, -0.15D, 0.15D);
            double dz = Mth.clamp((lift.z() - player.getZ()) * 0.3D, -0.15D, 0.15D);
            player.setDeltaMovement(dx, rise, dz);
            sync(player);
            entry.setValue(new Lift(lift.x(), lift.z(), ceiling, lift.remainingTicks() - 1));
        }
    }

    private boolean loaded(AABB bounds) {
        for (int x = Mth.floor(bounds.minX) >> 4; x <= Mth.floor(bounds.maxX) >> 4; x++) {
            for (int z = Mth.floor(bounds.minZ) >> 4; z <= Mth.floor(bounds.maxZ) >> 4; z++) {
                if (!WaterArenaData.isLoaded((ServerLevel) boss.level, new BlockPos(x * 16, 0, z * 16))) {
                    return false;
                }
            }
        }
        return true;
    }

    private void sync(ServerPlayer player) {
        player.fallDistance = 0;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.hurtMarked = false;
    }

    private void stop(ServerPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x, 0, velocity.z);
        sync(player);
    }

    private void release(UUID id) {
        if (boss.level instanceof ServerLevel server) {
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level == server && player.isAlive()) {
                stop(player);
            }
        }
    }

    /**
     * @return whether the phase must remain exclusive until a transport completes
     */
    boolean isActive() {
        return !lifts.isEmpty();
    }

    /**
     * Releases any remaining upward motion when combat is cancelled or the boss
     * dies.
     */
    void clear() {
        lifts.keySet().forEach(this::release);
        lifts.clear();
    }

    /**
     * @return captured transport anchors and remaining time for entity persistence
     */
    ListTag save() {
        ListTag tag = new ListTag();
        lifts.forEach((id, lift) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putDouble("X", lift.x());
            entry.putDouble("Z", lift.z());
            entry.putDouble("Ceiling", lift.ceiling());
            entry.putInt("Ticks", lift.remainingTicks());
            tag.add(entry);
        });
        return tag;
    }

    /** @param tag transports to resume without repeating fountain contact damage */
    void load(ListTag tag) {
        clear();
        for (Tag value : tag) {
            CompoundTag entry = (CompoundTag) value;
            double x = entry.getDouble("X");
            double z = entry.getDouble("Z");
            double ceiling = entry.getDouble("Ceiling");
            if (entry.hasUUID("Player") && Double.isFinite(x) && Double.isFinite(z) && Double.isFinite(ceiling)) {
                lifts.put(entry.getUUID("Player"), new Lift(x, z, ceiling, Mth.clamp(entry.getInt("Ticks"), 1, 3600)));
            }
        }
    }
}
