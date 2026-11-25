package de.codemorra.sheeper.world;

import de.codemorra.sheeper.entity.LightningRingSurface;
import de.codemorra.sheeper.network.LightningWeatherPacket.Arena;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** A moving segment of wind whose curved front is shared by rendering and contact checks. */
public record LightningWindGust(Vec3 origin, double width, double speed, long start, int duration, long pathStart, Size size) {
    public enum Size {
        MEDIUM, LARGE
    }

    private static final double CREST_DEPTH = 1.15;

    public Vec3 centerAt(LightningWindField field, double now) {
        return origin.add(field.travelBetween(pathStart, Math.min(now, start + duration)).scale(speed));
    }

    /** Keeps the current position when an arena replaces the previous turn data. */
    LightningWindGust rebase(LightningWindField field, long now) {
        return new LightningWindGust(centerAt(field, now), width, speed, start, duration, now, size);
    }

    public boolean expired(long now) {
        return now - start >= duration;
    }

    /** Fades the ends of the travel path without changing the speed of the front. */
    public float opacityAt(double now) {
        double age = now - start;
        return (float) Mth.clamp(Math.min(age / 5, (duration - age) / 12), 0, 1);
    }

    /** Resistance lingers where the crest has passed and grows as the gust travels. */
    public double wakeLength(double now) {
        double travelled = Mth.clamp(now - start, 0, duration) * speed;
        return Math.min(size == Size.LARGE ? 8.0 : 6.4, CREST_DEPTH + travelled);
    }

    /** Horizontal wave offset across the crest, where lateral runs from -1 to 1. */
    public double bend(double lateral) {
        return (0.37 * Math.sin(lateral * 4.2) + 0.32 * (1 - lateral * lateral)) * width / 6;
    }

    /** Follows small steps in the terrain while keeping the gust on its original floor. */
    public double groundAt(Level level, LightningWindField field, Vec3 point) {
        if (!level.getChunkSource().hasChunk(Mth.floor(point.x) >> 4, Mth.floor(point.z) >> 4)) {
            return Double.NaN;
        }
        return LightningRingSurface.find(level, point.x, point.z, origin.y,
                Math.max(field.arena().minimumY(), Mth.ceil(origin.y - 2)),
                Math.min(field.arena().maximumY() - 1, Mth.floor(origin.y + 2)));
    }

    /** Returns sustained pressure at the player's position after the curved front has passed. */
    public double exposure(Level level, LightningWindField field, Player player, Vec3 direction, double now) {
        Vec3 offset = player.position().subtract(centerAt(field, now));
        double lateral = (offset.z * direction.x - offset.x * direction.z) / (width / 2);
        double depth = offset.dot(direction) - bend(lateral);
        double rearDepth = wakeLength(now);
        if (Math.abs(lateral) >= 1 || depth <= -rearDepth || depth >= CREST_DEPTH || Math.abs(offset.y) > 4) {
            return 0;
        }
        double ground = groundAt(level, field, player.position());
        if (!Double.isFinite(ground) || player.getY() < ground - 0.3 || player.getY() > ground + 1.8) {
            return 0;
        }
        double edge = (1 - Math.abs(lateral)) / 0.35;
        double leading = (CREST_DEPTH - depth) / CREST_DEPTH;
        double trailing = (rearDepth + depth) / Math.min(2.6, rearDepth);
        return opacityAt(now) * smoothFade(edge) * smoothFade(Math.min(leading, trailing));
    }

    private static double smoothFade(double value) {
        double clamped = Mth.clamp(value, 0, 1);
        return clamped * clamped * (3 - 2 * clamped);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(origin.x);
        buffer.writeDouble(origin.y);
        buffer.writeDouble(origin.z);
        buffer.writeDouble(width);
        buffer.writeDouble(speed);
        buffer.writeLong(start);
        buffer.writeVarInt(duration);
        buffer.writeLong(pathStart);
        buffer.writeEnum(size);
    }

    public static LightningWindGust decode(FriendlyByteBuf buffer, Arena arena) {
        Vec3 origin = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        var gust = new LightningWindGust(origin, buffer.readDouble(), buffer.readDouble(), buffer.readLong(),
                buffer.readVarInt(), buffer.readLong(), buffer.readEnum(Size.class));
        if (!(Math.abs(origin.x) <= 30_000_000 && Math.abs(origin.z) <= 30_000_000 && arena.containsY(origin.y)
                && gust.width >= 0.5 && gust.width <= 10 && gust.speed > 0 && gust.speed <= 0.32
                && gust.start >= 0 && gust.duration > 0 && gust.duration <= 60
                && gust.start <= Long.MAX_VALUE - gust.duration
                && gust.pathStart >= gust.start && gust.pathStart - gust.start <= gust.duration)) {
            throw new IllegalArgumentException("Invalid lightning wind gust geometry");
        }
        return gust;
    }
}
