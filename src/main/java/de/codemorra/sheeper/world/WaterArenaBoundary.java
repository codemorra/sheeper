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

    /** Returns the fraction of an attack segment that stays in the owner's cylinder, without a movement inset. */
    public static double attackFraction(VolumeBounds volume, Vec3 origin, Vec3 motion) {
        Interval hit = intersect(volume, origin, motion);
        return hit == null || hit.start() > EPSILON ? 0 : Math.max(0, Math.min(1, hit.end()));
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

    /** Nearest exposed side of the water union; positive distance denotes an interior point. */
    public record LateralBoundary(Vec3 normal, double distance, double width) {
    }

    /** Retains an accessible inner region even when a configured transition is wider than a small arena. */
    public static double slowdownWidth(double configured, double radius) {
        return Math.min(configured, radius * 0.5D);
    }

    /**
     * Finds nearby exposed arcs in a horizontal cross-section. A circle's closest
     * point and its intersections with other circles contain every possible nearest
     * point of an exposed arc. Covered candidates are discarded before choosing a
     * normal, so internal arena faces cannot slow swimmers.
     *
     * @param point body center whose height selects the relevant water cross-section
     * @param reach maximum distance to either side of an exterior boundary
     * @return nearest exterior side, or null when no exposed side is within reach
     */
    @Nullable
    public static LateralBoundary lateralBoundary(List<VolumeBounds> volumes, Vec3 point, double reach) {
        List<VolumeBounds> covering = new ArrayList<>();
        List<VolumeBounds> candidates = new ArrayList<>();
        boolean inside = false;
        for (VolumeBounds volume : volumes) {
            if (point.y < volume.minimumY() || point.y > volume.maximumY() + 1) {
                continue;
            }
            double distance = horizontalOffset(volume, point).length();
            if (distance <= volume.radius() + reach) {
                covering.add(volume);
                inside |= distance <= volume.radius();
                if (Math.abs(distance - volume.radius()) <= reach) {
                    candidates.add(volume);
                }
            }
        }
        ClosestSide closest = new ClosestSide(point, reach, covering);
        for (int first = 0; first < candidates.size(); first++) {
            VolumeBounds circle = candidates.get(first);
            Vec3 radial = horizontalOffset(circle, point);
            Vec3 normal = radial.lengthSqr() > EPSILON * EPSILON ? radial.normalize() : new Vec3(1, 0, 0);
            Vec3 center = new Vec3(circle.center().getX() + 0.5D, point.y, circle.center().getZ() + 0.5D);
            closest.consider(center.add(normal.scale(circle.radius())), normal, circle.radius());
            for (int second = first + 1; second < candidates.size(); second++) {
                VolumeBounds other = candidates.get(second);
                Vec3 between = new Vec3(other.center().getX() - circle.center().getX(), 0,
                        other.center().getZ() - circle.center().getZ());
                double separation = between.length();
                if (separation < EPSILON || separation > circle.radius() + other.radius()
                        || separation < Math.abs(circle.radius() - other.radius())) {
                    continue;
                }
                double along = (circle.radius() * circle.radius() - other.radius() * other.radius()
                        + separation * separation) / (2 * separation);
                double height = Math.sqrt(Math.max(0, circle.radius() * circle.radius() - along * along));
                Vec3 axis = between.scale(1 / separation);
                Vec3 midpoint = center.add(axis.scale(along));
                Vec3 offset = new Vec3(-axis.z, 0, axis.x).scale(height);
                for (Vec3 intersection : new Vec3[] { midpoint.add(offset), midpoint.subtract(offset) }) {
                    Vec3 combined = horizontalOffset(circle, intersection).normalize()
                            .add(horizontalOffset(other, intersection).normalize());
                    closest.consider(intersection, combined.lengthSqr() > EPSILON * EPSILON
                            ? combined.normalize() : horizontalOffset(circle, intersection).normalize(),
                            Math.min(circle.radius(), other.radius()));
                }
            }
        }
        return closest.result(inside);
    }

    private static Vec3 horizontalOffset(VolumeBounds volume, Vec3 point) {
        return new Vec3(point.x - volume.center().getX() - 0.5D, 0, point.z - volume.center().getZ() - 0.5D);
    }

    /** Accumulates exposed candidates without retaining geometry between changing arena snapshots. */
    private static final class ClosestSide {
        private final Vec3 point;
        private final List<VolumeBounds> volumes;
        private double distanceSquared;
        private final double maximumWidth;
        private double boundaryWidth;
        @Nullable
        private Vec3 boundary;
        private Vec3 fallbackNormal = Vec3.ZERO;

        private ClosestSide(Vec3 point, double reach, List<VolumeBounds> volumes) {
            this.point = point;
            this.volumes = volumes;
            distanceSquared = reach * reach;
            maximumWidth = reach;
        }

        private void consider(Vec3 candidate, Vec3 normal, double radiusLimit) {
            double distance = candidate.distanceToSqr(point);
            double width = slowdownWidth(maximumWidth, radiusLimit);
            if (distance > distanceSquared || distance > width * width) {
                return;
            }
            for (VolumeBounds volume : volumes) {
                // Use a world-space tolerance so distant world coordinates do not
                // classify a rounded point on its own circle as covered interior.
                double radius = Math.max(0, volume.radius() - EPSILON);
                if (horizontalOffset(volume, candidate).lengthSqr() < radius * radius) {
                    return;
                }
            }
            distanceSquared = distance;
            boundary = candidate;
            fallbackNormal = normal;
            boundaryWidth = width;
        }

        @Nullable
        private LateralBoundary result(boolean inside) {
            if (boundary == null) {
                return null;
            }
            double distance = Math.sqrt(distanceSquared);
            Vec3 normal = distance > EPSILON
                    ? (inside ? boundary.subtract(point) : point.subtract(boundary)).scale(1 / distance)
                    : fallbackNormal;
            return new LateralBoundary(normal, inside ? distance : -distance, boundaryWidth);
        }
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
