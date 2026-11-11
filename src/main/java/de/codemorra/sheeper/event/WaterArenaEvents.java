package de.codemorra.sheeper.event;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.WaterSheeperEntity;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Cleanup continues even when the responsible boss is no longer loaded. */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WaterArenaEvents {
    private WaterArenaEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            WaterArenaData.tick(level, SheeperConfig.WATER_ARENA_BLOCK_BUDGET_PER_SECOND.get() / 20);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof WaterSheeperEntity boss) {
            RemovalReason reason = boss.getRemovalReason();
            if (reason != null && (reason.shouldDestroy() || reason == RemovalReason.CHANGED_DIMENSION)) {
                WaterArenaData.get(level).release(boss.getUUID());
            }
        }
    }
}
