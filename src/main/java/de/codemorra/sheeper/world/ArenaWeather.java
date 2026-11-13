package de.codemorra.sheeper.world;

import java.util.function.BiFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Position-based additions to vanilla rain; dimension-wide weather stays
 * untouched.
 */
public final class ArenaWeather {
    private static boolean enabled;
    private static BiFunction<Level, BlockPos, Float> clientLookup = (level, pos) -> 0F;

    private ArenaWeather() {
    }

    public static void enable() {
        enabled = true;
    }

    public static void setClientLookup(BiFunction<Level, BlockPos, Float> lookup) {
        clientLookup = lookup;
    }

    public static float intensity(Level level, BlockPos pos) {
        if (!enabled) {
            return 0;
        }
        return level instanceof ServerLevel server ? LightningStormData.get(server).intensity(server, pos)
                : clientLookup.apply(level, pos);
    }

    /**
     * Used by the Level.isRainingAt core hook, preserving roofs and loaded-chunk
     * boundaries.
     */
    public static boolean isRainingAt(Level level, BlockPos pos) {
        return intensity(level, pos) > 0.2F && level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && level.canSeeSky(pos)
                && level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY();
    }

    /**
     * Fire's existing positional rain checks still decide whether the fire is
     * actually exposed.
     */
    public static boolean hasRain(Level level) {
        return level.isRaining() || enabled && level instanceof ServerLevel server
                && LightningStormData.get(server).hasStorms();
    }

    /**
     * Reuses vanilla precipitation handling, e.g. cauldron filling, only in exposed
     * local rain.
     */
    public static void tickPrecipitation(ServerLevel level, LevelChunk chunk) {
        if (!enabled || level.isRaining() || !LightningStormData.get(level).hasStorms()
                || level.random.nextInt(16) != 0) {
            return;
        }
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                level.getBlockRandomPos(chunk.getPos().getMinBlockX(), 0, chunk.getPos().getMinBlockZ(), 15));
        if (isRainingAt(level, pos)) {
            BlockPos floor = pos.below();
            var state = level.getBlockState(floor);
            state.getBlock().handlePrecipitation(state, level, floor, Biome.Precipitation.RAIN);
        }
    }
}
