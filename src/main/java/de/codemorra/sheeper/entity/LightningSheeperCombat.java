package de.codemorra.sheeper.entity;

import java.util.List;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Alternates movement, stationary attacks and recovery, retaining announced
 * strike positions.
 */
final class LightningSheeperCombat {
    private enum Phase {
        IDLE, STRIKES, FRONT, REST
    }

    /** Only implemented attacks take part in weighted selection. */
    private enum Attack {
        STRIKES, FRONT;

        double weight() {
            return switch (this) {
                case STRIKES -> SheeperConfig.LIGHTNING_STRIKE_WEIGHT.get();
                case FRONT -> SheeperConfig.LIGHTNING_FRONT_WEIGHT.get();
            };
        }
    }

    private final LightningSheeperEntity boss;
    private final LightningStrikeAttack strikes;
    private final LightningFrontAttack fronts;
    private final LightningAftershockZones aftershocks;
    private Phase phase = Phase.IDLE;
    private int phaseRemaining = -1;

    LightningSheeperCombat(LightningSheeperEntity boss) {
        this.boss = boss;
        aftershocks = new LightningAftershockZones(boss);
        strikes = new LightningStrikeAttack(boss, aftershocks);
        fronts = new LightningFrontAttack(boss);
    }

    boolean canMove() {
        return phase == Phase.IDLE;
    }

    private boolean isAttacking() {
        return phase == Phase.STRIKES || phase == Phase.FRONT;
    }

    void tick(boolean teleportPreparing) {
        if (boss.isNoAi()) {
            aftershocks.clear();
            if (isAttacking()) {
                beginRest();
            }
            boss.setTarget(null);
            return;
        }
        strikes.tickCooldowns();
        List<ServerPlayer> players = boss.getCombatParticipants();
        aftershocks.tick(players);
        if (phase == Phase.REST) {
            // Recovery also ends without players so the boss can resume idle movement.
            if (--phaseRemaining <= 0) {
                phase = Phase.IDLE;
                phaseRemaining = -1;
            }
            return;
        }
        if (players.isEmpty()) {
            if (isAttacking()) {
                beginRest();
            }
            boss.setTarget(null);
            return;
        }
        if (phase == Phase.IDLE) {
            if (phaseRemaining < 0) {
                phaseRemaining = interval(SheeperConfig.LIGHTNING_ATTACK_INTERVAL_MIN.get(),
                        SheeperConfig.LIGHTNING_ATTACK_INTERVAL_MAX.get());
            }
            if (phaseRemaining > 0) {
                phaseRemaining--;
            }
            if (phaseRemaining > 0 || teleportPreparing) {
                return;
            }
            Attack attack = selectAttack();
            if (attack == null) {
                phaseRemaining = -1;
                return;
            }
            switch (attack) {
                case STRIKES -> {
                    phase = Phase.STRIKES;
                    strikes.start();
                }
                case FRONT -> {
                    phase = Phase.FRONT;
                    fronts.start();
                }
            }
        }
        if (phase == Phase.STRIKES) {
            ServerPlayer focus = strikes.tick(players);
            if (strikes.isFinished()) {
                beginRest();
                focus = null;
            }
            boss.setTarget(focus);
            if (focus != null) {
                boss.lookAt(EntityAnchorArgument.Anchor.EYES, focus.getEyePosition());
                boss.setYHeadRot(boss.getYRot());
            }
        } else if (phase == Phase.FRONT) {
            boss.setTarget(null);
            fronts.tick(players);
            if (fronts.isFinished()) {
                beginRest();
            } else {
                boss.lookAt(EntityAnchorArgument.Anchor.EYES, fronts.getLookPoint());
                boss.setYHeadRot(boss.getYRot());
            }
        }
    }

    @Nullable
    private Attack selectAttack() {
        double total = 0.0D;
        for (Attack attack : Attack.values()) {
            total += attack.weight();
        }
        if (total <= 0.0D) {
            return null;
        }
        double choice = boss.getRandom().nextDouble() * total;
        for (Attack attack : Attack.values()) {
            choice -= attack.weight();
            if (choice < 0.0D) {
                return attack;
            }
        }
        return null;
    }

    private int interval(double minimum, double maximum) {
        int min = SheeperConfig.secondsToTicks(minimum);
        int max = Math.max(min, SheeperConfig.secondsToTicks(maximum));
        return min + boss.getRandom().nextInt(max - min + 1);
    }

    private void beginRest() {
        if (phase == Phase.STRIKES) {
            aftershocks.finishPhase();
        }
        strikes.clear();
        fronts.clear();
        boss.setTarget(null);
        phaseRemaining = interval(SheeperConfig.LIGHTNING_ATTACK_PAUSE_MIN.get(),
                SheeperConfig.LIGHTNING_ATTACK_PAUSE_MAX.get());
        phase = phaseRemaining > 0 ? Phase.REST : Phase.IDLE;
        if (phase == Phase.IDLE) {
            phaseRemaining = -1;
        }
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Phase", phase.name());
        tag.putInt("PhaseRemaining", phaseRemaining);
        tag.put("StrikeAttack", strikes.save());
        tag.put("FrontAttack", fronts.save());
        tag.put("Aftershocks", aftershocks.save());
        return tag;
    }

    void load(CompoundTag tag) {
        phase = switch (tag.getString("Phase")) {
            case "STRIKES" -> Phase.STRIKES;
            case "FRONT" -> Phase.FRONT;
            case "REST" -> Phase.REST;
            default -> Phase.IDLE;
        };
        phaseRemaining = tag.contains("PhaseRemaining", Tag.TAG_INT)
                ? Mth.clamp(tag.getInt("PhaseRemaining"), -1, 12000)
                : -1;
        strikes.load(tag.getCompound("StrikeAttack"));
        if (phase != Phase.STRIKES) {
            strikes.clear();
        }
        if (phase == Phase.FRONT) {
            fronts.load(tag.getCompound("FrontAttack"));
        } else {
            fronts.clear();
        }
        aftershocks.load(tag.getCompound("Aftershocks"));
        if (phase != Phase.STRIKES) {
            aftershocks.finishPhase();
        }
        boss.setTarget(null);
    }
}
