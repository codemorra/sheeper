package de.codemorra.sheeper.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import de.codemorra.sheeper.SheeperMod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Submits local rain through OptiFine's regular weather program when available. */
final class OptiFineWeather {
    @Nullable
    private static Access access = findAccess();

    private OptiFineWeather() {
    }

    /**
     * Runs during world rendering, after normal precipitation has flushed.
     *
     * @return whether the shaderpack handled the arena rain on this frame
     */
    static boolean draw(Runnable rain) {
        Access hooks = access;
        if (hooks == null) {
            return false;
        }
        try {
            if (!hooks.world().getBoolean(null) || !hooks.framebuffer().getBoolean(null)
                    || hooks.shadow().getBoolean(null)) {
                return false;
            }
            return drawWeather(hooks, rain);
        } catch (ReflectiveOperationException | LinkageError exception) {
            access = null;
            SheeperMod.LOGGER.warn("Could not render arena rain through OptiFine's weather pass", exception);
            return false;
        }
    }

    private static boolean drawWeather(Access hooks, Runnable rain) throws ReflectiveOperationException {
        var previousShader = RenderSystem.getShader();
        float[] previousColor = RenderSystem.getShaderColor().clone();
        int previousTexture = RenderSystem.getShaderTexture(0);
        int previousLightmap = RenderSystem.getShaderTexture(2);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int previousSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int previousDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int previousSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int previousDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            hooks.begin().invoke(null);
            if (hooks.programId().getInt(null) <= 0) {
                return false;
            }
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask((boolean) hooks.rainDepth().invoke(null));
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            drawWithRainUniform(hooks, rain);
            return true;
        } finally {
            try {
                hooks.end().invoke(null);
            } finally {
                RenderSystem.setShader(() -> previousShader);
                RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
                RenderSystem.setShaderTexture(0, previousTexture);
                RenderSystem.setShaderTexture(2, previousLightmap);
                RenderSystem.depthMask(previousDepthMask);
                RenderSystem.blendFuncSeparate(previousSrcRgb, previousDstRgb, previousSrcAlpha, previousDstAlpha);
                if (previousBlend) {
                    RenderSystem.enableBlend();
                } else {
                    RenderSystem.disableBlend();
                }
                if (previousDepthTest) {
                    RenderSystem.enableDepthTest();
                } else {
                    RenderSystem.disableDepthTest();
                }
                if (previousCull) {
                    RenderSystem.enableCull();
                } else {
                    RenderSystem.disableCull();
                }
            }
        }
    }

    /** Leaves OptiFine's world rain and wetness values alone. Vertex alpha still fades the local rain. */
    private static void drawWithRainUniform(Access hooks, Runnable rain) throws ReflectiveOperationException {
        Object uniform = hooks.rainStrength().get(null);
        if (uniform == null || !(boolean) hooks.uniformDefined().invoke(uniform)) {
            rain.run();
            return;
        }
        float previous = (float) hooks.uniformGet().invoke(uniform);
        hooks.uniformSet().invoke(uniform, 1.0F);
        try {
            rain.run();
        } finally {
            hooks.uniformSet().invoke(uniform, previous);
        }
    }

    @Nullable
    private static Access findAccess() {
        try {
            Class<?> shaders = Class.forName("net.optifine.shaders.Shaders", false,
                    OptiFineWeather.class.getClassLoader());
            Field strength = shaders.getField("uniform_rainStrength");
            Class<?> uniform = strength.getType();
            return new Access(shaders.getField("isRenderingWorld"), shaders.getField("isRenderingDfb"),
                    shaders.getField("isShadowPass"), shaders.getField("activeProgramID"),
                    shaders.getMethod("beginWeather"), shaders.getMethod("endWeather"), shaders.getMethod("isRainDepth"),
                    strength, uniform.getMethod("isDefined"), uniform.getMethod("getValue"),
                    uniform.getMethod("setValue", float.class));
        } catch (ClassNotFoundException exception) {
            return null;
        } catch (ReflectiveOperationException | LinkageError | SecurityException exception) {
            SheeperMod.LOGGER.warn("OptiFine's weather pass is unavailable for arena rain", exception);
            return null;
        }
    }

    private record Access(Field world, Field framebuffer, Field shadow, Field programId, Method begin, Method end,
            Method rainDepth, Field rainStrength, Method uniformDefined, Method uniformGet, Method uniformSet) {
    }
}
