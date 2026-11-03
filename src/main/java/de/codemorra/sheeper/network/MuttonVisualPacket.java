package de.codemorra.sheeper.network;

import java.util.UUID;
import java.util.function.Supplier;

import de.codemorra.sheeper.client.MuttonEffectVisuals;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Carries remaining effect time and actual burst events, including the final
 * burst.
 * UUIDs allow snapshots to arrive before the corresponding player entity
 * spawns.
 *
 * @param playerId    the affected player's UUID
 * @param rawTicks    remaining server-side raw-meat ticks; zero means inactive
 * @param cookedTicks remaining server-side cooked-meat ticks; zero means
 *                    inactive
 * @param rawBurst    whether a raw internal explosion occurred, even if its
 *                    countdown
 *                    just ended
 * @param cookedBurst whether a cooked internal explosion occurred, even if its
 *                    countdown just ended
 * @param foodDamage  whether the player accepted damage from this burst,
 *                    allowing
 *                    the client to replace its vanilla damage flash
 */
public record MuttonVisualPacket(UUID playerId, int rawTicks, int cookedTicks,
        boolean rawBurst, boolean cookedBurst, boolean foodDamage) {

    /**
     * Writes the wire fields in the same order consumed by
     * {@link #decode(FriendlyByteBuf)}.
     *
     * @param buffer the outgoing packet buffer
     */
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(playerId);
        buffer.writeVarInt(rawTicks);
        buffer.writeVarInt(cookedTicks);
        buffer.writeBoolean(rawBurst);
        buffer.writeBoolean(cookedBurst);
        buffer.writeBoolean(foodDamage);
    }

    /**
     * Reads a snapshot without accessing world or rendering state.
     *
     * @param buffer the incoming packet buffer
     * @return the decoded server snapshot
     */
    public static MuttonVisualPacket decode(FriendlyByteBuf buffer) {
        return new MuttonVisualPacket(buffer.readUUID(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }

    /**
     * Applies the snapshot through the channel's main-thread consumer.
     * The distribution guard prevents client rendering classes from loading on
     * a dedicated server.
     *
     * @param context the Forge context supplied by the registered channel
     */
    public void handle(Supplier<NetworkEvent.Context> context) {
        // The channel schedules this on the client thread; keep client classes
        // behind the distribution boundary for dedicated servers.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MuttonEffectVisuals.receive(this));
    }
}
