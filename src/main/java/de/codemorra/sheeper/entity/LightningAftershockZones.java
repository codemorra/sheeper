package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.math.Vector3f;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.phys.Vec3;

/**
 * Residual ground hazards that outlive the attack and share one damage cooldown
 * per player.
 */
final class LightningAftershockZones {
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.0F);

    private static final class Zone {
        private final Vec3 pos;
        private final double radius;
        private final double height;
        private final float damage;
        private final int interval;
        private int remaining;

        Zone(Vec3 pos, double radius, double height, float damage, int interval, int remaining) {
            this.pos = pos;
            this.radius = radius;
            this.height = height;
            this.damage = damage;
            this.interval = interval;
            this.remaining = remaining;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("X", pos.x);
            tag.putDouble("Y", pos.y);
            tag.putDouble("Z", pos.z);
            tag.putDouble("Radius", radius);
            tag.putDouble("Height", height);
            tag.putFloat("Damage", damage);
            tag.putInt("Interval", interval);
            tag.putInt("Remaining", remaining);
            return tag;
        }
    }

    private final LightningSheeperEntity boss;
    private final List<Zone> zones = new ArrayList<>();
    private final Map<UUID, Integer> damageCooldowns = new HashMap<>();
    private int effectTicks;

    LightningAftershockZones(LightningSheeperEntity boss) {
        this.boss = boss;
    }

    void add(Vec3 pos) {
        // Negative remaining time keeps this zone active until its storm phase ends.
        Zone zone = new Zone(pos, SheeperConfig.LIGHTNING_AFTERSHOCK_RADIUS.get(),
                SheeperConfig.LIGHTNING_AFTERSHOCK_HEIGHT.get(),
                SheeperConfig.LIGHTNING_AFTERSHOCK_DAMAGE.get().floatValue(),
                SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_AFTERSHOCK_INTERVAL.get()), -1);
        zones.add(zone);
        show((ServerLevel) boss.level, zone);
    }

    void finishPhase() {
        int tail = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_AFTERSHOCK_AFTER.get());
        for (Zone zone : zones) {
            if (zone.remaining < 0) {
                zone.remaining = tail;
            }
        }
        zones.removeIf(zone -> zone.remaining == 0);
    }

    void tick(List<ServerPlayer> players) {
        ServerLevel level = (ServerLevel) boss.level;
        effectTicks++;
        var active = zones.iterator();
        while (active.hasNext()) {
            Zone zone = active.next();
            if (zone.remaining >= 0 && --zone.remaining <= 0) {
                active.remove();
                continue;
            }
            if (!level.getChunkSource().hasChunk(Mth.floor(zone.pos.x) >> 4, Mth.floor(zone.pos.z) >> 4)) {
                continue;
            }
            if (effectTicks % 4 == 0) {
                show(level, zone);
            }
            float volume = SheeperConfig.LIGHTNING_AFTERSHOCK_VOLUME.get().floatValue();
            if (volume > 0.0F && effectTicks % 20 == 0) {
                level.playSound(null, zone.pos.x, zone.pos.y, zone.pos.z, SoundEvents.REDSTONE_TORCH_BURNOUT,
                        SoundSource.HOSTILE, volume, 1.3F + boss.getRandom().nextFloat() * 0.2F);
            }
        }
        damageCooldowns.replaceAll((id, ticks) -> ticks - 1);
        damageCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0
                || players.stream().noneMatch(player -> player.getUUID().equals(entry.getKey())));
        if (zones.isEmpty()) {
            damageCooldowns.clear();
            return;
        }
        for (ServerPlayer player : players) {
            if (damageCooldowns.containsKey(player.getUUID())) {
                continue;
            }
            Zone hit = null;
            for (Zone zone : zones) {
                if (zone.damage > 0 && (hit == null || zone.damage > hit.damage)
                        && level.getChunkSource().hasChunk(Mth.floor(zone.pos.x) >> 4, Mth.floor(zone.pos.z) >> 4)
                        && LightningAreaDamage.contains(boss, player, zone.pos, zone.radius, zone.height)) {
                    hit = zone;
                }
            }
            if (hit != null) {
                // Residual damage must not add attacker-relative knockback on every pulse.
                Vec3 motion = player.getDeltaMovement();
                if (player.hurt(new EntityDamageSource("lightningBolt", boss), hit.damage)) {
                    player.setDeltaMovement(motion);
                }
                damageCooldowns.put(player.getUUID(), hit.interval);
            }
        }
    }

    private void show(ServerLevel level, Zone zone) {
        for (int index = 0; index < 24; index++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = Math.sqrt(boss.getRandom().nextDouble()) * zone.radius;
            double height = boss.getRandom().nextDouble() * Math.min(1.0D, zone.height);
            level.sendParticles(index % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : GLOW,
                    zone.pos.x + Math.cos(angle) * radius, zone.pos.y + height,
                    zone.pos.z + Math.sin(angle) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    void clear() {
        zones.clear();
        damageCooldowns.clear();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        zones.forEach(zone -> entries.add(zone.save()));
        tag.put("Zones", entries);
        ListTag cooldowns = new ListTag();
        damageCooldowns.forEach((id, ticks) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Remaining", ticks);
            cooldowns.add(entry);
        });
        tag.put("Cooldowns", cooldowns);
        return tag;
    }

    void load(CompoundTag tag) {
        clear();
        for (Tag value : tag.getList("Zones", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            Vec3 pos = new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z"));
            double radius = entry.getDouble("Radius");
            double height = entry.getDouble("Height");
            float damage = entry.getFloat("Damage");
            if (Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z)
                    && Double.isFinite(radius) && radius > 0 && Double.isFinite(height) && height > 0
                    && Float.isFinite(damage) && (entry.getInt("Remaining") == -1 || entry.getInt("Remaining") > 0)) {
                zones.add(new Zone(pos, Math.min(radius, 8.0D), Math.min(height, 8.0D), Mth.clamp(damage, 0, 40),
                        Mth.clamp(entry.getInt("Interval"), 1, 1200),
                        entry.getInt("Remaining") == -1 ? -1 : Mth.clamp(entry.getInt("Remaining"), 1, 2400)));
            }
        }
        for (Tag value : tag.getList("Cooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player") && entry.getInt("Remaining") > 0) {
                damageCooldowns.put(entry.getUUID("Player"), Mth.clamp(entry.getInt("Remaining"), 1, 1200));
            }
        }
    }
}
