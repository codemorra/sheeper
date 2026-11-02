package de.codemorra.sheeper.registry;

import de.codemorra.sheeper.SheeperMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers particle types on both sides; rendering providers remain
 * client-only.
 */
public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister
            .create(ForgeRegistries.PARTICLE_TYPES, SheeperMod.MODID);

    public static final RegistryObject<SimpleParticleType> MUTTON_CLOUD = PARTICLES.register("mutton_cloud",
            () -> new SimpleParticleType(false));

    private ModParticles() {
    }

    /** Connects particle registration to the mod event bus during construction. */
    public static void register(IEventBus eventBus) {
        PARTICLES.register(eventBus);
    }
}
