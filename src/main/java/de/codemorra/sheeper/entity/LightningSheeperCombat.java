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
 * Alternates teleport-capable rest and stationary attacks, retaining announced
 * strike positions.
 */
final class LightningSheeperCombat {
    private enum Phase {
        REST, STRIKES, FRONT, RING
    }

    /** Only implemented attacks take part in weighted selection. */
    private enum Attack {
        STRIKES, FRONT, RING;

        double weight() {
            return switch (this) {
                case STRIKES -> SheeperConfig.LIGHTNING_STRIKE_WEIGHT.get();
                case FRONT -> SheeperConfig.LIGHTNING_FRONT_WEIGHT.get();
                case RING -> SheeperConfig.LIGHTNING_RING_WEIGHT.get();
            };
        }
    }

    private final LightningSheeperEntity boss;
    private final LightningStrikeAttack strikes;
    private final LightningFrontAttack fronts;
    private final LightningRingAttack rings;
    private final LightningAftershockZones aftershocks;
    private Phase phase = Phase.REST;
    private int phaseRemaining = -1;

    LightningSheeperCombat(LightningSheeperEntity boss, LightningTeleportMovement teleports) {
        this.boss = boss;
        aftershocks = new LightningAftershockZones(boss);
        strikes = new LightningStrikeAttack(boss, aftershocks);
        fronts = new LightningFrontAttack(boss);
        rings = new LightningRingAttack(boss, teleports);
    }

    boolean canMove() {
        return phase == Phase.REST;
    }

    private boolean isAttacking() {
        return phase == Phase.STRIKES || phase == Phase.FRONT || phase == Phase.RING;
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
        if (players.isEmpty()) {
            if (isAttacking()) {
                beginRest();
            }
            boss.setTarget(null);
            return;
        }
        if (phase == Phase.REST) {
            if (phaseRemaining < 0) {
                phaseRemaining = interval(SheeperConfig.LIGHTNING_ATTACK_PAUSE_MIN.get(),
                        SheeperConfig.LIGHTNING_ATTACK_PAUSE_MAX.get());
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
                case RING -> {
                    phase = Phase.RING;
                    rings.start();
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
        } else if (phase == Phase.RING) {
            boss.setTarget(null);
            rings.tick(players);
            if (rings.isFinished()) {
                beginRest();
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
        rings.clear();
        boss.setTarget(null);
        phaseRemaining = interval(SheeperConfig.LIGHTNING_ATTACK_PAUSE_MIN.get(),
                SheeperConfig.LIGHTNING_ATTACK_PAUSE_MAX.get());
        phase = Phase.REST;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Phase", phase.name());
        tag.putInt("PhaseRemaining", phaseRemaining);
        tag.put("StrikeAttack", strikes.save());
        tag.put("FrontAttack", fronts.save());
        tag.put("RingAttack", rings.save());
        tag.put("Aftershocks", aftershocks.save());
        return tag;
    }

    void load(CompoundTag tag) {
        phase = switch (tag.getString("Phase")) {
            case "STRIKES" -> Phase.STRIKES;
            case "FRONT" -> Phase.FRONT;
            case "RING" -> Phase.RING;
            default -> Phase.REST;
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
        if (phase == Phase.RING) {
            rings.load(tag.getCompound("RingAttack"));
        } else {
            rings.clear();
        }
        aftershocks.load(tag.getCompound("Aftershocks"));
        if (phase != Phase.STRIKES) {
            aftershocks.finishPhase();
        }
        boss.setTarget(null);
    }
}
