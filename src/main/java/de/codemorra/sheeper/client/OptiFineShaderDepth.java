package de.codemorra.sheeper.client;

import java.lang.reflect.Field;
import java.nio.IntBuffer;

import javax.annotation.Nullable;

import de.codemorra.sheeper.SheeperMod;

/** Reads OptiFine 1.19.2's scene depth without requiring OptiFine on the classpath. */
final class OptiFineShaderDepth {
    @Nullable
    private static Access access = findAccess();

    private OptiFineShaderDepth() {
    }

    /** Returns true only while a shaderpack is active. */
    static boolean isActive() {
        if (access == null) {
            return false;
        }
        try {
            return access.active().getBoolean(null);
        } catch (IllegalAccessException exception) {
            disable(exception);
            return false;
        }
    }

    /** Returns the world depth texture, or zero outside the scene pass. */
    static int sceneTexture() {
        if (access == null) {
            return 0;
        }
        try {
            if (access.shadowPass().getBoolean(null)) {
                return 0;
            }
            Object framebuffer = access.framebuffer().get(null);
            if (framebuffer != null && access.depthTextures().get(framebuffer) instanceof IntBuffer textures
                    && textures.limit() > 0) {
                // depthtex0 is OptiFine's full-resolution scene depth attachment.
                return textures.get(0);
            }
        } catch (IllegalAccessException exception) {
            disable(exception);
        }
        return 0;
    }

    @Nullable
    private static Access findAccess() {
        try {
            Class<?> shaders = Class.forName("net.optifine.shaders.Shaders", false,
                    OptiFineShaderDepth.class.getClassLoader());
            Field framebuffer = shaders.getDeclaredField("dfb");
            Field depthTextures = framebuffer.getType().getDeclaredField("depthTextures");
            if (!framebuffer.trySetAccessible() || !depthTextures.trySetAccessible()) {
                throw new IllegalAccessException("OptiFine's scene depth fields are inaccessible");
            }
            return new Access(shaders.getField("shaderPackLoaded"), shaders.getField("isShadowPass"),
                    framebuffer, depthTextures);
        } catch (ClassNotFoundException exception) {
            return null;
        } catch (ReflectiveOperationException | LinkageError | SecurityException exception) {
            disable(exception);
            return null;
        }
    }

    private static void disable(Throwable exception) {
        access = null;
        SheeperMod.LOGGER.warn("Could not access OptiFine's water arena fog depth source", exception);
    }

    private record Access(Field active, Field shadowPass, Field framebuffer, Field depthTextures) {
    }
}
