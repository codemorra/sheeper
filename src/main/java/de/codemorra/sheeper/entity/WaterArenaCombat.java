package de.codemorra.sheeper.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.nbt.CompoundTag;
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
 * Enforces arena damage ownership and the empty-arena grace/regeneration cycle.
 * Membership is checked at damage time rather than cached from the previous
 * tick.
 */
final class WaterArenaCombat {
    private final WaterSheeperEntity boss;
    private boolean encounterActive;
    private int emptyTicks;
    private int regenerationTicks;

    /**
     * @param boss owner whose arena membership and health determine encounter state
     */
    WaterArenaCombat(WaterSheeperEntity boss) {
        this.boss = boss;
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
        return player != null && boss.canPlayerDamage(player);
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
        int grace = SheeperConfig.secondsToTicks(SheeperConfig.WATER_EMPTY_ARENA_GRACE.get());
        emptyTicks = Math.min(emptyTicks + 1, grace);
        if (emptyTicks < grace) {
            return;
        }
        if (regenerationTicks > 0) {
            regenerationTicks--;
        }
        if (regenerationTicks == 0) {
            boss.heal((float) (boss.getMaxHealth()
                    * SheeperConfig.WATER_EMPTY_ARENA_REGENERATION.get() / 100.0D));
            regenerationTicks = 20;
        }
        if (boss.getHealth() >= boss.getMaxHealth()) {
            // Reset only after full recovery; a brief boundary exit keeps combat progress.
            boss.resetArenaEncounter();
            encounterActive = false;
            emptyTicks = 0;
            regenerationTicks = 0;
        }
    }

    /**
     * @return grace and regeneration progress, frozen while the entity is unloaded
     */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("EncounterActive", encounterActive);
        tag.putInt("EmptyTicks", emptyTicks);
        tag.putInt("RegenerationTicks", regenerationTicks);
        return tag;
    }

    /**
     * Restores the empty-arena timers. Damaged bosses from older saves also need
     * recovery even when the saved encounter marker is absent.
     *
     * @param tag saved arena combat state
     */
    void load(CompoundTag tag) {
        encounterActive = tag.getBoolean("EncounterActive") || boss.getHealth() < boss.getMaxHealth();
        emptyTicks = Mth.clamp(tag.getInt("EmptyTicks"), 0,
                SheeperConfig.secondsToTicks(SheeperConfig.WATER_EMPTY_ARENA_GRACE.get()));
        regenerationTicks = Mth.clamp(tag.getInt("RegenerationTicks"), 0, 20);
    }
}
