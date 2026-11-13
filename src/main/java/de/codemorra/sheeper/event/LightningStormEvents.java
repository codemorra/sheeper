package de.codemorra.sheeper.event;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.world.LightningStormData;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Weather and edge lightning do not depend on an arena participant being
 * present.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LightningStormEvents {
    private LightningStormEvents() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            LightningStormData.get(level).tick(level);
        }
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof LightningSheeperEntity boss) {
            RemovalReason reason = boss.getRemovalReason();
            if (reason != null && (reason.shouldDestroy() || reason == RemovalReason.CHANGED_DIMENSION)) {
                LightningStormData.get(level).remove(level, boss.getUUID());
            }
        }
    }
}
