package de.codemorra.sheeper.client.renderer.layer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;

/**
 * Repeats elemental textures at four times the original UV density. Consumes
 * the
 * complete, uniformly colored quads emitted by ModelPart.Cube.compile.
 * Faces are split at tile edges so atlas sampling stays inside the chosen
 * sprite.
 */
final class TiledFleeceVertexConsumer extends VertexConsumerWrapper {
    private static final int REPEATS = 4;
    private final float[][] vertices = new float[4][5];
    private final float[] horizontalCuts = new float[REPEATS + 2];
    private final float[] verticalCuts = new float[REPEATS + 2];
    private final float surfaceOffset;
    private final float flowOffset;
    private int count;

    TiledFleeceVertexConsumer(VertexConsumer parent, float surfaceOffset) {
        this(parent, surfaceOffset, 0.0F);
    }

    /** Adds vertical texture motion while retaining tile-boundary splitting. */
    TiledFleeceVertexConsumer(VertexConsumer parent, float surfaceOffset, float flowOffset) {
        super(parent);
        this.surfaceOffset = surfaceOffset;
        this.flowOffset = flowOffset;
    }

    @Override
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha,
            float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
        float[] vertex = vertices[count++];
        // Move each face along its outward normal, including top and inner faces.
        float offset = surfaceOffset / Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        vertex[0] = x + normalX * offset;
        vertex[1] = y + normalY * offset;
        vertex[2] = z + normalZ * offset;
        vertex[3] = u * REPEATS;
        vertex[4] = (v + flowOffset) * REPEATS;
        if (count < 4) {
            return;
        }
        count = 0;
        float startU = vertices[0][3];
        float startV = vertices[0][4];
        float deltaU = vertices[1][3] - startU;
        float deltaV = vertices[3][4] - startV;
        int columns = cuts(startU, deltaU, horizontalCuts);
        int rows = cuts(startV, deltaV, verticalCuts);
        for (int column = 0; column < columns - 1; column++) {
            float left = horizontalCuts[column];
            float right = horizontalCuts[column + 1];
            int tileU = Mth.floor(startU + deltaU * (left + right) * 0.5F);
            for (int row = 0; row < rows - 1; row++) {
                float top = verticalCuts[row];
                float bottom = verticalCuts[row + 1];
                int tileV = Mth.floor(startV + deltaV * (top + bottom) * 0.5F);
                emit(left, top, tileU, tileV, red, green, blue, alpha, overlay, light, normalX, normalY, normalZ);
                emit(right, top, tileU, tileV, red, green, blue, alpha, overlay, light, normalX, normalY, normalZ);
                emit(right, bottom, tileU, tileV, red, green, blue, alpha, overlay, light, normalX, normalY, normalZ);
                emit(left, bottom, tileU, tileV, red, green, blue, alpha, overlay, light, normalX, normalY, normalZ);
            }
        }
    }

    /**
     * Returns face coordinates at each integer UV boundary, including both ends.
     */
    private static int cuts(float start, float delta, float[] result) {
        int length = 0;
        result[length++] = 0.0F;
        if (delta != 0.0F) {
            int direction = delta > 0.0F ? 1 : -1;
            int boundary = delta > 0.0F ? Mth.floor(start) + 1 : Mth.ceil(start) - 1;
            float position = (boundary - start) / delta;
            while (position < 1.0F) {
                result[length++] = position;
                boundary += direction;
                position = (boundary - start) / delta;
            }
        }
        result[length++] = 1.0F;
        return length;
    }

    private void emit(float horizontal, float vertical, int tileU, int tileV,
            float red, float green, float blue, float alpha, int overlay, int light,
            float normalX, float normalY, float normalZ) {
        float[] origin = vertices[0];
        float[] across = vertices[1];
        float[] down = vertices[3];
        float x = origin[0] + (across[0] - origin[0]) * horizontal + (down[0] - origin[0]) * vertical;
        float y = origin[1] + (across[1] - origin[1]) * horizontal + (down[1] - origin[1]) * vertical;
        float z = origin[2] + (across[2] - origin[2]) * horizontal + (down[2] - origin[2]) * vertical;
        float u = Mth.clamp(origin[3] + (across[3] - origin[3]) * horizontal - tileU, 0.0F, 1.0F);
        float v = Mth.clamp(origin[4] + (down[4] - origin[4]) * vertical - tileV, 0.0F, 1.0F);
        parent.vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, normalX, normalY, normalZ);
    }
}
