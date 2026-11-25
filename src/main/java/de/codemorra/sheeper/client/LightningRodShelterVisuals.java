package de.codemorra.sheeper.client;

import java.util.HashMap;
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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draws the shelter spheres and brightens the shell while the local player is inside. */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LightningRodShelterVisuals {
    private static final int SEGMENTS = 32;
    private static final int BANDS = 12;
    private static final Map<UUID, Snapshot> active = new HashMap<>();
    private static ClientLevel currentLevel;

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
        for (Snapshot snapshot : active.values()) {
            double visibility = snapshot.visibilityAt(time);
            if (visibility <= 0) {
                continue;
            }
            for (LightningRodShelter shelter : snapshot.packet().shelters()) {
                if (camera.distanceToSqr(shelter.center()) <= 64 * 64) {
                    double radius = shelter.radius() * (0.8 + 0.2 * visibility);
                    drawSphere(buffer, shelter.center().subtract(camera), radius,
                            shelter.contains(player), time, visibility);
                }
            }
        }
        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Renders both halves with depth testing so solid terrain hides the buried part. */
    private static void drawSphere(BufferBuilder buffer, Vec3 center, double radius, boolean inside, double time,
            double visibility) {
        double brightness = inside ? 0.16 : 0.10;
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double angle = segment * Math.PI * 2 / SEGMENTS;
            double nextAngle = (segment + 1) * Math.PI * 2 / SEGMENTS;
            for (int band = 0; band < BANDS; band++) {
                double from = band * Math.PI / BANDS - Math.PI / 2;
                double to = (band + 1) * Math.PI / BANDS - Math.PI / 2;
                float alpha = (float) (visibility * (brightness
                        + 0.026 * Math.sin(angle * 3 + from * 4 - time * 0.14)));
                vertex(buffer, spherePoint(center, radius, angle, from), alpha);
                vertex(buffer, spherePoint(center, radius, nextAngle, from), alpha);
                vertex(buffer, spherePoint(center, radius, nextAngle, to), alpha);
                vertex(buffer, spherePoint(center, radius, angle, to), alpha);
            }
        }
    }

    private static Vec3 spherePoint(Vec3 center, double radius, double angle, double elevation) {
        double horizontalRadius = radius * Math.cos(elevation);
        return center.add(Math.cos(angle) * horizontalRadius, radius * Math.sin(elevation),
                Math.sin(angle) * horizontalRadius);
    }

    private static void vertex(BufferBuilder buffer, Vec3 point, float alpha) {
        buffer.vertex((float) point.x, (float) point.y, (float) point.z).color(0.36F, 1, 0.86F, alpha).endVertex();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        active.clear();
        currentLevel = null;
    }
}
