package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.registry.ModParticles;
import de.codemorra.sheeper.registry.ModBlocks;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

/** Server-owned, independently timed hazards and cosmetic arena rain. */
final class WaterFountainAttack {
    private final WaterSheeperEntity boss;
    private final Map<UUID, Integer> playerDelays = new HashMap<>();
    private final List<Warning> warnings = new ArrayList<>();
    private int arenaDelay;
    private int rainDelay = -1;
    private int rainTicks;
    private boolean erupted;

    private static final class Warning {
        private final BlockPos pos;
        private final int size;
        private int ticks;

        private Warning(BlockPos pos, int size, int ticks) {
            this.pos = pos;
            this.size = size;
            this.ticks = ticks;
        }
    }

    WaterFountainAttack(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    void start() {
        playerDelays.clear();
        warnings.clear();
        arenaDelay = initialDelay();
        erupted = false;
    }

    void finish() {
        warnings.clear();
        playerDelays.clear();
        // The tail is independent of combat participants and NoAI.
        if (erupted) {
            rainDelay = 0;
            rainTicks = SheeperConfig.WATER_FOUNTAIN_RAIN_AFTER.get();
        }
    }

    private int initialDelay() {
        return 1 + boss.getRandom().nextInt(SheeperConfig.WATER_FOUNTAIN_INTERVAL_MIN.get());
    }

    private int nextDelay() {
        int min = SheeperConfig.WATER_FOUNTAIN_INTERVAL_MIN.get();
        int max = Math.max(min, SheeperConfig.WATER_FOUNTAIN_INTERVAL_MAX.get());
        return min + boss.getRandom().nextInt(max - min + 1);
    }

    void tick(List<ServerPlayer> players, int remainingTicks) {
        // Resolve existing warnings before adding new ones: every warning gets
        // its full configured duration, including the tick it is first visible.
        var iterator = warnings.iterator();
        while (iterator.hasNext()) {
            Warning warning = iterator.next();
            if (!suitable(warning.pos, warning.size)) {
                iterator.remove();
            } else if (--warning.ticks <= 0) {
                erupt(warning, players);
                iterator.remove();
            } else if (warning.ticks % 2 == 0) {
                showWarning(warning);
            }
        }
        // Do not announce hazards that would be cancelled by the phase ending.
        if (remainingTicks <= SheeperConfig.WATER_FOUNTAIN_WARNING.get()) {
            return;
        }
        playerDelays.keySet().removeIf(id -> players.stream().noneMatch(p -> p.getUUID().equals(id)));
        for (ServerPlayer player : players) {
            int delay = playerDelays.computeIfAbsent(player.getUUID(), id -> initialDelay()) - 1;
            if (delay <= 0) {
                BlockPos pos = boss.getArenaSurfacePositions().stream()
                        .filter(cell -> cell.getX() == player.blockPosition().getX()
                                && cell.getZ() == player.blockPosition().getZ()
                                && player.getY() >= cell.getY()
                                && player.getY() <= cell.getY() + 1 + SheeperConfig.WATER_FOUNTAIN_HIT_HEIGHT.get())
                        .max(java.util.Comparator.comparingInt(BlockPos::getY)).orElse(null);
                if (pos != null) {
                    announce(pos);
                }
                delay = nextDelay();
            }
            playerDelays.put(player.getUUID(), delay);
        }
        if (--arenaDelay <= 0) {
            // Sample distinct centers uniformly without copying the whole arena.
            List<BlockPos> selected = new ArrayList<>();
            int limit = SheeperConfig.WATER_FOUNTAIN_ARENA_COUNT.get();
            int count = 0;
            for (BlockPos pos : boss.getArenaSurfacePositions()) {
                if (!suitable(pos, SheeperConfig.WATER_FOUNTAIN_SIZE.get())
                        || warnings.stream().anyMatch(w -> w.pos.equals(pos))) {
                    continue;
                }
                int slot = boss.getRandom().nextInt(++count);
                if (selected.size() < limit) {
                    selected.add(pos);
                } else if (slot < limit) {
                    selected.set(slot, pos);
                }
            }
            selected.forEach(this::announce);
            arenaDelay = nextDelay();
        }
    }

    private void announce(BlockPos pos) {
        int size = SheeperConfig.WATER_FOUNTAIN_SIZE.get();
        if (suitable(pos, size) && warnings.stream().noneMatch(w -> w.pos.equals(pos))) {
            Warning warning = new Warning(pos, size, SheeperConfig.WATER_FOUNTAIN_WARNING.get());
            warnings.add(warning);
            showWarning(warning);
        }
    }

    private boolean suitable(BlockPos center, int size) {
        int offset = size / 2;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                BlockPos pos = center.offset(x - offset, 0, z - offset);
                if (!boss.getArenaSurfacePositions().contains(pos)
                        || !WaterArenaData.isLoaded((ServerLevel) boss.level, pos)
                        || !(boss.level.getBlockState(pos).is(ModBlocks.BOSS_WATER.get())
                                || boss.level.getBlockState(pos).getBlock() instanceof LiquidBlock)
                        || !boss.level.getFluidState(pos).is(FluidTags.WATER)
                        || !WaterArenaSearch.isSurface(boss.level, pos)) {
                    return false;
                }
            }
        }
        return true;
    }

    private AABB area(Warning warning) {
        BlockPos min = warning.pos.offset(-warning.size / 2, 0, -warning.size / 2);
        return new AABB(min.getX(), min.getY(), min.getZ(), min.getX() + warning.size,
                min.getY() + 1 + SheeperConfig.WATER_FOUNTAIN_HIT_HEIGHT.get(), min.getZ() + warning.size);
    }

    private void showWarning(Warning warning) {
        AABB area = area(warning);
        for (int x = 0; x < warning.size; x++) {
            for (int z = 0; z < warning.size; z++) {
                ((ServerLevel) boss.level).sendParticles(ParticleTypes.FISHING,
                        area.minX + x + 0.5D, warning.pos.getY() + 0.95D, area.minZ + z + 0.5D,
                        4, 0.2D, 0.02D, 0.2D, 0.0D);
            }
        }
    }

    private void erupt(Warning warning, List<ServerPlayer> players) {
        if (!erupted) {
            erupted = true;
            rainDelay = SheeperConfig.WATER_FOUNTAIN_RAIN_DELAY.get();
        }
        AABB area = area(warning);
        ((ServerLevel) boss.level).sendParticles(ParticleTypes.SPLASH, area.getCenter().x,
                warning.pos.getY() + 1, area.getCenter().z, warning.size * warning.size * 12,
                warning.size * 0.3D, 0.3D, warning.size * 0.3D, 0.4D);
        double jetSpeed = Math.min(1.2D, Math.sqrt(0.12D * SheeperConfig.WATER_FOUNTAIN_HEIGHT.get()));
        for (int x = 0; x < warning.size; x++) {
            for (int z = 0; z < warning.size; z++) {
                // One packet per cell starts a sustained client-side jet.
                ((ServerLevel) boss.level).sendParticles(ModParticles.WATER_FOUNTAIN.get(),
                        area.minX + x + 0.5D, warning.pos.getY() + 1.01D, area.minZ + z + 0.5D,
                        0, 0, jetSpeed, 0, 1);
            }
        }
        for (ServerPlayer player : players) {
            if (!area.intersects(player.getBoundingBox())) {
                continue;
            }
            double height = Math.max(0, Math.min(20, SheeperConfig.WATER_FOUNTAIN_HEIGHT.get())
                    - Math.max(0, player.getY() - warning.pos.getY()));
            if (height == 0) {
                continue;
            }
            double gravity = player.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get());
            Vec3 movement = player.getDeltaMovement();
            player.setDeltaMovement(movement.x, launchSpeed(height, gravity), movement.z);
            player.fallDistance = 0;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.hurtMarked = false;
        }
    }

    /** Solve vanilla's discrete air trajectory; water drag only lowers its apex. */
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

    void tickRain(boolean active) {
        boolean phaseRain = active && erupted;
        boolean tailRain = rainTicks > 0;
        if (phaseRain && rainDelay > 0) {
            rainDelay--;
            phaseRain = false;
        }
        if (tailRain) {
            rainTicks--;
        }
        if (!phaseRain && !tailRain) {
            return;
        }
        if (!SheeperConfig.WATER_FOUNTAIN_RAIN_ENABLED.get() || boss.level.isRaining()) {
            return;
        }
        List<BlockPos> surfaces = new ArrayList<>(boss.getArenaSurfacePositions());
        if (surfaces.isEmpty()) {
            return;
        }
        ServerLevel level = (ServerLevel) boss.level;
        for (int i = 0; i < SheeperConfig.WATER_FOUNTAIN_RAIN_PARTICLES.get(); i++) {
            BlockPos pos = surfaces.get(boss.getRandom().nextInt(surfaces.size()));
            if (!WaterArenaData.isLoaded(level, pos)) {
                continue;
            }
            Vec3 bottom = Vec3.atBottomCenterOf(pos).add(0, 1.01D, 0);
            Vec3 top = bottom.add(0, 5 + boss.getRandom().nextDouble() * 4, 0);
            // Artificial rain also works indoors, with drops starting below the
            // first ceiling instead of falling through it.
            double ceiling = level.clip(new ClipContext(bottom, top, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, boss)).getLocation().y;
            if (ceiling - bottom.y > 0.2D) {
                level.sendParticles(ModParticles.ARENA_RAIN.get(), bottom.x,
                        ceiling - 0.1D, bottom.z, 1, 0, 0, 0, 0);
            }
        }
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ArenaDelay", arenaDelay);
        tag.putInt("RainDelay", rainDelay);
        tag.putInt("RainTicks", rainTicks);
        tag.putBoolean("Erupted", erupted);
        ListTag delays = new ListTag();
        playerDelays.forEach((id, ticks) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Ticks", ticks);
            delays.add(entry);
        });
        tag.put("Players", delays);
        ListTag pending = new ListTag();
        warnings.forEach(warning -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", warning.pos.asLong());
            entry.putInt("Size", warning.size);
            entry.putInt("Ticks", warning.ticks);
            pending.add(entry);
        });
        tag.put("Warnings", pending);
        return tag;
    }

    void load(CompoundTag tag) {
        arenaDelay = Math.max(0, tag.getInt("ArenaDelay"));
        rainDelay = Math.max(0, tag.getInt("RainDelay"));
        rainTicks = Math.max(0, tag.getInt("RainTicks"));
        erupted = tag.getBoolean("Erupted");
        playerDelays.clear();
        for (Tag value : tag.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                playerDelays.put(entry.getUUID("Player"), Math.max(0, entry.getInt("Ticks")));
            }
        }
        warnings.clear();
        for (Tag value : tag.getList("Warnings", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            warnings.add(new Warning(BlockPos.of(entry.getLong("Pos")),
                    Math.max(1, Math.min(9, entry.getInt("Size"))), Math.max(1, entry.getInt("Ticks"))));
        }
    }
}
