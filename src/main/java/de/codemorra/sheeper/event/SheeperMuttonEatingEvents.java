package de.codemorra.sheeper.event;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Applies the player's repeated internal bursts after eating raw Sheeper
 * mutton.
 * The countdown is saved with the player and does not stack across meals.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SheeperMuttonEatingEvents {
    private static final String RAW_TICKS = SheeperMod.MODID + ":raw_mutton_ticks";
    private static final int DURATION_TICKS = 120;
    private static final int BURST_INTERVAL = 20;
    private static final float BURST_DAMAGE = 1.0F;
    private static final double BURST_PUSH = 0.12D;

    private SheeperMuttonEatingEvents() {
    }

    /**
     * Starts a fresh countdown only after consumption finishes; milk clears it.
     */
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        // Only consider players on the server side.
        if (!(event.getEntity() instanceof Player player)
                || !(player.getLevel() instanceof ServerLevel)) {
            return;
        }

        // Start the countdown for raw Sheeper mutton consumption.
        if (event.getItem().is(ModItems.RAW_SHEEPER_MUTTON.get())) {
            player.getPersistentData().putInt(RAW_TICKS, DURATION_TICKS);
        } else if (event.getItem().is(Items.MILK_BUCKET)) {
            player.getPersistentData().remove(RAW_TICKS);
        }
    }

    /**
     * Produces six bursts, one per second, starting after the first second.
     * Only the server changes health and sends motion updates to the client.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Only proceed at the end of the tick and on the server side.
        if (event.phase != TickEvent.Phase.END
                || !(event.player.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Player player = event.player;
        CompoundTag data = player.getPersistentData();
        // Skip if the player has no active raw Sheeper mutton countdown.
        if (!data.contains(RAW_TICKS)) {
            return;
        }

        int remaining = data.getInt(RAW_TICKS) - 1;

        // Decrement the remaining raw Sheeper mutton ticks.
        if (remaining <= 0 || !player.isAlive()) {
            data.remove(RAW_TICKS);
        } else {
            data.putInt(RAW_TICKS, remaining);
        }

        // Trigger a burst if the interval has elapsed and the player is eligible.
        if (remaining >= 0 && remaining % BURST_INTERVAL == 0
                && player.isAlive() && !player.isCreative() && !player.isSpectator()) {
            burst(level, player);
        }
    }

    private static void burst(ServerLevel level, Player player) {
        // Sound and personal damage avoid real explosions affecting bystanders.
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.2F, 1.6F);
        // Generic damage bypasses armor and uses the standard death message.
        player.hurt(DamageSource.GENERIC, BURST_DAMAGE);

        // Apply the burst push only if the player survived the damage.
        if (player.isAlive()) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
            player.push(Math.cos(angle) * BURST_PUSH, 0.0D, Math.sin(angle) * BURST_PUSH);
            // Player motion is client-controlled; explicitly synchronize this push.
            player.hurtMarked = true;
        }
    }
}
