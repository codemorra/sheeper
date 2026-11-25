package de.codemorra.sheeper.client;

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
import de.codemorra.sheeper.network.LightningTornadoPacket;
import de.codemorra.sheeper.world.LightningTornado;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Renders a world-anchored funnel with fast layers of wind and occasional lightning. */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LightningTornadoVisuals {
    private record Snapshot(List<LightningTornado> tornados, long receivedAt) {
        boolean expired(long now) {
            return now - receivedAt > 8 || tornados.stream().noneMatch(tornado -> now < tornado.endTick());
        }
    }

    private static final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private static ClientLevel currentLevel;

    private LightningTornadoVisuals() {
    }

    public static void receive(LightningTornadoPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !packet.dimension().equals(level.dimension().location())) {
            return;
        }
        if (currentLevel != level) {
            snapshots.clear();
            currentLevel = level;
        }
        if (packet.tornados().isEmpty()) {
            snapshots.remove(packet.owner());
        } else {
            snapshots.put(packet.owner(), new Snapshot(packet.tornados(), level.getGameTime()));
        }
    }

    /** Provides the same live funnel samples to the renderer and predicted movement. */
    public static List<LightningTornado> activeTornados(Level level) {
        if (level != currentLevel || snapshots.isEmpty()) {
            return List.of();
        }
        long now = level.getGameTime();
        snapshots.values().removeIf(snapshot -> snapshot.expired(now));
        return snapshots.values().stream().flatMap(snapshot -> snapshot.tornados().stream())
                .filter(tornado -> now < tornado.endTick()).toList();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || level == null) {
            return;
        }
        List<LightningTornado> active = activeTornados(level);
        if (active.isEmpty()) {
            return;
        }
        double now = level.getGameTime() + (double) event.getPartialTick();
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
        for (LightningTornado tornado : active) {
            Vec3 center = tornado.centerAt(now);
            if (camera.distanceToSqr(center) <= 68 * 68) {
                drawFunnel(buffer, camera, tornado, center, now);
            }
        }
        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawFunnel(BufferBuilder buffer, Vec3 camera, LightningTornado tornado, Vec3 center,
            double now) {
        boolean warning = now < tornado.activeFrom();
        float intensity = warning
                ? (float) (0.27 + 0.20 * Mth.clamp(1 - (tornado.activeFrom() - now) / 25, 0, 1))
                : (float) (0.47 + 0.53 * tornado.powerAt(now));
        float fade = (float) tornado.fadeAt(now);
        double phase = (now % 2000) * 0.29;
        double shellWidth = 0.64 + 0.36 * fade;
        for (int strand = 0; strand < 7; strand++) {
            Vec3 previous = null;
            for (int step = 0; step <= 20; step++) {
                double portion = step / 20.0;
                double angle = phase + strand * Math.PI * 2 / 7 + portion * 8;
                double radius = tornado.radiusAtHeight(portion) * shellWidth * (0.71 + 0.20 * Math.sin(strand * 2.7 + portion * 4));
                double y = 0.18 + portion * (tornado.height() - 0.48);
                Vec3 point = center.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                if (!tornado.isInsideArena(point)) {
                    previous = null;
                    continue;
                }
                Vec3 relative = point.subtract(camera);
                if (previous != null) {
                    float alpha = (float) (0.30 * intensity * fade * Math.sin(0.18 + Math.PI * 0.94 * portion));
                    ribbon(buffer, previous, relative, 0.20 + portion * 0.45, 0.40F, 0.51F, 0.65F, alpha);
                    if (strand % 3 == 0 && (step + (int) (now / 3)) % 5 < 2) {
                        ribbon(buffer, previous, relative, 0.035, 0.76F, 0.90F, 1, intensity * fade * 0.78F);
                    }
                }
                previous = relative;
            }
        }
    }

    private static void ribbon(BufferBuilder buffer, Vec3 start, Vec3 end, double halfHeight, float red, float green,
            float blue, float alpha) {
        vertex(buffer, start, -halfHeight, red, green, blue, alpha * 0.30F);
        vertex(buffer, end, -halfHeight, red, green, blue, alpha * 0.30F);
        vertex(buffer, end, halfHeight, red, green, blue, alpha);
        vertex(buffer, start, halfHeight, red, green, blue, alpha);
    }

    private static void vertex(BufferBuilder buffer, Vec3 point, double height, float red, float green, float blue,
            float alpha) {
        buffer.vertex((float) point.x, (float) (point.y + height), (float) point.z)
                .color(red, green, blue, alpha).endVertex();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        snapshots.clear();
        currentLevel = null;
    }
}
