package de.codemorra.sheeper.world;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import de.codemorra.sheeper.world.WaterArenaData.AnimatedVolume;
import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Lowers swimmers safely with the receding water, continuing below the old floor until landing. */
final class WaterArenaDrain {
    private final Set<UUID> descending = new HashSet<>();

    /**
     * Leaves horizontal movement and normal block collision intact. Natural water,
     * another arena, flight or leaving the horizontal arena ends forced descent.
     *
     * @param otherWater whether another active arena still supplies water at a point
     * @return whether persisted descent membership changed
     */
    boolean tick(ServerLevel level, AnimatedVolume volume, Predicate<Vec3> otherWater) {
        boolean changed = false;
        boolean hasWater = volume.hasWater(level.getGameTime());
        double surface = volume.at(level.getGameTime()).maximumY() + 1;
        // Counter water drag without turning the final descent through air into a drop.
        double speed = Mth.clamp((volume.bounds().maximumY() + 1 - volume.bounds().minimumY())
                / volume.drainDurationTicks() * 1.25D, 0.08D, 0.2D);
        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            // Vanilla also reports ground contact at the virtual floor. Keep those
            // swimmers tracked until the floor disappears and they reach real terrain.
            boolean onRealGround = player.isOnGround() && level.getBlockCollisions(player,
                    player.getBoundingBox().move(0, -0.05D, 0)).iterator().hasNext();
            boolean free = !player.isAlive() || player.isSpectator() || player.isCreative()
                    || player.getAbilities().flying || player.isFallFlying() || player.isPassenger() || onRealGround
                    || player.isInLava() || level.getFluidState(new BlockPos(player.position())).is(FluidTags.WATER)
                    || otherWater.test(player.getEyePosition()) || !insideColumn(volume.bounds(), player.position());
            if (free) {
                changed |= descending.remove(id);
                continue;
            }
            if (hasWater && volume.bounds().contains(player.position())) {
                changed |= descending.add(id);
            }
            if (!descending.contains(id)) {
                continue;
            }
            WaterArenaFallProtection.protect(player);
            if (hasWater && player.getBoundingBox().maxY <= surface - ArenaWater.BOUNDARY_MARGIN) {
                continue;
            }
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, -speed, motion.z);
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.hurtMarked = false;
        }
        var iterator = descending.iterator();
        while (iterator.hasNext()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(iterator.next());
            if (player == null || player.level != level) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static boolean insideColumn(VolumeBounds bounds, Vec3 point) {
        double x = point.x - bounds.center().getX() - 0.5D;
        double z = point.z - bounds.center().getZ() - 0.5D;
        return x * x + z * z <= bounds.radius() * bounds.radius();
    }

    /** Geometry may finish before swimmers reach real ground below the former virtual floor. */
    boolean isFinished() {
        return descending.isEmpty();
    }

    /** Saves continuing descents so a world reload cannot interrupt their safe landing. */
    ListTag save() {
        ListTag players = new ListTag();
        for (UUID id : descending) {
            CompoundTag player = new CompoundTag();
            player.putUUID("Player", id);
            players.add(player);
        }
        return players;
    }

    void load(ListTag players) {
        descending.clear();
        for (Tag value : players) {
            CompoundTag player = (CompoundTag) value;
            if (player.hasUUID("Player")) {
                descending.add(player.getUUID("Player"));
            }
        }
    }
}
