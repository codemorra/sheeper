package de.codemorra.sheeper.network;

import de.codemorra.sheeper.SheeperMod;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Synchronizes server-authoritative mutton visuals with tracking clients.
 */
public final class ModNetwork {
    // This identifies the packet format, independently of the mod's release
    // version.
    private static final String VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(SheeperMod.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {
    }

    /**
     * Registers clientbound packets once during mod construction on both sides.
     * Decoding may occur on a network thread; handling is scheduled on the client
     * thread.
     */
    public static void register() {
        CHANNEL.messageBuilder(MuttonVisualPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MuttonVisualPacket::encode)
                .decoder(MuttonVisualPacket::decode)
                .consumerMainThread(MuttonVisualPacket::handle)
                .add();
    }

    /**
     * Sends a player's visual snapshot to that player and all tracking observers.
     * Must be called on the server thread. Successful food damage also synchronizes
     * the owner's health before the snapshot replaces the vanilla damage flash.
     *
     * @param player the player whose state and optional health are synchronized
     * @param packet that player's current visual state and burst information
     */
    public static void broadcast(ServerPlayer player, MuttonVisualPacket packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), packet);
        if (packet.foodDamage()) {
            // LocalPlayer.hurtTo also starts a damage flash. Send current health
            // before our snapshot clears it; a later identical health update is safe.
            player.connection.send(new ClientboundSetHealthPacket(player.getHealth(),
                    player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
        }
        // Login/respawn snapshots must reach the owner even before entity tracking
        // starts.
        sendTo(player, packet);
    }

    /**
     * Sends a snapshot to one observer, for example when entity tracking begins.
     * Unlike broadcast, this does not synchronize health or notify other clients.
     *
     * @param observer the server-side recipient
     * @param packet   the observed player's visual snapshot
     */
    public static void sendTo(ServerPlayer observer, MuttonVisualPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer), packet);
    }
}
