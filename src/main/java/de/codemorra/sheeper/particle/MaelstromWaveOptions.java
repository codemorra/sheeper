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

/** Identifies the owning arena's circle so nearby arenas cannot extend the visible wave. */
public record MaelstromWaveOptions(double arenaX, double arenaZ, double arenaRadius) implements ParticleOptions {
    public static final Codec<MaelstromWaveOptions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("arena_x").forGetter(MaelstromWaveOptions::arenaX),
            Codec.DOUBLE.fieldOf("arena_z").forGetter(MaelstromWaveOptions::arenaZ),
            Codec.doubleRange(1, 128).fieldOf("arena_radius").forGetter(MaelstromWaveOptions::arenaRadius))
            .apply(instance, MaelstromWaveOptions::new));

    @SuppressWarnings("deprecation")
    public static final Deserializer<MaelstromWaveOptions> DESERIALIZER = new Deserializer<>() {
        @Override
        public MaelstromWaveOptions fromCommand(ParticleType<MaelstromWaveOptions> type, StringReader reader)
                throws CommandSyntaxException {
            reader.expect(' ');
            double x = reader.readDouble();
            reader.expect(' ');
            double z = reader.readDouble();
            reader.expect(' ');
            return new MaelstromWaveOptions(x, z, reader.readDouble());
        }

        @Override
        public MaelstromWaveOptions fromNetwork(ParticleType<MaelstromWaveOptions> type, FriendlyByteBuf buffer) {
            return new MaelstromWaveOptions(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }
    };

    /** Checks the wave's horizontal sample against the owner's arena. */
    public boolean contains(double x, double z) {
        double dx = x - arenaX;
        double dz = z - arenaZ;
        return dx * dx + dz * dz <= arenaRadius * arenaRadius;
    }

    @Override
    public ParticleType<MaelstromWaveOptions> getType() {
        return ModParticles.MAELSTROM_WAVE.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeDouble(arenaX);
        buffer.writeDouble(arenaZ);
        buffer.writeDouble(arenaRadius);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "sheeper:maelstrom_wave %f %f %f", arenaX, arenaZ, arenaRadius);
    }
}
