package de.codemorra.sheeper.particle;

import java.util.Locale;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.codemorra.sheeper.registry.ModParticles;
import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/** Identifies the arena that bounds a mine's visual pressure wave. */
public record WaterMineWaveOptions(BlockPos center, double radius, int minimumY, double maximumY)
        implements ParticleOptions {
    public static final Codec<WaterMineWaveOptions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("center").forGetter(WaterMineWaveOptions::center),
            Codec.doubleRange(1, 128).fieldOf("arena_radius").forGetter(WaterMineWaveOptions::radius),
            Codec.INT.fieldOf("min_y").forGetter(WaterMineWaveOptions::minimumY),
            Codec.DOUBLE.fieldOf("max_y").forGetter(WaterMineWaveOptions::maximumY))
            .apply(instance, WaterMineWaveOptions::new));

    @SuppressWarnings("deprecation")
    public static final Deserializer<WaterMineWaveOptions> DESERIALIZER = new Deserializer<>() {
        @Override
        public WaterMineWaveOptions fromCommand(ParticleType<WaterMineWaveOptions> type, StringReader reader)
                throws CommandSyntaxException {
            reader.expect(' ');
            int x = reader.readInt();
            reader.expect(' ');
            int y = reader.readInt();
            reader.expect(' ');
            int z = reader.readInt();
            reader.expect(' ');
            double radius = reader.readDouble();
            reader.expect(' ');
            int minY = reader.readInt();
            reader.expect(' ');
            return new WaterMineWaveOptions(new BlockPos(x, y, z), radius, minY, reader.readDouble());
        }

        @Override
        public WaterMineWaveOptions fromNetwork(ParticleType<WaterMineWaveOptions> type, FriendlyByteBuf buffer) {
            return new WaterMineWaveOptions(buffer.readBlockPos(), buffer.readDouble(), buffer.readInt(),
                    buffer.readDouble());
        }
    };

    /** @return captured arena bounds for clipping wave samples */
    public VolumeBounds bounds() {
        return new VolumeBounds(center, radius, minimumY, maximumY);
    }

    @Override
    public ParticleType<WaterMineWaveOptions> getType() {
        return ModParticles.WATER_MINE_WAVE.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(center);
        buffer.writeDouble(radius);
        buffer.writeInt(minimumY);
        buffer.writeDouble(maximumY);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "sheeper:water_mine_wave %d %d %d %f %d %f",
                center.getX(), center.getY(), center.getZ(), radius, minimumY, maximumY);
    }
}
