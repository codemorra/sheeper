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
        lines.keySet().removeIf(arena -> fields.stream().noneMatch(field -> field.arena().equals(arena)));
        long now = level.getGameTime();
        frontLines.keySet().removeIf(arena -> !arena.isBuilding(now) || !arenas.contains(arena));
        for (Arena arena : arenas) {
            if (arena.isBuilding(now)) {
                tickFront(level, arena, camera, now, density);
            }
        }
        for (LightningWindField field : fields) {
            Arena arena = field.arena();
            List<Line> current = lines.computeIfAbsent(arena, key -> new ArrayList<>());
            Vec3 travel = field.travelBetween(now - 1, now).scale(lineSpeed(field));
            current.replaceAll(line -> line.move(travel));
            current.removeIf(line -> now - line.bornAt() >= line.lifetime()
                    || line.position().distanceToSqr(camera) >= (VIEW_DISTANCE + 8) * (VIEW_DISTANCE + 8)
                    || intensity(arena, now, line.position()) <= 0);
            int births = birthsPerTick(level, arena, density);
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

    void draw(BufferBuilder buffer, ClientLevel level, LightningWindField field, Vec3 camera, double time) {
        Arena arena = field.arena();
        Vec3 direction = field.directionAt(time);
        Vec3 travel = field.travelBetween(level.getGameTime(), time).scale(lineSpeed(field));
        for (Line line : lines.getOrDefault(arena, List.of())) {
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

    private static double lineSpeed(LightningWindField field) {
        return SPEED * Math.sqrt(field.strength());
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
            BlockPos pos = new BlockPos(point);
            if (intensity <= 0 || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                    || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                previous = null;
                continue;
            }
            Vec3 relative = point.subtract(camera);
            float brightness = (float) (alpha * intensity * Math.sin(Math.PI * fraction));
            if (previous != null) {
                vertex(buffer, previous, -0.02, previousAlpha * 0.25F);
                vertex(buffer, relative, -0.02, brightness * 0.25F);
                vertex(buffer, relative, 0.02, brightness);
                vertex(buffer, previous, 0.02, previousAlpha);
            }
            previous = relative;
            previousAlpha = brightness;
        }
    }

    private static float intensity(Arena arena, double time, Vec3 point) {
        return arena.intensityAt(time, point.x, point.y, point.z);
    }

    private static void vertex(BufferBuilder buffer, Vec3 point, double offset, float alpha) {
        buffer.vertex((float) point.x, (float) (point.y + offset), (float) point.z)
                .color(0.76F, 0.84F, 0.91F, alpha).endVertex();
    }
}
