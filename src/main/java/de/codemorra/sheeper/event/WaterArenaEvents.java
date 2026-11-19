package de.codemorra.sheeper.event;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.world.WaterArenaData;
import de.codemorra.sheeper.network.ModNetwork;
import de.codemorra.sheeper.network.WaterArenaPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Advances arena startup and synchronization even while the boss is unloaded. */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WaterArenaEvents {
    private WaterArenaEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            // Player movement is client-driven; apply vertical water bounds after
            // every entity has moved, including fountain and knockback impulses.
            for (var entity : level.getAllEntities()) {
                if (entity instanceof WaterSheeperEntity boss) {
                    boss.enforceArenaPlayerBounds();
                }
            }
            WaterArenaData.tick(level);
            if (level.getGameTime() % 10 == 0) {
                sendSnapshot(level);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof WaterSheeperEntity boss) {
            RemovalReason reason = boss.getRemovalReason();
            if (reason != null && (reason.shouldDestroy() || reason == RemovalReason.CHANGED_DIMENSION)) {
                WaterArenaData.get(level).startDraining(level, boss.getUUID());
                sendSnapshot(level);
            }
        }
    }
    /** Starts visible drainage immediately; periodic snapshots also cover login and missed updates. */
    private static void sendSnapshot(ServerLevel level) {
        WaterArenaPacket snapshot = new WaterArenaPacket(level.dimension().location(),
                WaterArenaData.animatedVolumes(level));
        for (var player : level.players()) {
            ModNetwork.sendWaterArenas(player, snapshot);
        }
    }

}
