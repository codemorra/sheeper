package de.codemorra.sheeper.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.LightningRingSurface;
import de.codemorra.sheeper.entity.LightningAreaDamage;
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

/**
 * Persistent arena weather keeps running independently of boss/player chunk
 * tracking.
 */
public final class LightningStormData extends SavedData {
    private record Warning(Vec3 pos, long impact) {
    }

    private static final class Storm {
        private LightningWeatherPacket.Arena arena;
        private long patternUntil;
        private double strikeChance;
        private long nextDecoration;
        private long nextRumble;
        private final List<Warning> warnings = new ArrayList<>();
    }

    private final Map<UUID, Storm> storms = new HashMap<>();

    public static LightningStormData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LightningStormData::load, LightningStormData::new,
                "sheeper_lightning_storms");
    }

    public void start(ServerLevel level, UUID id, BlockPos center, double radius, int transition) {
        if (!storms.containsKey(id)) {
            Storm storm = new Storm();
            storm.arena = new LightningWeatherPacket.Arena(center.immutable(), radius, level.getGameTime(), transition);
            storms.put(id, storm);
            setDirty();
            sync(level);
        }
    }

    public void remove(ServerLevel level, UUID id) {
        if (storms.remove(id) != null) {
            setDirty();
            sync(level);
        }
    }

    public int remainingAwakening(ServerLevel level, UUID id) {
        Storm storm = storms.get(id);
        return storm == null ? 0
                : (int) Math.max(0,
                        storm.arena.start() + storm.arena.transition() - level.getGameTime());
    }

    public boolean hasStorms() {
        return SheeperConfig.LIGHTNING_STORM_ENABLED.get() && !storms.isEmpty();
    }

    public float intensity(ServerLevel level, BlockPos pos) {
        if (!hasStorms()) {
            return 0;
        }
        float intensity = 0;
        for (Storm storm : storms.values()) {
            if (storm.arena.contains(pos.getX() + 0.5D, pos.getZ() + 0.5D)) {
                intensity = Math.max(intensity, storm.arena.intensity(level.getGameTime()));
            }
        }
        return intensity;
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        for (Storm storm : storms.values()) {
            boolean awakening = now < storm.arena.start() + storm.arena.transition();
            if (awakening) {
                if (now >= storm.nextDecoration) {
                    BlockPos center = storm.arena.center();
                    Vec3 core = surface(level, center.getX() + 0.5D + level.random.nextInt(3) - 1,
                            center.getZ() + 0.5D + level.random.nextInt(3) - 1, center.getY());
                    if (core != null) {
                        LightningStrikeEffect.spawnAmbient(level, core);
                    }
                    Vec3 edge = edge(level, storm);
                    if (edge != null) {
                        LightningStrikeEffect.spawnAmbient(level, edge);
                    }
                    storm.nextDecoration = now + 2 + level.random.nextInt(5);
                }
            } else if (SheeperConfig.LIGHTNING_STORM_EDGE_ENABLED.get()) {
                tickEdges(level, storm, now);
            } else {
                storm.warnings.clear();
            }
            if (SheeperConfig.LIGHTNING_STORM_ENABLED.get() && now >= storm.nextRumble) {
                float volume = SheeperConfig.LIGHTNING_STORM_RUMBLE_VOLUME.get().floatValue();
                for (var player : level.players()) {
                    if (volume > 0 && storm.arena.contains(player.getX(), player.getZ())) {
                        player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                                volume, 0.7F + level.random.nextFloat() * 0.25F);
                    }
                }
                storm.nextRumble = now + 100 + level.random.nextInt(201);
            }
        }
        if (!storms.isEmpty()) {
            setDirty();
        }
        if (now % 10 == 0) {
            sync(level);
        }
    }

    private void tickEdges(ServerLevel level, Storm storm, long now) {
        var iterator = storm.warnings.iterator();
        while (iterator.hasNext()) {
            Warning warning = iterator.next();
            if (now >= warning.impact()) {
                if (level.getChunkSource().hasChunk(Mth.floor(warning.pos().x) >> 4, Mth.floor(warning.pos().z) >> 4)) {
                    LightningStrikeEffect.spawnAmbient(level, warning.pos());
                    var players = level.players().stream().filter(player -> storm.arena.contains(player.getX(),
                            player.getZ())
                            && Math.abs(player.getY()
                                    - storm.arena.center().getY()) <= SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE.get())
                            .toList();
                    LightningAreaDamage.hurtAmbientPlayers(level, players, warning.pos(), 1.0D, 3.0D,
                            SheeperConfig.LIGHTNING_STORM_EDGE_DAMAGE.get().floatValue());
                }
                iterator.remove();
            } else if (now % 2 == 0) {
                LightningStrikeEffect.showWarning(level, warning.pos(), 1.0D);
            }
        }
        // A changing strike rate produces clusters and quiet stretches without a fixed
        // cadence.
        if (now >= storm.patternUntil) {
            storm.strikeChance = 0.005D + Math.pow(level.random.nextDouble(), 2) * 0.13D;
            storm.patternUntil = now + 20 + level.random.nextInt(101);
        }
        if (level.random.nextDouble() < storm.strikeChance) {
            Vec3 pos = edge(level, storm);
            if (pos != null) {
                int warning = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_STORM_EDGE_WARNING.get());
                storm.warnings.add(new Warning(pos, now + warning));
                LightningStrikeEffect.showWarning(level, pos, 1.0D);
            }
        }
    }

    private static Vec3 edge(ServerLevel level, Storm storm) {
        double angle = level.random.nextDouble() * Math.PI * 2;
        double radius = Math.max(0,
                storm.arena.radius() - level.random.nextDouble() * Math.min(2, storm.arena.radius()));
        return surface(level, storm.arena.center().getX() + 0.5D + Math.cos(angle) * radius,
                storm.arena.center().getZ() + 0.5D + Math.sin(angle) * radius, storm.arena.center().getY());
    }

    private static Vec3 surface(ServerLevel level, double x, double z, double y) {
        int height = Mth.ceil(SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE.get());
        double ground = LightningRingSurface.find(level, x, z, y, Mth.floor(y) - height, Mth.ceil(y) + height);
        return Double.isFinite(ground) ? new Vec3(x, ground, z) : null;
    }

    private void sync(ServerLevel level) {
        List<LightningWeatherPacket.Arena> arenas = SheeperConfig.LIGHTNING_STORM_ENABLED.get()
                ? storms.values().stream().map(storm -> storm.arena).toList()
                : List.of();
        LightningWeatherPacket packet = new LightningWeatherPacket(level.dimension().location(), arenas);
        for (var player : level.players()) {
            ModNetwork.sendWeather(player, packet);
        }
    }

    private static LightningStormData load(CompoundTag tag) {
        LightningStormData data = new LightningStormData();
        for (Tag value : tag.getList("Storms", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            double radius = entry.getDouble("Radius");
            if (!entry.hasUUID("Boss") || !Double.isFinite(radius) || radius <= 0 || radius > 128) {
                continue;
            }
            Storm storm = new Storm();
            storm.arena = new LightningWeatherPacket.Arena(BlockPos.of(entry.getLong("Center")), radius,
                    entry.getLong("Start"), Mth.clamp(entry.getInt("Transition"), 0, 1200));
            storm.patternUntil = entry.getLong("PatternUntil");
            double chance = entry.getDouble("StrikeChance");
            storm.strikeChance = Double.isFinite(chance) ? Mth.clamp(chance, 0, 0.2D) : 0;
            storm.nextDecoration = entry.getLong("NextDecoration");
            storm.nextRumble = entry.getLong("NextRumble");
            for (Tag item : entry.getList("Warnings", Tag.TAG_COMPOUND)) {
                CompoundTag warning = (CompoundTag) item;
                Vec3 pos = new Vec3(warning.getDouble("X"), warning.getDouble("Y"), warning.getDouble("Z"));
                if (Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z)
                        && storm.arena.contains(pos.x, pos.z) && storm.warnings.size() < 256) {
                    storm.warnings.add(new Warning(pos, warning.getLong("Impact")));
                }
            }
            data.storms.put(entry.getUUID("Boss"), storm);
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
            entry.putLong("Start", storm.arena.start());
            entry.putInt("Transition", storm.arena.transition());
            entry.putLong("PatternUntil", storm.patternUntil);
            entry.putDouble("StrikeChance", storm.strikeChance);
            entry.putLong("NextDecoration", storm.nextDecoration);
            entry.putLong("NextRumble", storm.nextRumble);
            ListTag warnings = new ListTag();
            for (Warning warning : storm.warnings) {
                CompoundTag item = new CompoundTag();
                item.putDouble("X", warning.pos().x);
                item.putDouble("Y", warning.pos().y);
                item.putDouble("Z", warning.pos().z);
                item.putLong("Impact", warning.impact());
                warnings.add(item);
            }
            entry.put("Warnings", warnings);
            entries.add(entry);
        });
        tag.put("Storms", entries);
        return tag;
    }
}
