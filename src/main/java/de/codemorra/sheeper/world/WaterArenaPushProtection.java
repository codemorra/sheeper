package de.codemorra.sheeper.world;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.network.ModNetwork;
import de.codemorra.sheeper.network.WaterArenaPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Temporary movement protection for boss impulses; ordinary players retain open lateral exits. */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WaterArenaPushProtection {
    private static final String STATE = "SheeperWaterPush";
    // Retain protection for sprint swimming and the slower drag of Dolphin's Grace.
    private static final int IMPULSE_TICKS = 80;
    private static final int DOLPHIN_IMPULSE_TICKS = 240;

    private WaterArenaPushProtection() {
    }

    /** Arms protection before vanilla damage can apply its own attacker-relative knockback. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() instanceof WaterSheeperEntity) {
            protect(player);
        }
    }

    /** Applies a boss-controlled velocity using the live union rather than the attacking boss's circle. */
    public static void apply(ServerPlayer player, Vec3 motion) {
        protect(player);
        player.setDeltaMovement(limitMovement(player, motion));
    }

    /** Stores an initial safe position and synchronizes geometry before the following motion packet. */
    private static void protect(ServerPlayer player) {
        if (!eligible(player) || !WaterArenaBoundary.containsBody(ArenaWater.activeVolumes(player.level),
                player.position(), player.getBbHeight())) {
            return;
        }
        CompoundTag state = player.getPersistentData().getCompound(STATE);
        long until = player.level.getGameTime()
                + (player.hasEffect(MobEffects.DOLPHINS_GRACE) ? DOLPHIN_IMPULSE_TICKS : IMPULSE_TICKS);
        if (active(player) && state.getLong("Until") == until) {
            WaterArenaFallProtection.protect(player);
            return;
        }
        if (!active(player)) {
            savePosition(state, player.position());
        }
        state.putString("Dimension", player.level.dimension().location().toString());
        state.putLong("Until", until);
        player.getPersistentData().put(STATE, state);
        WaterArenaFallProtection.protect(player);
        synchronize(player);
    }

    /** Updates local prediction from the recipient's arena snapshot without loading client classes here. */
    public static void receive(Player player, long until) {
        if (until <= player.level.getGameTime()) {
            player.getPersistentData().remove(STATE);
            return;
        }
        CompoundTag state = new CompoundTag();
        state.putString("Dimension", player.level.dimension().location().toString());
        state.putLong("Until", until);
        savePosition(state, player.position());
        player.getPersistentData().put(STATE, state);
    }

    /** Recipient-specific metadata also lets a periodic snapshot recover a missed protection update. */
    public static long protectedUntil(Player player) {
        return active(player) ? player.getPersistentData().getCompound(STATE).getLong("Until") : 0;
    }

    /** Clears protection when a controller has explicitly stopped the complete launch velocity. */
    public static void stop(ServerPlayer player) {
        if (player.getDeltaMovement().lengthSqr() < 1.0E-8D && player.getPersistentData().contains(STATE)) {
            player.getPersistentData().remove(STATE);
            synchronize(player);
        }
    }

    /** Only actual outward input near the exterior edge can end the impulse restraint early. */
    public static boolean wantsToLeave(Player player, Vec3 direction) {
        if (!active(player) || direction.horizontalDistanceSqr() < 1.0E-8D) {
            return false;
        }
        Vec3 intended = direction.normalize().scale(0.75D);
        Vec3 limited = WaterArenaBoundary.limit(ArenaWater.activeVolumes(player.level),
                player.position(), player.getBbHeight(), intended);
        return limited.distanceToSqr(intended) > 1.0E-8D;
    }

    /** Cancels the remaining launch, so an intentional exit cannot inherit dangerous boss momentum. */
    public static void leave(Player player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.getPersistentData().remove(STATE);
    }

    /** Validates client intent against current server geometry; no remote position or velocity is accepted. */
    public static void requestExit(ServerPlayer player, Vec3 direction) {
        if (wantsToLeave(player, direction)) {
            leave(player);
            synchronize(player);
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.hurtMarked = false;
        }
        // Rejected requests need no direct reply; periodic snapshots recover a stale
        // client prediction without allowing packet spam to amplify arena snapshots.
    }

    /** Called before native movement/collision so sliding does not zero both horizontal velocity axes. */
    public static Vec3 limitMovement(Entity entity, Vec3 motion) {
        if (!(entity instanceof Player player) || !active(player)) {
            return motion;
        }
        var volumes = ArenaWater.activeVolumes(player.level);
        Vec3 limited = WaterArenaBoundary.limit(volumes, player.position(), player.getBbHeight(), motion);
        if (limited.distanceToSqr(motion) > 1.0E-12D) {
            player.setDeltaMovement(WaterArenaBoundary.limit(volumes, player.position(), player.getBbHeight(),
                    player.getDeltaMovement()));
        }
        player.fallDistance = 0;
        return limited;
    }

    /** Rechecks authoritative movement after client packets, including changes to overlapping water volumes. */
    public static void enforce(ServerPlayer player) {
        if (!active(player)) {
            player.getPersistentData().remove(STATE);
            return;
        }
        CompoundTag state = player.getPersistentData().getCompound(STATE);
        if (player.hasEffect(MobEffects.DOLPHINS_GRACE)
                && state.getLong("Until") < player.level.getGameTime() + IMPULSE_TICKS) {
            state.putLong("Until", player.level.getGameTime() + IMPULSE_TICKS);
            synchronize(player);
        }
        var volumes = ArenaWater.activeVolumes(player.level);
        Vec3 previous = new Vec3(state.getDouble("X"), state.getDouble("Y"), state.getDouble("Z"));
        Vec3 movement = player.position().subtract(previous);
        // Commands and dimension teleports must not become simulated knockback paths.
        if (movement.lengthSqr() <= 64 && WaterArenaBoundary.containsBody(volumes, previous, player.getBbHeight())) {
            Vec3 corrected = previous.add(WaterArenaBoundary.limit(volumes, previous, player.getBbHeight(), movement));
            if (corrected.distanceToSqr(player.position()) > 1.0E-8D) {
                var body = player.getBoundingBox().move(corrected.subtract(player.position()));
                boolean loaded = hasLoadedChunks((ServerLevel) player.level, body);
                if (loaded && !player.level.getBlockCollisions(player, body).iterator().hasNext()) {
                    player.connection.teleport(corrected.x, corrected.y, corrected.z, player.getYRot(), player.getXRot());
                }
            }
        }
        Vec3 motion = player.getDeltaMovement();
        Vec3 limited = limitMovement(player, motion);
        if (limited.distanceToSqr(motion) > 1.0E-12D) {
            player.setDeltaMovement(limited);
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.hurtMarked = false;
        }
        if (WaterArenaBoundary.containsBody(volumes, player.position(), player.getBbHeight())) {
            savePosition(state, player.position());
        }
        WaterArenaFallProtection.protect(player);
    }

    /** Checks every touched chunk without loading terrain for a correction position. */
    private static boolean hasLoadedChunks(ServerLevel level, AABB bounds) {
        for (int x = Mth.floor(bounds.minX) >> 4; x <= Mth.floor(bounds.maxX) >> 4; x++) {
            for (int z = Mth.floor(bounds.minZ) >> 4; z <= Mth.floor(bounds.maxZ) >> 4; z++) {
                if (!WaterArenaData.isLoaded(level, new BlockPos(x * 16, 0, z * 16))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean eligible(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    private static boolean active(Player player) {
        CompoundTag state = player.getPersistentData().getCompound(STATE);
        return eligible(player) && state.getLong("Until") > player.level.getGameTime()
                && state.getString("Dimension").equals(player.level.dimension().location().toString());
    }

    private static void savePosition(CompoundTag state, Vec3 position) {
        state.putDouble("X", position.x);
        state.putDouble("Y", position.y);
        state.putDouble("Z", position.z);
    }

    private static void synchronize(ServerPlayer player) {
        ModNetwork.sendWaterArenas(player, new WaterArenaPacket(player.level.dimension().location(),
                WaterArenaData.animatedVolumes((ServerLevel) player.level), protectedUntil(player)));
    }
}
