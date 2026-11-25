package de.codemorra.sheeper.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.LightningRingSurface;
import de.codemorra.sheeper.network.LightningStrikeMarkerPacket;
import de.codemorra.sheeper.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Renders branching ground warnings and the flash and sparks of a completed strike. */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LightningStrikeMarkerVisuals {
    private static final int SPOKES = 5;
    private static final double VIEW_RANGE = 48;
    private static final List<Marker> markers = new ArrayList<>();
    private static ClientLevel currentLevel;

    private record Segment(Vec3 from, Vec3 to, float startStrength, float endStrength) {
    }

    private static final class Marker {
        private final LightningStrikeMarkerPacket geometry;
        private final List<Segment> lines = new ArrayList<>();
        private long expiresAt;
        private long impactStarted = -1;
        private int impactDuration;

        private Marker(LightningStrikeMarkerPacket packet) {
            geometry = packet;
        }

        private boolean matches(LightningStrikeMarkerPacket packet) {
            return geometry.impactTick() == packet.impactTick() && geometry.position().equals(packet.position())
                    && geometry.arenaCenter().equals(packet.arenaCenter());
        }

        private void update(ClientLevel level, LightningStrikeMarkerPacket packet) {
            if (lines.isEmpty()) {
                // The terrain may not have been available when the first warning arrived.
                buildLines(level);
            }
            if (packet.impact()) {
                impactStarted = level.getGameTime();
                impactDuration = packet.durationTicks();
                sparkDome(level, true);
            }
            expiresAt = level.getGameTime() + packet.durationTicks();
        }

        private void buildLines(ClientLevel level) {
            Vec3 origin = geometry.position();
            RandomSource random = RandomSource.create(new BlockPos(origin).asLong() ^ geometry.impactTick());
            double rotation = random.nextDouble() * 2 * Math.PI;
            for (int index = 0; index < SPOKES; index++) {
                double bearing = rotation + (index + random.nextDouble() * 0.42) * 2 * Math.PI / SPOKES;
                Vec3 tip = trace(level, origin.add(0, 0.055, 0), random, bearing, 0,
                        0, geometry.centerRadius(), 0.92F, 0.43F);
                if (tip == null) {
                    continue;
                }
                // Both outer forks share the exact endpoint of the inner line.
                double split = 0.26 + random.nextDouble() * 0.20;
                trace(level, tip, random, bearing, -split, geometry.centerRadius(), geometry.outerRadius(),
                        0.31F, 0.06F);
                trace(level, tip, random, bearing, split, geometry.centerRadius(), geometry.outerRadius(),
                        0.31F, 0.06F);
            }
        }

        /** Samples radial bands with angular jitter and stops at changes of surface or terrain. */
        @Nullable
        private Vec3 trace(ClientLevel level, Vec3 start, RandomSource random, double bearing, double turn,
                double fromRadius, double toRadius, float fromStrength, float toStrength) {
            Vec3 previous = start;
            float previousStrength = fromStrength;
            int steps = Math.max(3, Mth.ceil((toRadius - fromRadius) * 4));
            for (int step = 1; step <= steps; step++) {
                double fraction = (double) step / steps;
                double radius = Mth.lerp(fraction, fromRadius, toRadius);
                double jitter = step == steps ? 0 : (random.nextDouble() - 0.5) * 0.55 / Math.max(1, radius);
                double angle = bearing + turn * (0.20 + 0.80 * fraction) + jitter;
                double x = geometry.position().x + Math.cos(angle) * radius;
                double z = geometry.position().z + Math.sin(angle) * radius;
                double y = surface(level, x, z);
                if (!Double.isFinite(y) || Math.abs(y + 0.055 - previous.y) > 1.1) {
                    return null;
                }
                var point = new Vec3(x, y + 0.055, z);
                float strength = Mth.lerp((float) fraction, fromStrength, toStrength);
                lines.add(new Segment(previous, point, previousStrength, strength));
                previous = point;
                previousStrength = strength;
            }
            return previous;
        }

        private double surface(ClientLevel level, double x, double z) {
            double dx = x - geometry.arenaCenter().getX() - 0.5;
            double dz = z - geometry.arenaCenter().getZ() - 0.5;
            if (dx * dx + dz * dz > geometry.arenaRadius() * geometry.arenaRadius()) {
                return Double.NaN;
            }
            double y = geometry.water() ? LightningRingSurface.findWater(level, x, z, geometry.minY(), geometry.maxY())
                    : LightningRingSurface.find(level, x, z, geometry.position().y, geometry.minY(), geometry.maxY());
            if (!geometry.water() && Double.isFinite(y)
                    && !level.getFluidState(new BlockPos(x, y + 0.04, z)).isEmpty()) {
                return Double.NaN;
            }
            return y;
        }

        private void tick(ClientLevel level, long now) {
            long age = now - impactStarted;
            if (impactStarted >= 0 && age > 0 && age + 2 < impactDuration && age % 3 == 0) {
                sparkDome(level, false);
            }
        }

        /** Distributes small sparks over the upper half of a flat dome above the impact area. */
        private void sparkDome(ClientLevel level, boolean firstFlash) {
            double radius = geometry.outerRadius();
            int count = Mth.clamp(Mth.ceil(14 + radius * radius * 1.8), 14, 48);
            if (!firstFlash) {
                count /= 2;
            }
            var particles = Minecraft.getInstance().particleEngine;
            for (int index = 0; index < count; index++) {
                double fraction = (index + level.random.nextDouble()) / count;
                double angle = level.random.nextDouble() * 2 * Math.PI;
                double distance = radius * Math.sqrt(fraction);
                double x = geometry.position().x + Math.cos(angle) * distance;
                double z = geometry.position().z + Math.sin(angle) * distance;
                double ground = surface(level, x, z);
                if (Double.isFinite(ground)) {
                    double cap = 0.46 * Math.sqrt(1 - fraction);
                    double y = ground + 0.035 + cap * (0.70 + level.random.nextDouble() * 0.3);
                    particles.createParticle(ModParticles.LIGHTNING_SURFACE_SPARK.get(), x, y, z,
                            0, 0.006, 0);
                }
            }
        }

        private void draw(BufferBuilder buffer, Vec3 camera, double now) {
            if (camera.distanceToSqr(geometry.position()) > VIEW_RANGE * VIEW_RANGE) {
                return;
            }
            float flash = impactStarted < 0 ? 0 : (float) Mth.clamp(1 - (now - impactStarted) / 3, 0, 1);
            float tail = impactStarted < 0 ? 1
                    : (float) Mth.clamp((expiresAt - now) / Math.min(8, impactDuration), 0, 1);
            for (Segment line : lines) {
                drawLine(buffer, line, camera, flash, tail);
            }
        }
    }

    private LightningStrikeMarkerVisuals() {
    }

    private static void drawLine(BufferBuilder buffer, Segment line, Vec3 camera, float flash, float tail) {
        Vec3 from = line.from().subtract(camera);
        Vec3 to = line.to().subtract(camera);
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-6) {
            return;
        }
        double width = (0.020 + 0.020 * flash) / length;
        var side = new Vec3(-dz * width, 0, dx * width);
        ribbon(buffer, from, to, side.scale(2.2), line.startStrength(), line.endStrength(), flash * 0.65F, tail * 0.30F);
        ribbon(buffer, from, to, side, line.startStrength(), line.endStrength(), flash, tail);
    }

    private static void ribbon(BufferBuilder buffer, Vec3 from, Vec3 to, Vec3 side, float startStrength,
            float endStrength, float flash, float tail) {
        Vec3 startWidth = side.scale(0.46 + startStrength * 0.54);
        Vec3 endWidth = side.scale(0.46 + endStrength * 0.54);
        float startAlpha = Mth.lerp(flash, startStrength * 0.52F, 1) * tail;
        float endAlpha = Mth.lerp(flash, endStrength * 0.52F, 1) * tail;
        vertex(buffer, from.add(startWidth), startAlpha);
        vertex(buffer, from.subtract(startWidth), startAlpha);
        vertex(buffer, to.subtract(endWidth), endAlpha);
        vertex(buffer, to.add(endWidth), endAlpha);
    }

    private static void vertex(BufferBuilder buffer, Vec3 point, float alpha) {
        buffer.vertex((float) point.x, (float) point.y, (float) point.z).color(0.72F, 0.88F, 1, alpha).endVertex();
    }

    public static void receive(LightningStrikeMarkerPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !packet.dimension().equals(level.dimension().location())) {
            return;
        }
        if (level != currentLevel) {
            markers.clear();
            currentLevel = level;
        }
        Marker marker = markers.stream().filter(existing -> existing.matches(packet)).findFirst().orElseGet(() -> {
            var created = new Marker(packet);
            markers.add(created);
            return created;
        });
        marker.update(level, packet);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.isPaused()) {
            return;
        }
        if (minecraft.level != currentLevel || minecraft.level == null) {
            clear();
            return;
        }
        long now = currentLevel.getGameTime();
        markers.removeIf(marker -> now >= marker.expiresAt);
        for (Marker marker : markers) {
            marker.tick(currentLevel, now);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || currentLevel == null
                || currentLevel != Minecraft.getInstance().level || markers.isEmpty()) {
            return;
        }
        double now = currentLevel.getGameTime() + event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Marker marker : markers) {
            marker.draw(buffer, camera, now);
        }
        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        markers.clear();
        currentLevel = null;
    }
}
