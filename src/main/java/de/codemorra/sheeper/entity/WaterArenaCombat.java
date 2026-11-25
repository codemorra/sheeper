package de.codemorra.sheeper.entity;

import de.codemorra.sheeper.config.WaterSheeperConfig;

/** Binds the shared encounter rules to the Water Sheeper's own configuration. */
final class WaterArenaCombat extends ElementalArenaCombat {
    WaterArenaCombat(WaterSheeperEntity boss) {
        super(boss, boss::canPlayerDamage, boss::resetArenaEncounter,
                WaterSheeperConfig.WATER_MAX_HEALTH::get, WaterSheeperConfig.WATER_HEALTH_PER_ADDITIONAL_PLAYER::get,
                WaterSheeperConfig.WATER_EMPTY_ARENA_GRACE::get, WaterSheeperConfig.WATER_EMPTY_ARENA_REGENERATION::get);
    }
}
