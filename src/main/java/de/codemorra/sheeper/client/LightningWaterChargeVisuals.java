package de.codemorra.sheeper.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.entity.LightningRingSurface;
import de.codemorra.sheeper.network.LightningWaterChargePacket;
import de.codemorra.sheeper.world.LightningWaterVolume;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draws forked lightning paths from the strike across the connected water surface. */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LightningWaterChargeVisuals {
    private static final int DURATION_TICKS = 34;
    private static final int COLUMNS_PER_TICK = 1200;
    private static final int ARC_FADE_TICKS = 9;
    private static final double VIEW_RANGE = 48;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final List<Charge> charges = new ArrayList<>();
    private static ClientLevel currentLevel;

    private LightningWaterChargeVisuals() {
    }

    private record WaterColumn(int x, int z, int bottom, int top, double distance, @Nullable WaterColumn parent) {
    }

    private record BranchStart(Vec3 position, Vec3 heading, long pattern, double revealAt, float strength) {
    }

    private record Arc(Vec3 from, Vec3 bend, Vec3 to, double revealAt, float startStrength, float endStrength) {
        void draw(BufferBuilder buffer, Vec3 camera, double age) {
            float alpha = (float) Mth.clamp(1 - (age - revealAt) / ARC_FADE_TICKS, 0, 1);
            if (age < revealAt || alpha <= 0 || camera.distanceToSqr(bend) > VIEW_RANGE * VIEW_RANGE) {
                return;
            }
            Vec3 start = from.subtract(camera);
            Vec3 elbow = bend.subtract(camera);
            Vec3 end = to.subtract(camera);
            float bendStrength = (startStrength + endStrength) * 0.5F;
            drawArcSegment(buffer, start, elbow, startStrength, bendStrength, alpha);
            drawArcSegment(buffer, elbow, end, bendStrength, endStrength, alpha);
        }
    }

    private static final class Charge {
        private final LightningWaterChargePacket impact;
        private final ClientLevel level;
        private final LightningWaterVolume water;
        private final LongSet reached = new LongOpenHashSet();
        private final LongSet traced = new LongOpenHashSet();
        private final ArrayDeque<WaterColumn> frontier = new ArrayDeque<>();
        private final List<Arc> arcs = new ArrayList<>();
        private final List<BranchStart> branchStarts = new ArrayList<>();
        private int age;

        private Charge(ClientLevel level, LightningWaterChargePacket impact) {
            this.level = level;
            this.impact = impact;
            water = new LightningWaterVolume(level, impact.arenaCenter(), impact.radius(), impact.minimumY(),
                    impact.maximumY());
        }

        private boolean covers(LightningWaterChargePacket other) {
            return age < 6 && impact.equals(other);
        }

        /** Groups vertical runs so deep and shallow water can spread across their surfaces at similar speeds. */
        private void discoverColumn(BlockPos seed, @Nullable WaterColumn parent, Vec3 camera) {
            if (!reached.add(seed.asLong())) {
                return;
            }
            int bottom = extend(seed, Direction.DOWN);
            int top = extend(seed, Direction.UP);
            double distance = parent == null ? 0
                    : parent.distance() + Math.hypot(seed.getX() - parent.x(), seed.getZ() - parent.z());
            var column = new WaterColumn(seed.getX(), seed.getZ(), bottom, top, distance, parent);
            frontier.add(column);
            if (parent == null) {
                addImpactArcs(column);
                return;
            }
            // Pick staggered tips, then trace them back along their water route to form continuous forks.
            if ((distance <= 2 || (hash(column) & 15) == 0)
                    && camera.distanceToSqr(seed.getX() + 0.5, top + 1, seed.getZ() + 0.5)
                            <= (VIEW_RANGE + 4) * (VIEW_RANGE + 4)) {
                trace(column, camera);
            }
        }

        /** Gives the impact a small fork even when the body of water occupies a single block. */
        private void addImpactArcs(WaterColumn column) {
            Vec3 origin = surfacePoint(column);
            if (origin == null) {
                return;
            }
            double rotation = fraction(hash(column)) * 2 * Math.PI;
            for (int branch = 0; branch < 3; branch++) {
                double angle = rotation + branch * 2 * Math.PI / 3;
                Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 bend = origin.add(direction.scale(0.17)).add(-direction.z * 0.075, 0.045, direction.x * 0.075);
                Vec3 end = origin.add(direction.scale(0.40));
                if (isOpen(bend) && isOpen(end)) {
                    arcs.add(new Arc(origin, bend, end, age - 1, 1, 0.75F));
                }
            }
        }

        private int extend(BlockPos seed, Direction direction) {
            var previous = seed.mutable();
            var next = seed.mutable().move(direction);
            while (!reached.contains(next.asLong()) && water.hasWater(next) && water.connects(previous, next, direction)) {
                reached.add(next.asLong());
                previous.set(next);
                next.move(direction);
            }
            return previous.getY();
        }

        private void spread(Vec3 camera, int budget) {
            BlockPos origin = impact.strike();
            if (age < 6 && reached.isEmpty() && water.hasWater(origin)) {
                discoverColumn(origin, null, camera);
            }
            var from = new BlockPos.MutableBlockPos();
            var to = new BlockPos.MutableBlockPos();
            var corner = new BlockPos.MutableBlockPos();
            for (int step = 0; step < budget && !frontier.isEmpty(); step++) {
                WaterColumn column = frontier.removeFirst();
                int firstSide = (int) (hash(column) & 3);
                for (int index = 0; index < HORIZONTAL.length; index++) {
                    int sideIndex = (firstSide + index) % HORIZONTAL.length;
                    Direction side = HORIZONTAL[sideIndex];
                    Direction nextSide = HORIZONTAL[(sideIndex + 1) % HORIZONTAL.length];
                    spreadTo(column, side, null, camera, from, to, corner);
                    spreadTo(column, side, nextSide, camera, from, to, corner);
                }
            }
            // Resolve forks after the surrounding water for this tick has been discovered.
            branchStarts.forEach(this::addSideBranch);
            branchStarts.clear();
        }

        private void spreadTo(WaterColumn column, Direction side, @Nullable Direction diagonal, Vec3 camera,
                BlockPos.MutableBlockPos from, BlockPos.MutableBlockPos to, BlockPos.MutableBlockPos corner) {
            int x = column.x() + side.getStepX();
            int z = column.z() + side.getStepZ();
            if (diagonal != null) {
                x += diagonal.getStepX();
                z += diagonal.getStepZ();
            }
            for (int y = column.top(); y >= column.bottom(); y--) {
                to.set(x, y, z);
                if (reached.contains(to.asLong()) || !water.hasWater(to)) {
                    continue;
                }
                from.set(column.x(), y, column.z());
                // Both paths around the corner must be wet so a diagonal never skips a dry barrier.
                boolean connected = diagonal == null ? water.connects(from, to, side)
                        : connectsVia(from, to, side, diagonal, corner)
                                && connectsVia(from, to, diagonal, side, corner);
                if (connected) {
                    discoverColumn(to, column, camera);
                }
            }
        }

        private boolean connectsVia(BlockPos from, BlockPos to, Direction first, Direction second,
                BlockPos.MutableBlockPos corner) {
            corner.setWithOffset(from, first);
            return water.hasWater(corner) && water.connects(from, corner, first) && water.connects(corner, to, second);
        }

        private void trace(WaterColumn tip, Vec3 camera) {
            double speed = Math.max(4, impact.radius() * 0.15);
            for (WaterColumn column = tip; column.parent() != null; column = column.parent()) {
                if (!traced.add(BlockPos.asLong(column.x(), column.top(), column.z()))) {
                    break;
                }
                Vec3 from = surfacePoint(column.parent());
                Vec3 to = surfacePoint(column);
                if (from == null || to == null || Math.abs(from.y - to.y) > 1.25
                        || camera.distanceToSqr(to) > (VIEW_RANGE + 4) * (VIEW_RANGE + 4)) {
                    continue;
                }
                Vec3 horizontal = to.subtract(from);
                long pattern = hash(column);
                double kink = (fraction(pattern >>> 20) - 0.5) * 0.66;
                Vec3 bend = from.lerp(to, 0.5).add(-horizontal.z * kink, 0.05, horizontal.x * kink);
                double revealAt = Math.max(age - 1, Math.min(23, column.distance() / speed));
                if (isOpen(bend)) {
                    float startStrength = strengthAt(column.parent().distance());
                    float endStrength = strengthAt(column.distance());
                    arcs.add(new Arc(from, bend, to, revealAt, startStrength, endStrength));
                    if (Math.floorMod(pattern >>> 28, 3) == 0) {
                        branchStarts.add(new BranchStart(bend, horizontal, pattern, revealAt,
                                (startStrength + endStrength) * 0.5F));
                    }
                }
            }
        }

        /** Weakens the discharge along its path while keeping the farthest lines faintly visible. */
        private float strengthAt(double distance) {
            double falloffDistance = Mth.clamp(impact.radius() * 0.6, 8, 30);
            return (float) Mth.lerp(Mth.clamp(distance / falloffDistance, 0, 1), 1, 0.24);
        }

        /** Starts a thinner fork at the parent arc's elbow, at an angle independent of the block grid. */
        private void addSideBranch(BranchStart branch) {
            Vec3 origin = branch.position();
            long pattern = branch.pattern();
            double headingAngle = Math.atan2(branch.heading().z, branch.heading().x);
            double angle = 0.55 + fraction(pattern >>> 34) * 0.85;
            double length = (0.65 + fraction(pattern >>> 44) * 0.95) * Math.sqrt(branch.strength());
            double turn = (pattern & 1) == 0 ? angle : -angle;
            for (int attempt = 0; attempt < 2; attempt++) {
                double bearing = headingAngle + turn;
                Vec3 direction = new Vec3(Math.cos(bearing), 0, Math.sin(bearing));
                Vec3 bend = branchPoint(origin.add(direction.scale(length * 0.45))
                        .add(-direction.z * 0.15, 0, direction.x * 0.15));
                Vec3 end = branchPoint(origin.add(direction.scale(length)));
                if (bend != null && end != null && branchPoint(origin.lerp(bend, 0.5)) != null
                        && branchPoint(bend.lerp(end, 0.5)) != null) {
                    arcs.add(new Arc(origin, bend, end, branch.revealAt() + 0.4,
                            branch.strength() * 0.65F, branch.strength() * 0.18F));
                    return;
                }
                turn = -turn;
            }
        }

        @Nullable
        private Vec3 branchPoint(Vec3 reference) {
            int y = Mth.floor(reference.y);
            double surface = LightningRingSurface.findWater(level, reference.x, reference.z,
                    Math.max(impact.minimumY(), y - 1), Math.min(impact.maximumY() - 1, y + 1));
            if (!Double.isFinite(surface) || Math.abs(surface + 0.085 - reference.y) > 0.6
                    || !reached.contains(new BlockPos(reference.x, Math.nextDown(surface), reference.z).asLong())) {
                return null;
            }
            var point = new Vec3(reference.x, surface + 0.085, reference.z);
            return isOpen(point) ? point : null;
        }

        @Nullable
        private Vec3 surfacePoint(WaterColumn column) {
            var cell = new BlockPos(column.x(), column.top(), column.z());
            if (!water.hasWater(cell) || level.getFluidState(cell.above()).is(FluidTags.WATER)) {
                return null;
            }
            double height = level.getFluidState(cell).getHeight(level, cell);
            long jitter = hash(column);
            boolean source = column.parent() == null;
            double x = column.x() + 0.5 + (source ? 0 : (fraction(jitter) - 0.5) * 0.64);
            double z = column.z() + 0.5 + (source ? 0 : (fraction(jitter >>> 10) - 0.5) * 0.64);
            var point = new Vec3(x, column.top() + height + 0.085, z);
            return isOpen(point) ? point : null;
        }

        private boolean isOpen(Vec3 point) {
            BlockPos block = new BlockPos(point);
            if (!level.getChunkSource().hasChunk(block.getX() >> 4, block.getZ() >> 4)) {
                return false;
            }
            for (var box : level.getBlockState(block).getCollisionShape(level, block).toAabbs()) {
                if (box.contains(point.x - block.getX(), point.y - block.getY(), point.z - block.getZ())) {
                    return false;
                }
            }
            return true;
        }

        private long hash(WaterColumn column) {
            return RandomSupport.mixStafford13(BlockPos.asLong(column.x(), column.top(), column.z())
                    + impact.strike().asLong());
        }
    }

    private static double fraction(long bits) {
        return (bits & 255) / 256.0;
    }

    private static void drawArcSegment(BufferBuilder buffer, Vec3 from, Vec3 to,
            float startStrength, float endStrength, float alpha) {
        double x = to.x - from.x;
        double z = to.z - from.z;
        double length = Math.hypot(x, z);
        if (length < 1.0E-6) {
            return;
        }
        Vec3 side = new Vec3(-z / length, 0, x / length);
        ribbon(buffer, from, to, side.scale(0.12), startStrength, endStrength, 0.23F, 0.66F, alpha * 0.40F);
        ribbon(buffer, from, to, side.scale(0.036), startStrength, endStrength, 0.82F, 0.95F, alpha * 0.96F);
    }

    private static void ribbon(BufferBuilder buffer, Vec3 from, Vec3 to, Vec3 side, float startStrength,
            float endStrength, float red, float green, float alpha) {
        Vec3 startWidth = side.scale(startStrength);
        Vec3 endWidth = side.scale(endStrength);
        vertex(buffer, from.add(startWidth), red, green, 1, alpha * startStrength);
        vertex(buffer, from.subtract(startWidth), red, green, 1, alpha * startStrength);
        vertex(buffer, to.subtract(endWidth), red, green, 1, alpha * endStrength);
        vertex(buffer, to.add(endWidth), red, green, 1, alpha * endStrength);
    }

    private static void vertex(BufferBuilder buffer, Vec3 point, float red, float green, float blue, float alpha) {
        buffer.vertex((float) point.x, (float) point.y, (float) point.z).color(red, green, blue, alpha).endVertex();
    }

    public static void receive(LightningWaterChargePacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !packet.dimension().equals(level.dimension().location())) {
            return;
        }
        if (level != currentLevel) {
            charges.clear();
            currentLevel = level;
        }
        if (charges.stream().noneMatch(charge -> charge.covers(packet))) {
            charges.add(new Charge(level, packet));
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.isPaused()) {
            return;
        }
        if (minecraft.level != currentLevel) {
            clear();
            return;
        }
        charges.removeIf(charge -> charge.age >= DURATION_TICKS);
        if (charges.isEmpty()) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        int budget = COLUMNS_PER_TICK / charges.size();
        for (Charge charge : charges) {
            charge.age++;
            charge.spread(camera, budget);
            charge.arcs.removeIf(arc -> charge.age - arc.revealAt() > ARC_FADE_TICKS);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || currentLevel == null
                || currentLevel != Minecraft.getInstance().level
                || charges.stream().noneMatch(charge -> !charge.arcs.isEmpty())) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Charge charge : charges) {
            double age = charge.age + event.getPartialTick();
            for (Arc arc : charge.arcs) {
                arc.draw(buffer, camera, age);
            }
        }
        tesselator.end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        charges.clear();
        currentLevel = null;
    }
}
