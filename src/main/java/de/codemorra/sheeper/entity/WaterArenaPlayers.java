package de.codemorra.sheeper.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.world.WaterArenaData;
import de.codemorra.sheeper.world.WaterArenaBoundary;
import de.codemorra.sheeper.world.ArenaWater;
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
 * Remembers submerged arena entry until a lateral exit and enforces vertical
 * water bounds. Players may enter from above but cannot leave vertically.
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

    /**
     * Registers entry only after the whole body is submerged. Existing members
     * remain eligible at any height until death, a game-mode change or lateral
     * exit.
     *
     * @param player potential combat participant
     * @return whether the player has entered and still belongs to this arena
     */
    boolean contains(ServerPlayer player) {
        if (!boss.isAlive() || !boss.isCombatReady() || !player.isAlive() || player.isRemoved()
                || player.isCreative() || player.isSpectator() || !boss.isInsideArenaColumn(player)) {
            players.remove(player.getUUID());
            return false;
        }
        if ((player.getY() < boss.getArenaWaterMinimumY()
                || player.getBoundingBox().maxY > boss.getArenaWaterSurfaceY()) && isBodyInWater(player)) {
            players.remove(player.getUUID());
            return false;
        }
        if (!players.containsKey(player.getUUID())
                && player.getY() >= boss.getArenaWaterMinimumY()
                && player.getBoundingBox().maxY <= boss.getArenaWaterSurfaceY() - SURFACE_MARGIN
                && ArenaWater.contains(boss.level, player.getEyePosition())) {
            players.put(player.getUUID(), player.position());
        }
        return players.containsKey(player.getUUID());
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
                    || player.isCreative() || player.isSpectator() || !boss.isInsideArenaColumn(player));
        });
        for (ServerPlayer player : level.players()) {
            if (!contains(player)) {
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
