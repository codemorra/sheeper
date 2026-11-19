package de.codemorra.sheeper.client;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.network.ModNetwork;
import de.codemorra.sheeper.network.WaterArenaExitPacket;
import de.codemorra.sheeper.world.WaterArenaPushProtection;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Distinguishes deliberate outward swimming from a boss carrying a passive player across the boundary. */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WaterArenaExitInput {
    private WaterArenaExitInput() {
    }

    /** Clear the remaining launch before normal swimming input resumes near the exterior edge. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        var player = event.getEntity();
        var input = event.getInput();
        Vec3 direction = new Vec3(input.leftImpulse, 0, input.forwardImpulse)
                .yRot(-player.getYRot() * Mth.DEG_TO_RAD).normalize();
        if (WaterArenaPushProtection.wantsToLeave(player, direction)) {
            WaterArenaPushProtection.leave(player);
            ModNetwork.requestWaterArenaExit(new WaterArenaExitPacket(direction.x, direction.z));
        }
    }
}
