package de.codemorra.sheeper.world;

import net.minecraft.server.level.ServerPlayer;

/** Keeps the vanilla floating timer from counting valid server-side arena movement. */
public final class ArenaFlightCheck {
    private ArenaFlightCheck() {
    }

    /**
     * Lets the packet listener reset its own timer while an arena supports the player.
     *
     * @param floating whether vanilla detected a floating player
     * @param player player whose current server-side position is being checked
     * @return whether vanilla should count this tick toward a floating kick
     */
    public static boolean shouldCountFloating(boolean floating, ServerPlayer player) {
        return floating && (player.isPassenger()
                || ArenaWater.waterHeight(player) <= 0 && !LightningTornadoMotion.isCarrying(player));
    }
}
