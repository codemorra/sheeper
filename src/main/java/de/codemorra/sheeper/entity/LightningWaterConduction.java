package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import de.codemorra.sheeper.world.LightningWaterVolume;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;

/** Resolves water contact and conduction inside one lightning arena. */
final class LightningWaterConduction {
    private static final Direction[] DIRECTIONS = Direction.values();

    private static final class Frontier {
        private final LongSet reached = new LongOpenHashSet();
        private final PriorityQueue<BlockPos> next;

        private Frontier(List<BlockPos> starts, BlockPos goal) {
            next = new PriorityQueue<>(Comparator.comparingInt(pos -> pos.distManhattan(goal)));
            starts.forEach(this::add);
        }

        private void add(BlockPos position) {
            if (reached.add(position.asLong())) {
                next.add(position);
            }
        }
    }

    private final ServerLevel level;
    private final LightningWaterVolume water;

    LightningWaterConduction(ServerLevel level, LightningSheeperEntity boss) {
        this.level = level;
        water = new LightningWaterVolume(level, boss.getArenaCenter(), boss.getCombatRadius(),
                boss.getArenaMinimumY(), boss.getArenaMaximumY());
    }

    /** Uses two search fronts to resolve nearby connections and isolated pools quickly. */
    boolean reaches(BlockPos strike, ServerPlayer player) {
        List<BlockPos> contact = waterContact(player);
        if (contact.isEmpty() || !water.hasWater(strike)) {
            return false;
        }
        if (contact.contains(strike)) {
            return true;
        }
        var fromStrike = new Frontier(List.of(strike), contact.get(0));
        var fromPlayer = new Frontier(contact, strike);
        while (!fromStrike.next.isEmpty() && !fromPlayer.next.isEmpty()) {
            if (expand(fromStrike, fromPlayer) || expand(fromPlayer, fromStrike)) {
                return true;
            }
        }
        return false;
    }

    private boolean expand(Frontier own, Frontier other) {
        BlockPos position = own.next.remove();
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbor = position.relative(direction);
            if (own.reached.contains(neighbor.asLong()) || !water.hasWater(neighbor)
                    || !water.connects(position, neighbor, direction)) {
                continue;
            }
            if (other.reached.contains(neighbor.asLong())) {
                return true;
            }
            own.add(neighbor);
        }
        return false;
    }

    /** Includes wading and swimming by checking the actual submerged part of the player's bounding box. */
    private List<BlockPos> waterContact(ServerPlayer player) {
        AABB bounds = player.getBoundingBox().deflate(0.001);
        var contact = new ArrayList<BlockPos>();
        for (BlockPos position : BlockPos.betweenClosed(new BlockPos(bounds.minX, bounds.minY, bounds.minZ),
                new BlockPos(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (!water.hasWater(position)) {
                continue;
            }
            double fluidHeight = level.getFluidState(position).getHeight(level, position);
            AABB water = new AABB(position.getX(), position.getY(), position.getZ(),
                    position.getX() + 1, position.getY() + fluidHeight, position.getZ() + 1);
            if (bounds.intersects(water)) {
                var overlap = Shapes.create(bounds.intersect(water).move(-position.getX(), -position.getY(),
                        -position.getZ()));
                var collision = level.getBlockState(position).getCollisionShape(level, position);
                if (Shapes.joinIsNotEmpty(overlap, collision, BooleanOp.ONLY_FIRST)) {
                    contact.add(position.immutable());
                }
            }
        }
        return contact;
    }
}
