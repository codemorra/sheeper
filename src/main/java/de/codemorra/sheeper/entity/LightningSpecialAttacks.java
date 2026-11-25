package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.List;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.LightningTornado;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/** Coordinates stationary special attacks with the teleport intervals between them. */
final class LightningSpecialAttacks {
    private enum Attack {
        LINE, RING, TORNADO, FAN
    }

    private final LightningSheeperEntity boss;
    private final LightningLineAttack line;
    private final LightningRingBarrage ring;
    private final LightningTornadoAttack tornado;
    private final LightningFanAttack fan;
    private int restTicks;
    private int lineCooldown;
    private int ringCooldown;
    private int tornadoCooldown;
    private int fanCooldown;

    LightningSpecialAttacks(LightningSheeperEntity boss, LightningTeleportMovement teleports) {
        this.boss = boss;
        line = new LightningLineAttack(boss);
        ring = new LightningRingBarrage(boss, teleports);
        tornado = new LightningTornadoAttack(boss);
        fan = new LightningFanAttack(boss);
    }

    boolean isAttacking() {
        return line.isActive() || ring.isActive() || tornado.isActive() || fan.isActive();
    }

    List<LightningTornado> tornadoSnapshots() {
        return tornado.snapshots();
    }

    /** Advances cooldown and recovery only during combat, and lets an announced teleport finish. */
    void tick(ServerLevel level, List<ServerPlayer> players, boolean fighting, boolean teleportPreparing,
            boolean fanOnly) {
        if (!fighting) {
            line.clear();
            ring.clear();
            tornado.clear();
            fan.clear();
            return;
        }
        boolean lineEnabled = !fanOnly && LightningSheeperConfig.LIGHTNING_LINE_ENABLED.get();
        boolean ringEnabled = !fanOnly && LightningSheeperConfig.LIGHTNING_RING_ENABLED.get();
        boolean tornadoEnabled = !fanOnly && LightningSheeperConfig.LIGHTNING_TORNADO_ENABLED.get();
        boolean fanEnabled = LightningSheeperConfig.LIGHTNING_FAN_ENABLED.get();
        if (!lineEnabled) {
            line.clear();
        }
        if (!ringEnabled) {
            ring.clear();
        }
        if (!tornadoEnabled) {
            tornado.clear();
        }
        if (!fanEnabled) {
            fan.clear();
        }
        if (!fan.isActive()) {
            fanCooldown = Math.max(0, fanCooldown - 1);
        }
        if (!tornado.isActive()) {
            tornadoCooldown = Math.max(0, tornadoCooldown - 1);
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
        if (tornado.isActive()) {
            tornado.tick(level, players);
            return;
        }
        if (fan.isActive()) {
            fan.tick(level, players);
            return;
        }
        restTicks = Math.max(0, restTicks - 1);
        if (restTicks > 0 || teleportPreparing) {
            return;
        }
        var ready = new ArrayList<Attack>(4);
        if (lineEnabled && lineCooldown == 0) {
            ready.add(Attack.LINE);
        }
        if (ringEnabled && ringCooldown == 0) {
            ready.add(Attack.RING);
        }
        if (tornadoEnabled && tornadoCooldown == 0) {
            ready.add(Attack.TORNADO);
        }
        if (fanEnabled && fanCooldown == 0) {
            ready.add(Attack.FAN);
        }
        if (ready.isEmpty()) {
            return;
        }

        int min = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ATTACK_PAUSE_MIN.get());
        int max = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ATTACK_PAUSE_MAX.get());
        restTicks = min + boss.getRandom().nextInt(Math.max(0, max - min) + 1);
        switch (ready.get(boss.getRandom().nextInt(ready.size()))) {
            case RING -> {
                ringCooldown = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_RING_COOLDOWN.get());
                boss.setTarget(null);
                ring.start(level);
                ring.tick(level, players);
            }
            case LINE -> {
                lineCooldown = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_LINE_COOLDOWN.get());
                line.start(level);
                line.tick(level, players);
            }
            case FAN -> {
                fanCooldown = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_FAN_COOLDOWN.get());
                fan.start();
                fan.tick(level, players);
            }
            case TORNADO -> {
                tornadoCooldown = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_TORNADO_COOLDOWN.get());
                boss.setTarget(null);
                tornado.start(level, players);
            }
        }
    }

    void reset() {
        line.clear();
        ring.clear();
        tornado.clear();
        fan.clear();
        restTicks = 0;
        lineCooldown = 0;
        ringCooldown = 0;
        tornadoCooldown = 0;
        fanCooldown = 0;
    }

    /** Persists the timers, but drops transient attacks rather than replaying impacts after a reload. */
    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("RestTicks", restTicks);
        tag.putInt("LineCooldown", lineCooldown);
        tag.putInt("RingCooldown", ringCooldown);
        tag.putInt("TornadoCooldown", tornadoCooldown);
        tag.putInt("FanCooldown", fanCooldown);
        return tag;
    }

    void load(CompoundTag tag) {
        reset();
        restTicks = Mth.clamp(tag.getInt("RestTicks"), 0, 600);
        lineCooldown = Mth.clamp(tag.getInt("LineCooldown"), 0, 2400);
        ringCooldown = Mth.clamp(tag.getInt("RingCooldown"), 0, 2400);
        tornadoCooldown = Mth.clamp(tag.getInt("TornadoCooldown"), 0, 2400);
        fanCooldown = Mth.clamp(tag.getInt("FanCooldown"), 0, 2400);
    }
}
