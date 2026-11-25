package de.codemorra.sheeper.world;

import java.util.List;
import java.util.function.Function;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Gives local input the same tornado response on the client and on server-driven movement. */
public final class LightningTornadoMotion {
    private static Function<Level, List<LightningTornado>> clientLookup = level -> List.of();

    private LightningTornadoMotion() {
    }

    public static void setClientLookup(Function<Level, List<LightningTornado>> lookup) {
        clientLookup = lookup;
    }

    /** Adds orbit and lift while preserving a slow escape for a player who moves outward. */
    public static Vec3 apply(Entity entity, MoverType type, Vec3 motion) {
        if (!(entity instanceof Player player) || type != MoverType.SELF || !player.isAlive()
                || player.isCreative() || player.isSpectator() || player.isPassenger()) {
            return motion;
        }
        Level level = player.level;
        List<LightningTornado> tornados = level instanceof ServerLevel server
                ? LightningStormData.get(server).activeTornados(server.getGameTime()) : clientLookup.apply(level);
        Vec3 point = player.position();
        long now = level.getGameTime();
        LightningTornado selected = null;
        double strongest = 0;
        for (LightningTornado tornado : tornados) {
            double exposure = tornado.exposure(point, now) * tornado.strength();
            double support = tornado.supportsDescent(point, now) ? tornado.contact(point, now) * tornado.strength() : 0;
            double weight = Math.max(exposure, support * 0.55);
            if (weight > strongest && LightningTornado.hasClearPath(level, player, tornado.centerAt(now))) {
                selected = tornado;
                strongest = weight;
            }
        }
        if (selected == null) {
            return motion;
        }
        Vec3 center = selected.centerAt(now);
        Vec3 radial = new Vec3(point.x - center.x, 0, point.z - center.z);
        Vec3 outward = radial.normalize();
        Vec3 tangent = new Vec3(-outward.z, 0, outward.x);
        double wind = selected.exposure(point, now) * selected.strength();
        double input = motion.dot(outward);
        double pull = 0.092 * wind * Math.min(1, radial.horizontalDistance() / 1.4);
        double resistance = Math.min(pull, Math.max(0, input * 0.68 - 0.012));
        double radialChange = input > 0.015 ? -resistance : -pull;
        Vec3 horizontal = outward.scale(radialChange).add(tangent.scale(0.105 * wind))
                .add(selected.velocity().multiply(0.68, 0, 0.68).scale(Math.min(1, wind)));

        double lift = Math.min(0.26 * wind,
                Math.max(0, center.y + selected.height() - player.getBbHeight() - point.y) * 0.18);
        double vertical = Mth.lerp(Math.min(1, wind * 2.6), motion.y, lift);
        if (selected.isDissolving(now)) {
            double onset = Math.min(1, (now - selected.dissolveFrom()) / 4.0);
            double support = Math.min(1, selected.contact(point, now) * selected.strength() * 3);
            double release = Math.min(1, (selected.endTick() - now) / 3.0);
            vertical = Mth.lerp(onset * support * release, vertical, -0.22);
            if (selected.supportsDescent(point, now)) {
                player.resetFallDistance();
            }
        } else if (point.y - center.y > 2 && (now - selected.activeFrom()) % 46 >= 39) {
            // A short dip breaks a continuous hover and gives players a clearer chance to drop out.
            vertical = Math.min(vertical, -0.09 * Math.min(1, wind * 2));
        }
        // Keep vanilla gravity and fall tracking active as soon as the player leaves the funnel.
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x, vertical, velocity.z);
        return motion.add(horizontal.x, vertical - motion.y, horizontal.z);
    }
}
