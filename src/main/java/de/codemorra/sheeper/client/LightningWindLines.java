package de.codemorra.sheeper.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final Map<Arena, List<Line>> lines = new HashMap<>();

    void clear() {
        lines.clear();
    }

    void tick(ClientLevel level, List<LightningWindField> fields, Vec3 camera) {
        double density = SheeperClientConfig.LIGHTNING_WIND_LINE_DENSITY.get();
        if (density == 0) {
            clear();
            return;
        }
        lines.keySet().removeIf(arena -> fields.stream().noneMatch(field -> field.arena().equals(arena)));
        long now = level.getGameTime();
        for (LightningWindField field : fields) {
            Arena arena = field.arena();
            List<Line> current = lines.computeIfAbsent(arena, key -> new ArrayList<>());
            Vec3 travel = field.travelBetween(now - 1, now).scale(SPEED);
            current.replaceAll(line -> line.move(travel));
            current.removeIf(line -> now - line.bornAt() >= line.lifetime()
                    || line.position().distanceToSqr(camera) >= (VIEW_DISTANCE + 8) * (VIEW_DISTANCE + 8)
                    || intensity(arena, now, line.position()) <= 0);
            double scaledBirths = Mth.clamp((int) Math.ceil(arena.radius() * arena.radius() / 480), 1, 4) * density;
            int births = Mth.floor(scaledBirths);
            // Round fractional spawn rates over time so low density values work in small arenas too.
            double fractionalBirth = scaledBirths - births;
            if (fractionalBirth > 0 && level.random.nextDouble() < fractionalBirth) {
                births++;
            }
            for (int index = 0; index < births; index++) {
                spawn(level, arena, camera, now, current);
            }
        }
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
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        if (!level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
            return;
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
            return;
        }
        Vec3 point = new Vec3(x, Mth.lerp(level.random.nextDouble(), bottom, top), z);
        if (intensity(arena, now, point) > 0) {
            double length = Math.min(arena.radius(), 1.2 + level.random.nextDouble() * 2.1);
            lines.add(new Line(point, length, 36 + level.random.nextInt(25), now));
        }
    }

    void draw(BufferBuilder buffer, ClientLevel level, LightningWindField field, Vec3 camera, double time) {
        Arena arena = field.arena();
        Vec3 direction = field.directionAt(time);
        Vec3 travel = field.travelBetween(level.getGameTime(), time).scale(SPEED);
        for (Line line : lines.getOrDefault(arena, List.of())) {
            Vec3 center = line.position().add(travel);
            if (center.distanceToSqr(camera) > VIEW_DISTANCE * VIEW_DISTANCE) {
                continue;
            }
            double age = time - line.bornAt();
            float alpha = (float) (0.14 * Mth.clamp(Math.min(age / 8, (line.lifetime() - age) / 12), 0, 1));
            if (alpha > 0 && intensity(arena, time, center) > 0) {
                drawLine(buffer, level, arena, camera, time, direction, line.length(), center, alpha);
            }
        }
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
