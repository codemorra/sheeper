package de.codemorra.sheeper.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.entity.LightningSheeperEntity.TravelArc;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Draws a moving lightning tip and a fading trail between fixed world-space endpoints. */
final class LightningTravelArcRenderer {
    private LightningTravelArcRenderer() {
    }

    static void render(LightningSheeperEntity boss, TravelArc arc, float partialTick, PoseStack poses,
            MultiBufferSource buffers) {
        Vec3 direction = arc.end().subtract(arc.start());
        Vec3 side = new Vec3(-direction.z, 0, direction.x).normalize();
        Vec3 up = side.cross(direction).normalize();
        int segments = Mth.clamp(Mth.ceil(direction.length() * 1.6), 5, 128);
        double time = boss.level.getGameTime() + (double) partialTick;
        double head = Math.min(segments, 0.7 + arc.progress(time) * segments);
        double tail = Math.max(0, head - Math.max(4, segments * 0.45));
        Vec3 offset = boss.getPosition(partialTick);
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = poses.last().pose();
        for (int index = Mth.floor(tail); index < head; index++) {
            Vec3 from = arcPoint(arc, side, up, segments, Math.max(index, tail)).subtract(offset);
            Vec3 to = arcPoint(arc, side, up, segments, Math.min(index + 1, head)).subtract(offset);
            float alpha = (float) Mth.clamp((index + 1 - tail) / (head - tail), 0.08, 1);
            drawSegment(buffer, matrix, from, to, side, up, alpha);
        }
    }

    /** Crossed ribbons keep lightning visible from either side of its path. */
    static void drawSegment(VertexConsumer buffer, Matrix4f matrix, Vec3 from, Vec3 to, Vec3 side, Vec3 up,
            float alpha) {
        ribbon(buffer, matrix, from, to, side.scale(0.16), 0.25F, 0.65F, alpha * 0.55F);
        ribbon(buffer, matrix, from, to, up.scale(0.16), 0.25F, 0.65F, alpha * 0.55F);
        ribbon(buffer, matrix, from, to, side.scale(0.055), 0.82F, 0.95F, alpha);
        ribbon(buffer, matrix, from, to, up.scale(0.055), 0.82F, 0.95F, alpha);
    }

    private static Vec3 arcPoint(TravelArc arc, Vec3 side, Vec3 up, int segments, double point) {
        int index = Mth.floor(point);
        return node(arc, side, up, segments, index)
                .lerp(node(arc, side, up, segments, Math.min(segments, index + 1)), point - index);
    }

    /** Keeps the same jagged shape while the tip travels along it. */
    private static Vec3 node(TravelArc arc, Vec3 side, Vec3 up, int segments, int index) {
        double fraction = (double) index / segments;
        double envelope = Math.sin(Math.PI * fraction);
        double phase = (arc.startTick() & 255) * 0.41 + index * 2.6;
        return arc.start().lerp(arc.end(), fraction)
                .add(side.scale(Math.sin(phase) * 0.55 * envelope))
                .add(up.scale((0.8 + Math.cos(phase * 1.3) * 0.44) * envelope));
    }

    private static void ribbon(VertexConsumer buffer, Matrix4f matrix, Vec3 from, Vec3 to, Vec3 width,
            float red, float green, float alpha) {
        Vec3 leftStart = from.subtract(width);
        Vec3 rightStart = from.add(width);
        Vec3 rightEnd = to.add(width);
        Vec3 leftEnd = to.subtract(width);
        vertex(buffer, matrix, leftStart, red, green, alpha);
        vertex(buffer, matrix, rightStart, red, green, alpha);
        vertex(buffer, matrix, rightEnd, red, green, alpha);
        vertex(buffer, matrix, leftEnd, red, green, alpha);
        // The vanilla lightning render type culls back faces.
        vertex(buffer, matrix, leftEnd, red, green, alpha);
        vertex(buffer, matrix, rightEnd, red, green, alpha);
        vertex(buffer, matrix, rightStart, red, green, alpha);
        vertex(buffer, matrix, leftStart, red, green, alpha);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 point,
            float red, float green, float alpha) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(red, green, 1, alpha).endVertex();
    }
}
