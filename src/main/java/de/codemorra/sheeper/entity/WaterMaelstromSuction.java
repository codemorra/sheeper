package de.codemorra.sheeper.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.world.WaterArenaPushProtection;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Adds horizontal vortex impulses while retaining client-controlled swimming
 * input.
 */
final class WaterMaelstromSuction {
    private final Map<UUID, Vec3> previousPositions = new HashMap<>();

    /**
     * Uses observed player displacement because server velocity alone does not
     * contain ordinary client swimming input. Vanilla water drag estimates the
     * velocity remaining after that displacement, then the vortex adds its pull.
     *
     * @param players  current eligible arena participants
     * @param center   horizontal vortex origin; player height remains uncontrolled
     * @param strength inward acceleration in blocks per tick squared
     */
    void tick(List<ServerPlayer> players, Vec3 center, double strength) {
        previousPositions.keySet().removeIf(id -> players.stream().noneMatch(player -> player.getUUID().equals(id)));
        double speedLimit = WaterSheeperConfig.WATER_MAELSTROM_PULL_SPEED_LIMIT.get() / 20.0D;
        for (ServerPlayer player : players) {
            Vec3 previous = previousPositions.put(player.getUUID(), player.position());
            Vec3 motion = player.getDeltaMovement();
            if (previous != null) {
                Vec3 displacement = player.position().subtract(previous);
                if (displacement.lengthSqr() > 4) {
                    // A teleport is not swimming input and must not become a launch impulse.
                    continue;
                }
                motion = displacement.scale(0.8D);
            }
            Vec3 inward = new Vec3(center.x - player.getX(), 0, center.z - player.getZ());
            double distance = inward.length();
            if (distance < 0.1D || strength <= 0) {
                continue;
            }
            Vec3 direction = inward.scale(1.0D / distance);
            double impulse = Math.min(strength * Math.min(1.0D, distance / 2),
                    Math.max(0, Math.min(speedLimit, distance) - motion.dot(direction)));
            if (impulse <= 0) {
                continue;
            }
            WaterArenaPushProtection.apply(player, motion.add(direction.scale(impulse)));
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.hurtMarked = false;
        }
    }

    /**
     * Discards movement samples when the attack ends, is aborted, or is loaded
     * again.
     */
    void clear() {
        previousPositions.clear();
    }
}
