package de.codemorra.sheeper.client.renderer;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.entity.LightningSheeperEntity.TravelArc;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.phys.Vec3;

/** Draws the travelling lightning and the compact charge it forms at the arena center. */
public final class LightningTravelArcRenderer {
    private LightningTravelArcRenderer() {
    }

    /** Returns whether the boss has lightning geometry to submit for this frame. */
    public static boolean hasEffect(LightningSheeperEntity boss, @Nullable TravelArc arc, float partialTick) {
        return !boss.isInvisible() && (arc != null || boss.getDeathSparkOpacity(partialTick) > 0
                || boss.isAlive() && boss.getLineCharge(partialTick) > 0);
    }

    /** Writes the boss's electrical effects using a pose translated to the boss's interpolated position. */
    public static void draw(LightningSheeperEntity boss, @Nullable TravelArc arc, float partialTick,
            Matrix4f matrix, VertexConsumer buffer) {
        float charge = boss.getLineCharge(partialTick);
        if (arc != null) {
            renderArc(boss, arc, partialTick, matrix, buffer);
            if (!arc.isCharging() && charge > 0) {
                double time = boss.level.getGameTime() + (double) partialTick;
                Vec3 feet = arc.start().lerp(arc.end(), arc.progress(time))
                        .subtract(boss.getPosition(partialTick)).add(0, -boss.getBbHeight() * 0.5, 0);
                renderBodySparks(boss, feet, partialTick, matrix, buffer, charge);
            }
        } else {
            float sparks = boss.isAlive() ? charge : boss.getDeathSparkOpacity(partialTick);
            if (sparks > 0) {
                renderBodySparks(boss, Vec3.ZERO, partialTick, matrix, buffer, sparks);
            }
        }
    }

    private static void renderArc(LightningSheeperEntity boss, TravelArc arc, float partialTick, Matrix4f matrix,
            VertexConsumer buffer) {
        if (arc.isCharging()) {
            renderCharge(boss, arc, partialTick, matrix, buffer);
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
        for (int index = Mth.floor(tail); index < head; index++) {
            Vec3 from = arcPoint(arc, side, up, segments, Math.max(index, tail)).subtract(offset);
            Vec3 to = arcPoint(arc, side, up, segments, Math.min(index + 1, head)).subtract(offset);
            float alpha = (float) Mth.clamp((index + 1 - tail) / (head - tail), 0.08, 1);
            drawSegment(buffer, matrix, from, to, side, up, alpha);
        }
    }

    /** Draws the electrical silhouette for both the row charge and the death fade. */
    private static void renderBodySparks(LightningSheeperEntity boss, Vec3 feet, float partialTick, Matrix4f matrix,
            VertexConsumer buffer, float charge) {
        double time = (boss.tickCount + (double) partialTick) * 0.72;
        double width = boss.getBbWidth();
        double height = boss.getBbHeight();
        float yaw = -Mth.rotLerp(partialTick, boss.yBodyRotO, boss.yBodyRot) * Mth.DEG_TO_RAD;
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

    /** Builds shifting, irregular forks around the charge's core. */
    private static void renderCharge(LightningSheeperEntity boss, TravelArc arc, float partialTick, Matrix4f matrix,
            VertexConsumer buffer) {
        Vec3 center = arc.end().subtract(boss.getPosition(partialTick));
        double time = boss.level.getGameTime() + (double) partialTick;
        float charge = arc.chargeAt(time);
        if (arc.compact()) {
            charge = Mth.lerp(charge, 0.16F, 0.32F);
        }
        double radius = boss.getBbWidth() * (0.24 + 2.1 * charge);
        double height = boss.getBbHeight() * (0.45 + 2.2 * charge);
        double thickness = 0.60 + 0.55 * charge;
        for (int index = 0; index < 12; index++) {
            float visibility = index < 4 ? 1 : Mth.clamp(charge * 8 - (index - 4), 0, 1);
            if (visibility == 0) {
                break;
            }
            long beat = Mth.lfloor((time + index * 0.73) / (3 + index % 3));
            long shape = RandomSupport.mixStafford13(arc.chargeStartedAt() + beat * 31 + index * 131);
            double angle = fraction(shape, 0) * Math.PI * 2;
            Vec3 outward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 across = new Vec3(-outward.z, 0, outward.x);
            double reach = radius * (0.55 + fraction(shape, 8) * 0.65);
            double rise = height * (2 * fraction(shape, 16) - 1);
            Vec3 bend = center.add(outward.scale(reach * 0.36)).add(0, rise * 0.48, 0);
            Vec3 knee = center.add(outward.scale(reach * 0.73))
                    .add(across.scale(radius * (fraction(shape, 24) - 0.5))).add(0, rise * 0.65, 0);
            Vec3 tip = center.add(outward.scale(reach)).add(0, rise, 0);
            Vec3 side = across.scale(thickness);
            Vec3 up = outward.scale(0.48).add(0, 0.72, 0).scale(thickness);
            float alpha = (float) (visibility * (0.32 + 0.50 * charge + 0.17 * fraction(shape, 32)));
            drawSegment(buffer, matrix, center, bend, side, up, alpha);
            drawSegment(buffer, matrix, bend, knee, side, up, alpha * 0.84F);
            drawSegment(buffer, matrix, knee, tip, side, up, alpha * 0.58F);
            if (fraction(shape, 40) > 0.35) {
                Vec3 fork = knee.add(across.scale(radius * (2 * fraction(shape, 48) - 1)))
                        .add(0, height * (fraction(shape, 56) - 0.5), 0);
                drawSegment(buffer, matrix, bend, fork, side, up, alpha * 0.47F);
            }
        }
    }

    private static double fraction(long pattern, int shift) {
        return ((pattern >>> shift) & 255) / 256.0;
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
