package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.registry.ModParticles;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Warned, fixed bubble columns from the local floor to the captured arena
 * ceiling.
 * Each column hits a player once; an independent transport completes the ascent
 * even if its originating column expires. No blocks or rain are created.
 */
final class WaterFountainAttack {
    private static final int SAVE_VERSION = 2;
    // Counts warning and erupted cells together; active columns always finish
    // normally.
    private static final int MAX_ACTIVE_COLUMN_CELLS = 512;
    private final WaterSheeperEntity boss;
    private final WaterFountainTransport transports;
    private final Map<UUID, Integer> playerDelays = new HashMap<>();
    private final List<Fountain> fountains = new ArrayList<>();
    private int arenaDelay;
    private boolean budgetWarningLogged;

    private static final class Fountain {
        private final BlockPos pos;
        private final int size;
        private final double ceiling;
        @Nullable
        private final UUID playerId;
        private final Set<UUID> hitPlayers = new HashSet<>();
        private int warningTicks;
        private int activeTicks;
        private boolean restoreEffects;

        private Fountain(BlockPos pos, int size, double ceiling, @Nullable UUID playerId,
                int warningTicks, int activeTicks) {
            this.pos = pos;
            this.size = size;
            this.ceiling = ceiling;
            this.playerId = playerId;
            this.warningTicks = warningTicks;
            this.activeTicks = activeTicks;
        }
    }

    /**
     * @param boss owner providing captured volume bounds and current arena
     *             participants
     */
    WaterFountainAttack(WaterSheeperEntity boss) {
        this.boss = boss;
        transports = new WaterFountainTransport(boss);
    }

    /**
     * Starts independent player and arena timers without carrying over old hazards.
     */
    void start() {
        reset();
        arenaDelay = initialDelay();
    }

    /**
     * Cancels columns and releases remaining transport when combat is interrupted.
     */
    void finish() {
        reset();
    }

    /**
     * Clears pending damage, one-hit records and upward movement after death or
     * full recovery.
     */
    void reset() {
        fountains.clear();
        playerDelays.clear();
        transports.clear();
        arenaDelay = 0;
    }

