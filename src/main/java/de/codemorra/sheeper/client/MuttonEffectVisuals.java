package de.codemorra.sheeper.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.client.particle.MuttonBurstParticles;
import de.codemorra.sheeper.network.MuttonVisualPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only visual interpolation. This cache never drives gameplay timers.
 * Packet handlers and tick callbacks access it only on the client thread.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MuttonEffectVisuals {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final int FADE_TICKS = 40;
    private static final int COOKED_PUFF_INTERVAL_TICKS = 4;
    private static final float BASE_INTENSITY = 0.30F;
    private static final float PULSE_AMPLITUDE = 0.60F;
    private static final float PULSE_DECAY = 0.94F;
    private static final float COOKED_STRENGTH = 0.25F;

    private MuttonEffectVisuals() {
    }

    /**
     * Reconciles predicted visual time with a server snapshot and starts any pulse.
     * A final burst may have zero remaining ticks and still needs its full fade.
     *
     * @param packet the authoritative snapshot, handled on the client thread
     */
    public static void receive(MuttonVisualPacket packet) {
        if (packet.foodDamage() && Minecraft.getInstance().level != null) {
            Player player = Minecraft.getInstance().level.getPlayerByUUID(packet.playerId());
            if (player != null) {
                // Damage animation and current health precede this snapshot. Replace
                // only that food-induced flash; later ordinary hits still animate.
                player.hurtTime = 0;
            }
        }
        State state = STATES.computeIfAbsent(packet.playerId(), id -> new State());
        state.raw.receive(packet.rawTicks(), packet.rawBurst());
        state.cooked.receive(packet.cookedTicks(), packet.cookedBurst());
        if ((packet.rawBurst() || packet.cookedBurst()) && Minecraft.getInstance().level != null) {
            Player player = Minecraft.getInstance().level.getPlayerByUUID(packet.playerId());
            if (player != null) {
                if (packet.rawBurst()) {
                    MuttonBurstParticles.spawnRaw(player);
                }
                if (packet.cookedBurst()) {
                    MuttonBurstParticles.spawnCookedBurst(player);
                }
            }
        }
    }

    /**
     * Returns the shared skin/viewport intensity, including the final fade.
     * Overlapping variants use the stronger contribution instead of adding opacity.
     *
     * @param player      the player being rendered
     * @param partialTick interpolation between the previous and current client tick
     * @return the interpolated intensity, or zero for missing or excluded players
     */
    public static float getIntensity(Player player, float partialTick) {
        State state = visibleState(player);
        return state == null ? 0.0F
                : Math.max(state.raw.getIntensity(partialTick),
                        state.cooked.getIntensity(partialTick) * COOKED_STRENGTH);
    }

    /** Returns cached state only for living Survival or Adventure players. */
    private static State visibleState(Player player) {
        return !player.isAlive() || player.isCreative() || player.isSpectator()
                ? null
                : STATES.get(player.getUUID());
    }

    /**
     * Advances visual envelopes once per unpaused client tick and discards them
     * only after both the active effects and their interpolated fades have ended.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.level == null || minecraft.isPaused()) {
            return;
        }

        MuttonBurstParticles.tick();
        STATES.entrySet().removeIf(entry -> {
            State state = entry.getValue();
            // Cooked vapor is continuous during the active countdown, not the
            // cosmetic fade. Emit from local ticks rather than packet arrivals.
            if (state.cooked.ticks > 0 && state.cooked.ticks % COOKED_PUFF_INTERVAL_TICKS == 0) {
                Player player = minecraft.level.getPlayerByUUID(entry.getKey());
                if (player != null) {
                    MuttonBurstParticles.emitCooked(player);
                }
            }
            state.raw.tick();
            state.cooked.tick();
            return state.raw.isFinished() && state.cooked.isFinished();
        });
    }

    /**
     * Prevents cached UUIDs and pending fades from leaking into another session.
     */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
        MuttonBurstParticles.clear();
    }

    /**
     * Holds separate, unscaled curves so either variant can finish independently.
     */
    private static final class State {
        private final Envelope raw = new Envelope();
        private final Envelope cooked = new Envelope();
    }

    /**
     * Keeps each variant's intensity through its own final fade, including when
     * the other variant remains active. Stop snapshots never restart a running
     * fade.
     */
    private static final class Envelope {
        private int ticks;
        private float pulse;
        private float previousIntensity;
        private float intensity;
        private int fadeTicks;
        private float fadeStart;

        /**
         * Refreshes remaining time without resetting an existing pulse. A stop
         * begins a fade once; an actual final burst can raise its starting peak.
         */
        private void receive(int remaining, boolean burst) {
            boolean wasActive = ticks > 0;
            ticks = Math.max(0, remaining);
            if (ticks > 0) {
                fadeTicks = 0;
                if (burst) {
                    pulse = PULSE_AMPLITUDE;
                }
            } else if (burst || (wasActive && fadeTicks == 0)) {
                startFade(burst);
            }
        }

        /** Advances the fast-rise/slow-decay curve or its finite final fade. */
        private void tick() {
            previousIntensity = intensity;
            if (ticks > 0) {
                float target = BASE_INTENSITY + pulse;
                float response = target > intensity ? 0.85F : 0.12F;
                intensity += (target - intensity) * response;
                pulse *= PULSE_DECAY;
                if (--ticks == 0) {
                    startFade(false);
                }
            } else if (fadeTicks > 0) {
                float progress = 1.0F - (float) --fadeTicks / FADE_TICKS;
                // Smoothstep reaches zero with a flat slope instead of cutting off.
                float smooth = progress * progress * (3.0F - 2.0F * progress);
                intensity = fadeStart * (1.0F - smooth);
            }
        }

        /**
         * Preserves the current brightness when stopped, or includes the final
         * burst's peak before fading. This does not extend the gameplay effect.
         */
        private void startFade(boolean finalBurst) {
            fadeStart = finalBurst ? Math.max(intensity, BASE_INTENSITY + PULSE_AMPLITUDE) : intensity;
            intensity = fadeStart;
            fadeTicks = FADE_TICKS;
            pulse = 0.0F;
        }

        /** Interpolates the unscaled curve between client ticks. */
        private float getIntensity(float partialTick) {
            return Mth.lerp(partialTick, previousIntensity, intensity);
        }

        /**
         * Retains the last interpolation frame even after current intensity is zero.
         */
        private boolean isFinished() {
            return ticks == 0 && fadeTicks == 0 && intensity == 0.0F && previousIntensity == 0.0F;
        }
    }
}
