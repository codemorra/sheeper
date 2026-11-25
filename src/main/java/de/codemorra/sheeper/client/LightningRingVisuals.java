package de.codemorra.sheeper.client;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.LightningRingSurface;
import de.codemorra.sheeper.network.LightningRingVisualPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draws charges around the boss and moving, jagged arcs along each ring's damage band. */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LightningRingVisuals {
    private static final double VIEW_RANGE = 52;
    private static final Map<LightningRingVisualPacket, Long> active = new HashMap<>();
    private static ClientLevel currentLevel;

    private LightningRingVisuals() {
    }

    public static void receive(LightningRingVisualPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !packet.dimension().equals(level.dimension().location())) {
            return;
        }
        if (level != currentLevel) {
            active.clear();
            currentLevel = level;
        }
        // Short refreshes let the effect expire quickly if the attack is cancelled or the boss unloads.
        active.put(packet, level.getGameTime() + 6);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || currentLevel == null
                || currentLevel != Minecraft.getInstance().level || active.isEmpty()) {
            return;
        }
        double time = currentLevel.getGameTime() + (double) event.getPartialTick();
        active.entrySet().removeIf(entry -> time >= entry.getValue());
        if (active.isEmpty()) {
            return;
        }
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
        for (LightningRingVisualPacket packet : active.keySet()) {
            double age = Math.max(0, time - packet.startTick());
            if (packet.warningTicks() > 0) {
                if (age < packet.warningTicks() && camera.distanceToSqr(packet.origin()) < VIEW_RANGE * VIEW_RANGE) {
                    drawWarning(buffer, camera, packet, age);
                }
            } else {
                drawWave(buffer, camera, packet, age);
            }
        }
        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Tightens a few flickering arcs around the boss before the first jump. */
    private static void drawWarning(BufferBuilder buffer, Vec3 camera, LightningRingVisualPacket packet, double age) {
        double progress = age / packet.warningTicks();
        Vec3 center = packet.origin().add(0, packet.bodyHeight() * 0.52, 0);
        double reach = packet.bodyHeight() * 0.36 + (1 - progress) * 0.85;
        for (int index = 0; index < 7; index++) {
            double angle = age * 0.19 + index * Math.PI * 2 / 7;
            double y = (Math.sin(index * 2.8 + age * 0.23) * 0.23) * packet.bodyHeight();
            Vec3 start = orbit(center, reach, angle, y);
            Vec3 bend = orbit(center, reach * 0.84, angle + 0.28, y + 0.17);
            Vec3 end = orbit(center, reach * 0.89, angle + 0.49, y - 0.10);
            float alpha = (float) (0.36 + progress * 0.42 + 0.13 * Math.sin(age * 1.3 + index * 2.5));
            arc(buffer, camera, start, bend, end, 0.028, alpha);
        }
    }

    private static void drawWave(BufferBuilder buffer, Vec3 camera, LightningRingVisualPacket packet, double age) {
        double radius = age * packet.speed();
        double centerDistance = packet.origin().subtract(Vec3.atBottomCenterOf(packet.arenaCenter())).horizontalDistance();
        if (radius > packet.arenaRadius() + centerDistance + packet.width() * 0.5
                || Math.abs(camera.subtract(packet.origin()).horizontalDistance() - radius) > VIEW_RANGE) {
            return;
        }
        if (age < 6) {
            drawRelease(buffer, camera, packet, radius, age);
        }
        if (radius < 0.25) {
            return;
        }
        double arcSpacing = Math.max(0.75, packet.spacing() * 3.1);
        int count = Mth.clamp(Mth.ceil(2 * Math.PI * radius / arcSpacing), 9, 600);
        double step = 2 * Math.PI / count;
        double phase = packet.startTick() % 97 * 0.17 + age * 1.15 / Math.max(4, radius);
        double wobble = Math.min(0.32, packet.width() * 0.45);
        for (int index = 0; index < count; index++) {
            double angle = phase + index * step;
            Vec3 sample = orbit(packet.origin(), radius, angle, 0);
            if (camera.subtract(sample).horizontalDistanceSqr() > VIEW_RANGE * VIEW_RANGE) {
                continue;
            }
            double jitter = Math.sin(index * 4.31 + age * 1.7);
            Vec3 from = groundPoint(packet, radius + wobble * jitter, angle - step * 0.44, 0.09);
            Vec3 bend = groundPoint(packet, radius - wobble * jitter, angle, 0.16 + 0.07 * jitter);
            Vec3 to = groundPoint(packet, radius + wobble * 0.5 * jitter, angle + step * 0.38, 0.09);
            if (from != null && bend != null && to != null
                    && Math.abs(from.y - bend.y) < 1.25 && Math.abs(bend.y - to.y) < 1.25) {
                float alpha = (float) (0.57 + 0.29 * Math.sin(index * 2.37 - age * 2.1));
                arc(buffer, camera, from, bend, to, 0.035, alpha);
            }
        }
    }

    /** Connects the short flash at the boss's body to the first expanding edge. */
    private static void drawRelease(BufferBuilder buffer, Vec3 camera, LightningRingVisualPacket packet,
            double radius, double age) {
        Vec3 center = packet.origin().add(0, packet.bodyHeight() * 0.55, 0);
        float alpha = (float) (1 - age / 6);
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI / 4 + (packet.startTick() % 29) * 0.20;
            Vec3 bend = orbit(center, Math.max(0.38, radius * 0.40), angle + 0.21, -packet.bodyHeight() * 0.18);
            Vec3 end = groundPoint(packet, Math.max(0.72, radius), angle, 0.13);
            if (end != null) {
                arc(buffer, camera, center, bend, end, 0.055, alpha);
            }
        }
    }

    @Nullable
    private static Vec3 groundPoint(LightningRingVisualPacket packet, double radius, double angle, double height) {
        Vec3 point = orbit(packet.origin(), Math.max(0, radius), angle, 0);
        if (!packet.isWithinArena(point.x, point.z)) {
            return null;
        }
        double ground = LightningRingSurface.find(currentLevel, point.x, point.z, packet.origin().y,
                packet.minY(), packet.maxY());
        return Double.isFinite(ground) ? new Vec3(point.x, ground + height, point.z) : null;
    }

    private static Vec3 orbit(Vec3 origin, double radius, double angle, double height) {
        return origin.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    private static void arc(BufferBuilder buffer, Vec3 camera, Vec3 from, Vec3 bend, Vec3 to, double width, float alpha) {
        Vec3 start = from.subtract(camera);
        Vec3 elbow = bend.subtract(camera);
        Vec3 end = to.subtract(camera);
        segment(buffer, start, elbow, width, alpha);
        segment(buffer, elbow, end, width, alpha);
    }

    private static void segment(BufferBuilder buffer, Vec3 from, Vec3 to, double width, float alpha) {
        Vec3 forward = to.subtract(from).normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x).scale(width);
        Vec3 up = forward.cross(side);
        ribbon(buffer, from, to, side.scale(2.6), 0.28F, 0.63F, alpha * 0.42F);
        ribbon(buffer, from, to, up.scale(2.6), 0.28F, 0.63F, alpha * 0.42F);
        ribbon(buffer, from, to, side, 0.80F, 0.94F, alpha);
        ribbon(buffer, from, to, up, 0.80F, 0.94F, alpha);
    }

    private static void ribbon(BufferBuilder buffer, Vec3 from, Vec3 to, Vec3 width, float red, float green,
            float alpha) {
        vertex(buffer, from.add(width), red, green, alpha);
        vertex(buffer, from.subtract(width), red, green, alpha);
        vertex(buffer, to.subtract(width), red, green, alpha);
        vertex(buffer, to.add(width), red, green, alpha);
    }

    private static void vertex(BufferBuilder buffer, Vec3 point, float red, float green, float alpha) {
        buffer.vertex((float) point.x, (float) point.y, (float) point.z).color(red, green, 1, alpha).endVertex();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        active.clear();
        currentLevel = null;
    }
}
