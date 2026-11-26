package de.codemorra.sheeper.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.entity.LightningRingSurface;
import de.codemorra.sheeper.entity.LightningSheeperEntity;
import de.codemorra.sheeper.entity.LightningStrikeEffect;
import de.codemorra.sheeper.network.LightningTornadoPacket;
import de.codemorra.sheeper.network.LightningWeatherPacket;
import de.codemorra.sheeper.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        private long deathDrainAt;
        private final LightningArenaWind wind = new LightningArenaWind();
        private final LightningSpawnPush spawnPush = new LightningSpawnPush();
        private final LightningLeafFire leafFire = new LightningLeafFire();
        private List<LightningTornado> tornados = List.of();
        private List<LightningRodShelter> shelters = List.of();

        private Storm(LightningWeatherPacket.Arena arena) {
            this.arena = arena;
        }
    }

    private final Map<UUID, Storm> storms = new HashMap<>();

    public static LightningStormData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LightningStormData::load, LightningStormData::new,
                "sheeper_lightning_storms");
    }

    /** Returns the captured storm bounds even while the boss is unloaded. */
    @Nullable
    public LightningWeatherPacket.Arena getArena(UUID owner) {
        Storm storm = storms.get(owner);
        return storm == null ? null : storm.arena;
    }

    /** Stops combat hazards and keeps the storm in place until the death effect finishes. */
    public void beginDeath(ServerLevel level, UUID id, int duration) {
        Storm storm = storms.get(id);
        if (storm == null || storm.arena.isDraining() || storm.deathDrainAt != 0) {
            return;
        }
        storm.deathDrainAt = level.getGameTime() + duration;
        stopHazards(level, id, storm);
        setDirty();
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
        storm.deathDrainAt = 0;
        stopHazards(level, id, storm);
        setDirty();
        sync(level);
    }

    private void stopHazards(ServerLevel level, UUID id, Storm storm) {
        storm.leafFire.clear();
        storm.shelters = List.of();
        storm.spawnPush.clear(level, Set.of());
        updateTornados(level, id, List.of());
    }

    /** Publishes transient protection for every lightning source in this dimension. */
    public void updateShelters(UUID owner, List<LightningRodShelter> shelters) {
        Storm storm = storms.get(owner);
        if (storm != null) {
            storm.shelters = shelters;
        }
    }

    /** Returns the first shelter surface along the bolt, or its original target on a miss. */
    public Vec3 clipLightning(Vec3 start, Vec3 target) {
        Vec3 impact = target;
        for (Storm storm : storms.values()) {
            for (LightningRodShelter shelter : storm.shelters) {
                impact = shelter.intercept(start, impact);
            }
        }
        return impact;
    }

    /** Checks player protection even if a nearby strike's damage spreads outside its own column. */
    public boolean isSheltered(Vec3 point) {
        for (Storm storm : storms.values()) {
            for (LightningRodShelter shelter : storm.shelters) {
                if (shelter.contains(point)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasStorms() {
        return LightningSheeperConfig.LIGHTNING_STORM_ENABLED.get() && !storms.isEmpty();
    }

    /** Sets a directly struck leaf alight in the attack owner's arena. */
    public void igniteLeaves(ServerLevel level, UUID owner, BlockPos pos) {
        Storm storm = storms.get(owner);
        if (storm != null && storm.deathDrainAt == 0 && !storm.arena.isDraining()
                && storm.leafFire.ignite(level, storm.arena, pos)) {
            setDirty();
        }
    }

    /** Checks the visible cloud-to-impact path for leaves in an active arena. */
    public void igniteCloudStrike(ServerLevel level, UUID owner, Vec3 impact, double cloudY) {
        Storm storm = storms.get(owner);
        if (storm != null && storm.deathDrainAt == 0 && !storm.arena.isDraining()
                && storm.leafFire.strike(level, storm.arena, impact, cloudY)) {
            setDirty();
        }
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
        boolean changed = removed;
        boolean windCleared = false;
        boolean enabled = hasStorms();
        Set<UUID> carriedPlayers = new HashSet<>();
        Set<UUID> playersTouchingFire = new HashSet<>();
        for (var entry : storms.entrySet()) {
            Storm storm = entry.getValue();
            if (storm.deathDrainAt != 0 && now >= storm.deathDrainAt) {
                // Finish even if the dying entity's chunk was unloaded during the sequence.
                beginDrain(level, entry.getKey());
            }
            if (storm.deathDrainAt != 0 || storm.arena.isDraining()) {
                continue;
            }
            changed |= storm.leafFire.tick(level, storm.arena, playersTouchingFire);
            changed |= enabled ? storm.spawnPush.tick(level, storm.arena, carriedPlayers)
                    : storm.spawnPush.clear(level, carriedPlayers);
            boolean hasBoss = level.getEntity(entry.getKey()) instanceof LightningSheeperEntity;
            if (!hasBoss) {
                storm.shelters = List.of();
            }
            if (enabled) {
                if (!hasBoss) {
                    windCleared |= storm.wind.clear();
                    if (storm.arena.quiet()) {
                        storm.arena = storm.arena.withoutQuiet();
                        windCleared = true;
                    }
                    if (storm.arena.rodDarkened()) {
                        storm.arena = storm.arena.withRodDarkening(false, now);
                        windCleared = true;
                    }
                }
                tickAtmosphere(level, entry.getKey(), storm, now);
            }
        }
        if (changed) {
            setDirty();
        }
        if (removed || windCleared || now % 10 == 0) {
            sync(level);
        }
    }

    /** Keeps the server movement snapshot current and sends short visual refreshes to nearby observers. */
    public void updateTornados(ServerLevel level, UUID owner, List<LightningTornado> tornados) {
        Storm storm = storms.get(owner);
        if (storm == null || storm.tornados.isEmpty() && tornados.isEmpty()) {
            return;
        }
        boolean changed = storm.tornados.size() != tornados.size();
        storm.tornados = tornados;
        if (!changed && level.getGameTime() % 2 != 0) {
            return;
        }
        var packet = new LightningTornadoPacket(level.dimension().location(), owner, tornados);
        Vec3 center = Vec3.atBottomCenterOf(storm.arena.center());
        double range = storm.arena.radius() + 64;
        for (ServerPlayer observer : level.players()) {
            if (observer.position().subtract(center).horizontalDistanceSqr() <= range * range) {
                ModNetwork.sendLightningTornado(observer, packet);
            }
        }
    }

    List<LightningTornado> activeTornados(long now) {
        return storms.values().stream().flatMap(storm -> storm.tornados.stream())
                .filter(tornado -> tornado.sampleTick() + 5 >= now && tornado.endTick() > now).toList();
    }

    /** Updates wind and synchronizes Lightning Rod's temporary weather changes. */
    public void tickWind(ServerLevel level, UUID owner, LightningArenaWind.Mode mode, List<ServerPlayer> participants,
            boolean quiet, int quietFadeTicks) {
        Storm storm = storms.get(owner);
        if (storm == null) {
            return;
        }
        boolean active = hasStorms() && !storm.arena.isBuilding(level.getGameTime()) && !storm.arena.isDraining()
                && LightningSheeperConfig.LIGHTNING_WIND_ENABLED.get();
        boolean rodActive = mode == LightningArenaWind.Mode.INTENSIFIED;
        boolean quietChanged = storm.arena.quiet() != quiet;
        boolean darkeningChanged = storm.arena.rodDarkened() != rodActive;
        if (quietChanged) {
            storm.arena = quiet ? storm.arena.withQuiet(level.getGameTime(), quietFadeTicks) : storm.arena.withoutQuiet();
        }
        if (darkeningChanged) {
            storm.arena = storm.arena.withRodDarkening(rodActive, level.getGameTime());
        }
        boolean changed = active ? storm.wind.tick(level, storm.arena, participants, rodActive)
                : storm.wind.clear();
        if (changed || quietChanged || darkeningChanged) {
            sync(level);
        }
    }

    List<LightningWindField> windFields() {
        return hasStorms() ? storms.values().stream().map(storm -> storm.wind.snapshot(storm.arena))
                .filter(Objects::nonNull).toList()
                : List.of();
    }

    /** Storm lightning uses the visual bolt alone, keeping warnings reserved for combat. */
    private static void tickAtmosphere(ServerLevel level, UUID owner, Storm storm, long now) {
        boolean building = storm.arena.isBuilding(now);
        if (now >= storm.nextFlash) {
            if (building) {
                flash(level, owner, storm.arena, now, true);
                storm.nextFlash = now + 8 + level.random.nextInt(10);
            } else {
                if (storm.arena.quiet() || !(level.getEntity(owner) instanceof LightningSheeperEntity boss)
                        || boss.getCombatParticipants().isEmpty()) {
                    int count = LightningSheeperConfig.LIGHTNING_AMBIENT_STRIKE_COUNT.get();
                    for (int index = 0; index < count; index++) {
                        flash(level, owner, storm.arena, now, storm.arena.quiet());
                    }
                }
                storm.nextFlash = now
                        + SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_AMBIENT_INTERVAL.get());
            }
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

    private static void flash(ServerLevel level, UUID owner, LightningWeatherPacket.Arena arena, long now,
            boolean atFront) {
        BlockPos center = arena.center();
        for (int attempt = 0; attempt < 4; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double fraction = atFront ? 0.9 + level.random.nextDouble() * 0.1
                    : Math.sqrt(level.random.nextDouble());
            double radius = arena.radiusAt(now) * fraction;
            double x = center.getX() + 0.5 + Math.cos(angle) * radius;
            double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
            double ground = LightningRingSurface.findWater(level, x, z, arena.minimumY(), arena.maximumY() - 1);
            if (!arena.containsY(ground)) {
                ground = LightningRingSurface.find(level, x, z, center.getY(), arena.minimumY(), arena.maximumY() - 1);
            }
            if (Double.isFinite(ground)) {
                Vec3 position = new Vec3(x, ground, z);
                double cloudY = arena.cloudStrikeY(x, z, now);
                if (arena.quiet()) {
                    LightningStrikeEffect.spawnVisualBolt(level, position, cloudY);
                } else {
                    LightningStrikeEffect.spawnWeatherBolt(level, owner, position, cloudY, false);
                }
                return;
            }
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
                LightningSheeperConfig.LIGHTNING_BOUNDARY_SLOWDOWN_STRENGTH.get(), windFields());
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
                    entry.getLong("DrainStart"), Mth.clamp(entry.getInt("DrainDuration"), 0, ArenaLimits.MAX_DRAIN_TICKS));
            if (entry.hasUUID("Boss") && arena.isValid()) {
                var storm = new Storm(arena);
                storm.deathDrainAt = arena.isDraining() ? 0 : entry.getLong("DeathDrainAt");
                storm.spawnPush.load(entry.getCompound("SpawnPush"));
                storm.leafFire.load(entry.getList("LeafFire", Tag.TAG_COMPOUND), arena);
                data.storms.put(entry.getUUID("Boss"), storm);
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
            entry.putLong("DeathDrainAt", storm.deathDrainAt);
            entry.put("SpawnPush", storm.spawnPush.save());
            entry.put("LeafFire", storm.leafFire.save());
            entries.add(entry);
        });
        tag.put("Storms", entries);
        return tag;
    }
}
