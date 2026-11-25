package de.codemorra.sheeper.entity;

import java.util.List;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/** Coordinates stationary special attacks with the teleport intervals between them. */
final class LightningSpecialAttacks {
    private final LightningSheeperEntity boss;
    private final LightningLineAttack line;
    private final LightningRingBarrage ring;
    private int restTicks;
    private int lineCooldown;
    private int ringCooldown;

    LightningSpecialAttacks(LightningSheeperEntity boss, LightningTeleportMovement teleports) {
        this.boss = boss;
        line = new LightningLineAttack(boss);
        ring = new LightningRingBarrage(boss, teleports);
    }

    boolean isAttacking() {
        return line.isActive() || ring.isActive();
    }

    /** Advances cooldown and recovery only during combat, and lets an announced teleport finish. */
    void tick(ServerLevel level, List<ServerPlayer> players, boolean fighting, boolean teleportPreparing,
            boolean ringOnly) {
        if (!fighting) {
            line.clear();
            ring.clear();
            return;
        }
        boolean lineEnabled = !ringOnly && LightningSheeperConfig.LIGHTNING_LINE_ENABLED.get();
        boolean ringEnabled = LightningSheeperConfig.LIGHTNING_RING_ENABLED.get();
        if (!lineEnabled) {
            line.clear();
        }
        if (!ringEnabled) {
            ring.clear();
        }
        if (!line.isActive()) {
            lineCooldown = Math.max(0, lineCooldown - 1);
        }
        if (!ring.isActive()) {
            ringCooldown = Math.max(0, ringCooldown - 1);
        }
        if (line.isActive()) {
            line.tick(level, players);
            return;
        }
        if (ring.isActive()) {
            ring.tick(level, players);
            return;
        }
        restTicks = Math.max(0, restTicks - 1);
        if (restTicks > 0 || teleportPreparing) {
            return;
        }
        boolean lineReady = lineEnabled && lineCooldown == 0;
        boolean ringReady = ringEnabled && ringCooldown == 0;
        if (!lineReady && !ringReady) {
            return;
        }

        int min = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ATTACK_PAUSE_MIN.get());
        int max = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ATTACK_PAUSE_MAX.get());
        restTicks = min + boss.getRandom().nextInt(Math.max(0, max - min) + 1);
        if (ringReady && (!lineReady || boss.getRandom().nextBoolean())) {
            ringCooldown = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_RING_COOLDOWN.get());
            boss.setTarget(null);
            ring.start(level);
            ring.tick(level, players);
        } else {
            lineCooldown = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_LINE_COOLDOWN.get());
            line.start(level);
            line.tick(level, players);
        }
    }

    void reset() {
        line.clear();
        ring.clear();
        restTicks = 0;
        lineCooldown = 0;
        ringCooldown = 0;
    }

    /** Persists the timers, but drops unfinished waves and rows rather than replaying impacts after a reload. */
    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("RestTicks", restTicks);
        tag.putInt("LineCooldown", lineCooldown);
        tag.putInt("RingCooldown", ringCooldown);
        return tag;
    }

    void load(CompoundTag tag) {
        reset();
        restTicks = Mth.clamp(tag.getInt("RestTicks"), 0, 600);
        lineCooldown = Mth.clamp(tag.getInt("LineCooldown"), 0, 2400);
        ringCooldown = Mth.clamp(tag.getInt("RingCooldown"), 0, 2400);
    }
}
