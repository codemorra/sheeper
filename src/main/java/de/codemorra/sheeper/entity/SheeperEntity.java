package de.codemorra.sheeper.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.Level;

/**
 * A custom entity that inherits vanilla sheep behavior.
 */
public class SheeperEntity extends Sheep {
    public SheeperEntity(EntityType<? extends Sheep> entityType, Level level) {
        super(entityType, level);
    }
}
