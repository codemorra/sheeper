package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.List;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.LightningArenaWind;
import de.codemorra.sheeper.world.LightningTornado;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/** Coordinates special attacks with ordinary movement and recovery. */
final class LightningSpecialAttacks {
    private enum Attack {
        LINE, RING, TORNADO, FAN, ROD
    }

    private final LightningSheeperEntity boss;
    private final LightningTeleportMovement teleports;
    private final LightningLineAttack line;
    private final LightningRingBarrage ring;
    private final LightningTornadoAttack tornado;
    private final LightningFanAttack fan;
    private final LightningRodAttack rod;
    private int restTicks;
    private int lineCooldown;
    private int ringCooldown;
    private int tornadoCooldown;
    private int fanCooldown;
    private int rodCooldown = -1;
    private int rodFailedAtJump = -1;

    LightningSpecialAttacks(LightningSheeperEntity boss, LightningTeleportMovement teleports) {
        this.boss = boss;
        this.teleports = teleports;
        line = new LightningLineAttack(boss, teleports);
        ring = new LightningRingBarrage(boss, teleports);
        tornado = new LightningTornadoAttack(boss, teleports);
        fan = new LightningFanAttack(boss, teleports);
        rod = new LightningRodAttack(boss, teleports);
    }

    /** @return whether a special attack is controlling the boss's position */
    boolean blocksNormalMovement() {
        return line.isFiring() || ring.isLaunching() || tornado.isLaunching() || fan.isActive() || rod.isActive();
    }

    /** Allows defensive reactions once released attack effects no longer control the boss. */
    boolean allowsCombatDefense() {
        return !blocksNormalMovement();
    }

    /** @return whether an attack is charging the boss, including the sustained charge between row salvos */
    boolean hasChargeRetaliation() {
        return line.isFiring() || ring.isCharging() || fan.isChargingOnGround() || rod.isChargingOnGround();
    }

    boolean isHovering() {
        return rod.isHovering() || fan.isActive();
    }

    /** @return whether the boss should move without pursuing a player during its tornado attack */
    boolean movesWithoutTarget() {
        return tornado.isActive();
    }

    boolean isRodQuiet() {
        return rod.isQuiet();
    }

    int rodQuietFadeTicks() {
        return rod.quietFadeTicks();
    }

    LightningArenaWind.Mode windMode() {
        return rod.isActive() ? LightningArenaWind.Mode.INTENSIFIED : LightningArenaWind.Mode.NORMAL;
    }

    /** Stops combat strikes after the rod charges while the storm itself keeps moving. */
    boolean allowsRandomStrikes() {
        return !rod.isActive() || rod.chargeStormTicksRemaining() > 0;
    }

    int intensifiedStormTicksRemaining() {
        return rod.chargeStormTicksRemaining();
    }

    List<LightningTornado> tornadoSnapshots() {
        return tornado.snapshots();
    }

    /** Applies tornado contact effects after their latest positions have been published. */
    void applyTornadoEffects(ServerLevel level, List<ServerPlayer> players) {
        tornado.applyPlayerEffects(level, players);
    }

