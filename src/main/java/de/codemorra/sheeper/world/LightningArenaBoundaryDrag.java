package de.codemorra.sheeper.world;

import java.util.List;
import java.util.function.Function;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.network.LightningWeatherPacket.Arena;
import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Applies local movement resistance to fully formed storm walls. */
public final class LightningArenaBoundaryDrag {
    private record Settings(double width, double strength) {
    }

    private static Function<Level, List<Arena>> clientLookup = level -> List.of();
    private static Settings clientSettings = new Settings(0, 0);

    private LightningArenaBoundaryDrag() {
    }

    public static void setClientLookup(Function<Level, List<Arena>> lookup) {
        clientLookup = lookup;
    }

    /** Client-side movement prediction uses the server's common config values. */
    public static void receive(double width, double strength) {
        clientSettings = new Settings(width, strength);
    }

    /** Uses the inner edge of the same side band for admission to a storm's combat area. */
    public static double entryInset(double radius) {
        return LightningSheeperConfig.LIGHTNING_STORM_ENABLED.get()
                ? WaterArenaBoundary.slowdownWidth(LightningSheeperConfig.LIGHTNING_BOUNDARY_SLOWDOWN_WIDTH.get(), radius)
                : 0;
    }

    /** Leaves vertical movement intact and slows only the component across an exterior side. */
    public static Vec3 limitMovement(Entity entity, MoverType type, Vec3 motion) {
        if (!(entity instanceof Player player) || !WaterArenaBoundaryDrag.canSlow(player, type, motion)) {
            return motion;
        }
        Level level = player.level;
        Settings settings = level instanceof ServerLevel
                ? new Settings(LightningSheeperConfig.LIGHTNING_BOUNDARY_SLOWDOWN_WIDTH.get(),
                        LightningSheeperConfig.LIGHTNING_BOUNDARY_SLOWDOWN_STRENGTH.get())
                : clientSettings;
        if (settings.width() <= 0 || settings.strength() <= 0) {
            return motion;
        }
        List<Arena> arenas = level instanceof ServerLevel server
                ? LightningStormData.get(server).visibleArenas()
                : clientLookup.apply(level);
        long now = level.getGameTime();
        // The shared cylinder solver takes an inclusive top block; storm ceilings are exclusive.
        List<VolumeBounds> bounds = arenas.stream()
                .filter(arena -> !arena.isBuilding(now) && !arena.isDraining())
                .map(arena -> new VolumeBounds(arena.center(), arena.radius(), arena.minimumY(), arena.maximumY() - 1))
                .toList();
        return WaterArenaBoundaryDrag.slowAcrossSides(bounds, player.getBoundingBox().getCenter(), motion,
                settings.width(), settings.strength());
    }
}
