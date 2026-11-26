package de.codemorra.sheeper.world;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Gives local input the same tornado response on the client and on server-driven movement. */
public final class LightningTornadoMotion {
    private static final Map<Player, Capture> clientCaptures = new WeakHashMap<>();
    private static final Map<Player, Capture> serverCaptures = new WeakHashMap<>();
    private static Function<Level, List<LightningTornado>> clientLookup = level -> List.of();

    private record Capture(ResourceKey<Level> dimension, UUID owner, long launchTick) {
        boolean matches(LightningTornado tornado) {
            return owner.equals(tornado.owner()) && launchTick == tornado.activeFrom();
        }
    }

    private record Selection(LightningTornado tornado, boolean held) {
    }

    private LightningTornadoMotion() {
    }

    public static void setClientLookup(Function<Level, List<LightningTornado>> lookup) {
        clientLookup = lookup;
    }

    private static Map<Player, Capture> captures(Player player) {
        return player.level.isClientSide ? clientCaptures : serverCaptures;
    }

    /** @return whether ordinary arena wind should yield to a funnel already carrying the player */
    static boolean isCaptured(Player player) {
        Capture capture = captures(player).get(player);
        return capture != null && capture.dimension().equals(player.level.dimension());
    }

    /** Uses the same funnel selection for physical movement and server-side damage or descent. */
    @Nullable
    public static LightningTornado controllingFunnel(ServerPlayer player) {
        Selection selection = select(player);
        return selection == null ? null : selection.tornado();
    }

    /** @return whether the active funnel is carrying the player or guiding their descent */
    static boolean isCarrying(ServerPlayer player) {
        Selection selection = select(player);
        return selection != null && (selection.held()
                || selection.tornado().supportsDescent(player.position(), player.level.getGameTime()));
    }

    @Nullable
    private static Selection select(Player player) {
        var captures = captures(player);
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || player.isPassenger()) {
            captures.remove(player);
            return null;
        }
        Level level = player.level;
        List<LightningTornado> tornados = level instanceof ServerLevel server
                ? LightningStormData.get(server).activeTornados(server.getGameTime()) : clientLookup.apply(level);
        Vec3 point = player.position();
        long now = level.getGameTime();
        Capture capture = captures.get(player);
        if (capture != null && capture.dimension().equals(level.dimension())) {
            for (LightningTornado tornado : tornados) {
                if (capture.matches(tornado) && tornado.retains(point, now)
                        && LightningTornado.hasClearPath(level, player, tornado.centerAt(now))) {
                    return new Selection(tornado, true);
                }
            }
        }
        captures.remove(player);
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
            return null;
        }
        boolean held = strongest >= 0.18;
        if (held) {
            captures.put(player, new Capture(level.dimension(), selected.owner(), selected.activeFrom()));
        }
        return new Selection(selected, held);
    }

    /** Carries the rider with the travelling funnel while still allowing outward movement to escape. */
    public static Vec3 apply(Entity entity, MoverType type, Vec3 motion) {
        if (!(entity instanceof Player player) || type != MoverType.SELF) {
            return motion;
        }
        Selection selection = select(player);
        if (selection == null) {
            return motion;
        }
        LightningTornado selected = selection.tornado();
        boolean held = selection.held();
        long now = player.level.getGameTime();
        Vec3 point = player.position();
        Vec3 center = selected.centerAt(now);
        Vec3 radial = new Vec3(point.x - center.x, 0, point.z - center.z);
        Vec3 outward = radial.normalize();
        Vec3 tangent = new Vec3(-outward.z, 0, outward.x);
        double wind = selected.exposure(point, now) * selected.strength();
        if (held) {
            // A brief contact loss at the rim or a terrain step must not drop the player out of the wind.
            wind = Math.max(wind, 0.4 * selected.strength() * selected.fadeAt(now));
        }
        double input = motion.dot(outward);
        double pull = 0.092 * wind * Math.min(1, radial.horizontalDistance() / 1.4);
        double resistance = Math.min(pull, Math.max(0, input * 0.68 - 0.012));
        double radialChange = input > 0.015 ? -resistance : -pull;
        double carry = held ? selected.fadeAt(now) : 0.68 * Math.min(1, wind);
        Vec3 horizontal = outward.scale(radialChange).add(tangent.scale(0.105 * wind))
                .add(selected.velocity().multiply(carry, 0, carry));

        double liftLimit = center.y + Math.max(0.1, selected.height() - player.getBbHeight() - 0.2);
        double lift = Mth.clamp((liftLimit - point.y) * 0.18, -0.12, 0.26 * wind);
        if (held && !selected.isDissolving(now)) {
            lift += selected.velocity().y;
        }
        double vertical = Mth.lerp(Math.min(1, wind * 2.6), motion.y, lift);
        if (selected.isDissolving(now)) {
            double onset = Math.min(1, (now - selected.dissolveFrom()) / 4.0);
            double support = Math.min(1, selected.contact(point, now) * selected.strength() * 3);
            double release = Math.min(1, (selected.endTick() - now) / 3.0);
            vertical = Mth.lerp(onset * support * release, vertical, -0.22);
            if (selected.supportsDescent(point, now)) {
                player.resetFallDistance();
            }
        }
        // Keep vanilla gravity and fall tracking active as soon as the player leaves the funnel.
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x, vertical, velocity.z);
        return motion.add(horizontal.x, vertical - motion.y, horizontal.z);
    }
}
