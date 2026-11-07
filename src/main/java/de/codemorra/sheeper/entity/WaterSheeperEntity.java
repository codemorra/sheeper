package de.codemorra.sheeper.entity;

import java.util.Set;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Comparator;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.Level;

/**
 * Water boss foundation with incremental discovery and tracked initial water
 * placement.
 */
public class WaterSheeperEntity extends ElementalSheeperEntity {
    @Nullable
    private BlockPos arenaCenter;
    @Nullable
    private WaterArenaSearch arenaSearch;
    @Nullable
    private Queue<BlockPos> waterPlacement;
    private int placementWave = -1;
    private long nextFloodWaveTick;

    public WaterSheeperEntity(EntityType<? extends Sheep> type, Level level) {
        super(type, level, Element.WATER);
    }

    /**
     * Returns the fixed arena origin on the logical server. A new boss captures
     * its placed position on the first tick, or earlier if queried or saved.
     */
    public BlockPos getArenaCenter() {
        if (arenaCenter == null) {
            arenaCenter = blockPosition().immutable();
        }
        return arenaCenter;
    }

    /**
     * Horizontal encounter radius in blocks, reserved for arena and combat logic.
     */
    public double getCombatRadius() {
        return SheeperConfig.WATER_COMBAT_RADIUS.get();
    }

    /** Keeps the configured movement area within the encounter area. */
    public double getMovementRadius() {
        return Math.min(SheeperConfig.WATER_MOVEMENT_RADIUS.get(), getCombatRadius());
    }

    public boolean isArenaSearchComplete() {
        return arenaSearch != null && arenaSearch.isComplete();
    }

    /**
     * Server-side, read-only surface cells, potentially incomplete during
     * discovery.
     */
    public Set<BlockPos> getArenaSurfacePositions() {
        return arenaSearch == null ? Set.of() : arenaSearch.getSurfacePositions();
    }

    @Override
    public void tick() {
        if (!level.isClientSide) {
            // Capture placement before gravity or future AI can move the boss.
            getArenaCenter();
            if (isAlive()) {
                if (arenaSearch == null) {
                    arenaSearch = new WaterArenaSearch(level, getArenaCenter(), getCombatRadius(),
                            SheeperConfig.WATER_ARENA_HEIGHT_RANGE.get(), SheeperConfig.WATER_ARENA_STEP_HEIGHT.get());
                }
                arenaSearch.advance(SheeperConfig.WATER_ARENA_SEARCH_BUDGET.get());
                if (arenaSearch.isComplete()) {
                    placeArenaWater((ServerLevel) level);
                }
            }
        }
        super.tick();
    }

    private void placeArenaWater(ServerLevel level) {
        WaterArenaData data = WaterArenaData.get(level);
        if (data.isGenerated(getUUID())) {
            return;
        }
        if (waterPlacement == null) {
            var positions = new ArrayList<>(getArenaSurfacePositions());
            // Deferred chunks may finish discovery late; still place nearer layers first.
            positions.sort(Comparator.comparingInt(arenaSearch::getSurfaceDistance));
            waterPlacement = new ArrayDeque<>(positions);
        }
        int interval = SheeperConfig.WATER_ARENA_FLOOD_WAVE_TICKS.get();
        long now = level.getGameTime();
        if (interval > 0 && !waterPlacement.isEmpty()
                && arenaSearch.getSurfaceDistance(waterPlacement.peek()) != placementWave) {
            if (now < nextFloodWaveTick) {
                return;
            }
            placementWave = arenaSearch.getSurfaceDistance(waterPlacement.peek());
        }
        int candidates = Math.min(waterPlacement.size(), SheeperConfig.WATER_ARENA_BLOCK_BUDGET.get());
        for (int examined = 0; examined < candidates; examined++) {
            BlockPos pos = waterPlacement.peek();
            int distance = arenaSearch.getSurfaceDistance(pos);
            if (interval > 0 && distance != placementWave || !WaterArenaData.isLoaded(level, pos)) {
                break;
            }
            waterPlacement.remove();
            data.claim(level, getUUID(), pos, distance);
        }
        if (interval > 0 && !waterPlacement.isEmpty()
                && arenaSearch.getSurfaceDistance(waterPlacement.peek()) != placementWave) {
            nextFloodWaveTick = now + interval;
        }
        if (waterPlacement.isEmpty()) {
            data.finishGeneration(getUUID());
            waterPlacement = null;
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (dead && level instanceof ServerLevel server) {
            WaterArenaData.get(server).release(getUUID());
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("WaterArenaCenter", getArenaCenter().asLong());
        tag.putString("WaterArenaDimension", level.dimension().location().toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Existing bosses without an origin establish one at their loaded position.
        arenaCenter = tag.contains("WaterArenaCenter", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("WaterArenaCenter"))
                : null;
        if (tag.contains("WaterArenaDimension", Tag.TAG_STRING)
                && !tag.getString("WaterArenaDimension").equals(level.dimension().location().toString())) {
            arenaCenter = null;
        }
        // Derived surface data is rediscovered from the saved origin after loading.
        arenaSearch = null;
        waterPlacement = null;
        placementWave = -1;
        nextFloodWaveTick = 0;
    }
}
