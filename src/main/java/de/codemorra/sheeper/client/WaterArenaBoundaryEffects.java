package de.codemorra.sheeper.client;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.registry.ModParticles;
import de.codemorra.sheeper.world.ArenaWater;
import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Stationary boundary bubbles mark all cylinder faces from
 * either side, independently of fluid blocks and combat membership.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WaterArenaBoundaryEffects {
    private static final double VISIBILITY_DISTANCE = 32.0D;
    private static final double PATCH_HALF_EXTENT = 16.0D;
    private static final double CELL_SIZE = 2.0D;

    private WaterArenaBoundaryEffects() {
    }

    @FunctionalInterface
    private interface FaceConsumer {
        void accept(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, float fade);
    }

    /** Emits a bounded local sample on the same faces; visible even when standing outside in air. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        var level = minecraft.level;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        emitStartupFront(minecraft, camera);
        if (level.getGameTime() % 4 != 0) {
            return;
        }
        int[] remaining = { 96 };
        for (VolumeBounds bounds : WaterArenaVisuals.activeVolumes(level)) {
            faces(bounds, camera, (a, b, c, d, normal, fade) -> {
                if (remaining[0] <= 0 || level.random.nextFloat() > fade * 0.12F) {
                    return;
                }
                Vec3 point = a.lerp(b, level.random.nextDouble())
                        .lerp(d.lerp(c, level.random.nextDouble()), level.random.nextDouble());
                level.addParticle(ModParticles.WATER_ARENA_SIDE.get(), true,
                        point.x, point.y, point.z, 0, 0, 0);
                remaining[0]--;
            });
        }
    }

    /** A denser moving wall of bubbles makes the growing water volume visible from either side. */
    private static void emitStartupFront(Minecraft minecraft, Vec3 camera) {
        var level = minecraft.level;
        int[] remaining = { 192 };
        for (var volume : WaterArenaVisuals.animatedVolumes(level)) {
            if (!volume.isExpanding(level.getGameTime())) {
                continue;
            }
            VolumeBounds bounds = volume.at(level.getGameTime());
            faces(bounds, camera, (a, b, c, d, normal, fade) -> {
                if (normal.y != 0 || remaining[0] <= 0 || level.random.nextFloat() > fade * 0.8F) {
                    return;
                }
                // Spawn just inside the advancing wall, where virtual water sustains the bubbles.
                Vec3 point = a.lerp(b, level.random.nextDouble())
                        .lerp(d.lerp(c, level.random.nextDouble()), level.random.nextDouble())
                        .subtract(normal.scale(0.25D));
                level.addParticle(ParticleTypes.BUBBLE, true, point.x, point.y, point.z,
                        normal.x * 0.15D, 0.08D, normal.z * 0.15D);
                remaining[0]--;
            });
        }
    }

    /** Visits nearby cap and wall patches for bounded bubble emission. */
    private static void faces(VolumeBounds bounds, Vec3 camera, FaceConsumer consumer) {
        double bottom = bounds.minimumY() + ArenaWater.BOUNDARY_MARGIN;
        double top = bounds.maximumY() + 1.0D - ArenaWater.BOUNDARY_MARGIN;
        if (top <= bottom) {
            return;
        }
        double dx = camera.x - bounds.center().getX() - 0.5D;
        double dz = camera.z - bounds.center().getZ() - 0.5D;
        double radial = Math.sqrt(dx * dx + dz * dz);
        double verticalGap = Math.max(0, Math.max(bottom - camera.y, camera.y - top));
        if (Math.max(0, radial - bounds.radius()) > VISIBILITY_DISTANCE || verticalGap > VISIBILITY_DISTANCE) {
            return;
        }
        plane(bounds, camera, bottom, consumer);
        plane(bounds, camera, top, consumer);
        if (Math.abs(radial - bounds.radius()) <= VISIBILITY_DISTANCE) {
            side(bounds, camera, bottom, top, Math.atan2(dz, dx), consumer);
        }
    }

    /** Clips a local grid to the circular cap instead of leaving square gaps at its edge. */
    private static void plane(VolumeBounds bounds, Vec3 camera, double y, FaceConsumer consumer) {
        if (Math.abs(camera.y - y) > VISIBILITY_DISTANCE) {
            return;
        }
        Vec3 projected = onDisc(bounds, new Vec3(camera.x, y, camera.z));
        // World-aligned cells keep sampling stable while the viewer moves.
        double startX = Math.floor((projected.x - PATCH_HALF_EXTENT) / CELL_SIZE) * CELL_SIZE;
        double startZ = Math.floor((projected.z - PATCH_HALF_EXTENT) / CELL_SIZE) * CELL_SIZE;
        double centerX = bounds.center().getX() + 0.5D;
        double centerZ = bounds.center().getZ() + 0.5D;
        for (double x = startX; x < projected.x + PATCH_HALF_EXTENT; x += CELL_SIZE) {
            for (double z = startZ; z < projected.z + PATCH_HALF_EXTENT; z += CELL_SIZE) {
                double nearX = Mth.clamp(centerX, x, x + CELL_SIZE) - centerX;
                double nearZ = Mth.clamp(centerZ, z, z + CELL_SIZE) - centerZ;
                if (nearX * nearX + nearZ * nearZ > bounds.radius() * bounds.radius()) {
                    continue;
                }
                Vec3 middle = new Vec3(x + CELL_SIZE * 0.5D, y, z + CELL_SIZE * 0.5D);
                float fade = patchFade(middle.x - projected.x, middle.z - projected.z) * distanceFade(camera, middle);
                if (fade <= 0) {
                    continue;
                }
                consumer.accept(onDisc(bounds, new Vec3(x, y, z)),
                        onDisc(bounds, new Vec3(x, y, z + CELL_SIZE)),
                        onDisc(bounds, new Vec3(x + CELL_SIZE, y, z + CELL_SIZE)),
                        onDisc(bounds, new Vec3(x + CELL_SIZE, y, z)), new Vec3(0, 1, 0), fade);
            }
        }
    }

    /** Uses analytic circle coordinates so every side arc closes without block-cell rounding gaps. */
    private static void side(VolumeBounds bounds, Vec3 camera, double bottom, double top,
            double angle, FaceConsumer consumer) {
        double halfArc = Math.min(Math.PI, PATCH_HALF_EXTENT / bounds.radius());
        int columns = Math.max(1, (int) Math.ceil(halfArc * 2 * bounds.radius() / CELL_SIZE));
        double baseY = Mth.clamp(camera.y, bottom, top);
        double startY = Math.max(bottom, Math.floor((baseY - PATCH_HALF_EXTENT) / CELL_SIZE) * CELL_SIZE);
        double endY = Math.min(top, baseY + PATCH_HALF_EXTENT);
        for (int column = 0; column < columns; column++) {
            double first = angle - halfArc + halfArc * 2 * column / columns;
            double second = angle - halfArc + halfArc * 2 * (column + 1) / columns;
            double midAngle = (first + second) * 0.5D;
            for (double y = startY; y < endY; y += CELL_SIZE) {
                double upper = Math.min(endY, y + CELL_SIZE);
                Vec3 middle = onSide(bounds, midAngle, (y + upper) * 0.5D);
                float fade = patchFade((midAngle - angle) * bounds.radius(), middle.y - baseY)
                        * distanceFade(camera, middle);
                if (fade > 0) {
                    consumer.accept(onSide(bounds, first, y), onSide(bounds, first, upper),
                            onSide(bounds, second, upper), onSide(bounds, second, y),
                            new Vec3(Math.cos(midAngle), 0, Math.sin(midAngle)), fade);
                }
            }
        }
    }

    private static Vec3 onDisc(VolumeBounds bounds, Vec3 point) {
        double x = point.x - bounds.center().getX() - 0.5D;
        double z = point.z - bounds.center().getZ() - 0.5D;
        double length = Math.sqrt(x * x + z * z);
        double scale = length > bounds.radius() ? bounds.radius() / length : 1;
        return new Vec3(bounds.center().getX() + 0.5D + x * scale, point.y,
                bounds.center().getZ() + 0.5D + z * scale);
    }

    private static Vec3 onSide(VolumeBounds bounds, double angle, double y) {
        return new Vec3(bounds.center().getX() + 0.5D + Math.cos(angle) * bounds.radius(), y,
                bounds.center().getZ() + 0.5D + Math.sin(angle) * bounds.radius());
    }

    private static float patchFade(double first, double second) {
        return (float) ((1 - Mth.clamp((Math.abs(first) - 12) / 4, 0, 1))
                * (1 - Mth.clamp((Math.abs(second) - 12) / 4, 0, 1)));
    }

    private static float distanceFade(Vec3 camera, Vec3 point) {
        return (float) (1 - Mth.clamp((camera.distanceTo(point) - 24) / 8, 0, 1));
    }

}
