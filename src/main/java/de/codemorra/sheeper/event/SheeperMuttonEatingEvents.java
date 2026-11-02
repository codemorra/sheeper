package de.codemorra.sheeper.event;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.registry.ModItems;
import de.codemorra.sheeper.network.ModNetwork;
import de.codemorra.sheeper.network.MuttonVisualPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Applies internal bursts after eating Sheeper mutton and a speed bonus for
 * cooked meat. Separate saved countdowns refresh rather than stack across
 * meals.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SheeperMuttonEatingEvents {
    // Only accessed on the server thread; entries are removed when players leave.
    private static final Map<UUID, Integer> LAST_VISUAL_STATES = new HashMap<>();
    private static final String RAW_TICKS = SheeperMod.MODID + ":raw_mutton_ticks";
    private static final String COOKED_TICKS = SheeperMod.MODID + ":cooked_mutton_ticks";
    private static final int RAW_DURATION_TICKS = 120;
    private static final int RAW_BURST_INTERVAL = 20;
    private static final int COOKED_DURATION_TICKS = 200;
    private static final int COOKED_BURST_INTERVAL = 40;
    private static final float BURST_DAMAGE = 1.0F;
    private static final double BURST_PUSH = 0.12D;
    private static final AttributeModifier COOKED_SPEED_BOOST = new AttributeModifier(
            UUID.fromString("27539948-57fb-49be-934c-7c1b97fc5982"),
            "Cooked Sheeper mutton speed boost", 0.5D,
            AttributeModifier.Operation.MULTIPLY_TOTAL);

    private SheeperMuttonEatingEvents() {
    }

    /**
     * Refreshes the corresponding countdown after consumption; milk clears both.
     */
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        // Only consider players on the server side.
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Determine which item was consumed and update the corresponding countdown.
        if (event.getItem().is(ModItems.RAW_SHEEPER_MUTTON.get())) {
            player.getPersistentData().putInt(RAW_TICKS, RAW_DURATION_TICKS);
        } else if (event.getItem().is(ModItems.COOKED_SHEEPER_MUTTON.get())) {
            player.getPersistentData().putInt(COOKED_TICKS, COOKED_DURATION_TICKS);
            updateSpeedBoost(player, isEligible(player));
        } else if (event.getItem().is(Items.MILK_BUCKET)) {
            player.getPersistentData().remove(RAW_TICKS);
            player.getPersistentData().remove(COOKED_TICKS);
            updateSpeedBoost(player, false);
        } else {
            return;
        }
        syncVisuals(player, true, false, false, false);
    }

    /**
     * Advances both countdowns and keeps the speed bonus in sync with cooked meat.
     * Raw meat bursts once per second; cooked meat bursts every two seconds.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.getLevel();
        CompoundTag data = player.getPersistentData();
        if (!player.isAlive()) {
            data.remove(RAW_TICKS);
            data.remove(COOKED_TICKS);
            updateSpeedBoost(player, false);
            syncVisuals(player, false, false, false, false);
            return;
        }

        boolean rawBurst = advanceCountdown(data, RAW_TICKS, RAW_BURST_INTERVAL);
        boolean cookedBurst = advanceCountdown(data, COOKED_TICKS, COOKED_BURST_INTERVAL);
        boolean eligible = isEligible(player);

        updateSpeedBoost(player, eligible && data.getInt(COOKED_TICKS) > 0);

        boolean foodDamage = false;
        if (eligible && (rawBurst || cookedBurst)) {
            // Combine simultaneous bursts so hurt immunity does not swallow one.
            float damage = (rawBurst ? BURST_DAMAGE : 0.0F)
                    + (cookedBurst ? BURST_DAMAGE : 0.0F);
            foodDamage = burst(level, player, damage, rawBurst);
        }
        syncVisuals(player, rawBurst || cookedBurst, rawBurst, cookedBurst, foodDamage);
    }

    /**
     * Sends changes immediately and refreshes active snapshots once per second.
     * Actual burst flags preserve the final pulse when its countdown reaches zero.
     *
     * @param player      the affected server-side player
     * @param force       whether to send even without an active-state change
     * @param rawBurst    whether a raw-meat pulse is due this tick
     * @param cookedBurst whether a cooked-meat pulse is due this tick
     * @param foodDamage  whether this tick's burst damage was accepted
     */
    private static void syncVisuals(ServerPlayer player, boolean force,
            boolean rawBurst, boolean cookedBurst, boolean foodDamage) {
        MuttonVisualPacket packet = visualSnapshot(player, rawBurst, cookedBurst, foodDamage);
        // Cache activity bits, not remaining time, to avoid sending every tick.
        int state = (packet.rawTicks() > 0 ? 1 : 0) | (packet.cookedTicks() > 0 ? 2 : 0);
        Integer previous = LAST_VISUAL_STATES.put(player.getUUID(), state);
        if (force || previous == null || previous != state || (state != 0 && player.tickCount % 20 == 0)) {
            ModNetwork.broadcast(player, packet);
        }
    }

    /**
     * Builds a snapshot without advancing timers. Excluded players receive an
     * inactive snapshot so clients can clear an earlier visible effect.
     */
    private static MuttonVisualPacket visualSnapshot(ServerPlayer player,
            boolean rawBurst, boolean cookedBurst, boolean foodDamage) {
        boolean eligible = isEligible(player);
        CompoundTag data = player.getPersistentData();
        return new MuttonVisualPacket(player.getUUID(),
                eligible ? data.getInt(RAW_TICKS) : 0,
                eligible ? data.getInt(COOKED_TICKS) : 0,
                eligible && rawBurst, eligible && cookedBurst, eligible && foodDamage);
    }

    /**
     * Sends current state to a newly tracking observer without replaying a burst.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof ServerPlayer target) {
            ModNetwork.sendTo(observer, visualSnapshot(target, false, false, false));
        }
    }

    /**
     * Restores client visuals for countdowns loaded from the player's saved data.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncVisuals(player, true, false, false, false);
        }
    }

    /**
     * Replaces any cached visuals for the previous player entity after respawning.
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncVisuals(player, true, false, false, false);
        }
    }

    /**
     * Refreshes the owner's state when entering another dimension's tracking set.
     */
    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncVisuals(player, true, false, false, false);
        }
    }

    /**
     * Releases the outgoing-state cache; the saved gameplay countdown is retained.
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_VISUAL_STATES.remove(event.getEntity().getUUID());
    }

    /**
     * Clears static state before another integrated or dedicated server session.
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_VISUAL_STATES.clear();
    }

    /**
     * Decrements one saved countdown and reports whether a burst is due.
     * The final burst occurs on the tick that removes the countdown.
     */
    private static boolean advanceCountdown(CompoundTag data, String key, int interval) {
        if (!data.contains(key)) {
            return false;
        }

        int remaining = data.getInt(key) - 1;
        // Update the remaining countdown before checking for a burst.
        if (remaining <= 0) {
            data.remove(key);
        } else {
            data.putInt(key, remaining);
        }

        return remaining >= 0 && remaining % interval == 0;
    }

    /**
     * Returns whether gameplay effects and their visuals may affect this player.
     */
    private static boolean isEligible(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    /**
     * Uses a fixed modifier ID to avoid stacking or replacing other speed bonuses.
     * Transient modifiers are restored from the saved countdown after reconnecting.
     */
    private static void updateSpeedBoost(Player player, boolean active) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }

        // Apply or remove the speed boost based on the active state.
        if (active && !speed.hasModifier(COOKED_SPEED_BOOST)) {
            speed.addTransientModifier(COOKED_SPEED_BOOST);
        } else if (!active && speed.hasModifier(COOKED_SPEED_BOOST)) {
            speed.removeModifier(COOKED_SPEED_BOOST.getId());
        }
    }

    /**
     * Applies a personal burst without a real area explosion.
     *
     * @param level  the server world receiving the sound
     * @param player the affected player
     * @param damage combined base damage from all bursts due this tick
     * @param push   whether raw meat also adds a random horizontal impulse
     * @return whether damage was accepted, allowing replacement of its vanilla
     *         flash
     */
    private static boolean burst(ServerLevel level, Player player, float damage, boolean push) {
        // Sound and personal damage avoid real explosions affecting bystanders.
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                push ? 0.2F : 0.12F, push ? 1.6F : 1.8F);
        // Generic damage bypasses armor and uses the standard death message.
        boolean damaged = player.hurt(DamageSource.GENERIC, damage);

        // Apply the burst push only if the player survived the damage.
        if (push && player.isAlive()) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
            player.push(Math.cos(angle) * BURST_PUSH, 0.0D, Math.sin(angle) * BURST_PUSH);
            // Player motion is client-controlled; explicitly synchronize this push.
            player.hurtMarked = true;
        }
        return damaged;
    }
}
