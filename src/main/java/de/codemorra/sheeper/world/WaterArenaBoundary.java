package de.codemorra.sheeper.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
import net.minecraft.world.phys.Vec3;

/** Continuous movement clipping against the union of water cylinders, including different water heights. */
public final class WaterArenaBoundary {
    private static final double EPSILON = 1.0E-7D;
    private static final double EDGE_MARGIN = 0.05D;
    private static final double HORIZONTAL_STOP_DISTANCE = 1.0D;

    private record Interval(double start, double end, Vec3 normal, double stopEnd) {
    }

    private WaterArenaBoundary() {
    }

    /** Checks the entire vertical body column, allowing connected water from several arenas. */
    public static boolean containsBody(List<VolumeBounds> volumes, Vec3 feet, double height) {
        return trace(volumes, feet, new Vec3(0, height, 0)).end() >= 1 - EPSILON;
    }

    /**
     * Clips only paths leaving connected water and projects the remaining movement
     * along the outer surface. Intervals are merged before clipping, so overlapping
     * arena walls cannot become internal barriers or allow jumps across dry gaps.
     *
     * @param feet position before movement
     * @param height current body height, including swimming or crouching poses
     * @return permitted displacement with the outward component removed at the boundary
     */
    public static Vec3 limit(List<VolumeBounds> volumes, Vec3 feet, double height, Vec3 movement) {
        if (movement.lengthSqr() < EPSILON * EPSILON || !containsBody(volumes, feet, height)) {
            return movement;
        }
        Vec3 result = Vec3.ZERO;
        Vec3 remaining = movement;
        for (int pass = 0; pass < 3; pass++) {
            Interval boundary = new Interval(0, Double.POSITIVE_INFINITY, Vec3.ZERO, Double.POSITIVE_INFINITY);
            for (double y : new double[] { 0, height * 0.5D, height }) {
                Interval hit = trace(volumes, feet.add(result).add(0, y, 0), remaining);
                if (hit.stopEnd() < boundary.stopEnd()) {
                    boundary = hit;
                }
            }
            if (boundary.end() < 0) {
                return result;
            }
            // The inset exit is computed even beyond this tick's destination, so
            // short swimming steps cannot creep into the protected outer strip.
            double fraction = Math.max(0, boundary.stopEnd());
            if (fraction >= 1) {
                return result.add(remaining);
            }
            Vec3 step = remaining.scale(fraction);
            result = result.add(step);
            Vec3 tail = remaining.subtract(step);
            Vec3 slide = tail.subtract(boundary.normal().scale(Math.max(0, tail.dot(boundary.normal()))));
            if (slide.lengthSqr() < EPSILON * EPSILON) {
                return result;
            }
            if (fraction <= EPSILON && slide.distanceToSqr(tail) < EPSILON * EPSILON) {
                // A straight tangent starting exactly on a curved wall immediately
                // leaves the circle. A small inward component lets it follow the rim.
                slide = slide.subtract(boundary.normal().scale(Math.min(EDGE_MARGIN, slide.length() * 0.25D)));
            }
            remaining = slide;
        }
        return result;
    }

    /** Finds the connected interval containing the ray origin, rather than just testing its destination. */
    private static Interval trace(List<VolumeBounds> volumes, Vec3 origin, Vec3 motion) {
        List<Interval> intervals = new ArrayList<>();
        for (VolumeBounds volume : volumes) {
            Interval hit = intersect(volume, origin, motion);
            if (hit != null && hit.end() >= -EPSILON) {
                intervals.add(hit);
            }
        }
        intervals.sort(Comparator.comparingDouble(Interval::start));
        double end = -1;
        Vec3 normal = Vec3.ZERO;
        double stopEnd = -1;
        for (Interval hit : intervals) {
            if (hit.start() > Math.max(0, end) + EPSILON) {
                break;
            }
            if (hit.end() > end || Math.abs(hit.end() - end) < EPSILON && hit.stopEnd() > stopEnd) {
                end = hit.end();
                normal = hit.normal();
                stopEnd = hit.stopEnd();
            }
        }
        return new Interval(0, end, normal, stopEnd);
    }

    /** Intersects a ray with an analytic cylinder and its vertical slab without loading terrain. */
    @Nullable
    private static Interval intersect(VolumeBounds volume, Vec3 origin, Vec3 motion) {
        double x = origin.x - volume.center().getX() - 0.5D;
        double z = origin.z - volume.center().getZ() - 0.5D;
        double a = motion.horizontalDistanceSqr();
        double radialStart = Double.NEGATIVE_INFINITY;
        double radialEnd = Double.POSITIVE_INFINITY;
        double radialStop = Double.POSITIVE_INFINITY;
        if (a < EPSILON * EPSILON) {
            if (x * x + z * z > volume.radius() * volume.radius() + EPSILON) {
                return null;
            }
        } else {
            double b = x * motion.x + z * motion.z;
            double discriminant = b * b - a * (x * x + z * z - volume.radius() * volume.radius());
            if (discriminant < 0) {
                return null;
            }
            double root = Math.sqrt(discriminant);
            radialStart = (-b - root) / a;
            radialEnd = (-b + root) / a;
            double insetRadius = Math.max(EPSILON, volume.radius() - HORIZONTAL_STOP_DISTANCE);
            double insetDiscriminant = b * b - a * (x * x + z * z - insetRadius * insetRadius);
            radialStop = insetDiscriminant < 0 ? 0 : (-b + Math.sqrt(insetDiscriminant)) / a;
        }
        double verticalStart = Double.NEGATIVE_INFINITY;
        double verticalEnd = Double.POSITIVE_INFINITY;
        if (Math.abs(motion.y) < EPSILON) {
            if (origin.y < volume.minimumY() - EPSILON || origin.y > volume.maximumY() + 1 + EPSILON) {
                return null;
            }
        } else {
            double bottom = (volume.minimumY() - origin.y) / motion.y;
            double top = (volume.maximumY() + 1 - origin.y) / motion.y;
            verticalStart = Math.min(bottom, top);
            verticalEnd = Math.max(bottom, top);
        }
        double start = Math.max(radialStart, verticalStart);
        double end = Math.min(radialEnd, verticalEnd);
        if (start > end + EPSILON) {
            return null;
        }
        double verticalStop = verticalEnd;
        if (Math.abs(motion.y) >= EPSILON) {
            verticalStop -= EDGE_MARGIN / Math.abs(motion.y);
        }
        double stopEnd = Math.min(radialStop, verticalStop);
        Vec3 normal = Vec3.ZERO;
        if (Double.isFinite(stopEnd)) {
            normal = radialStop < verticalStop
                    ? new Vec3(x + motion.x * Math.max(0, radialStop), 0,
                            z + motion.z * Math.max(0, radialStop)).normalize()
                    : new Vec3(0, Math.signum(motion.y), 0);
        }
        // trace() merges the original water intervals first and retains only the
        // final exterior face's inset. Overlap boundaries remain passable.
        return new Interval(start, end, normal, stopEnd);
    }
}
