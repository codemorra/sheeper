package de.codemorra.sheeper.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.network.LightningRodShelterPacket;
import de.codemorra.sheeper.world.LightningRodShelter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draws the blue formation bolts and shelter spheres, highlighting the shell from inside. */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LightningRodShelterVisuals {
    private static final int SEGMENTS = 32;
    private static final int BANDS = 12;
    private static final Map<UUID, Snapshot> active = new HashMap<>();
    private static ClientLevel currentLevel;
    private static ShaderInstance glowShader;

    private record Snapshot(LightningRodShelterPacket packet, long receivedAt) {
        double visibilityAt(double time) {
            if (packet.dissolveTicks() == 0) {
                return 1;
            }
            double progress = Mth.clamp((time - packet.dissolveStart()) / packet.dissolveTicks(), 0, 1);
            return 1 - progress * progress * (3 - 2 * progress);
        }
    }

    private LightningRodShelterVisuals() {
    }

    /** Picks up the additive shader after each resource reload. */
    static void setGlowShader(ShaderInstance shader) {
        glowShader = shader;
    }

    /** Keeps overlapping encounters independent and drops stale positions on a dimension change. */
    public static void receive(LightningRodShelterPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !packet.dimension().equals(level.dimension().location())) {
            return;
        }
        if (level != currentLevel) {
            active.clear();
            currentLevel = level;
        }
        if (packet.shelters().isEmpty()) {
            active.remove(packet.owner());
        } else {
            active.put(packet.owner(), new Snapshot(packet, level.getGameTime()));
        }
    }

    /** Uses the visible shell sizes when clipping jagged arcs on this frame. */
    public static List<LightningRodShelter> visibleShells(ClientLevel level, double time) {
        if (level != currentLevel || active.isEmpty()) {
            return List.of();
        }
        var shells = new ArrayList<LightningRodShelter>();
        for (Snapshot snapshot : active.values()) {
            if (level.getGameTime() - snapshot.receivedAt() > 25 || snapshot.visibilityAt(time) <= 0) {
                continue;
            }
            double scale = LightningRodShelter.formationRadiusScale(time - snapshot.packet().formationStart());
            if (scale > 0) {
                for (LightningRodShelter shelter : snapshot.packet().shelters()) {
                    shells.add(scale == 1 ? shelter
                            : new LightningRodShelter(shelter.center(), shelter.radius() * scale));
                }
            }
        }
        return shells;
    }

    /** Clips one segment to its first visible shelter, preserving the endpoint on a miss. */
    public static Vec3 clipSegment(List<LightningRodShelter> shells, Vec3 from, Vec3 to) {
        Vec3 hit = to;
        for (LightningRodShelter shell : shells) {
            hit = shell.intercept(from, hit);
        }
        return hit;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        var minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || currentLevel == null
                || currentLevel != minecraft.level || active.isEmpty()) {
            return;
        }
        long tick = currentLevel.getGameTime();
        active.values().removeIf(snapshot -> tick - snapshot.receivedAt() > 25);
        if (active.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        Vec3 player = minecraft.player == null ? camera : minecraft.player.getBoundingBox().getCenter();
        double time = tick + (double) event.getPartialTick();

        RenderSystem.setShader(() -> glowShader == null ? GameRenderer.getPositionColorShader() : glowShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        List<LightningRodShelter> shells = visibleShells(currentLevel, time);
        for (Snapshot snapshot : active.values()) {
            double visibility = snapshot.visibilityAt(time);
            if (visibility <= 0) {
                continue;
            }
            double age = time - snapshot.packet().formationStart();
            if (age < 0) {
                continue;
            }
            Vec3 origin = snapshot.packet().boltOrigin();
            for (LightningRodShelter shelter : snapshot.packet().shelters()) {
                double sinceImpact = age - LightningRodShelter.BOLT_TRAVEL_TICKS;
                if (sinceImpact < 3) {
                    if (camera.distanceToSqr(origin) <= 96 * 96 || camera.distanceToSqr(shelter.center()) <= 96 * 96) {
                        double progress = Math.min(1, age / LightningRodShelter.BOLT_TRAVEL_TICKS);
                        float brightness = (float) (1 - Math.max(0, sinceImpact) / 3);
                        drawBolt(buffer, camera, origin, shelter.center(), progress, brightness, shells);
                    }
                }
                if (sinceImpact >= 0 && camera.distanceToSqr(shelter.center()) <= 64 * 64) {
                    double progress = Math.min(1, sinceImpact / LightningRodShelter.FORMATION_TICKS);
                    double size = 1 - (1 - progress) * (1 - progress);
                    double radius = shelter.radius() * LightningRodShelter.formationRadiusScale(age);
                    drawSphere(buffer, shelter.center().subtract(camera), radius,
                            player.distanceToSqr(shelter.center()) <= radius * radius,
                            time, visibility * (0.35 + 0.65 * size));
                }
            }
        }
        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Advances a jagged blue bolt toward the sphere's center without revealing the sphere early. */
    private static void drawBolt(BufferBuilder buffer, Vec3 camera, Vec3 origin, Vec3 end, double progress,
            float brightness, List<LightningRodShelter> shells) {
        Vec3 delta = end.subtract(origin);
        Vec3 side = new Vec3(-delta.z, 0, delta.x).normalize();
        if (side.lengthSqr() == 0) {
            side = new Vec3(1, 0, 0);
        }
        Vec3 up = side.cross(delta).normalize();
        int segments = Mth.clamp(Mth.ceil(delta.length()), 5, 36);
        double head = segments * progress;
        Vec3 previous = origin;
        for (int step = 0; step < head; step++) {
            Vec3 from = boltPoint(origin, end, side, up, step, segments);
            Vec3 to = boltPoint(origin, end, side, up, step + 1, segments);
            Vec3 next = from.lerp(to, Math.min(1, head - step));
            Vec3 hit = clipSegment(shells, previous, next);
            boltRibbon(buffer, previous.subtract(camera), hit.subtract(camera), side.scale(0.09), brightness);
            boltRibbon(buffer, previous.subtract(camera), hit.subtract(camera), up.scale(0.09), brightness);
            if (hit != next) {
                break;
            }
            previous = next;
        }
    }

    private static Vec3 boltPoint(Vec3 origin, Vec3 end, Vec3 side, Vec3 up, int step, int segments) {
        double progress = (double) step / segments;
        double envelope = Math.sin(progress * Math.PI);
        return origin.lerp(end, progress).add(side.scale(0.8 * envelope * Math.sin(step * 2.61)))
                .add(up.scale(0.55 * envelope * Math.sin(step * 2.13)));
    }

    private static void boltRibbon(BufferBuilder buffer, Vec3 start, Vec3 end, Vec3 offset, float brightness) {
        vertex(buffer, start.subtract(offset), brightness * 0.8F);
        vertex(buffer, end.subtract(offset), brightness * 0.92F);
        vertex(buffer, end.add(offset), brightness * 0.92F);
        vertex(buffer, start.add(offset), brightness * 0.8F);
    }

    /** Draws an emissive shell whose glow is strongest along the silhouette. */
    private static void drawSphere(BufferBuilder buffer, Vec3 center, double radius, boolean inside, double time,
            double visibility) {
        double brightness = inside ? 0.16 : 0.11;
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double angle = segment * Math.PI * 2 / SEGMENTS;
            double nextAngle = (segment + 1) * Math.PI * 2 / SEGMENTS;
            for (int band = 0; band < BANDS; band++) {
                double from = band * Math.PI / BANDS - Math.PI / 2;
                double to = (band + 1) * Math.PI / BANDS - Math.PI / 2;
                sphereVertex(buffer, center, radius, angle, from, time, brightness, visibility);
                sphereVertex(buffer, center, radius, nextAngle, from, time, brightness, visibility);
                sphereVertex(buffer, center, radius, nextAngle, to, time, brightness, visibility);
                sphereVertex(buffer, center, radius, angle, to, time, brightness, visibility);
            }
        }
    }

    private static void sphereVertex(BufferBuilder buffer, Vec3 center, double radius, double angle,
            double elevation, double time, double brightness, double visibility) {
        double horizontal = Math.cos(elevation);
        var normal = new Vec3(Math.cos(angle) * horizontal, Math.sin(elevation), Math.sin(angle) * horizontal);
        Vec3 point = center.add(normal.scale(radius));
        double facing = Math.abs(normal.dot(point.normalize()));
        float rim = (float) Mth.clamp(1 - facing, 0, 1);
        double pulse = 0.026 * Math.sin(angle * 3 + elevation * 4 - time * 0.14);
        float alpha = (float) (visibility * (brightness + pulse + 0.38 * rim * rim));
        buffer.vertex((float) point.x, (float) point.y, (float) point.z)
                .color(0.28F + rim * 0.42F, 0.62F + rim * 0.32F, 1, alpha).endVertex();
    }

    private static void vertex(BufferBuilder buffer, Vec3 point, float alpha) {
        buffer.vertex((float) point.x, (float) point.y, (float) point.z).color(0.30F, 0.64F, 1, alpha).endVertex();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        active.clear();
        currentLevel = null;
    }
}
