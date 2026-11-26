package de.codemorra.sheeper.client;

import static de.codemorra.sheeper.client.LightningWorldArcs.arc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.network.LightningFanPacket;
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

/** Draws the simultaneous fans from the hovering lightning form to the ground. */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LightningFanVisuals {
    private static final Map<Integer, Fan> active = new HashMap<>();
    private static ClientLevel currentLevel;

    private record Fan(LightningFanPacket previous, LightningFanPacket current, long receivedAt) {
    }

    private LightningFanVisuals() {
    }

    /** Updates the visible fan independently for each boss in the current dimension. */
    public static void receive(LightningFanPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !packet.dimension().equals(level.dimension().location())) {
            return;
        }
        if (level != currentLevel) {
            active.clear();
            currentLevel = level;
        }
        if (packet.ends().isEmpty()) {
            active.remove(packet.bossId());
            return;
        }
        long now = level.getGameTime();
        Fan old = active.get(packet.bossId());
        LightningFanPacket previous = old != null && now - old.receivedAt < 5 ? old.current : packet;
        active.put(packet.bossId(), new Fan(previous, packet, now));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER
                || currentLevel == null || currentLevel != Minecraft.getInstance().level || active.isEmpty()) {
            return;
        }
        double time = currentLevel.getGameTime() + (double) event.getPartialTick();
        active.values().removeIf(fan -> time - fan.receivedAt >= 5);
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
        for (Fan fan : active.values()) {
            drawFan(buffer, camera, fan, time);
        }
        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Matches the previous impact points by player so a target change never drags another fan. */
    private static void drawFan(BufferBuilder buffer, Vec3 camera, Fan fan, double time) {
        double progress = Mth.clamp((time - fan.receivedAt) / 2, 0, 1);
        var entity = currentLevel.getEntity(fan.current.bossId());
        if (entity instanceof LightningSheeperEntity boss && (!boss.isAlive() || boss.isInvisible())) {
            return;
        }
        Vec3 origin = fan.current.origin();
        var shells = LightningRodShelterVisuals.visibleShells(currentLevel, time);
        for (var target : fan.current.ends().entrySet()) {
            List<Vec3> previous = fan.previous.ends().get(target.getKey());
            List<Vec3> current = target.getValue();
            for (int lane = 0; lane < current.size(); lane++) {
                Vec3 end = current.get(lane);
                if (end.equals(origin)) {
                    continue;
                }
                if (previous != null) {
                    Vec3 oldEnd = previous.get(lane);
                    // An arc that hits cover or a body must not keep drawing past its new end.
                    if (!oldEnd.equals(fan.previous.origin()) && oldEnd.distanceToSqr(end) <= 4
                            && oldEnd.distanceToSqr(origin) <= end.distanceToSqr(origin)) {
                        end = oldEnd.lerp(end, progress);
                    }
                }
                drawBolt(buffer, camera, origin, end, time, target.getKey(), lane, shells);
            }
        }
    }

    /** Breaks each arc into linked bends while keeping the core and impact anchored. */
    private static void drawBolt(BufferBuilder buffer, Vec3 camera, Vec3 origin, Vec3 end, double time, int targetId, int lane,
            List<LightningRodShelter> shells) {
        Vec3 direction = end.subtract(origin);
        double length = direction.length();
        Vec3 side = new Vec3(-direction.z, 0, direction.x).normalize();
        Vec3 up = direction.normalize().cross(side);
        int segments = Mth.clamp(Mth.ceil(length / 1.4), 1, 112);
        Vec3 from = origin;
        for (int index = 0; index < segments; index++) {
            double phase = time * 1.28 + targetId * 0.37 + lane * 2.86 + index * 3.95;
            Vec3 anchor = origin.lerp(end, (index + 1.0) / segments);
            // Keep the first stretch steady where it leaves the lightning core.
            double jitter = Mth.clamp(length * index / (2 * segments), 0, 1);
            Vec3 to = index == segments - 1 ? end : anchor.add(side.scale(jitter * 0.19 * Math.sin(phase)))
                    .add(up.scale(jitter * 0.13 * Math.cos(phase * 1.3)));
            Vec3 bend = from.lerp(to, 0.5).add(side.scale(jitter * 0.23 * Math.cos(phase)))
                    .add(up.scale(jitter * 0.17 * Math.sin(phase * 1.1)));
            float alpha = (float) (0.82 + Math.sin(phase) * 0.16);
            Vec3 firstHit = LightningRodShelterVisuals.clipSegment(shells, from, bend);
            Vec3 lastHit = firstHit == bend ? LightningRodShelterVisuals.clipSegment(shells, bend, to) : firstHit;
            arc(buffer, camera, from, firstHit, lastHit, lane == 1 ? 0.038 : 0.026, alpha);
            if (lastHit != to) {
                break;
            }
            from = to;
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        active.clear();
        currentLevel = null;
    }
}
