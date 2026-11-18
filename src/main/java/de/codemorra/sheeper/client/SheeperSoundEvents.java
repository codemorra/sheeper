package de.codemorra.sheeper.client;

import java.util.HashMap;
import java.util.Map;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.config.SheeperClientConfig;
import de.codemorra.sheeper.entity.WaterProjectile;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Applies local cue volumes while preserving server sound positions and timing.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SheeperSoundEvents {
    private static final Map<Integer, WaterBeamSound> BEAM_SOUNDS = new HashMap<>();

    private SheeperSoundEvents() {
    }

    /**
     * Starts one locally tracked flight sound for each projectile entering the
     * client world.
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof WaterProjectile projectile) {
            Minecraft.getInstance().getSoundManager().play(new WaterProjectileSound(projectile));
        }
    }

    /**
     * Advances one local emitter per beam only while synchronized firing is active.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BEAM_SOUNDS.values().removeIf(sound -> !sound.isBeamActive());
        if (minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof WaterSheeperEntity boss && boss.isAlive() && !boss.isSilent()
                    && boss.getBeamOffset().lengthSqr() > 1.0E-8D) {
                BEAM_SOUNDS.computeIfAbsent(boss.getId(), ignored -> new WaterBeamSound(boss)).tick();
            }
        }
    }

    /**
     * Scales only dedicated mod aliases; ordinary TNT, sheep, and damage sounds
     * remain unchanged.
     */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || !sound.getLocation().getNamespace().equals(SheeperMod.MODID)) {
            return;
        }
        double multiplier = switch (sound.getLocation().getPath()) {
            case "warning_bleat", "warning_fuse" -> SheeperClientConfig.WARNING_VOLUME.get();
            case "internal_explosion_raw" -> SheeperClientConfig.RAW_INTERNAL_EXPLOSION_VOLUME.get();
            case "internal_explosion_cooked" -> SheeperClientConfig.COOKED_INTERNAL_EXPLOSION_VOLUME.get();
            default -> 1.0D;
        };
        if (multiplier == 0.0D) {
            event.setSound(null);
        } else if (multiplier != 1.0D) {
            // These aliases are one-shot sounds. Preserve all spatial properties.
            event.setSound(new SimpleSoundInstance(sound.getLocation(), sound.getSource(),
                    (float) (sound.getVolume() * multiplier), sound.getPitch(), RandomSource.create(),
                    sound.isLooping(), sound.getDelay(), sound.getAttenuation(),
                    sound.getX(), sound.getY(), sound.getZ(), sound.isRelative()));
        }
    }
}
