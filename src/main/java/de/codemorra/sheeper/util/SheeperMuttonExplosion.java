package de.codemorra.sheeper.util;

import de.codemorra.sheeper.SheeperMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Creates small mutton blasts that preserve blocks and dropped items.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SheeperMuttonExplosion {
    private static final DamageSource DAMAGE_SOURCE = DamageSource.explosion((LivingEntity) null);

    private SheeperMuttonExplosion() {
    }

    /**
     * Damages and knocks back nearby entities without starting fires or breaking
     * blocks.
     * Cooking callers remove the affected appliance separately before invoking
     * this.
     */
    public static void explode(ServerLevel level, double x, double y, double z) {
        level.explode(null, DAMAGE_SOURCE, null, x, y, z,
                1.0F, false, Explosion.BlockInteraction.NONE);
    }

    /**
     * Excludes dropped items from both cooking and direct-fire mutton blasts.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getExplosion().getDamageSource() == DAMAGE_SOURCE) {
            event.getAffectedEntities().removeIf(entity -> entity instanceof ItemEntity);
        }
    }
}
