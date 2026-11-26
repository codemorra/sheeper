package de.codemorra.sheeper.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.ArenaLimits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Enforces arena damage ownership, multiplayer health and empty-arena recovery.
 * Membership is checked at damage time rather than cached from the previous
 * tick.
 */
class ElementalArenaCombat {
    private static final int HEALTH_ENTRY_TICKS = 60;
    private final ElementalSheeperEntity boss;
    private final Predicate<ServerPlayer> canDamage;
    private final Runnable resetEncounter;
    private final DoubleSupplier baseHealth;
    private final DoubleSupplier additionalHealthPercent;
    private final DoubleSupplier graceSeconds;
    private final DoubleSupplier recoveryPercent;
    private final Map<UUID, Integer> healthEntryTicks = new HashMap<>();
    private int highestPlayerCount = 1;
    private boolean encounterActive;
    private int emptyTicks;
    private int regenerationTicks;

    /** Shares encounter rules while keeping each boss's settings and membership independent. */
    ElementalArenaCombat(ElementalSheeperEntity boss, Predicate<ServerPlayer> canDamage, Runnable resetEncounter,
            DoubleSupplier baseHealth, DoubleSupplier additionalHealthPercent, DoubleSupplier graceSeconds,
            DoubleSupplier recoveryPercent) {
        this.boss = boss;
        this.canDamage = canDamage;
        this.resetEncounter = resetEncounter;
        this.baseHealth = baseHealth;
        this.additionalHealthPercent = additionalHealthPercent;
        this.graceSeconds = graceSeconds;
        this.recoveryPercent = recoveryPercent;
    }

    /**
     * Allows attacks from players with arena damage permission, including creative
     * players and attacks whose direct entity belongs to a player. Unattributed
     * environmental damage cannot borrow ownership from the boss's last attacker.
     *
     * @param source incoming damage with its direct and responsible entities
     * @return whether the source can damage the prepared boss
     */
    boolean allowsDamage(DamageSource source) {
        if (!boss.isCombatReady()) {
            return false;
        }
        ServerPlayer player = responsiblePlayer(source.getEntity());
        if (player == null) {
            player = responsiblePlayer(source.getDirectEntity());
        }
        return player != null && canDamage.test(player);
    }

    /**
     * Follows explicit ownership links instead of proximity or combat-history
     * guesses. The visited set also handles malformed cyclic ownership safely.
     *
     * @param entity source or direct entity reported by the damage source
     * @return responsible player, or {@code null} when ownership is unavailable
     */
    @Nullable
    private ServerPlayer responsiblePlayer(@Nullable Entity entity) {
        Set<Entity> visited = new HashSet<>();
        while (entity != null && visited.add(entity)) {
            if (entity instanceof ServerPlayer player) {
                return player;
            }
            if (entity instanceof Projectile projectile) {
                entity = projectile.getOwner();
            } else if (entity instanceof OwnableEntity ownable) {
                entity = ownable.getOwner();
            } else if (entity instanceof PrimedTnt tnt) {
                entity = tnt.getOwner();
            } else if (entity instanceof AreaEffectCloud cloud) {
                entity = cloud.getOwner();
            } else if (entity instanceof EvokerFangs fangs) {
                entity = fangs.getOwner();
            } else if (entity instanceof LightningBolt lightning) {
                entity = lightning.getCause();
            } else {
                return null;
            }
        }
        return null;
    }

