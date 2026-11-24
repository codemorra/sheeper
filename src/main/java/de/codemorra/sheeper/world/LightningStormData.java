package de.codemorra.sheeper.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.LightningRingSurface;
import de.codemorra.sheeper.entity.LightningStrikeEffect;
import de.codemorra.sheeper.network.LightningWeatherPacket;
import de.codemorra.sheeper.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** Keeps the storm and its lifecycle available independently of boss tracking. */
public final class LightningStormData extends SavedData {
    private static final class Storm {
        private LightningWeatherPacket.Arena arena;
        private long nextFlash;
        private long nextRumble;

        private Storm(LightningWeatherPacket.Arena arena) {
            this.arena = arena;
        }
    }

    private final Map<UUID, Storm> storms = new HashMap<>();

    public static LightningStormData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LightningStormData::load, LightningStormData::new,
                "sheeper_lightning_storms");
    }

    /**
     * Registers the storm on the first boss tick and resumes its saved front on reload.
     *
     * @return the captured geometry and opening time, or null if the bounds are invalid
     */
    @Nullable
    public LightningWeatherPacket.Arena startOrResume(ServerLevel level, UUID id, BlockPos center, double radius,
            int minimumY, int maximumY) {
        Storm storm = storms.get(id);
        if (storm == null) {
            int duration = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_PREPARATION_DURATION.get());
            var arena = new LightningWeatherPacket.Arena(center.immutable(), radius, minimumY, maximumY,
                    level.getGameTime(), duration, 0, 0);
            if (!arena.isValid()) {
                return null;
            }
            storm = new Storm(arena);
            storms.put(id, storm);
            setDirty();
            sync(level);
        }
        return storm.arena;
    }

    /**
     * Starts one retreat after defeat or permanent removal. Subsequent removal
     * callbacks for the same boss leave the original animation clock intact.
     */
    public void beginDrain(ServerLevel level, UUID id) {
        Storm storm = storms.get(id);
        if (storm == null || storm.arena.isDraining()) {
            return;
        }
        int duration = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ARENA_DRAIN_DURATION.get());
        storm.arena = storm.arena.beginDrain(level.getGameTime(), duration);
        setDirty();
        sync(level);
    }

    public boolean hasStorms() {
        return LightningSheeperConfig.LIGHTNING_STORM_ENABLED.get() && !storms.isEmpty();
    }

    public float intensity(ServerLevel level, BlockPos pos) {
        if (!hasStorms()) {
            return 0;
        }
        float intensity = 0;
        for (Storm storm : storms.values()) {
            intensity = Math.max(intensity,
                    storm.arena.intensityAt(level.getGameTime(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        }
        return intensity;
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        boolean removed = storms.values().removeIf(storm -> storm.arena.hasFinishedDraining(now));
        if (removed) {
            setDirty();
        }
        if (hasStorms()) {
            for (Storm storm : storms.values()) {
                if (!storm.arena.isDraining()) {
                    tickAtmosphere(level, storm, now);
                }
            }
        }
        if (removed || now % 10 == 0) {
            sync(level);
        }
    }

    /** Storm lightning uses the visual bolt alone, keeping warnings reserved for combat. */
    private static void tickAtmosphere(ServerLevel level, Storm storm, long now) {
        boolean building = storm.arena.isBuilding(now);
        if (now >= storm.nextFlash) {
            flashAtFront(level, storm.arena, now);
            storm.nextFlash = now + (building ? 8 + level.random.nextInt(10) : 100 + level.random.nextInt(140));
        }
        if (now >= storm.nextRumble) {
            float volume = LightningSheeperConfig.LIGHTNING_STORM_RUMBLE_VOLUME.get().floatValue();
            for (var player : level.players()) {
                float strength = storm.arena.intensityAt(now, player.getX(), player.getEyeY(), player.getZ());
                if (volume > 0 && strength > 0) {
                    player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                            volume * strength, 0.7F + level.random.nextFloat() * 0.25F);
                }
            }
            storm.nextRumble = now + 100 + level.random.nextInt(160);
        }
    }

    private static void flashAtFront(ServerLevel level, LightningWeatherPacket.Arena arena, long now) {
        BlockPos center = arena.center();
        double angle = level.random.nextDouble() * Math.PI * 2;
        double radius = arena.radiusAt(now) * (0.9 + level.random.nextDouble() * 0.1);
        double x = center.getX() + 0.5 + Math.cos(angle) * radius;
        double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
        double ground = LightningRingSurface.find(level, x, z, center.getY(),
                arena.minimumY(), arena.maximumY() - 1);
        if (Double.isFinite(ground)) {
            LightningStrikeEffect.spawnAmbient(level, new Vec3(x, ground, z));
        }
    }

    /** Uses the same visible storms for local weather and storm wall prediction. */
    public List<LightningWeatherPacket.Arena> visibleArenas() {
        return hasStorms()
                ? storms.values().stream().map(storm -> storm.arena).toList()
                : List.of();
    }

    private void sync(ServerLevel level) {
        LightningWeatherPacket packet = new LightningWeatherPacket(level.dimension().location(), visibleArenas(),
                LightningSheeperConfig.LIGHTNING_BOUNDARY_SLOWDOWN_WIDTH.get(),
                LightningSheeperConfig.LIGHTNING_BOUNDARY_SLOWDOWN_STRENGTH.get());
        for (var player : level.players()) {
            ModNetwork.sendWeather(player, packet);
        }
    }

    private static LightningStormData load(CompoundTag tag) {
        LightningStormData data = new LightningStormData();
        for (Tag value : tag.getList("Storms", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            var arena = new LightningWeatherPacket.Arena(BlockPos.of(entry.getLong("Center")), entry.getDouble("Radius"),
                    entry.getInt("MinimumY"), entry.getInt("MaximumY"),
                    entry.getLong("Start"), Mth.clamp(entry.getInt("Transition"), 0, 1200),
                    entry.getLong("DrainStart"), Mth.clamp(entry.getInt("DrainDuration"), 0, 600));
            if (entry.hasUUID("Boss") && arena.isValid()) {
                data.storms.put(entry.getUUID("Boss"), new Storm(arena));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        storms.forEach((id, storm) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Boss", id);
            entry.putLong("Center", storm.arena.center().asLong());
            entry.putDouble("Radius", storm.arena.radius());
            entry.putInt("MinimumY", storm.arena.minimumY());
            entry.putInt("MaximumY", storm.arena.maximumY());
            entry.putLong("Start", storm.arena.start());
            entry.putInt("Transition", storm.arena.transition());
            entry.putLong("DrainStart", storm.arena.drainStart());
            entry.putInt("DrainDuration", storm.arena.drainDuration());
            entries.add(entry);
        });
        tag.put("Storms", entries);
        return tag;
    }
}
