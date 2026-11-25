package de.codemorra.sheeper.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.world.phys.Vec3;

/** Draws a two-segment lightning arc with a bright core and a wider blue glow. */
final class LightningWorldArcs {
    private LightningWorldArcs() {
    }

    static void arc(BufferBuilder buffer, Vec3 camera, Vec3 from, Vec3 bend, Vec3 to, double width, float alpha) {
        Vec3 start = from.subtract(camera);
        Vec3 elbow = bend.subtract(camera);
        Vec3 end = to.subtract(camera);
        segment(buffer, start, elbow, width, alpha);
        segment(buffer, elbow, end, width, alpha);
    }

    private static void segment(BufferBuilder buffer, Vec3 from, Vec3 to, double width, float alpha) {
        Vec3 forward = to.subtract(from).normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x).normalize().scale(width);
        if (side.lengthSqr() == 0) {
            side = new Vec3(width, 0, 0);
        }
        Vec3 up = forward.cross(side);
        ribbon(buffer, from, to, side.scale(2.6), 0.28F, 0.63F, alpha * 0.42F);
        ribbon(buffer, from, to, up.scale(2.6), 0.28F, 0.63F, alpha * 0.42F);
        ribbon(buffer, from, to, side, 0.80F, 0.94F, alpha);
        ribbon(buffer, from, to, up, 0.80F, 0.94F, alpha);
    }

    private static void ribbon(BufferBuilder buffer, Vec3 from, Vec3 to, Vec3 width, float red, float green,
            float alpha) {
        vertex(buffer, from.add(width), red, green, alpha);
        vertex(buffer, from.subtract(width), red, green, alpha);
        vertex(buffer, to.subtract(width), red, green, alpha);
        vertex(buffer, to.add(width), red, green, alpha);
    }

    private static void vertex(BufferBuilder buffer, Vec3 point, float red, float green, float alpha) {
        buffer.vertex((float) point.x, (float) point.y, (float) point.z).color(red, green, 1, alpha).endVertex();
    }
}
