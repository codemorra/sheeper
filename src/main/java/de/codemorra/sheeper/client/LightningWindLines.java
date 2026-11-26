package de.codemorra.sheeper.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.BufferBuilder;
import de.codemorra.sheeper.config.SheeperClientConfig;
import de.codemorra.sheeper.entity.LightningRingSurface;
import de.codemorra.sheeper.network.LightningWeatherPacket.Arena;
import de.codemorra.sheeper.world.LightningWindField;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Tracks short independent wind lines in world space, including while the wind turns. */
final class LightningWindLines {
    private static final double SPEED = 0.19;
    private static final double VIEW_DISTANCE = 40;
    private static final double BOUNDARY_VIEW_DISTANCE = 64;

    private record Line(Vec3 position, double length, int lifetime, long bornAt) {
        Line move(Vec3 offset) {
            return new Line(position.add(offset), length, lifetime, bornAt);
        }
    }

    private record FrontLine(Line line, Vec3 direction, double startRadius) {
        Vec3 positionAt(Arena arena, double time) {
            return line.position().add(direction.scale(arena.radiusAt(time) - startRadius));
        }
    }

    private final Map<Arena, List<Line>> lines = new HashMap<>();
    private final Map<Arena, List<FrontLine>> frontLines = new HashMap<>();

    void clear() {
        lines.clear();
        frontLines.clear();
    }

    void tick(ClientLevel level, List<Arena> arenas, List<LightningWindField> fields, Vec3 camera) {
        double density = SheeperClientConfig.LIGHTNING_WIND_LINE_DENSITY.get();
        if (density == 0) {
            clear();
            return;
        }
        lines.keySet().removeIf(arena -> fields.stream().noneMatch(field -> lineKey(field.arena()).equals(arena)));
        long now = level.getGameTime();
        frontLines.keySet().removeIf(arena -> !arena.isBuilding(now) || !arenas.contains(arena));
        for (Arena arena : arenas) {
            if (arena.isBuilding(now)) {
                tickFront(level, arena, camera, now, density);
            }
        }
        for (LightningWindField field : fields) {
            Arena arena = field.arena();
            List<Line> current = lines.computeIfAbsent(lineKey(arena), key -> new ArrayList<>());
            Vec3 travel = field.travelBetween(now - 1, now).scale(lineSpeed(field, now));
            current.replaceAll(line -> line.move(travel));
            current.removeIf(line -> now - line.bornAt() >= line.lifetime()
                    || line.position().distanceToSqr(camera) >= (VIEW_DISTANCE + 8) * (VIEW_DISTANCE + 8)
                    || intensity(arena, now, line.position()) <= 0);
            int births = birthsPerTick(level, arena, density * arena.opacityAt(now));
            for (int index = 0; index < births; index++) {
                spawn(level, arena, camera, now, current);
            }
        }
    }

    /** Spawns independent lines on a nearby arc just behind the expanding storm front. */
    private void tickFront(ClientLevel level, Arena arena, Vec3 camera, long now, double density) {
        List<FrontLine> current = frontLines.computeIfAbsent(arena, key -> new ArrayList<>());
        current.removeIf(front -> now - front.line().bornAt() >= front.line().lifetime()
                || front.positionAt(arena, now).distanceToSqr(camera) > (VIEW_DISTANCE + 8) * (VIEW_DISTANCE + 8));
        Vec3 center = Vec3.atBottomCenterOf(arena.center());
        Vec3 toCamera = camera.subtract(center);
        double radius = arena.radiusAt(now);
        if (radius <= 0.25 || Math.abs(toCamera.horizontalDistance() - radius) > VIEW_DISTANCE + 6) {
            return;
        }
        double spread = toCamera.horizontalDistance() + radius <= VIEW_DISTANCE
                ? Math.PI : Math.min(Math.PI, VIEW_DISTANCE / radius);
        double bearing = Math.atan2(toCamera.z, toCamera.x);
        int births = birthsPerTick(level, arena, density);
        for (int index = 0; index < births; index++) {
            double angle = bearing + (level.random.nextDouble() * 2 - 1) * spread;
            Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            double depth = Math.min(radius * 0.8, 1.6 + level.random.nextDouble() * 4.5);
            Vec3 position = center.add(direction.scale(radius - depth));
            Line line = sampleLine(level, arena, camera, now, position.x, position.z, 12 + level.random.nextInt(9));
            if (line != null) {
                current.add(new FrontLine(line, direction, radius));
            }
        }
    }

