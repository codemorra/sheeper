package de.codemorra.sheeper.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.codemorra.sheeper.network.LightningWeatherPacket.Arena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Tracks flames on leaves without placing vanilla fire that rain could extinguish or that could burn other blocks. */
final class LightningLeafFire {
    private static final int MAX_BURNS = 1024;
    private final Map<BlockPos, Long> burning = new HashMap<>();

    /** Starts a burn once per leaf; another strike does not reset its burn time. */
    boolean ignite(ServerLevel level, Arena arena, BlockPos pos) {
        if (burning.size() >= MAX_BURNS || !contains(arena, pos) || burning.containsKey(pos) || !isLoaded(level, pos)
                || !level.getBlockState(pos).is(BlockTags.LEAVES)) {
            return false;
        }
        burning.put(pos.immutable(), level.getGameTime() + 40 + level.random.nextInt(61));
        return true;
    }

    /** Ignites the first leaf in a cloud bolt's column, with solid terrain blocking the strike. */
    boolean strike(ServerLevel level, Arena arena, Vec3 impact, double cloudY) {
        var pos = new BlockPos.MutableBlockPos(Mth.floor(impact.x), 0, Mth.floor(impact.z));
        if (!isLoaded(level, pos)) {
            return false;
        }
        int top = Math.min(arena.maximumY() - 1, Mth.floor(Math.max(impact.y, cloudY)));
        int bottom = Math.max(arena.minimumY(), Mth.floor(Math.nextDown(impact.y)));
        for (int y = top; y >= bottom; y--) {
            pos.setY(y);
            var state = level.getBlockState(pos);
            if (state.is(BlockTags.LEAVES)) {
                return ignite(level, arena, pos);
            }
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }
        return false;
    }

    /** Spreads to adjacent leaves, burns players touching flames, and consumes spent leaves. */
    boolean tick(ServerLevel level, Arena arena, Set<UUID> playersTouchingFire) {
        if (burning.isEmpty()) {
            return false;
        }
        long now = level.getGameTime();
        boolean changed = false;
        int visible = 0;
        var spread = new ArrayList<BlockPos>();
        var entries = burning.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            BlockPos pos = entry.getKey();
            if (!isLoaded(level, pos)) {
                continue;
            }
            if (!level.getBlockState(pos).is(BlockTags.LEAVES)) {
                entries.remove();
                changed = true;
                continue;
            }
            boolean spent = now >= entry.getValue();
            if (spent || Math.floorMod(now + pos.hashCode(), 12) == 0) {
                for (Direction side : Direction.values()) {
                    if (spent || level.random.nextInt(4) == 0) {
                        spread.add(pos.relative(side));
                    }
                }
            }
            if (spent) {
                level.removeBlock(pos, false);
                entries.remove();
                changed = true;
                continue;
            }
            burnPlayers(level, pos, playersTouchingFire);
            if (visible < 48 && Math.floorMod(now + pos.hashCode(), 8) == 0) {
                showFlames(level, pos);
                visible++;
            }
        }
        for (BlockPos neighbor : spread) {
            changed |= ignite(level, arena, neighbor);
        }
        return changed;
    }

    /** Applies vanilla fire contact damage and keeps players alight while they stand in the flames. */
    private static void burnPlayers(ServerLevel level, BlockPos pos, Set<UUID> playersTouchingFire) {
        AABB flames = new AABB(pos.above());
        for (var player : level.players()) {
            if (!player.getBoundingBox().intersects(flames) || !player.isAlive() || player.isCreative()
                    || player.isSpectator() || player.fireImmune() || !playersTouchingFire.add(player.getUUID())) {
                continue;
            }
            int fireTicks = player.getRemainingFireTicks();
            // Virtual flames cannot advance vanilla's countdown to ignition during entity movement.
            if (fireTicks <= 0) {
                player.setSecondsOnFire(8);
            } else {
                player.setRemainingFireTicks(fireTicks + 1);
            }
            player.hurt(DamageSource.IN_FIRE, 1);
        }
    }

    private static boolean contains(Arena arena, BlockPos pos) {
        double dx = (double) pos.getX() - arena.center().getX();
        double dz = (double) pos.getZ() - arena.center().getZ();
        return arena.containsY(pos.getY() + 0.5) && dx * dx + dz * dz <= arena.radius() * arena.radius();
    }

    private static boolean isLoaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static void showFlames(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.FLAME, x, pos.getY() + 0.86, z, 5, 0.32, 0.23, 0.32, 0.018);
        level.sendParticles(ParticleTypes.SMOKE, x, pos.getY() + 1.08, z, 2, 0.28, 0.10, 0.28, 0.025);
    }

    void clear() {
        burning.clear();
    }

    ListTag save() {
        var entries = new ListTag();
        burning.forEach((pos, endTick) -> {
            var tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.putLong("EndTick", endTick);
            entries.add(tag);
        });
        return entries;
    }

    void load(ListTag entries, Arena arena) {
        if (arena.isDraining()) {
            return;
        }
        for (Tag value : entries) {
            CompoundTag tag = (CompoundTag) value;
            BlockPos pos = BlockPos.of(tag.getLong("Pos"));
            long endTick = tag.getLong("EndTick");
            if (burning.size() == MAX_BURNS) {
                break;
            }
            if (endTick > 0 && contains(arena, pos)) {
                burning.put(pos, endTick);
            }
        }
    }
}
