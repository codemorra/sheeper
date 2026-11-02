package de.codemorra.sheeper.client.particle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import de.codemorra.sheeper.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Places vapor relative to the player's head and body orientation. Raw bursts
 * produce short triple sequences; cooked meat supplies individual rear puffs
 * throughout its synchronized active countdown and orange-red rear triples on
 * bursts.
 */
public final class MuttonBurstParticles {
    private static final int PUFF_INTERVAL_TICKS = 2;
    private static final List<Sequence> PENDING = new ArrayList<>();
    private static final Vec3 HEAD_COLOR = new Vec3(0.95D, 0.22D, 0.06D);
    private static final Vec3 REAR_COLOR = new Vec3(0.38D, 0.68D, 0.12D);

    /** Separates source placement and color without changing particle behavior. */
    private enum Emission {
        RAW_BURST,
        COOKED_STREAM,
        COOKED_BURST
    }

    private MuttonBurstParticles() {
    }

    /**
     * Starts three puffs per outlet, separated by two client ticks (about 100 ms).
     * The first is emitted immediately; subsequent puffs follow the current pose.
     *
     * @param player the client-side player whose burst was received
     */
    public static void spawnRaw(Player player) {
        startSequence(player, Emission.RAW_BURST);
    }

    /** Starts three orange-red rear puffs for a synchronized cooked-meat burst. */
    public static void spawnCookedBurst(Player player) {
        startSequence(player, Emission.COOKED_BURST);
    }

    /**
     * Emits the first puff now and queues the remaining two for this burst type.
     */
    private static void startSequence(Player player, Emission emission) {
        Minecraft minecraft = Minecraft.getInstance();
        if (emit(minecraft, player, emission)) {
            PENDING.add(new Sequence(player.getUUID(), minecraft.level, emission));
        }
    }

    /**
     * Emits one enlarged rear puff for an active cooked-meat countdown. Scheduling
     * belongs to the visual cache, so periodic snapshots cannot duplicate puffs.
     */
    public static void emitCooked(Player player) {
        emit(Minecraft.getInstance(), player, Emission.COOKED_STREAM);
    }

    /**
     * Emits the two delayed puffs on the client thread. Sequences cannot cross
     * dimensions or continue after the player disappears from the client world.
     */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Iterator<Sequence> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Sequence sequence = iterator.next();
            if (minecraft.level == null || minecraft.level != sequence.level) {
                iterator.remove();
                continue;
            }
            if (--sequence.delay > 0) {
                continue;
            }

            Player player = minecraft.level.getPlayerByUUID(sequence.playerId);
            if (player == null || !emit(minecraft, player, sequence.emission) || --sequence.remaining == 0) {
                iterator.remove();
            } else {
                sequence.delay = PUFF_INTERVAL_TICKS;
            }
        }
    }

    /** Discards delayed puffs on disconnect before another session can begin. */
    public static void clear() {
        PENDING.clear();
    }

    /**
     * Emits raw puffs from all outlets, or green stream/orange-red burst puffs only
     * from
     * the rear for cooked meat. Particle settings control visibility and size.
     *
     * @return whether the sequence may continue for this visible player
     */
    private static boolean emit(Minecraft minecraft, Player player, Emission emission) {
        if (minecraft.level == null || minecraft.player == null || !player.isAlive()
                || player.isInvisible() || player.isCreative() || player.isSpectator()
                || minecraft.player.distanceToSqr(player) > 32.0D * 32.0D) {
            return false;
        }

        ParticleStatus status = minecraft.options.particles().get();
        if (status == ParticleStatus.MINIMAL) {
            return false;
        }
        boolean raw = emission == Emission.RAW_BURST;
        float scale = (raw ? 1.0F : 1.30F) * (status == ParticleStatus.DECREASED ? 0.8F : 1.0F);

        if (raw) {
            Vec3 headForward = Vec3.directionFromRotation(player.getXRot(), player.yHeadRot);
            Vec3 headRight = Vec3.directionFromRotation(0.0F, player.yHeadRot + 90.0F);
            Vec3 headUp = headRight.cross(headForward).normalize();
            Vec3 headCenter = player.getEyePosition().subtract(headUp.scale(0.10D));
            puff(minecraft, player, headCenter.add(headForward.scale(0.29D)).subtract(headUp.scale(0.06D)),
                    headForward, scale, HEAD_COLOR);
            puff(minecraft, player, headCenter.add(headRight.scale(0.27D)), headRight, scale, HEAD_COLOR);
            puff(minecraft, player, headCenter.subtract(headRight.scale(0.27D)), headRight.scale(-1.0D), scale,
                    HEAD_COLOR);
        }
        Vec3 bodyForward = Vec3.directionFromRotation(0.0F, player.yBodyRot);
        puff(minecraft, player,
                player.position().add(0.0D, player.getBbHeight() * 0.45D, 0.0D).subtract(bodyForward.scale(0.32D)),
                bodyForward.scale(-1.0D), scale,
                emission == Emission.COOKED_BURST ? HEAD_COLOR : REAR_COLOR);
        return true;
    }

    /**
     * Adds spread and inherited motion to one cloud. Brightness varies slightly
     * while preserving the requested outlet or burst color.
     */
    private static void puff(Minecraft minecraft, Player player, Vec3 origin, Vec3 direction,
            float scale, Vec3 color) {
        RandomSource random = player.getRandom();
        Vec3 position = origin.add((random.nextDouble() - 0.5D) * 0.08D,
                (random.nextDouble() - 0.5D) * 0.08D, (random.nextDouble() - 0.5D) * 0.08D);
        Vec3 velocity = direction.scale(0.06D + random.nextDouble() * 0.03D)
                .add(player.getDeltaMovement().scale(0.25D))
                .add((random.nextDouble() - 0.5D) * 0.02D, 0.015D,
                        (random.nextDouble() - 0.5D) * 0.02D);
        Particle particle = minecraft.particleEngine.createParticle(ModParticles.MUTTON_CLOUD.get(),
                position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
        if (particle != null) {
            particle.scale(scale);
            float shade = 0.85F + random.nextFloat() * 0.15F;
            particle.setColor((float) color.x * shade, (float) color.y * shade, (float) color.z * shade);
        }
    }

    /** Retains only the source identity, world, and two outstanding emissions. */
    private static final class Sequence {
        private final UUID playerId;
        private final ClientLevel level;
        private final Emission emission;
        private int delay = PUFF_INTERVAL_TICKS;
        private int remaining = 2;

        private Sequence(UUID playerId, ClientLevel level, Emission emission) {
            this.playerId = playerId;
            this.level = level;
            this.emission = emission;
        }
    }
}