    private static int birthsPerTick(ClientLevel level, Arena arena, double density) {
        double scaled = Mth.clamp((int) Math.ceil(arena.radius() * arena.radius() / 480), 1, 4) * density;
        int whole = Mth.floor(scaled);
        // Round fractional spawn rates over time so low density also works in small arenas.
        double fractional = scaled - whole;
        return fractional > 0 && level.random.nextDouble() < fractional ? whole + 1 : whole;
    }

    /** Samples the nearby arena volume independently on each axis, with several heights above terrain. */
    private static void spawn(ClientLevel level, Arena arena, Vec3 camera, long now, List<Line> lines) {
        double cx = arena.center().getX() + 0.5;
        double cz = arena.center().getZ() + 0.5;
        double minX = Math.max(cx - arena.radius(), camera.x - VIEW_DISTANCE);
        double maxX = Math.min(cx + arena.radius(), camera.x + VIEW_DISTANCE);
        double minZ = Math.max(cz - arena.radius(), camera.z - VIEW_DISTANCE);
        double maxZ = Math.min(cz + arena.radius(), camera.z + VIEW_DISTANCE);
        if (minX >= maxX || minZ >= maxZ) {
            return;
        }
        double x = Mth.lerp(level.random.nextDouble(), minX, maxX);
        double z = Mth.lerp(level.random.nextDouble(), minZ, maxZ);
        Line line = sampleLine(level, arena, camera, now, x, z, 36 + level.random.nextInt(25));
        if (line != null) {
            lines.add(line);
        }
    }

    @Nullable
    private static Line sampleLine(ClientLevel level, Arena arena, Vec3 camera, long now, double x, double z,
            int lifetime) {
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        if (!level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
            return null;
        }
        double floor = Math.max(arena.minimumY(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ));
        if (floor > camera.y + 2) {
            // A nearby lower floor can be visible even when there is terrain overhead.
            floor = LightningRingSurface.find(level, x, z, camera.y - 1,
                    Math.max(arena.minimumY(), Mth.floor(camera.y) - 8),
                    Math.min(arena.maximumY() - 1, Mth.floor(camera.y)));
        }
        double bottom = Math.max(floor + 0.30, camera.y - 10);
        double top = Math.min(arena.maximumY() - 0.3, Math.max(floor + 5, camera.y + 4));
        if (!(bottom < top)) {
            return null;
        }
        Vec3 point = new Vec3(x, Mth.lerp(level.random.nextDouble(), bottom, top), z);
        if (intensity(arena, now, point) > 0) {
            double length = Math.min(arena.radius(), 1.2 + level.random.nextDouble() * 2.1);
            return new Line(point, length, lifetime, now);
        }
        return null;
    }

    /** Interpolates the movement of the front and fades the startup lines into the regular wind. */
    void drawFront(BufferBuilder buffer, ClientLevel level, Arena arena, Vec3 camera, double time) {
        if (!arena.isBuilding(time)) {
            return;
        }
        double endFade = Mth.clamp((arena.start() + arena.transition() - time) / 8, 0, 1);
        for (FrontLine front : frontLines.getOrDefault(arena, List.of())) {
            Line line = front.line();
            Vec3 center = front.positionAt(arena, time);
            if (center.distanceToSqr(camera) > VIEW_DISTANCE * VIEW_DISTANCE) {
                continue;
            }
            double age = time - line.bornAt();
            float alpha = (float) (0.22 * endFade
                    * Mth.clamp(Math.min(age / 3, (line.lifetime() - age) / 5), 0, 1));
            if (alpha > 0) {
                drawLine(buffer, level, arena, camera, time, front.direction(), line.length(), center, alpha);
            }
        }
    }

