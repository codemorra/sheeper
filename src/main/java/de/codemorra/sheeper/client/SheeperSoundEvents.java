package de.codemorra.sheeper.client;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.config.SheeperClientConfig;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Applies local cue volumes while preserving server sound positions and timing.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SheeperSoundEvents {
    private SheeperSoundEvents() {
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
