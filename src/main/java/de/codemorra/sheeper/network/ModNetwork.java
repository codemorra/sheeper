package de.codemorra.sheeper.network;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.util.ResourceLocations;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Synchronizes server-authoritative player effects and arena weather with
 * observing clients.
 */
public final class ModNetwork {
    // This identifies the packet format, independently of the mod's release
    // version.
    private static final String VERSION = "28";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocations.create(SheeperMod.MODID, "main"),
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
        CHANNEL.messageBuilder(LightningWeatherPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LightningWeatherPacket::encode).decoder(LightningWeatherPacket::decode)
                .consumerMainThread(LightningWeatherPacket::handle).add();
        CHANNEL.messageBuilder(WaterArenaPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WaterArenaPacket::encode).decoder(WaterArenaPacket::decode)
                .consumerMainThread(WaterArenaPacket::handle).add();
        CHANNEL.messageBuilder(WaterArenaExitPacket.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WaterArenaExitPacket::encode).decoder(WaterArenaExitPacket::decode)
                .consumerMainThread(WaterArenaExitPacket::handle).add();
        CHANNEL.messageBuilder(LightningWaterChargePacket.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LightningWaterChargePacket::encode).decoder(LightningWaterChargePacket::decode)
                .consumerMainThread(LightningWaterChargePacket::handle).add();
        CHANNEL.messageBuilder(LightningStrikeMarkerPacket.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LightningStrikeMarkerPacket::encode).decoder(LightningStrikeMarkerPacket::decode)
                .consumerMainThread(LightningStrikeMarkerPacket::handle).add();
        CHANNEL.messageBuilder(LightningRingVisualPacket.class, 6, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LightningRingVisualPacket::encode).decoder(LightningRingVisualPacket::decode)
                .consumerMainThread(LightningRingVisualPacket::handle).add();
        CHANNEL.messageBuilder(LightningTornadoPacket.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LightningTornadoPacket::encode).decoder(LightningTornadoPacket::decode)
                .consumerMainThread(LightningTornadoPacket::handle).add();
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

    public static void sendWeather(ServerPlayer observer, LightningWeatherPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer), packet);
    }

    /**
     * Sends captured arena bounds for water rendering and the local air display.
     */
    public static void sendWaterArenas(ServerPlayer observer, WaterArenaPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer), packet);
    }
    /** Starts the visible water discharge for this observer. */
    public static void sendLightningWaterCharge(ServerPlayer observer, LightningWaterChargePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer), packet);
    }

    /** Updates the phase of a ground marker for a nearby observer. */
    public static void sendLightningStrikeMarker(ServerPlayer observer, LightningStrikeMarkerPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer), packet);
    }

    /** Refreshes an announced ring attack or a wave that is still travelling. */
    public static void sendLightningRingVisual(ServerPlayer observer, LightningRingVisualPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer), packet);
    }

    /** Updates the active tornado for a nearby observer. */
    public static void sendLightningTornado(ServerPlayer observer, LightningTornadoPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> observer), packet);
    }

    /** Sends only movement intent; the server decides whether the player is near an exterior boundary. */
    public static void requestWaterArenaExit(WaterArenaExitPacket packet) {
        CHANNEL.sendToServer(packet);
    }

}