    /** Marks the moving boundary with staggered wind ribbons on the surface and along the sides. */
    void drawBoundary(BufferBuilder buffer, ClientLevel level, Arena arena, Vec3 camera, double time) {
        Vec3 center = Vec3.atBottomCenterOf(arena.center());
        Vec3 toCamera = camera.subtract(center);
        double radius = arena.radiusAt(time);
        double radialDistance = toCamera.horizontalDistance();
        if (radius < 0.25 || Math.abs(radialDistance - radius) >= BOUNDARY_VIEW_DISTANCE) {
            return;
        }
        // Keep angular slots stable so the ribbons travel smoothly as the storm expands or retreats.
        int slots = Math.max(6, Mth.ceil(2 * Math.PI * arena.radius() / 3));
        double step = 2 * Math.PI / slots;
        double bearing = Math.atan2(toCamera.z, toCamera.x);
        double arc = Math.PI;
        if (radialDistance + radius > BOUNDARY_VIEW_DISTANCE) {
            double cosine = (radialDistance * radialDistance + radius * radius
                    - BOUNDARY_VIEW_DISTANCE * BOUNDARY_VIEW_DISTANCE) / (2 * radialDistance * radius);
            arc = Math.acos(Mth.clamp(cosine, -1, 1));
        }
        int first = Mth.floor((bearing - arc) / step);
        int last = Math.min(first + slots - 1, Mth.ceil((bearing + arc) / step));
        int firstBand = Mth.floor((camera.y - 12) / 3);
        int lastBand = Mth.floor((camera.y + 12) / 3);
        for (int slot = first; slot <= last; slot++) {
            int index = Math.floorMod(slot, slots);
            double phase = fractional(time / 22 + index * 0.618);
            double angle = (slot + phase) * step;
            double x = center.x + radius * Math.cos(angle);
            double z = center.z + radius * Math.sin(angle);
            int blockX = Mth.floor(x);
            int blockZ = Mth.floor(z);
            if (!level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
                continue;
            }
            double pulse = Math.min(1, Math.min(phase, 1 - phase) * 5);
            float alpha = arena.opacityAt(time) * (float) (pulse * Math.min(1, radius / 8));
            double surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
            double water = LightningRingSurface.findWater(level, x, z, arena.minimumY(), arena.maximumY() - 1);
            double floor = Double.isFinite(water) ? water : surface;
            if (arena.containsY(floor) && floor > level.getMinBuildHeight()) {
                // Stack the surface ribbons so the edge stays legible from either side and above water.
                drawBoundaryLine(buffer, level, arena, center, camera, radius, angle, floor + 0.35,
                        step * 1.5, alpha * 0.90F);
                drawBoundaryLine(buffer, level, arena, center, camera, radius, angle + step * 0.5, floor + 1.05,
                        step * 1.25, alpha * 0.74F);
            }
            for (int band = firstBand; band <= lastBand; band++) {
                if (Math.floorMod(index + band, 2) == 0) {
                    double y = band * 3 + 0.5 + fractional(index * 0.382) * 2;
                    double heightFade = Mth.clamp((12 - Math.abs(camera.y - y)) / 4, 0, 1);
                    if (heightFade > 0) {
                        drawBoundaryLine(buffer, level, arena, center, camera, radius, angle + step * 0.45, y,
                                step * 1.35, alpha * (float) heightFade * 0.68F);
                    }
                }
            }
        }
    }

    /** Follows the circular edge so the wind stays on the boundary even in small arenas. */
    private static void drawBoundaryLine(BufferBuilder buffer, ClientLevel level, Arena arena, Vec3 center,
            Vec3 camera, double radius, double angle, double y, double span, float alpha) {
        Vec3 previous = null;
        float previousAlpha = 0;
        for (int index = 0; index <= 5; index++) {
            double fraction = index / 5.0;
            double heading = angle + (fraction - 0.5) * span;
            Vec3 point = new Vec3(center.x + Math.cos(heading) * radius,
                    y + Math.sin(fraction * 5) * 0.12, center.z + Math.sin(heading) * radius);
            double distance = point.distanceTo(camera);
            if (distance >= BOUNDARY_VIEW_DISTANCE || !arena.containsY(point.y) || !isOpen(level, point)) {
                previous = null;
                continue;
            }
            Vec3 relative = point.subtract(camera);
            float brightness = (float) (alpha * Math.sin(Math.PI * fraction)
                    * Math.min(1, (BOUNDARY_VIEW_DISTANCE - distance) / 8));
            if (previous != null) {
                ribbonSegment(buffer, previous, relative, previousAlpha, brightness, 0.10);
            }
            previous = relative;
            previousAlpha = brightness;
        }
    }

