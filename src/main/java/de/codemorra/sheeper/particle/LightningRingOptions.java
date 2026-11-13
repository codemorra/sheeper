package de.codemorra.sheeper.particle;

import java.util.Locale;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.codemorra.sheeper.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One compact visual snapshot lets clients draw an entire dense ring locally.
 */
public record LightningRingOptions(float radius, float width, float spacing, int minY, int maxY,
        double arenaX, double arenaZ, float arenaRadius) implements ParticleOptions {
    public static final Codec<LightningRingOptions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("radius").forGetter(LightningRingOptions::radius),
            Codec.FLOAT.fieldOf("width").forGetter(LightningRingOptions::width),
            Codec.FLOAT.fieldOf("spacing").forGetter(LightningRingOptions::spacing),
            Codec.INT.fieldOf("min_y").forGetter(LightningRingOptions::minY),
            Codec.INT.fieldOf("max_y").forGetter(LightningRingOptions::maxY),
            Codec.DOUBLE.fieldOf("arena_x").forGetter(LightningRingOptions::arenaX),
            Codec.DOUBLE.fieldOf("arena_z").forGetter(LightningRingOptions::arenaZ),
            Codec.FLOAT.fieldOf("arena_radius").forGetter(LightningRingOptions::arenaRadius))
            .apply(instance, LightningRingOptions::new));

    // Minecraft 1.19.2 still requires this deprecated type in ParticleType's
    // constructor.
    @SuppressWarnings("deprecation")
    public static final Deserializer<LightningRingOptions> DESERIALIZER = new Deserializer<>() {
        @Override
        public LightningRingOptions fromCommand(ParticleType<LightningRingOptions> type, StringReader reader)
                throws CommandSyntaxException {
            reader.expect(' ');
            float radius = reader.readFloat();
            reader.expect(' ');
            float width = reader.readFloat();
            reader.expect(' ');
            float spacing = reader.readFloat();
            reader.expect(' ');
            int minY = reader.readInt();
            reader.expect(' ');
            int maxY = reader.readInt();
            reader.expect(' ');
            double arenaX = reader.readDouble();
            reader.expect(' ');
            double arenaZ = reader.readDouble();
            reader.expect(' ');
            return new LightningRingOptions(radius, width, spacing, minY, maxY, arenaX, arenaZ, reader.readFloat());
        }

        @Override
        public LightningRingOptions fromNetwork(ParticleType<LightningRingOptions> type, FriendlyByteBuf buffer) {
            return new LightningRingOptions(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                    buffer.readInt(), buffer.readInt(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat());
        }
    };

    @Override
    public ParticleType<LightningRingOptions> getType() {
        return ModParticles.LIGHTNING_RING.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeFloat(radius);
        buffer.writeFloat(width);
        buffer.writeFloat(spacing);
        buffer.writeInt(minY);
        buffer.writeInt(maxY);
        buffer.writeDouble(arenaX);
        buffer.writeDouble(arenaZ);
        buffer.writeFloat(arenaRadius);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "sheeper:lightning_ring %f %f %f %d %d %f %f %f",
                radius, width, spacing, minY, maxY, arenaX, arenaZ, arenaRadius);
    }
}