    private int initialDelay() {
        return 1 + boss.getRandom()
                .nextInt(SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_FOUNTAIN_INTERVAL_MIN.get()));
    }

    private int nextDelay() {
        int minimum = SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_FOUNTAIN_INTERVAL_MIN.get());
        int maximum = Math.max(minimum,
                SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_FOUNTAIN_INTERVAL_MAX.get()));
        return minimum + boss.getRandom().nextInt(maximum - minimum + 1);
    }

    /**
     * Advances fixed warnings, active columns and player transports. Once the
     * announcement window ends, existing columns and transports finish normally.
     *
     * @param players        current arena participants eligible for fountain
     *                       contact
     * @param remainingTicks remaining announcement time; zero only drains existing
     *                       effects
     */
    void tick(List<ServerPlayer> players, int remainingTicks) {
        var iterator = fountains.iterator();
        while (iterator.hasNext()) {
            Fountain fountain = iterator.next();
            if (!suitable(fountain.pos, fountain.size)) {
                iterator.remove();
                continue;
            }
            if (fountain.restoreEffects) {
                // Loading NBT precedes arena initialization. Recreate only the
                // emitter once water bounds are available, using remaining time.
                showColumn(fountain, fountain.warningTicks > 0);
                fountain.restoreEffects = false;
            }
            if (fountain.warningTicks > 0) {
                fountain.warningTicks--;
                if (fountain.warningTicks == 0) {
                    showCurrent(fountain);
                } else {
                    continue;
                }
            }
            hitPlayers(fountain, players);
            if (--fountain.activeTicks <= 0) {
                iterator.remove();
            }
        }
        transports.tick(players);
        if (remainingTicks <= SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_FOUNTAIN_WARNING.get())) {
            return;
        }
        playerDelays.keySet().removeIf(id -> players.stream().noneMatch(player -> player.getUUID().equals(id)));
        for (ServerPlayer player : players) {
            // Only one pending snapshot per player; moving cannot drag an existing warning.
            if (fountains.stream().anyMatch(fountain -> fountain.warningTicks > 0
                    && player.getUUID().equals(fountain.playerId))) {
                continue;
            }
            int delay = playerDelays.computeIfAbsent(player.getUUID(), ignored -> initialDelay()) - 1;
            if (delay <= 0) {
                BlockPos floor = findFloor(player.blockPosition());
                if (floor != null && !hasColumnCapacity(1)) {
                    // Retry the expired announcement without losing its place in
                    // the player's cycle or cancelling an already running column.
                    playerDelays.put(player.getUUID(), 0);
                    continue;
                }
                if (floor != null) {
                    announce(floor, player.getUUID());
                }
                delay = nextDelay();
            }
            playerDelays.put(player.getUUID(), delay);
        }
        if (--arenaDelay <= 0) {
            List<BlockPos> selected = new ArrayList<>();
            int limit = WaterSheeperConfig.WATER_FOUNTAIN_ARENA_COUNT.get();
            if (!hasColumnCapacity(limit)) {
                arenaDelay = 0;
                return;
            }
            int count = 0;
            for (BlockPos pos : boss.getArenaSurfacePositions()) {
                if (!suitable(pos, WaterSheeperConfig.WATER_FOUNTAIN_SIZE.get()) || occupied(pos)) {
                    continue;
                }
                int slot = boss.getRandom().nextInt(++count);
                if (selected.size() < limit) {
                    selected.add(pos);
                } else if (slot < limit) {
                    selected.set(slot, pos);
                }
            }
            selected.forEach(pos -> announce(pos, null));
            arenaDelay = nextDelay();
        }
    }

    /**
     * Finds real ground or the captured arena floor below a swimmer without
     * tunneling through solid roofs.
     */
    @Nullable
    private BlockPos findFloor(BlockPos playerPos) {
        int top = Math.min(playerPos.getY(), (int) boss.getArenaWaterSurfaceY() - 1);
        for (int y = top; y >= boss.getArenaWaterMinimumY(); y--) {
            BlockPos pos = new BlockPos(playerPos.getX(), y, playerPos.getZ());
            if (!isOpenWater(pos)) {
                return null;
            }
            if (WaterArenaSearch.isSurface(boss.level, pos, boss.getArenaWaterMinimumY())) {
                return pos;
            }
        }
        return null;
    }

    private boolean occupied(BlockPos pos) {
        return fountains.stream().anyMatch(fountain -> fountain.pos.equals(pos));
    }

    /**
     * Defers whole announcements when their cell emitters would exceed the shared
     * budget.
     */
    private boolean hasColumnCapacity(int count) {
        int size = WaterSheeperConfig.WATER_FOUNTAIN_SIZE.get();
        int used = fountains.stream().mapToInt(fountain -> fountain.size * fountain.size).sum();
        boolean available = used + count * size * size <= MAX_ACTIVE_COLUMN_CELLS;
        if (!available && !budgetWarningLogged) {
            budgetWarningLogged = true;
            SheeperMod.LOGGER.warn("Water Sheeper {}: fountain announcements deferred at the internal limit of {} "
                    + "active column cells (used={}, size={}, requested={}). Running fountains are preserved.",
                    boss.getUUID(), MAX_ACTIVE_COLUMN_CELLS, used, size, count);
        }
        return available;
    }

    private void announce(BlockPos pos, @Nullable UUID playerId) {
        int size = WaterSheeperConfig.WATER_FOUNTAIN_SIZE.get();
        if (!suitable(pos, size) || occupied(pos)) {
            return;
        }
        Fountain fountain = new Fountain(pos, size, boss.getArenaWaterSurfaceY(), playerId,
                SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_FOUNTAIN_WARNING.get()),
                SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_FOUNTAIN_ACTIVE.get()));
        fountains.add(fountain);
        showWarning(fountain);
    }

    private boolean suitable(BlockPos center, int size) {
        if (!isOpenWater(center)
                || !WaterArenaSearch.isSurface(boss.level, center, boss.getArenaWaterMinimumY())) {
            return false;
        }
        int offset = size / 2;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                BlockPos pos = center.offset(x - offset, 0, z - offset);
                if (!boss.isArenaWaterCell(pos) || !WaterArenaData.isLoaded((ServerLevel) boss.level, pos)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isOpenWater(BlockPos pos) {
        return boss.isArenaWaterCell(pos) && WaterArenaData.isLoaded((ServerLevel) boss.level, pos)
                && boss.level.getFluidState(pos).is(FluidTags.WATER)
                && boss.level.getBlockState(pos).getCollisionShape(boss.level, pos).isEmpty();
    }

    private AABB area(Fountain fountain) {
        BlockPos minimum = fountain.pos.offset(-fountain.size / 2, 0, -fountain.size / 2);
        return new AABB(minimum.getX(), minimum.getY(), minimum.getZ(), minimum.getX() + fountain.size,
                fountain.ceiling, minimum.getZ() + fountain.size);
    }

    /**
     * Starts sparse bubbles across the entire height of the fixed warning column.
     */
    private void showWarning(Fountain fountain) {
        showColumn(fountain, true);
        playColumnSound(fountain, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.5F, 0.65F);
    }

    /** Replaces the sparse warning with a dense, rapidly rising bubble stream. */
    private void showCurrent(Fountain fountain) {
        showColumn(fountain, false);
        playColumnSound(fountain, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.8F, 1.1F);
    }

    /**
     * Plays at each swimmer's nearest point on the column, covering its full
     * height.
     */
    private void playColumnSound(Fountain fountain, SoundEvent sound, float volume, float pitch) {
        AABB bounds = area(fountain);
        Vec3 center = bounds.getCenter();
        WaterAttackSounds.segment(boss, new Vec3(center.x, bounds.minY, center.z),
                new Vec3(center.x, bounds.maxY, center.z), sound, volume, pitch);
    }

    /**
     * Sends one local emitter per cell. Motion fields carry height, speed and
     * lifetime; zero speed selects the sparse warning instead of the eruption.
     */
    private void showColumn(Fountain fountain, boolean warning) {
        AABB area = area(fountain);
        for (int x = 0; x < fountain.size; x++) {
            for (int z = 0; z < fountain.size; z++) {
                BlockPos base = new BlockPos(area.minX + x, area.minY, area.minZ + z);
                double top = base.getY();
                while (top < fountain.ceiling && isOpenWater(new BlockPos(base.getX(), top, base.getZ()))) {
                    top++;
                }
                double height = Math.min(top, fountain.ceiling) - base.getY();
                if (height > 0) {
                    ((ServerLevel) boss.level).sendParticles(ModParticles.WATER_FOUNTAIN.get(),
                            base.getX() + 0.5D, base.getY() + 0.1D, base.getZ() + 0.5D,
                            0, height - 0.2D, warning ? 0 : WaterSheeperConfig.WATER_FOUNTAIN_LIFT_SPEED.get() / 20.0D,
                            warning ? fountain.warningTicks : fountain.activeTicks, 1);
                }
            }
        }
    }

    /**
     * Applies damage once per player and column, then hands ongoing ascent to the
     * transport controller.
     */
    private void hitPlayers(Fountain fountain, List<ServerPlayer> players) {
        AABB area = area(fountain);
        for (ServerPlayer player : players) {
            if (fountain.hitPlayers.contains(player.getUUID()) || !area.intersects(player.getBoundingBox())
                    || !hasWaterPath(fountain, player, area)) {
                continue;
            }
            fountain.hitPlayers.add(player.getUUID());
            if (transports.contains(player)) {
                continue;
            }
            player.hurt(DamageSource.mobAttack(boss), WaterSheeperConfig.WATER_FOUNTAIN_DAMAGE.get().floatValue());
            if (player.isAlive()) {
                transports.start(player, fountain.ceiling);
            }
        }
    }

    /**
     * Prevents a lower column damaging a player through a solid platform or roof.
     */
    private boolean hasWaterPath(Fountain fountain, ServerPlayer player, AABB area) {
        int x = Mth.floor(Mth.clamp(player.getX(), area.minX + 0.01D, area.maxX - 0.01D));
        int z = Mth.floor(Mth.clamp(player.getZ(), area.minZ + 0.01D, area.maxZ - 0.01D));
        int top = Math.min((int) fountain.ceiling - 1, Mth.floor(player.getY()));
        for (int y = fountain.pos.getY(); y <= top; y++) {
            if (!isOpenWater(new BlockPos(x, y, z))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return whether existing columns or captured victims still keep the phase
     *         active
     */
    boolean hasActiveEffects() {
        return !fountains.isEmpty() || transports.isActive();
    }

    /**
     * Legacy spawn-wave impulse; fountain transport uses continuous lift instead of
     * an air trajectory.
     */
    static double launchSpeed(double height, double gravity) {
        double low = 0;
        double high = height;
        for (int step = 0; step < 40; step++) {
            double speed = (low + high) / 2;
            double rise = 0;
            double velocity = speed;
            for (int tick = 0; tick < 1000 && velocity > 0 && rise <= height; tick++) {
                rise += velocity;
                velocity = (velocity - Math.max(0, gravity)) * 0.98D;
            }
            if (rise > height) {
                high = speed;
            } else {
                low = speed;
            }
        }
        return low;
    }

    /**
     * @return complete warning/current hit history and transport progress for
     *         reloads
     */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SAVE_VERSION);
        tag.putInt("ArenaDelay", arenaDelay);
        ListTag delays = new ListTag();
        playerDelays.forEach((id, ticks) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Ticks", ticks);
            delays.add(entry);
        });
        tag.put("Players", delays);
        ListTag currents = new ListTag();
        for (Fountain fountain : fountains) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", fountain.pos.asLong());
            entry.putInt("Size", fountain.size);
            entry.putDouble("Ceiling", fountain.ceiling);
            entry.putInt("WarningTicks", fountain.warningTicks);
            entry.putInt("ActiveTicks", fountain.activeTicks);
            if (fountain.playerId != null) {
                entry.putUUID("Player", fountain.playerId);
            }
            ListTag hits = new ListTag();
            fountain.hitPlayers.forEach(id -> {
                CompoundTag hit = new CompoundTag();
                hit.putUUID("Player", id);
                hits.add(hit);
            });
            entry.put("Hits", hits);
            currents.add(entry);
        }
        tag.put("Currents", currents);
        tag.put("Transports", transports.save());
        return tag;
    }

    /**
     * Restores hit history and transport without replaying their initial effects.
     * Saved warning/current emitters resume on the next active tick, after the
     * arena has initialized, for their remaining lifetime only.
     *
     * @param tag saved columns; incompatible old launch/rain state is discarded
     */
    void load(CompoundTag tag) {
        reset();
        if (tag.getInt("Version") != SAVE_VERSION) {
            return;
        }
        arenaDelay = Mth.clamp(tag.getInt("ArenaDelay"), 0, 200);
        for (Tag value : tag.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                playerDelays.put(entry.getUUID("Player"), Mth.clamp(entry.getInt("Ticks"), 0, 200));
            }
        }
        for (Tag value : tag.getList("Currents", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            double ceiling = entry.getDouble("Ceiling");
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            if (!Double.isFinite(ceiling) || ceiling <= pos.getY() || ceiling - pos.getY() > 128) {
                continue;
            }
            Fountain fountain = new Fountain(pos, Mth.clamp(entry.getInt("Size"), 1, 9), ceiling,
                    entry.hasUUID("Player") ? entry.getUUID("Player") : null,
                    Mth.clamp(entry.getInt("WarningTicks"), 0, 100),
                    Mth.clamp(entry.getInt("ActiveTicks"), 1, 160));
            fountain.restoreEffects = true;
            for (Tag hitValue : entry.getList("Hits", Tag.TAG_COMPOUND)) {
                CompoundTag hit = (CompoundTag) hitValue;
                if (hit.hasUUID("Player")) {
                    fountain.hitPlayers.add(hit.getUUID("Player"));
                }
            }
            fountains.add(fountain);
        }
        transports.load(tag.getList("Transports", Tag.TAG_COMPOUND));
    }
}
