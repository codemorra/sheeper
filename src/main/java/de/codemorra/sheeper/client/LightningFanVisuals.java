package de.codemorra.sheeper.client;

import static de.codemorra.sheeper.client.LightningWorldArcs.arc;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.network.LightningFanPacket;
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

/** Draws the charge at the Sheeper's head and the arcs to the synchronized impact points. */
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
            drawFan(buffer, camera, fan, time, event.getPartialTick());
        }
        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Interpolates ordinary tracking motion while dropping concealed endpoints immediately. */
    private static void drawFan(BufferBuilder buffer, Vec3 camera, Fan fan, double time, float partialTick) {
        double progress = Mth.clamp((time - fan.receivedAt) / 2, 0, 1);
        var entity = currentLevel.getEntity(fan.current.bossId());
        LightningSheeperEntity boss = entity instanceof LightningSheeperEntity sheeper ? sheeper : null;
        if (boss != null && (!boss.isAlive() || boss.isInvisible())) {
            return;
        }
        Vec3 origin = boss == null ? fan.previous.origin().lerp(fan.current.origin(), progress)
                : boss.getBeamOrigin(partialTick);
        if (fan.current.charge() > 0) {
            double charge = Mth.lerp(progress, fan.previous.charge(), fan.current.charge());
            drawCharge(buffer, camera, boss, origin, partialTick, time, charge);
            return;
        }
        for (int lane = 0; lane < fan.current.ends().size(); lane++) {
            Vec3 end = fan.current.ends().get(lane);
            if (end.equals(fan.current.origin())) {
                continue;
            }
            if (fan.previous.charge() == 0) {
                Vec3 oldEnd = fan.previous.ends().get(lane);
                // A sudden change in length usually means the arc met or cleared an obstacle.
                if (!oldEnd.equals(fan.previous.origin()) && oldEnd.distanceToSqr(end) <= 4) {
                    end = oldEnd.lerp(end, progress);
                }
            }
            drawBolt(buffer, camera, origin, end, time, lane);
        }
    }

    /** Draws staggered, short sparks that converge on the muzzle as the charge builds. */
    private static void drawCharge(BufferBuilder buffer, Vec3 camera, LightningSheeperEntity boss, Vec3 origin,
            float partialTick, double time, double charge) {
        double size = boss == null ? 1 : boss.getSizeMultiplier();
        Vec3 forward = boss == null ? new Vec3(0, 0, 1) : boss.getViewVector(partialTick);
        Vec3 reference = Math.abs(forward.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = forward.cross(reference).normalize();
        Vec3 up = side.cross(forward);
        Vec3 muzzle = origin.add(forward.scale(0.65 * size));
        double reach = (0.42 + charge * 0.26) * size;
        for (int index = 0; index < 6; index++) {
            double life = (time + index * 2.7) / (4 + index * 0.6);
            double age = Mth.frac(life);
            // Pick a new direction for each flash and keep it steady as the spark moves inward.
            double phase = Math.floor(life) * 7.13 + index * 9.47;
            Vec3 offset = side.scale(Math.sin(phase) * reach)
                    .add(up.scale(Math.sin(phase * 1.63 + 1) * reach))
                    .add(forward.scale((0.45 + 0.35 * Math.sin(phase * 2.37)) * reach));
            Vec3 from = muzzle.add(offset.scale(1 - age * 0.7));
            Vec3 to = muzzle.add(offset.scale(0.62 - age * 0.6));
            Vec3 bend = from.lerp(to, 0.5).add(side.scale(Math.sin(phase * 1.91) * 0.09 * size))
                    .add(up.scale(Math.cos(phase * 1.47) * 0.06 * size));
            float alpha = (float) ((0.36 + 0.60 * charge) * Math.sin(Math.PI * age));
            arc(buffer, camera, from, bend, to, 0.018 + charge * 0.017, alpha);
        }
    }

    /** Breaks each arc into linked bends while keeping the mouth and impact anchored. */
    private static void drawBolt(BufferBuilder buffer, Vec3 camera, Vec3 origin, Vec3 end, double time, int lane) {
        Vec3 direction = end.subtract(origin);
        double length = direction.length();
        Vec3 side = new Vec3(-direction.z, 0, direction.x).normalize();
        Vec3 up = direction.normalize().cross(side);
        int segments = Mth.clamp(Mth.ceil(length / 1.4), 1, 112);
        Vec3 from = origin;
        for (int index = 0; index < segments; index++) {
            double phase = time * 1.28 + lane * 2.86 + index * 3.95;
            Vec3 anchor = origin.lerp(end, (index + 1.0) / segments);
            // Keep the first stretch steady as it leaves the head, like the water beam.
            double jitter = Mth.clamp(length * index / (2 * segments), 0, 1);
            Vec3 to = index == segments - 1 ? end : anchor.add(side.scale(jitter * 0.19 * Math.sin(phase)))
                    .add(up.scale(jitter * 0.13 * Math.cos(phase * 1.3)));
            Vec3 bend = from.lerp(to, 0.5).add(side.scale(jitter * 0.23 * Math.cos(phase)))
                    .add(up.scale(jitter * 0.17 * Math.sin(phase * 1.1)));
            float alpha = (float) (0.82 + Math.sin(phase) * 0.16);
            arc(buffer, camera, from, bend, to, lane == 1 ? 0.038 : 0.026, alpha);
            from = to;
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        active.clear();
        currentLevel = null;
    }
}
