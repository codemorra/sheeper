package de.codemorra.sheeper.client;

import java.util.List;

import com.mojang.blaze3d.vertex.BufferBuilder;
import de.codemorra.sheeper.entity.LightningRingSurface;
import de.codemorra.sheeper.network.LightningRodShelterPacket.GroundCharge;
import de.codemorra.sheeper.world.LightningRodShelter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.phys.Vec3;

/** Draws moving electrical paths across the terrain outside the active shelter spheres. */
final class LightningRodGroundVisuals {
    private static final int SPACING = 5;
    private static final int VIEW_DISTANCE = 52;
    private static final int SEGMENTS = 6;

    private LightningRodGroundVisuals() {
    }

    /** Reuses the shelter pass so the ground effect also renders after OptiFine composites the world. */
    static void draw(BufferBuilder buffer, ClientLevel level, Vec3 camera, GroundCharge charge, double time,
            List<LightningRodShelter> shells) {
        float strength = charge.intensityAt(time);
        if (strength <= 0) {
            return;
        }
        Vec3 center = Vec3.atBottomCenterOf(charge.center());
        double radius = charge.radius();
        double reach = VIEW_DISTANCE + SPACING;
        int minX = Mth.ceil(Math.max(-radius - SPACING, camera.x - center.x - reach) / SPACING);
        int maxX = Mth.floor(Math.min(radius + SPACING, camera.x - center.x + reach) / SPACING);
        int minZ = Mth.ceil(Math.max(-radius - SPACING, camera.z - center.z - reach) / SPACING);
        int maxZ = Mth.floor(Math.min(radius + SPACING, camera.z - center.z + reach) / SPACING);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Vec3 tile = center.add(x * SPACING, 0, z * SPACING);
                if (tile.subtract(camera).horizontalDistanceSqr() <= reach * reach) {
                    long seed = RandomSupport.mixStafford13(BlockPos.asLong(x, 0, z) ^ charge.start());
                    drawPath(buffer, level, camera, charge, center, tile, seed, time - charge.start(), strength, shells);
                }
            }
        }
    }

    /** Each anchored path winds sideways as a pulse travels across its tile. */
    private static void drawPath(BufferBuilder buffer, ClientLevel level, Vec3 camera, GroundCharge charge,
            Vec3 center, Vec3 tile, long seed, double age, float strength, List<LightningRodShelter> shells) {
        double angle = fraction(seed, 0) * Math.PI * 2;
        Vec3 forward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        double phase = fraction(seed, 8) * Math.PI * 2 + age * 0.34;
        double travel = fraction(seed, 16) + age * 0.075;
        double pulse = travel - Math.floor(travel);
        double drift = (pulse - 0.5) * 4;
        float alpha = strength * (float) (0.45 + 0.45 * Math.sin(pulse * Math.PI));
        Vec3 previous = null;
        for (int index = 0; index <= SEGMENTS; index++) {
            double distance = (index - SEGMENTS * 0.5) * 1.2 + drift;
            double bend = Math.sin(phase + index * 1.45) * 0.65 + Math.sin(index * 2.6 + angle) * 0.18;
            Vec3 point = tile.add(forward.scale(distance)).add(side.scale(bend));
            if (point.subtract(center).horizontalDistanceSqr() > charge.radius() * charge.radius()) {
                previous = null;
                continue;
            }
            double floor = LightningRingSurface.findForRod(level, point.x, point.z, center.y, charge.minY(), charge.maxY());
            if (!Double.isFinite(floor)) {
                previous = null;
                continue;
            }
            Vec3 sample = new Vec3(point.x, floor + 0.14, point.z);
            if (previous != null && Math.abs(previous.y - sample.y) <= 1) {
                drawOutsideShelters(buffer, camera, previous, sample, alpha, shells);
            }
            previous = sample;
        }
    }

    /** Clips from both ends so a segment crossing a sphere stops and resumes at its shell. */
    private static void drawOutsideShelters(BufferBuilder buffer, Vec3 camera, Vec3 start, Vec3 end, float alpha,
            List<LightningRodShelter> shells) {
        Vec3 entry = LightningRodShelterVisuals.clipSegment(shells, start, end);
        if (entry != start) {
            LightningWorldArcs.segment(buffer, start.subtract(camera), entry.subtract(camera), 0.045, alpha);
        }
        if (entry != end) {
            Vec3 exit = LightningRodShelterVisuals.clipSegment(shells, end, start);
            if (exit != end) {
                LightningWorldArcs.segment(buffer, exit.subtract(camera), end.subtract(camera), 0.045, alpha);
            }
        }
    }

    private static double fraction(long seed, int shift) {
        return ((seed >>> shift) & 255) / 256.0;
    }
}
