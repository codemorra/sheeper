package de.codemorra.sheeper.event;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.util.SheeperWoolMining;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Protects dropped items from unsafe wool and carpet mining blasts.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SheeperWoolExplosionEvents {
    private SheeperWoolExplosionEvents() {
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getExplosion().getDamageSource() != SheeperWoolMining.DAMAGE) {
            return;
        }

        event.getAffectedEntities().removeIf(entity -> entity instanceof ItemEntity);
    }
}
