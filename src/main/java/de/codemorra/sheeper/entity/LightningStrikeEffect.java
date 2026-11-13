package de.codemorra.sheeper.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * Uses the vanilla bolt renderer without running lightning's world
 * interactions.
 */
public final class LightningStrikeEffect extends LightningBolt {
    private int age;

    public LightningStrikeEffect(EntityType<? extends LightningBolt> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        // LightningBolt.tick also affects rods, copper and entities even in some
        // visual-only paths.
        // Only ordinary entity housekeeping is appropriate for this short-lived effect.
        baseTick();
        if (++age >= 8) {
            discard();
        } else if (level.isClientSide && age % 2 == 0) {
            seed = random.nextLong();
        }
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