    /** Advances cooldown and recovery only during combat, and lets an announced teleport finish. */
    void tick(ServerLevel level, List<ServerPlayer> players, boolean fighting, boolean teleportPreparing) {
        if (!fighting) {
            line.clear();
            ring.clear();
            tornado.clear();
            fan.tick(level, List.of());
            rod.clear();
            return;
        }
        boolean lineEnabled = LightningSheeperConfig.LIGHTNING_LINE_ENABLED.get();
        boolean ringEnabled = LightningSheeperConfig.LIGHTNING_RING_ENABLED.get();
        boolean tornadoEnabled = LightningSheeperConfig.LIGHTNING_TORNADO_ENABLED.get();
        boolean fanEnabled = LightningSheeperConfig.LIGHTNING_FAN_ENABLED.get();
        boolean rodEnabled = LightningSheeperConfig.LIGHTNING_ROD_ENABLED.get();
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
        if (!rodEnabled) {
            rod.clear();
        }
        if (rodCooldown < 0) {
            rodCooldown = rodCooldownTicks();
        } else if (!rod.isActive()) {
            rodCooldown = Math.max(0, rodCooldown - 1);
        }
        if (!fan.isActive()) {
            fanCooldown = Math.max(0, fanCooldown - 1);
        }
        if (!tornado.isActive()) {
            tornadoCooldown = Math.max(0, tornadoCooldown - 1);
        }
        if (!line.isFiring()) {
            lineCooldown = Math.max(0, lineCooldown - 1);
        }
        if (!ring.isActive()) {
            ringCooldown = Math.max(0, ringCooldown - 1);
        }
        if (line.isActive()) {
            boolean wasFiring = line.isFiring();
            line.tick(level, players);
            if (wasFiring) {
                if (!line.isFiring()) {
                    resumeCombatMovement(players);
                }
                return;
            }
        }
        if (ring.isActive()) {
            boolean wasLaunching = ring.isLaunching();
            ring.tick(level, players);
            if (wasLaunching && !ring.isLaunching()) {
                resumeCombatMovement(players);
            }
            return;
        }
        if (tornado.isActive()) {
            // Funnels roam independently. Keep all other specials and the recovery timer waiting until they end.
            tornado.tick(level);
            return;
        }
        if (fan.isActive()) {
            fan.tick(level, players);
            return;
        }
        if (rod.isActive()) {
            rod.tick(level, players);
            return;
        }
        restTicks = Math.max(0, restTicks - 1);
        if (restTicks > 0 || teleportPreparing || line.isActive()) {
            return;
        }
        var ready = new ArrayList<Attack>(5);
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
        if (rodEnabled && rodCooldown == 0 && rodFailedAtJump != teleports.jumpAttempts()) {
            ready.add(Attack.ROD);
        }
        if (ready.isEmpty()) {
            return;
        }

        int min = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ATTACK_PAUSE_MIN.get());
        int max = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ATTACK_PAUSE_MAX.get());
        int nextRest = min + boss.getRandom().nextInt(Math.max(0, max - min) + 1);
        while (!ready.isEmpty()) {
            if (startAttack(ready.remove(boss.getRandom().nextInt(ready.size())), level, players)) {
                restTicks = nextRest;
                return;
            }
        }
    }

    /** Attempts a cast without consuming the shared pause or cooldown when its placement fails. */
    private boolean startAttack(Attack attack, ServerLevel level, List<ServerPlayer> players) {
        switch (attack) {
            case RING -> {
                ringCooldown = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_RING_COOLDOWN.get());
                boss.setTarget(null);
                ring.start(level);
                ring.tick(level, players);
            }
            case LINE -> {
                lineCooldown = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_LINE_COOLDOWN.get());
                boss.setTarget(null);
                line.start(level);
            }
            case FAN -> {
                if (!fan.start(level)) {
                    return false;
                }
                fanCooldown = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_FAN_COOLDOWN.get());
            }
            case TORNADO -> {
                tornadoCooldown = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_TORNADO_COOLDOWN.get());
                boss.setTarget(null);
                tornado.start(level, players);
            }
            case ROD -> {
                if (!rod.start(level, players)) {
                    // Let the next ordinary movement attempt run before scanning the arena again.
                    rodFailedAtJump = teleports.jumpAttempts();
                    return false;
                }
                rodCooldown = rodCooldownTicks();
            }
        }
        return true;
    }

    /** Prepares a normal jump immediately while already released effects continue independently. */
    private void resumeCombatMovement(List<ServerPlayer> players) {
        ServerPlayer focus = boss.getMovementFocus(players);
        if (focus != null) {
            teleports.prepareCombatJump(focus);
        }
    }

    private int rodCooldownTicks() {
        return SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ROD_COOLDOWN.get());
    }

    void reset() {
        line.clear();
        ring.clear();
        tornado.clear();
        fan.clear();
        rod.clear();
        restTicks = 0;
        lineCooldown = 0;
        ringCooldown = 0;
        tornadoCooldown = 0;
        fanCooldown = 0;
        rodCooldown = -1;
        rodFailedAtJump = -1;
    }

    /** Persists the timers, but drops transient attacks rather than replaying impacts after a reload. */
    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("RestTicks", restTicks);
        tag.putInt("LineCooldown", lineCooldown);
        tag.putInt("RingCooldown", ringCooldown);
        tag.putInt("TornadoCooldown", tornadoCooldown);
        tag.putInt("FanCooldown", fanCooldown);
        tag.putInt("RodCooldown", rodCooldown);
        return tag;
    }

    void load(CompoundTag tag) {
        reset();
        restTicks = Mth.clamp(tag.getInt("RestTicks"), 0, 600);
        lineCooldown = Mth.clamp(tag.getInt("LineCooldown"), 0, 2400);
        ringCooldown = Mth.clamp(tag.getInt("RingCooldown"), 0, 2400);
        tornadoCooldown = Mth.clamp(tag.getInt("TornadoCooldown"), 0, 2400);
        fanCooldown = Mth.clamp(tag.getInt("FanCooldown"), 0, 2400);
        rodCooldown = tag.contains("RodCooldown", Tag.TAG_INT) ? Mth.clamp(tag.getInt("RodCooldown"), -1, 2400) : -1;
    }
}
