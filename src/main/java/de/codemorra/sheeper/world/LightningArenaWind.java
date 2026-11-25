package de.codemorra.sheeper.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.LightningRingSurface;
import de.codemorra.sheeper.network.LightningWeatherPacket.Arena;
import de.codemorra.sheeper.world.LightningWindGust.Size;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Runs continuous wind and stronger local gusts in each lightning arena. */
public final class LightningArenaWind {
    private static Function<Level, List<LightningWindField>> clientFields = level -> List.of();

    private final List<LightningWindGust> active = new ArrayList<>();
    private double fromAngle;
    private double turnAngle;
    private long turnStart;
    private long nextTurn = -1;
    private long nextGust;

    public static void setClientLookup(Function<Level, List<LightningWindField>> lookup) {
        clientFields = lookup;
    }

    @Nullable
    LightningWindField snapshot(Arena arena) {
        return nextTurn < 0 ? null : new LightningWindField(arena, fromAngle, turnAngle, turnStart, List.copyOf(active));
    }

    /** Stops wind and reports whether the clients' snapshot needs clearing. */
    boolean clear() {
        boolean hadWind = nextTurn >= 0;
        active.clear();
        nextTurn = -1;
        return hadWind;
    }

    /** Turns the wind continuously while new gusts follow the usual spawn rhythm. */
    boolean tick(ServerLevel level, Arena arena, List<ServerPlayer> participants) {
        long now = level.getGameTime();
        boolean changed = active.removeIf(gust -> gust.expired(now));
        if (nextTurn < 0) {
            fromAngle = level.random.nextDouble() * Math.PI * 2;
            turnAngle = 0;
            turnStart = now;
            nextGust = now + 12;
            nextTurn = now + turnDelay(level);
            changed = true;
        } else if (now >= nextTurn) {
            LightningWindField previous = snapshot(arena);
            active.replaceAll(gust -> gust.rebase(previous, now));
            fromAngle = (fromAngle + turnAngle) % (2 * Math.PI);
            turnAngle = Math.PI / 4 + level.random.nextDouble() * Math.PI * 1.5;
            if (turnAngle > Math.PI) {
                turnAngle -= 2 * Math.PI;
            }
            turnStart = now;
            nextTurn = now + turnDelay(level);
            changed = true;
        }
        LightningWindField field = snapshot(arena);
        if (!participants.isEmpty() && now >= nextGust) {
            LightningWindGust gust = plan(level, field, participants, now);
            nextGust = now + 24 + level.random.nextInt(23);
            if (gust != null) {
                active.add(gust);
                changed = true;
            }
        }
        for (LightningWindGust gust : active) {
            if (gust.size() == Size.LARGE && (now - gust.start()) % 5 == 0) {
                showSpray(level, field, gust, now);
            }
        }
        return changed;
    }

