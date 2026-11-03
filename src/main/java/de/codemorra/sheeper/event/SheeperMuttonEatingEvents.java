package de.codemorra.sheeper.event;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.registry.ModItems;
import de.codemorra.sheeper.registry.ModSounds;
import de.codemorra.sheeper.network.ModNetwork;
import de.codemorra.sheeper.network.MuttonVisualPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * Applies internal explosions after eating Sheeper mutton and a speed bonus for
 * cooked meat. Separate saved countdowns refresh rather than stack across
 * meals.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SheeperMuttonEatingEvents {
    // Only accessed on the server thread; entries are removed when players leave.
    private static final Map<UUID, Integer> LAST_VISUAL_STATES = new HashMap<>();
    private static final String RAW_TICKS = SheeperMod.MODID + ":raw_mutton_ticks";
    private static final String COOKED_TICKS = SheeperMod.MODID + ":cooked_mutton_ticks";
    private static final String RAW_EXPLOSION_WAIT = SheeperMod.MODID + ":raw_explosion_wait";
    private static final String COOKED_EXPLOSION_WAIT = SheeperMod.MODID + ":cooked_explosion_wait";
    private static final UUID COOKED_SPEED_BOOST_ID = UUID.fromString("27539948-57fb-49be-934c-7c1b97fc5982");

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
            startCountdown(player.getPersistentData(), RAW_TICKS, RAW_EXPLOSION_WAIT,
                    SheeperConfig.RAW_MUTTON_DURATION.get(), SheeperConfig.RAW_MUTTON_INTERVAL.get());
        } else if (event.getItem().is(ModItems.COOKED_SHEEPER_MUTTON.get())) {
            startCountdown(player.getPersistentData(), COOKED_TICKS, COOKED_EXPLOSION_WAIT,
                    SheeperConfig.COOKED_MUTTON_DURATION.get(), SheeperConfig.COOKED_MUTTON_INTERVAL.get());
            updateSpeedBoost(player, isEligible(player) && player.getPersistentData().getInt(COOKED_TICKS) > 0);
        } else if (event.getItem().is(Items.MILK_BUCKET)) {
            player.getPersistentData().remove(RAW_TICKS);
            player.getPersistentData().remove(COOKED_TICKS);
            player.getPersistentData().remove(RAW_EXPLOSION_WAIT);
            player.getPersistentData().remove(COOKED_EXPLOSION_WAIT);
            updateSpeedBoost(player, false);
        } else {
            return;
        }
        syncVisuals(player, true, false, false, false);
    }

    /**
     * Advances both countdowns and keeps the speed bonus in sync with cooked meat.
     * Each variant uses its configured internal-explosion interval.
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
            data.remove(RAW_EXPLOSION_WAIT);
            data.remove(COOKED_EXPLOSION_WAIT);
            updateSpeedBoost(player, false);
            syncVisuals(player, false, false, false, false);
            return;
        }

        boolean rawBurst = advanceCountdown(data, RAW_TICKS, RAW_EXPLOSION_WAIT,
                SheeperConfig.secondsToTicks(SheeperConfig.RAW_MUTTON_INTERVAL.get()));
        boolean cookedBurst = advanceCountdown(data, COOKED_TICKS, COOKED_EXPLOSION_WAIT,
                SheeperConfig.secondsToTicks(SheeperConfig.COOKED_MUTTON_INTERVAL.get()));
        boolean eligible = isEligible(player);

        updateSpeedBoost(player, eligible && data.getInt(COOKED_TICKS) > 0);

        boolean foodDamage = false;
        if (eligible && (rawBurst || cookedBurst)) {
            // Combine simultaneous internal explosions so hurt immunity does not swallow
            // one.
            float damage = (rawBurst ? SheeperConfig.RAW_MUTTON_DAMAGE.get().floatValue() : 0.0F)
                    + (cookedBurst ? SheeperConfig.COOKED_MUTTON_DAMAGE.get().floatValue() : 0.0F);
            foodDamage = internalExplosion(level, player, damage, rawBurst);
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
     * Captures duration/initial interval at consumption; a zero duration clears
     * this variant.
     */
    private static void startCountdown(CompoundTag data, String key, String waitKey,
            double durationSeconds, double intervalSeconds) {
        int duration = SheeperConfig.secondsToTicks(durationSeconds);
        if (duration == 0) {
            data.remove(key);
            data.remove(waitKey);
        } else {
            data.putInt(key, duration);
            data.putInt(waitKey, Math.max(1, SheeperConfig.secondsToTicks(intervalSeconds)));
        }
    }

    /**
     * Advances duration and an independent interval countdown. This ensures an
     * arbitrary duration still waits a full interval before the first explosion.
     * New interval settings take effect after the next scheduled explosion.
     */
    private static boolean advanceCountdown(CompoundTag data, String key, String waitKey, int interval) {
        if (!data.contains(key)) {
            data.remove(waitKey);
            return false;
        }

        int intervalTicks = Math.max(1, interval);
        int previousRemaining = data.getInt(key);
        int remaining = previousRemaining - 1;
        // Older saved effects have no independent interval counter. Preserve their
        // remaining phase until the next internal explosion, then use this config.
        int legacyPhase = previousRemaining % intervalTicks;
        int wait = (data.contains(waitKey) ? data.getInt(waitKey)
                : legacyPhase == 0 ? intervalTicks : legacyPhase) - 1;
        // Update the remaining countdown before checking for an internal explosion.
        if (remaining <= 0) {
            data.remove(key);
            data.remove(waitKey);
        } else {
            data.putInt(key, remaining);
            data.putInt(waitKey, wait <= 0 ? intervalTicks : wait);
        }

        return remaining >= 0 && wait <= 0;
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

        double amount = SheeperConfig.COOKED_MUTTON_SPEED_PERCENT.get() / 100.0D;
        AttributeModifier current = speed.getModifier(COOKED_SPEED_BOOST_ID);
        // A config reload replaces only our modifier, without stacking or altering
        // other bonuses.
        if (current != null && (!active || amount == 0.0D || current.getAmount() != amount)) {
            speed.removeModifier(COOKED_SPEED_BOOST_ID);
        }
        if (active && amount > 0.0D && speed.getModifier(COOKED_SPEED_BOOST_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(COOKED_SPEED_BOOST_ID,
                    "Cooked Sheeper mutton speed boost", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /**
     * Applies a personal internal explosion without a real area explosion.
     *
     * @param level  the server world receiving the sound
     * @param player the affected player
     * @param damage combined base damage from all internal explosions due this tick
     * @param raw    whether raw meat also adds its configured horizontal impulse
     * @return whether damage was accepted, allowing replacement of its vanilla
     *         flash
     */
    private static boolean internalExplosion(ServerLevel level, Player player, float damage, boolean raw) {
        // Sound and personal damage avoid real explosions affecting bystanders.
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                raw ? ModSounds.RAW_INTERNAL_EXPLOSION.get() : ModSounds.COOKED_INTERNAL_EXPLOSION.get(),
                SoundSource.PLAYERS, raw ? 0.2F : 0.12F, raw ? 1.6F : 1.8F);
        // Generic damage bypasses armor and uses the standard death message.
        boolean damaged = damage > 0.0F && player.hurt(DamageSource.GENERIC, damage);

        // Apply the burst push only if the player survived the damage.
        double push = SheeperConfig.RAW_MUTTON_PUSH.get();
        if (raw && push > 0.0D && player.isAlive()) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
            player.push(Math.cos(angle) * push, 0.0D, Math.sin(angle) * push);
            // Player motion is client-controlled; explicitly synchronize this push.
            player.hurtMarked = true;
        }
        return damaged;
    }
}
