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
 * Carries wave victims outward; collision can stop transport without cancelling
 * damage.
 */
final class WaterMaelstromTransport {
    private record Ride(Vec3 target, Vec3 direction, int remainingTicks) {
    }

    private final WaterSheeperEntity boss;
    private final Map<UUID, Ride> rides = new HashMap<>();

    /** @param boss arena owner supplying the circular edge and water ceiling */
    WaterMaelstromTransport(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    /**
     * Captures swimming height and an endpoint at the safe ring, or two blocks
     * inside an unprotected edge.
     */
    void start(ServerPlayer player, Vec3 impact) {
        Vec3 center = new Vec3(boss.getArenaCenter().getX() + 0.5D, player.getY(),
                boss.getArenaCenter().getZ() + 0.5D);
        Vec3 relative = player.position().subtract(center);
        Vec3 outward = new Vec3(player.getX() - impact.x, 0, player.getZ() - impact.z);
        double radius = Math.min(Math.max(0, boss.getCombatRadius() - 2), WaterMaelstromWave.dangerRadius(boss));
        if (relative.horizontalDistanceSqr() >= radius * radius) {
            return;
        }
        if (outward.lengthSqr() < 0.01D) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2;
            outward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        }
        Vec3 direction = outward.normalize();
        // Intersect the ray away from the actual impact with the inset arena
        // circle; preparation may have occurred away from the exact arena center.
        double projection = relative.dot(direction);
        double distance = -projection + Math.sqrt(Math.max(0,
                projection * projection + radius * radius - relative.lengthSqr()));
        Vec3 target = player.position().add(direction.scale(distance));
        int ticks = (int) Math.ceil(player.position().distanceTo(target)
                / SheeperConfig.WATER_MAELSTROM_TRANSPORT_SPEED.get() * 40) + 40;
        player.stopRiding();
        rides.put(player.getUUID(), new Ride(target, direction, Mth.clamp(ticks, 40, 2600)));
    }

