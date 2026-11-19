package de.codemorra.sheeper.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.world.ArenaWater;
import de.codemorra.sheeper.world.WaterArenaBoundary;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Qualifies entry behind the inner transition edge and retains permission until
 * the outer edge is cleared. Physical water bounds remain separate from combat.
 */
final class WaterArenaPlayers {
    private static final double SURFACE_MARGIN = ArenaWater.BOUNDARY_MARGIN;
    private final WaterSheeperEntity boss;
    private final Map<UUID, Vec3> players = new HashMap<>();

    /**
     * @param boss owner providing captured water bounds and the horizontal boundary
     */
    WaterArenaPlayers(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    /** Combat and damage share qualification; creative players retain only the damage permission. */
    boolean contains(ServerPlayer player) {
        return updateMembership(player) && !player.isCreative();
    }

    /** Resolves current qualification at hit time, including arrows and other attributable attacks. */
    boolean canDamage(ServerPlayer player) {
        return updateMembership(player);
    }

    /**
     * A new participant must be fully past the inner edge and submerged. Once
     * qualified, crossing either half of the transition does not toggle combat;
     * the entire body must clear the outer edge before qualification is lost.
     */
    private boolean updateMembership(ServerPlayer player) {
        if (!boss.isAlive() || !boss.isCombatReady() || !player.isAlive() || player.isRemoved()
                || player.isSpectator() || player.level != boss.level || isOutsideExit(player)) {
            players.remove(player.getUUID());
            return false;
        }
        boolean outsideHeight = player.getY() < boss.getArenaWaterMinimumY()
                || player.getBoundingBox().maxY > boss.getArenaWaterSurfaceY();
        // Creative players bypass vertical confinement. A different water arena
        // can also legitimately receive a survival player through a shared face.
        if (outsideHeight && (player.isCreative() || isBodyInWater(player))) {
            players.remove(player.getUUID());
            return false;
        }
        if (!players.containsKey(player.getUUID()) && isInsideEntry(player)
                && player.getY() >= boss.getArenaWaterMinimumY()
                && player.getBoundingBox().maxY <= boss.getArenaWaterSurfaceY() - SURFACE_MARGIN
                && ArenaWater.contains(boss.level, player.getEyePosition())) {
            players.put(player.getUUID(), player.position());
        }
        return players.containsKey(player.getUUID());
    }

    private double transitionWidth() {
        return WaterArenaBoundary.slowdownWidth(WaterSheeperConfig.WATER_BOUNDARY_SLOWDOWN_WIDTH.get(),
                boss.getArenaRadius());
    }

    /** The farthest horizontal body corner must fit behind the inner circular edge. */
    private boolean isInsideEntry(ServerPlayer player) {
        AABB body = player.getBoundingBox();
        double x = boss.getArenaCenter().getX() + 0.5D;
        double z = boss.getArenaCenter().getZ() + 0.5D;
        double dx = Math.max(Math.abs(body.minX - x), Math.abs(body.maxX - x));
        double dz = Math.max(Math.abs(body.minZ - z), Math.abs(body.maxZ - z));
        double radius = Math.max(0, boss.getArenaRadius() - transitionWidth());
        return dx * dx + dz * dz <= radius * radius;
    }

    /** Keeps partial body overlap with the outer edge eligible, without extending water physics there. */
    private boolean isOutsideExit(ServerPlayer player) {
        AABB body = player.getBoundingBox();
        double x = boss.getArenaCenter().getX() + 0.5D;
        double z = boss.getArenaCenter().getZ() + 0.5D;
        double dx = x - Mth.clamp(x, body.minX, body.maxX);
        double dz = z - Mth.clamp(z, body.minZ, body.maxZ);
        double radius = boss.getArenaRadius() + transitionWidth();
        if (dx * dx + dz * dz > radius * radius) {
            return true;
        }
        // Outside the water column, a player can also leave the finite-height
        // transition by walking down a slope; do not teleport them back inside.
        return !boss.isInsideArenaColumn(player) && (body.maxY < boss.getArenaWaterMinimumY()
                || body.minY > boss.getArenaWaterSurfaceY());
    }

    /**
     * Repairs vertical escapes after movement, including teleports or missing
     * client snapshots. Normal floor contact uses predicted collision instead.
     * Horizontal velocity and view direction are preserved.
     */
    void enforceVerticalBounds() {
        ServerLevel level = (ServerLevel) boss.level;
        players.keySet().removeIf(id -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            return player != null && (player.level != level || !player.isAlive() || player.isRemoved()
                    || player.isSpectator() || isOutsideExit(player));
        });
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || !contains(player) || !boss.isInsideArenaColumn(player)) {
                continue;
            }
            double minimumY = boss.getArenaWaterMinimumY() + SURFACE_MARGIN;
            double maximumY = boss.getArenaWaterSurfaceY() - SURFACE_MARGIN - player.getBbHeight();
            boolean below = player.getY() < minimumY - 1.0E-5D;
            double outsideY = below ? boss.getArenaWaterMinimumY() - 1.0E-5D
                    : boss.getArenaWaterSurfaceY() + 1.0E-5D;
            boolean coveredBoundary = isBodyInWater(player) && ArenaWater.contains(level,
                    new Vec3(player.getX(), outsideY, player.getZ()));
            if ((below || player.getY() > maximumY) && !coveredBoundary) {
                Vec3 destination = correctionPosition(player, minimumY, maximumY, below);
                if (destination != null) {
                    player.stopRiding();
                    player.connection.teleport(destination.x, destination.y, destination.z,
                            player.getYRot(), player.getXRot());
                }
                stopVerticalExit(player, below);
            } else {
                if (isClear(player, player.position())) {
                    players.put(player.getUUID(), player.position());
                }
            }
        }
    }

    /** Connected water can cover the body across several arenas with different vertical bounds. */
    private boolean isBodyInWater(ServerPlayer player) {
        return WaterArenaBoundary.containsBody(ArenaWater.activeVolumes(boss.level),
                player.position(), player.getBbHeight());
    }

    /**
     * Searches inward from the crossed boundary before falling back to the last
     * safe column. Both searches stay within the captured vertical volume.
     */
    @Nullable
    private Vec3 correctionPosition(ServerPlayer player, double minimumY, double maximumY, boolean below) {
        Vec3 previous = players.get(player.getUUID());
        for (Vec3 column : new Vec3[] { player.position(), previous }) {
            double start = Mth.clamp(column.y, minimumY, maximumY);
            double step = below ? 0.5D : -0.5D;
            for (double y = start; y >= minimumY && y <= maximumY; y += step) {
                Vec3 candidate = new Vec3(column.x, y, column.z);
                if (isClear(player, candidate)) {
                    return candidate;
                }
            }
        }
        return isClear(player, previous) ? previous : null;
    }

    /**
     * Cancels only velocity directed out of the water and prevents accumulated fall
     * damage.
     */
    private void stopVerticalExit(ServerPlayer player, boolean below) {
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x, below ? Math.max(0, velocity.y) : Math.min(0, velocity.y), velocity.z);
        player.fallDistance = 0;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.hurtMarked = false;
    }

    /**
     * Never correct through solid terrain or load chunks for an old safe position.
     */
    private boolean isClear(ServerPlayer player, Vec3 destination) {
        if (!boss.isInsideArenaColumn(destination.x, destination.z)
                || destination.y < boss.getArenaWaterMinimumY() + SURFACE_MARGIN - 1.0E-6D
                || destination.y + player.getBbHeight() > boss.getArenaWaterSurfaceY() - SURFACE_MARGIN + 1.0E-6D) {
            return false;
        }
        AABB bounds = player.getBoundingBox().move(destination.subtract(player.position()));
        ServerLevel level = (ServerLevel) boss.level;
        for (int x = (int) Math.floor(bounds.minX) >> 4; x <= (int) Math.floor(bounds.maxX) >> 4; x++) {
            for (int z = (int) Math.floor(bounds.minZ) >> 4; z <= (int) Math.floor(bounds.maxZ) >> 4; z++) {
                if (!WaterArenaData.isLoaded(level, new BlockPos(x * 16, 0, z * 16))) {
                    return false;
                }
            }
        }
        return !level.getBlockCollisions(player, bounds).iterator().hasNext();
    }

    /**
     * @return entry membership and last unobstructed underwater positions across
     *         reloads
     */
    ListTag save() {
        ListTag tag = new ListTag();
        players.forEach((id, position) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putDouble("X", position.x);
            entry.putDouble("Y", position.y);
            entry.putDouble("Z", position.z);
            tag.add(entry);
        });
        return tag;
    }

    /**
     * @param tag saved entries; current game mode and horizontal membership are
     *            rechecked on use
     */
    void load(ListTag tag) {
        players.clear();
        for (Tag value : tag) {
            CompoundTag entry = (CompoundTag) value;
            Vec3 position = new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z"));
            if (entry.hasUUID("Player") && Double.isFinite(position.x)
                    && Double.isFinite(position.y) && Double.isFinite(position.z)) {
                players.put(entry.getUUID("Player"), position);
            }
        }
    }
}