    private static int turnDelay(ServerLevel level) {
        int min = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_WIND_DIRECTION_MIN.get());
        int max = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_WIND_DIRECTION_MAX.get());
        return min + level.random.nextInt(Math.max(0, max - min) + 1);
    }

    @Nullable
    private LightningWindGust plan(ServerLevel level, LightningWindField field, List<ServerPlayer> participants, long now) {
        Arena arena = field.arena();
        int roll = level.random.nextInt(100);
        int largeChance = LightningSheeperConfig.LIGHTNING_WIND_LARGE_CHANCE.get();
        Size size = roll < largeChance ? Size.LARGE : Size.MEDIUM;
        double width = switch (size) {
            case MEDIUM -> 4 + level.random.nextDouble() * 2;
            case LARGE -> 6 + level.random.nextDouble() * 3;
        };
        width = Math.min(width, arena.radius() * 0.6);
        int duration = 34 + level.random.nextInt(15);
        Vec3 direction = field.directionAt(now);
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        Vec3 center = Vec3.atBottomCenterOf(arena.center());
        double room = arena.radius() - width / 2 - Math.min(1.6, arena.radius() * 0.15);
        double speed = Math.min(0.32, room / duration);
        ServerPlayer nearby = level.random.nextBoolean() ? participants.get(level.random.nextInt(participants.size()))
                : null;
        for (int attempt = 0; attempt < 12; attempt++) {
            boolean local = nearby != null && attempt < 6;
            Vec3 reference = local ? nearby.position() : center;
            Vec3 origin = local
                    ? reference.subtract(direction.scale(3 + level.random.nextDouble() * 5))
                            .add(side.scale((level.random.nextDouble() - 0.5) * (width + 4)))
                    : center.add(direction.scale((level.random.nextDouble() * 2 - 1) * room))
                            .add(side.scale((level.random.nextDouble() * 2 - 1) * room));
            Vec3 end = origin.add(field.travelBetween(now, now + duration).scale(speed));
            if (origin.subtract(center).horizontalDistanceSqr() > room * room
                    || end.subtract(center).horizontalDistanceSqr() > room * room
                    || tooClose(field, origin, direction, side, now, width)) {
                continue;
            }
            double ground = LightningRingSurface.find(level, origin.x, origin.z, reference.y,
                    arena.minimumY(), arena.maximumY() - 4);
            if (Double.isFinite(ground)) {
                return new LightningWindGust(new Vec3(origin.x, ground, origin.z), width, speed, now, duration, now, size);
            }
        }
        return null;
    }

    /** Keeps successive crests staggered in depth when their lanes overlap. */
    private boolean tooClose(LightningWindField field, Vec3 origin, Vec3 direction, Vec3 side, long now, double width) {
        for (LightningWindGust gust : active) {
            Vec3 offset = origin.subtract(gust.centerAt(field, now));
            if (Math.abs(offset.dot(direction)) < 4 && Math.abs(offset.dot(side)) < (width + gust.width()) / 2) {
                return true;
            }
        }
        return false;
    }

    /** Adds sparse rising spray only to the strongest gusts. */
    private static void showSpray(ServerLevel level, LightningWindField field, LightningWindGust gust, long now) {
        if (gust.opacityAt(now) <= 0) {
            return;
        }
        Vec3 center = gust.centerAt(field, now);
        Vec3 direction = field.directionAt(now);
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        for (int index = 0; index < 3; index++) {
            double lateral = level.random.nextDouble() * 1.6 - 0.8;
            Vec3 point = center.add(side.scale(lateral * gust.width() / 2)).add(direction.scale(gust.bend(lateral)));
            double y = gust.groundAt(level, field, point);
            if (!Double.isFinite(y) || field.arena().intensityAt(now, point.x, y + 0.3, point.z) <= 0) {
                continue;
            }
            var pos = new BlockPos(point.x, y + 0.3, point.z);
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                level.sendParticles(ParticleTypes.POOF, point.x, y + 0.3, point.z,
                        0, direction.x, 0.45, direction.z, 0.22);
            }
        }
    }

    /** Adds horizontal drift and reduces the component of movement opposing the wind. */
    public static Vec3 applyWind(Entity entity, MoverType type, Vec3 motion) {
        if (!(entity instanceof Player player) || type != MoverType.SELF || !player.isAlive()
                || player.isCreative() || player.isSpectator() || player.isPassenger()) {
            return motion;
        }
        Level level = player.level;
        List<LightningWindField> fields = level instanceof ServerLevel server
                ? LightningStormData.get(server).windFields() : clientFields.apply(level);
        Vec3 position = player.getBoundingBox().getCenter();
        long now = level.getGameTime();
        Vec3 correction = Vec3.ZERO;
        for (LightningWindField field : fields) {
            float intensity = field.arena().intensityAt(now, position.x, position.y, position.z);
            if (intensity <= 0) {
                continue;
            }
            Vec3 direction = field.directionAt(now);
            if (!LightningWindField.isExposed(level, player, direction)) {
                continue;
            }
            double drift = 0.018;
            double resistance = 0.13;
            for (LightningWindGust gust : field.gusts()) {
                double exposure = gust.exposure(level, field, player, direction, now);
                boolean large = gust.size() == Size.LARGE;
                drift = Math.max(drift, 0.018 + exposure * (large ? 0.080 : 0.044));
                resistance = Math.max(resistance, 0.13 + exposure * (large ? 0.51 : 0.32));
            }
            double against = Math.max(0, -motion.dot(direction));
            Vec3 candidate = direction.scale((drift + against * resistance) * intensity);
            if (candidate.lengthSqr() > correction.lengthSqr()) {
                correction = candidate;
            }
        }
        return motion.add(correction);
    }
}