    /**
     * Synchronizes outward velocity and clips it against real block collision.
     * A single block may be stepped over; taller barriers end only this ride.
     *
     * @param players current arena participants; departed or dead players are
     *                released
     */
    void tick(List<ServerPlayer> players) {
        var iterator = rides.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = players.stream().filter(value -> value.getUUID().equals(entry.getKey()))
                    .findFirst().orElse(null);
            Ride ride = entry.getValue();
            if (player == null || ride.remainingTicks() <= 0
                    || WaterMaelstromWave.isSafeArea(boss, player.position())) {
                release(entry.getKey());
                iterator.remove();
                continue;
            }
            Vec3 remaining = new Vec3(ride.target().x - player.getX(), 0, ride.target().z - player.getZ());
            double speed = SheeperConfig.WATER_MAELSTROM_TRANSPORT_SPEED.get() / 20.0D;
            if (remaining.length() <= speed || remaining.dot(ride.direction()) <= 0) {
                Vec3 endpoint = ride.target();
                Vec3 correction = endpoint.subtract(player.position());
                if (!clear(player, player.getBoundingBox().expandTowards(correction))) {
                    // Retain a necessary one-block step when terrain still occupies
                    // the original swimming height at the endpoint.
                    endpoint = new Vec3(ride.target().x, player.getY(), ride.target().z);
                    correction = endpoint.subtract(player.position());
                }
                if (clear(player, player.getBoundingBox().expandTowards(correction))) {
                    player.connection.teleport(endpoint.x, endpoint.y, endpoint.z, player.getYRot(), player.getXRot());
                    stop(player);
                    iterator.remove();
                    continue;
                }
                if (remaining.dot(ride.direction()) <= 0) {
                    stop(player);
                    iterator.remove();
                    continue;
                }
            }
            Vec3 horizontal = remaining.normalize().scale(Math.min(speed, remaining.length()));
            Vec3 clipped = clip(player, horizontal);
            if (clipped.subtract(horizontal).horizontalDistanceSqr() > 0.0001D) {
                double step = 1.0D;
                AABB raised = player.getBoundingBox().move(0, step, 0);
                Vec3 up = clip(player, new Vec3(0, step, 0));
                if (up.y < step - 0.001D || raised.maxY > boss.getArenaWaterSurfaceY() - 0.15D
                        || !clear(player, raised.expandTowards(horizontal))) {
                    // A wall or low ceiling must not be bypassed by repeated small lifts.
                    stop(player);
                    iterator.remove();
                    continue;
                }
                player.connection.teleport(player.getX(), player.getY() + step, player.getZ(),
                        player.getYRot(), player.getXRot());
            }
            double dy = Mth.clamp((ride.target().y - player.getY()) * 0.3D, -0.2D, 0.2D);
            Vec3 motion = clip(player, new Vec3(horizontal.x, dy, horizontal.z));
            player.setDeltaMovement(motion);
            sync(player);
            entry.setValue(new Ride(ride.target(), ride.direction(), ride.remainingTicks() - 1));
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

    private boolean clear(ServerPlayer player, AABB bounds) {
        return loaded(bounds) && !boss.level.getBlockCollisions(player, bounds).iterator().hasNext();
    }

    /**
     * Matches ordinary axis collision without loading the swept destination chunks.
     */
    private Vec3 clip(ServerPlayer player, Vec3 motion) {
        AABB bounds = player.getBoundingBox();
        AABB swept = bounds.expandTowards(motion);
        if (!loaded(swept)) {
            return Vec3.ZERO;
        }
        var collisions = boss.level.getBlockCollisions(player, swept);
        double y = Shapes.collide(Direction.Axis.Y, bounds, collisions, motion.y);
        bounds = bounds.move(0, y, 0);
        double x = Shapes.collide(Direction.Axis.X, bounds, collisions, motion.x);
        bounds = bounds.move(x, 0, 0);
        double z = Shapes.collide(Direction.Axis.Z, bounds, collisions, motion.z);
        return new Vec3(x, y, z);
    }

    private void sync(ServerPlayer player) {
        player.fallDistance = 0;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.hurtMarked = false;
    }

    private void stop(ServerPlayer player) {
        player.setDeltaMovement(Vec3.ZERO);
        sync(player);
    }

    private void release(UUID id) {
        ServerPlayer player = ((ServerLevel) boss.level).getServer().getPlayerList().getPlayer(id);
        if (player != null && player.level == boss.level && player.isAlive()) {
            stop(player);
        }
    }

    /** Releases forced motion when combat is cancelled or the boss dies. */
    void clear() {
        rides.keySet().forEach(this::release);
        rides.clear();
    }

    /** @return transport endpoints and timeouts for continuing after a reload */
    ListTag save() {
        ListTag tag = new ListTag();
        rides.forEach((id, ride) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putDouble("X", ride.target().x);
            entry.putDouble("Y", ride.target().y);
            entry.putDouble("Z", ride.target().z);
            entry.putDouble("Dx", ride.direction().x);
            entry.putDouble("Dz", ride.direction().z);
            entry.putInt("Ticks", ride.remainingTicks());
            tag.add(entry);
        });
        return tag;
    }

    /**
     * @param tag saved transports; collision and participation are rechecked before
     *            moving
     */
    void load(ListTag tag) {
        clear();
        for (Tag value : tag) {
            CompoundTag entry = (CompoundTag) value;
            Vec3 target = new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z"));
            Vec3 direction = new Vec3(entry.getDouble("Dx"), 0, entry.getDouble("Dz"));
            if (entry.hasUUID("Player") && Double.isFinite(target.x) && Double.isFinite(target.y)
                    && Double.isFinite(target.z) && Double.isFinite(direction.x) && Double.isFinite(direction.z)
                    && direction.lengthSqr() > 0.01D) {
                rides.put(entry.getUUID("Player"), new Ride(target, direction.normalize(),
                        Mth.clamp(entry.getInt("Ticks"), 1, 2600)));
            }
        }
    }
}
