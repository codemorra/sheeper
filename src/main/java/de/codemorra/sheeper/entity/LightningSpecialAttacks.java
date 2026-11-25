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
    private int restTicks;
    private int lineCooldown;

    LightningSpecialAttacks(LightningSheeperEntity boss) {
        this.boss = boss;
        line = new LightningLineAttack(boss);
    }

    boolean isAttacking() {
        return line.isActive();
    }

    /** Advances cooldown and recovery only during combat, and lets an announced teleport finish. */
    void tick(ServerLevel level, List<ServerPlayer> players, boolean fighting, boolean teleportPreparing) {
        boolean lineEnabled = LightningSheeperConfig.LIGHTNING_LINE_ENABLED.get();
        if (!fighting || !lineEnabled) {
            line.clear();
            return;
        }
        if (line.isActive()) {
            line.tick(level, players);
            return;
        }
        restTicks = Math.max(0, restTicks - 1);
        lineCooldown = Math.max(0, lineCooldown - 1);
        if (restTicks > 0 || lineCooldown > 0 || teleportPreparing) {
            return;
        }
        int min = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ATTACK_PAUSE_MIN.get());
        int max = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_ATTACK_PAUSE_MAX.get());
        restTicks = min + boss.getRandom().nextInt(Math.max(0, max - min) + 1);
        lineCooldown = SheeperConfig.secondsToTicks(LightningSheeperConfig.LIGHTNING_LINE_COOLDOWN.get());
        line.start(level);
        line.tick(level, players);
    }

    void reset() {
        line.clear();
        restTicks = 0;
        lineCooldown = 0;
    }

    /** Persists the timers, but drops any unfinished row rather than replaying impacts after a reload. */
    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("RestTicks", restTicks);
        tag.putInt("LineCooldown", lineCooldown);
        return tag;
    }

    void load(CompoundTag tag) {
        reset();
        restTicks = Mth.clamp(tag.getInt("RestTicks"), 0, 600);
        lineCooldown = Mth.clamp(tag.getInt("LineCooldown"), 0, 2400);
    }
}
