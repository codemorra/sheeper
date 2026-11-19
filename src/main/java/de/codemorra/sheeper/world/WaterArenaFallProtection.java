package de.codemorra.sheeper.world;

import de.codemorra.sheeper.SheeperMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Protects forced arena movement until the player lands or returns to water. */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WaterArenaFallProtection {
    private static final String FALL_PROTECTION = "SheeperArenaFallProtection";

    private WaterArenaFallProtection() {
    }

    /** Preserves protection after transport ends, including across a player save. */
    public static void protect(ServerPlayer player) {
        player.fallDistance = 0;
        player.getPersistentData().putBoolean(FALL_PROTECTION, true);
    }

    /**
     * Keeps a released swimmer safe until landing or returning to water, rather
     * than granting general damage immunity or a fixed protection timeout.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || !player.getPersistentData().getBoolean(FALL_PROTECTION)) {
            return;
        }
        player.fallDistance = 0;
        if (!player.isAlive() || player.isOnGround() || player.isInWaterOrBubble() || player.isInLava()
                || player.isSpectator() || player.getAbilities().flying) {
            player.getPersistentData().remove(FALL_PROTECTION);
        }
    }

    /** Landing is handled before the player end-tick hook can clear the protection. */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getPersistentData().getBoolean(FALL_PROTECTION)) {
            event.setCanceled(true);
            player.fallDistance = 0;
            player.getPersistentData().remove(FALL_PROTECTION);
        }
    }

}