    /**
     * Preserves health throughout the grace period, then heals once per second.
     * Returning players immediately stop regeneration at the current health.
     * The caller pauses this timer while AI is disabled or the arena is preparing.
     * Creative damage also starts recovery without adding a combat participant.
     *
     * @param participants currently eligible arena players
     */
    void tick(List<ServerPlayer> participants) {
        updateHealthScaling(participants);
        if (!participants.isEmpty()) {
            encounterActive = true;
            emptyTicks = 0;
            regenerationTicks = 0;
            return;
        }
        if (!encounterActive && boss.getHealth() >= boss.getMaxHealth()) {
            return;
        }
        // Creative attacks can injure the boss before any combat participant enters.
        encounterActive = true;
        int grace = SheeperConfig.secondsToTicks(graceSeconds.getAsDouble());
        emptyTicks = Math.min(emptyTicks + 1, grace);
        if (emptyTicks < grace) {
            return;
        }
        if (regenerationTicks > 0) {
            regenerationTicks--;
        }
        if (regenerationTicks == 0) {
            boss.heal((float) (boss.getMaxHealth()
                    * recoveryPercent.getAsDouble() / 100.0D));
            regenerationTicks = 20;
        }
        if (boss.getHealth() >= boss.getMaxHealth()) {
            // Reset only after full recovery; a brief boundary exit keeps combat progress.
            highestPlayerCount = 1;
            healthEntryTicks.clear();
            boss.refreshConfiguredHealth();
            resetEncounter.run();
            encounterActive = false;
            emptyTicks = 0;
            regenerationTicks = 0;
        }
    }

    /**
     * Counts only simultaneous, continuously present participants. Replacements
     * cannot increase an already reached count; exits never lower it mid-fight.
     */
    private void updateHealthScaling(List<ServerPlayer> participants) {
        Set<UUID> present = new HashSet<>();
        int qualified = 0;
        for (ServerPlayer player : participants) {
            UUID id = player.getUUID();
            present.add(id);
            int ticks = Math.min(HEALTH_ENTRY_TICKS, healthEntryTicks.getOrDefault(id, 0) + 1);
            healthEntryTicks.put(id, ticks);
            if (ticks == HEALTH_ENTRY_TICKS) {
                qualified++;
            }
        }
        healthEntryTicks.keySet().retainAll(present);
        if (qualified > highestPlayerCount) {
            highestPlayerCount = qualified;
            // Apply before other attacks this tick, preserving the health ratio
            // rather than reducing the progress shown by the boss bar.
            boss.refreshConfiguredHealth();
        }
    }

    /**
     * @return solo health plus the encounter's peak-player bonus, capped at
     *         Minecraft's limit
     */
    double getScaledMaxHealth() {
        double bonus = (highestPlayerCount - 1)
                * additionalHealthPercent.getAsDouble() / 100.0D;
        return Math.min(ArenaLimits.MAX_BOSS_HEALTH, baseHealth.getAsDouble() * (1 + bonus));
    }

    /**
     * @return recovery progress, peak player count and entry timers, frozen while
     *         unloaded
     */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("EncounterActive", encounterActive);
        tag.putInt("EmptyTicks", emptyTicks);
        tag.putInt("RegenerationTicks", regenerationTicks);
        tag.putInt("HighestPlayerCount", highestPlayerCount);
        ListTag entries = new ListTag();
        healthEntryTicks.forEach((id, ticks) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Ticks", ticks);
            entries.add(entry);
        });
        tag.put("HealthEntryTicks", entries);
        return tag;
    }

    /**
     * Restores the empty-arena timers. Damaged bosses from older saves also need
     * recovery even when the saved encounter marker is absent.
     *
     * @param tag saved arena combat state
     */
    void load(CompoundTag tag) {
        highestPlayerCount = Math.max(1, tag.getInt("HighestPlayerCount"));
        healthEntryTicks.clear();
        for (Tag value : tag.getList("HealthEntryTicks", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                healthEntryTicks.put(entry.getUUID("Player"), Mth.clamp(entry.getInt("Ticks"), 0, HEALTH_ENTRY_TICKS));
            }
        }
        encounterActive = tag.getBoolean("EncounterActive") || boss.getHealth() < boss.getMaxHealth();
        emptyTicks = Mth.clamp(tag.getInt("EmptyTicks"), 0,
                SheeperConfig.secondsToTicks(graceSeconds.getAsDouble()));
        regenerationTicks = Mth.clamp(tag.getInt("RegenerationTicks"), 0, 20);
    }
}
