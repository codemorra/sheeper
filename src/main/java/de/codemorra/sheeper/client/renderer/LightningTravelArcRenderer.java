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

/** Draws the travelling lightning and the compact charge it forms at the arena center. */
final class LightningTravelArcRenderer {
    private LightningTravelArcRenderer() {
    }

    static void render(LightningSheeperEntity boss, TravelArc arc, float partialTick, PoseStack poses,
            MultiBufferSource buffers) {
        if (arc.isCharging()) {
            renderCharge(boss, arc, partialTick, poses, buffers);
            return;
        }
        Vec3 direction = arc.end().subtract(arc.start());
        Vec3 side = new Vec3(-direction.z, 0, direction.x).normalize();
        if (side.lengthSqr() == 0) {
            side = new Vec3(1, 0, 0);
        }
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

    /** Keeps short branching bolts attached to the body while its charge builds and fades. */
    static void renderLineCharge(LightningSheeperEntity boss, Vec3 feet, float partialTick, PoseStack poses,
            MultiBufferSource buffers) {
        double time = (boss.tickCount + (double) partialTick) * 0.72;
        double width = boss.getBbWidth();
        double height = boss.getBbHeight();
        float yaw = -Mth.rotLerp(partialTick, boss.yBodyRotO, boss.yBodyRot) * Mth.DEG_TO_RAD;
        float charge = boss.getLineCharge(partialTick);
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = poses.last().pose();
        for (int index = 0; index < 6; index++) {
            double phase = time + index * 2.61;
            double angle = index * Math.PI / 3 + Math.sin(phase * 0.28) * 0.33;
            double y = height * (0.39 + (index % 3) * 0.22);
            Vec3 start = bodySparkPoint(feet, width, y - height * 0.16, yaw, angle - 0.26);
            Vec3 bend = bodySparkPoint(feet, width * (1.08 + 0.09 * Math.sin(phase)), y, yaw, angle + 0.24);
            Vec3 end = bodySparkPoint(feet, width, y + height * 0.16, yaw, angle + 0.02);
            Vec3 side = new Vec3(Math.cos(angle + 0.5), 0, Math.sin(angle + 0.5)).yRot(yaw).scale(0.55);
            Vec3 up = new Vec3(0, 0.55, 0);
            float alpha = charge * (float) (0.76 + 0.24 * Math.sin(phase));
            drawSegment(buffer, matrix, start, bend, side, up, alpha);
            drawSegment(buffer, matrix, bend, end, side, up, alpha * 0.8F);
            if (index % 2 == 0) {
                Vec3 branch = bodySparkPoint(feet, width * 1.22, y + height * 0.04, yaw, angle + 0.47);
                drawSegment(buffer, matrix, bend, branch, side, up, alpha * 0.55F);
            }
        }
    }

    private static Vec3 bodySparkPoint(Vec3 feet, double width, double y, float yaw, double angle) {
        return feet.add(new Vec3(Math.cos(angle) * width * 0.62, y, Math.sin(angle) * width).yRot(yaw));
    }

    /** Connects several flickering paths through a common core at the travel endpoint. */
    private static void renderCharge(LightningSheeperEntity boss, TravelArc arc, float partialTick, PoseStack poses,
            MultiBufferSource buffers) {
        Vec3 center = arc.end().subtract(boss.getPosition(partialTick));
        double time = boss.level.getGameTime() + (double) partialTick;
        double radius = boss.getBbWidth() * (0.24 + 0.15 * arc.charge());
        double height = boss.getBbHeight() * (0.45 + 0.15 * arc.charge());
        Vec3 side = new Vec3(0.75, 0, 0);
        Vec3 up = new Vec3(0, 0, 0.75);
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = poses.last().pose();
        for (int index = 0; index < 4; index++) {
            double phase = time * (0.52 + index * 0.09) + index * 2.51;
            double x = Math.cos(phase) * radius;
            double z = Math.sin(phase * 1.27) * radius;
            Vec3 top = center.add(x * 0.56, height * (0.7 + 0.22 * Math.sin(phase * 1.65)), z * 0.56);
            Vec3 bend = center.add(x, height * 0.29, z);
            Vec3 lowerBend = center.add(-x * 0.72, -height * 0.27, -z * 0.72);
            Vec3 bottom = center.add(x * 0.15, -height * (0.74 + 0.15 * Math.cos(phase)), z * 0.33);
            float alpha = (float) (0.43 + 0.28 * arc.charge() + 0.19 * Math.sin(phase * 1.91));
            drawSegment(buffer, matrix, top, bend, side, up, alpha * 0.55F);
            drawSegment(buffer, matrix, bend, center, side, up, alpha);
            drawSegment(buffer, matrix, center, lowerBend, side, up, alpha);
            drawSegment(buffer, matrix, lowerBend, bottom, side, up, alpha * 0.55F);
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
