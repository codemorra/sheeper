package de.codemorra.sheeper.util;

import net.minecraft.resources.ResourceLocation;

/** Creates resource identifiers on the Minecraft 1.19.2 runtime. */
public final class ResourceLocations {
    private ResourceLocations() {
    }

    /**
     * Uses the constructor because OptiFine's 1.19.2 replacement class lacks
     * the factory method present in the Forge development environment.
     *
     * @param namespace owning namespace
     * @param path identifier within the namespace
     * @return the resource identifier
     */
    @SuppressWarnings("removal")
    public static ResourceLocation create(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }
}
