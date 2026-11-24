package de.codemorra.sheeper.world;

import java.util.List;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Soft lateral resistance at exterior arena sides; vertical and tangential motion remain untouched. */
public final class WaterArenaBoundaryDrag {
    private record Settings(double width, double strength) {
    }

    private static volatile Settings clientSettings = new Settings(0, 0);

    private WaterArenaBoundaryDrag() {
    }

    /** Common configs are server-owned; prediction receives the same values with the arena geometry. */
    public static void receive(double width, double strength) {
        clientSettings = new Settings(width, strength);
    }

    /**
     * Slows only movement crossing an exposed side. Native velocity is retained:
     * repeated water ticks must not compound the slowdown into cobweb-like drag.
     *
     * @param type movement origin; client-reported PLAYER displacement is already slowed
     * @param motion requested local movement, including deliberate arena entry or exit
     * @return displacement with only its boundary-normal component reduced
     */
    public static Vec3 limitMovement(Entity entity, MoverType type, Vec3 motion) {
        if (!(entity instanceof Player player) || !canSlow(player, type, motion)) {
            return motion;
        }
        Settings settings = player.level instanceof ServerLevel
                ? new Settings(WaterSheeperConfig.WATER_BOUNDARY_SLOWDOWN_WIDTH.get(),
                        WaterSheeperConfig.WATER_BOUNDARY_SLOWDOWN_STRENGTH.get())
                : clientSettings;
        if (settings.width() <= 0 || settings.strength() <= 0) {
            return motion;
        }
        return slowAcrossSides(ArenaWater.activeVolumes(player.level), player.getBoundingBox().getCenter(), motion,
                settings.width(), settings.strength());
    }

    static boolean canSlow(Player player, MoverType type, Vec3 motion) {
        return type == MoverType.SELF && player.isAlive() && !player.isCreative() && !player.isSpectator()
                && motion.horizontalDistanceSqr() >= 1.0E-12D;
    }

    /** Applies resistance to the exposed cylinder sides, shared by water and storms. */
    static Vec3 slowAcrossSides(List<WaterArenaData.VolumeBounds> volumes, Vec3 center, Vec3 motion,
            double width, double strength) {
        if (volumes.isEmpty()) {
            return motion;
        }
        // Sample swept motion as well as the starting point, keeping work
        // bounded for unusually fast movement. Teleports never use SELF.
        int samples = Mth.clamp((int) Math.ceil(motion.horizontalDistance()
                / Math.max(0.25D, width * 0.5D)), 1, 8);
        double strongest = 0;
        Vec3 normal = Vec3.ZERO;
        for (int sample = 0; sample <= samples; sample++) {
            Vec3 point = center.add(motion.scale((double) sample / samples));
            var boundary = WaterArenaBoundary.lateralBoundary(volumes, point, width);
            if (boundary == null) {
                continue;
            }
            double proximity = Math.max(0, 1 - Math.abs(boundary.distance()) / boundary.width());
            double resistance = strength * proximity * proximity * (3 - 2 * proximity);
            if (resistance > strongest) {
                strongest = resistance;
                normal = boundary.normal();
            }
        }
        return motion.subtract(normal.scale(motion.dot(normal) * strongest));
    }
}
