package de.codemorra.sheeper.entity.navigation;

import de.codemorra.sheeper.registry.ModFluids;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;

/**
 * Extends ground navigation's liquid detection to boss water so ordinary
 * collision-aware paths can still be created and followed inside the arena.
 */
public final class WaterArenaNavigation extends GroundPathNavigation {
    /**
     * Creates ground navigation with support for the arena's custom fluid.
     *
     * @param mob   entity whose dimensions and movement control are used for paths
     * @param level world containing the navigable terrain
     */
    public WaterArenaNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    /** {@inheritDoc} Includes boss water alongside vanilla water and lava. */
    @Override
    protected boolean isInLiquid() {
        // Vanilla ground navigation only recognizes ordinary water and lava.
        return super.isInLiquid() || mob.isInFluidType(ModFluids.BOSS_WATER_TYPE.get());
    }
}