    private static double fractional(double value) {
        return value - Math.floor(value);
    }

    void draw(BufferBuilder buffer, ClientLevel level, LightningWindField field, Vec3 camera, double time) {
        Arena arena = field.arena();
        Vec3 direction = field.directionAt(time);
        Vec3 travel = field.travelBetween(level.getGameTime(), time).scale(lineSpeed(field, time));
        for (Line line : lines.getOrDefault(lineKey(arena), List.of())) {
            Vec3 center = line.position().add(travel);
            if (center.distanceToSqr(camera) > VIEW_DISTANCE * VIEW_DISTANCE) {
                continue;
            }
            double age = time - line.bornAt();
            float alpha = (float) (0.14 * Math.sqrt(field.strength())
                    * Mth.clamp(Math.min(age / 8, (line.lifetime() - age) / 12), 0, 1));
            if (alpha > 0 && intensity(arena, time, center) > 0) {
                drawLine(buffer, level, arena, camera, time, direction, line.length(), center, alpha);
            }
        }
    }

    /** Keeps existing ribbons across the snapshot change that starts the retreat. */
    private static Arena lineKey(Arena arena) {
        if (arena.isDraining()) {
            return arena.beginDrain(0, 0);
        }
        return arena.quiet() || arena.rodDarkeningChangedAt() >= 0 ? arena.withoutAttackEffects() : arena;
    }

    private static double lineSpeed(LightningWindField field, double time) {
        return SPEED * Math.sqrt(field.strength()) * field.arena().opacityAt(time);
    }

    private static void drawLine(BufferBuilder buffer, ClientLevel level, Arena arena, Vec3 camera, double time,
            Vec3 direction, double length, Vec3 center, float alpha) {
        Vec3 previous = null;
        float previousAlpha = 0;
        for (int index = 0; index <= 5; index++) {
            double fraction = index / 5.0;
            Vec3 point = center.add(direction.scale((fraction - 0.5) * length))
                    .add(0, Math.sin(fraction * 5) * 0.09, 0);
            float intensity = intensity(arena, time, point);
            if (intensity <= 0 || !isOpen(level, point)) {
                previous = null;
                continue;
            }
            Vec3 relative = point.subtract(camera);
            float brightness = (float) (alpha * intensity * Math.sin(Math.PI * fraction));
            if (previous != null) {
                ribbonSegment(buffer, previous, relative, previousAlpha, brightness, 0.02);
            }
            previous = relative;
            previousAlpha = brightness;
        }
    }

    private static boolean isOpen(ClientLevel level, Vec3 point) {
        BlockPos pos = new BlockPos(point);
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static void ribbonSegment(BufferBuilder buffer, Vec3 from, Vec3 to, float fromAlpha, float toAlpha,
            double halfHeight) {
        vertex(buffer, from, -halfHeight, fromAlpha * 0.25F);
        vertex(buffer, to, -halfHeight, toAlpha * 0.25F);
        vertex(buffer, to, halfHeight, toAlpha);
        vertex(buffer, from, halfHeight, fromAlpha);
    }

    private static float intensity(Arena arena, double time, Vec3 point) {
        return arena.intensityAt(time, point.x, point.y, point.z);
    }

    private static void vertex(BufferBuilder buffer, Vec3 point, double offset, float alpha) {
        buffer.vertex((float) point.x, (float) (point.y + offset), (float) point.z)
                .color(0.76F, 0.84F, 0.91F, alpha).endVertex();
    }
}
