package de.codemorra.sheeper.world;

import java.util.function.BiPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;

/**
 * Positional breathing protection without changing natural water or granting
 * potion effects.
 */
public final class ArenaWater {
    private static BiPredicate<Level, BlockPos> clientLookup = (level, pos) -> false;

    private ArenaWater() {
    }

    /**
     * Installs the client snapshot lookup without loading client classes on a
     * dedicated server.
     */
    public static void setClientLookup(BiPredicate<Level, BlockPos> lookup) {
        clientLookup = lookup;
    }

    /**
     * Called by the Forge drowning hook before air consumption. Only players in
     * ordinary water inside a live captured arena gain the same protection as boss
     * water.
     */
    public static boolean allowsBreathing(LivingEntity entity, FluidType type) {
        if (!(entity instanceof Player) || type != ForgeMod.WATER_TYPE.get()) {
            return false;
        }
        BlockPos eye = new BlockPos(entity.getEyePosition());
        if (entity.level instanceof ServerLevel server) {
            return WaterArenaData.activeVolumes(server).stream().anyMatch(bounds -> bounds.contains(eye));
        }
        return clientLookup.test(entity.level, eye);
    }
}
