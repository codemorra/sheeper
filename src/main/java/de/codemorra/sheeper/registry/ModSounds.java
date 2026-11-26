package de.codemorra.sheeper.registry;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.util.ResourceLocations;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Identifies mod cues separately so local volume settings do not alter vanilla
 * sounds.
 * Sound descriptions can reference bundled samples or vanilla sound events.
 */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,
            SheeperMod.MODID);

    public static final RegistryObject<SoundEvent> WARNING_BLEAT = register("warning_bleat");
    public static final RegistryObject<SoundEvent> WARNING_FUSE = register("warning_fuse");
    public static final RegistryObject<SoundEvent> RAW_INTERNAL_EXPLOSION = register("internal_explosion_raw");
    public static final RegistryObject<SoundEvent> COOKED_INTERNAL_EXPLOSION = register("internal_explosion_cooked");
    public static final RegistryObject<SoundEvent> LIGHTNING_FAN_CRACKLE = register("lightning_fan_crackle");

    private ModSounds() {
    }

    /**
     * Registers a variable-range cue; sound contents are supplied by sounds.json.
     */
    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> new SoundEvent(ResourceLocations.create(SheeperMod.MODID, name)));
    }

    /** Connects sound registration to the mod bus on both physical sides. */
    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}
