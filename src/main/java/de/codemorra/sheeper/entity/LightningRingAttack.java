package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.particle.LightningRingOptions;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rapid safe teleports, visible charging and repeated ground-following rings.
 */
final class LightningRingAttack {
    private enum Stage {
        TELEPORTS, CHARGE, WAVES, DONE
    }

    private static final class Wave {
        private final Set<UUID> hitPlayers = new HashSet<>();
        private double radius;
        private int age;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("Radius", radius);
            tag.putInt("Age", age);
            ListTag hits = new ListTag();
            for (UUID id : hitPlayers) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Player", id);
                hits.add(entry);
            }
            tag.put("HitPlayers", hits);
            return tag;
        }
    }

    private final LightningSheeperEntity boss;
    private final LightningTeleportMovement teleports;
    private final List<Wave> waves = new ArrayList<>();
    private Stage stage = Stage.DONE;
    private int teleportsRemaining;
    private int teleportDelay;
    private int teleportInterval;
    private int chargeDuration;
    private int chargeRemaining;
    private int wavesRemaining;
    private int releaseDelay;
    private int releaseInterval;
    private Vec3 origin = Vec3.ZERO;
    private Vec3 arenaCenter = Vec3.ZERO;
    private double arenaRadius;
    private double maxRadius;
    private int minY;
    private int maxY;
    private double speed;
    private double width;
    private double hitHeight;
    private float damage;
    private float particleSpacing;

    LightningRingAttack(LightningSheeperEntity boss, LightningTeleportMovement teleports) {
        this.boss = boss;
        this.teleports = teleports;
    }

    void start() {
        clear();
        stage = Stage.TELEPORTS;
        teleportsRemaining = count(SheeperConfig.LIGHTNING_RING_TELEPORT_COUNT_MIN.get(),
                SheeperConfig.LIGHTNING_RING_TELEPORT_COUNT_MAX.get());
        teleportInterval = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_RING_TELEPORT_INTERVAL.get());
        chargeDuration = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_RING_CHARGE.get());
        chargeRemaining = chargeDuration;
        wavesRemaining = count(SheeperConfig.LIGHTNING_RING_COUNT_MIN.get(),
                SheeperConfig.LIGHTNING_RING_COUNT_MAX.get());
        releaseInterval = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_RING_INTERVAL.get());
        speed = SheeperConfig.LIGHTNING_RING_SPEED.get();
        width = SheeperConfig.LIGHTNING_RING_WIDTH.get();
        hitHeight = SheeperConfig.LIGHTNING_RING_HIT_HEIGHT.get();
        damage = SheeperConfig.LIGHTNING_RING_DAMAGE.get().floatValue();
        particleSpacing = SheeperConfig.LIGHTNING_RING_PARTICLE_SPACING.get().floatValue();
    }

    private int count(int min, int max) {
        return min + boss.getRandom().nextInt(Math.max(min, max) - min + 1);
    }

    boolean isFinished() {
        return stage == Stage.DONE;
    }

    void tick(List<ServerPlayer> players) {
        if (stage == Stage.TELEPORTS) {
            if (teleportsRemaining > 0 && --teleportDelay <= 0) {
                teleports.teleportForAttack();
                teleportsRemaining--;
                teleportDelay = teleportInterval;
            }
            if (teleportsRemaining == 0) {
                origin = boss.position();
                arenaCenter = Vec3.atBottomCenterOf(boss.getArenaCenter());
                arenaRadius = boss.getCombatRadius();
                maxRadius = arenaRadius + Math.sqrt(origin.subtract(arenaCenter).horizontalDistanceSqr());
                double height = SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE.get();
                minY = Math.max(boss.level.getMinBuildHeight(), Mth.ceil(arenaCenter.y - height));
                maxY = Math.min(boss.level.getMaxBuildHeight() - 1, Mth.floor(arenaCenter.y + height));
                stage = Stage.CHARGE;
            }
            return;
        }
        if (stage == Stage.CHARGE) {
            showCharge();
            if (--chargeRemaining <= 0) {
                stage = Stage.WAVES;
                tickWaves(players);
            }
        } else if (stage == Stage.WAVES) {
            tickWaves(players);
        }
    }

    private void showCharge() {
        if (chargeRemaining % 2 == 0) {
            double progress = 1.0D - (double) chargeRemaining / chargeDuration;
            ServerLevel level = (ServerLevel) boss.level;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5D,
                    boss.getZ(), 12 + (int) (progress * 24), boss.getBbWidth() * 0.6D,
                    boss.getBbHeight() * 0.4D, boss.getBbWidth() * 0.6D, 0.04D + progress * 0.08D);
            level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getEyeY(), boss.getZ(),
                    4, 0.3D, 0.3D, 0.3D, 0.015D);
        }
    }

    private void tickWaves(List<ServerPlayer> players) {
        ServerLevel level = (ServerLevel) boss.level;
        var active = waves.iterator();
        while (active.hasNext()) {
            Wave wave = active.next();
            double previous = wave.radius;
            wave.radius = Math.min(maxRadius + width * 0.5D, wave.radius + speed / 20.0D);
            wave.age++;
            hurtPlayers(wave, previous, players);
            if (wave.age % 2 == 0) {
                showWave(level, wave);
            }
            if (wave.radius >= maxRadius + width * 0.5D) {
                active.remove();
            }
        }
        if (wavesRemaining > 0 && --releaseDelay <= 0) {
            Wave wave = new Wave();
            waves.add(wave);
            showWave(level, wave);
            float volume = SheeperConfig.LIGHTNING_STRIKE_IMPACT_VOLUME.get().floatValue();
            if (volume > 0) {
                level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                        SoundSource.HOSTILE, volume, 1.4F);
            }
            wavesRemaining--;
            releaseDelay = releaseInterval;
        }
        if (wavesRemaining == 0 && waves.isEmpty()) {
            stage = Stage.DONE;
        }
    }

    private void hurtPlayers(Wave wave, double previous, List<ServerPlayer> players) {
        if (damage <= 0) {
            return;
        }
        for (ServerPlayer player : players) {
            if (wave.hitPlayers.contains(player.getUUID())) {
                continue;
            }
            AABB bounds = player.getBoundingBox();
            double near = Math.hypot(Mth.clamp(origin.x, bounds.minX, bounds.maxX) - origin.x,
                    Mth.clamp(origin.z, bounds.minZ, bounds.maxZ) - origin.z);
            double far = Math.hypot(Math.max(Math.abs(bounds.minX - origin.x), Math.abs(bounds.maxX - origin.x)),
                    Math.max(Math.abs(bounds.minZ - origin.z), Math.abs(bounds.maxZ - origin.z)));
            // Sweep the whole distance travelled this tick, so faster waves cannot skip
            // players.
            if (near > wave.radius + width * 0.5D || far < previous - width * 0.5D) {
                continue;
            }
            double ground = LightningRingSurface.find(boss.level, player.getX(), player.getZ(), origin.y, minY, maxY);
            if (!Double.isFinite(ground) || bounds.minY < ground - 0.1D || bounds.minY >= ground + hitHeight) {
                continue;
            }
            Vec3 motion = player.getDeltaMovement();
            if (player.hurt(new EntityDamageSource("lightningBolt", boss), damage)) {
                wave.hitPlayers.add(player.getUUID());
                player.setDeltaMovement(motion);
            }
        }
    }

    private void showWave(ServerLevel level, Wave wave) {
        LightningRingOptions options = new LightningRingOptions((float) wave.radius, (float) width, particleSpacing,
                minY, maxY, arenaCenter.x, arenaCenter.z, (float) arenaRadius);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(origin) <= (maxRadius + 32) * (maxRadius + 32)) {
                // The center can be far away even while the expanding edge is next to this
                // player.
                level.sendParticles(player, options, true, origin.x, origin.y, origin.z, 0, 0, 0, 0, 0);
            }
        }
    }

    void clear() {
        waves.clear();
        stage = Stage.DONE;
        teleportDelay = 0;
        releaseDelay = 0;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Stage", stage.name());
        tag.putInt("TeleportsRemaining", teleportsRemaining);
        tag.putInt("TeleportDelay", teleportDelay);
        tag.putInt("TeleportInterval", teleportInterval);
        tag.putInt("ChargeDuration", chargeDuration);
        tag.putInt("ChargeRemaining", chargeRemaining);
        tag.putInt("WavesRemaining", wavesRemaining);
        tag.putInt("ReleaseDelay", releaseDelay);
        tag.putInt("ReleaseInterval", releaseInterval);
        tag.putDouble("X", origin.x);
        tag.putDouble("Y", origin.y);
        tag.putDouble("Z", origin.z);
        tag.putDouble("ArenaX", arenaCenter.x);
        tag.putDouble("ArenaY", arenaCenter.y);
        tag.putDouble("ArenaZ", arenaCenter.z);
        tag.putDouble("ArenaRadius", arenaRadius);
        tag.putDouble("MaxRadius", maxRadius);
        tag.putInt("MinY", minY);
        tag.putInt("MaxY", maxY);
        tag.putDouble("Speed", speed);
        tag.putDouble("Width", width);
        tag.putDouble("HitHeight", hitHeight);
        tag.putFloat("Damage", damage);
        tag.putFloat("ParticleSpacing", particleSpacing);
        ListTag active = new ListTag();
        waves.forEach(wave -> active.add(wave.save()));
        tag.put("Waves", active);
        return tag;
    }

    private double bounded(CompoundTag tag, String key, double fallback, double min, double max) {
        double value = tag.getDouble(key);
        return Double.isFinite(value) ? Mth.clamp(value, min, max) : fallback;
    }

    void load(CompoundTag tag) {
        clear();
        stage = switch (tag.getString("Stage")) {
            case "TELEPORTS" -> Stage.TELEPORTS;
            case "CHARGE" -> Stage.CHARGE;
            case "WAVES" -> Stage.WAVES;
            default -> Stage.DONE;
        };
        teleportsRemaining = Mth.clamp(tag.getInt("TeleportsRemaining"), 0, 32);
        teleportDelay = Mth.clamp(tag.getInt("TeleportDelay"), 0, 200);
        teleportInterval = Mth.clamp(tag.getInt("TeleportInterval"), 1, 200);
        chargeDuration = Mth.clamp(tag.getInt("ChargeDuration"), 1, 1200);
        chargeRemaining = Mth.clamp(tag.getInt("ChargeRemaining"), 0, chargeDuration);
        wavesRemaining = Mth.clamp(tag.getInt("WavesRemaining"), 0, 16);
        releaseDelay = Mth.clamp(tag.getInt("ReleaseDelay"), 0, 200);
        releaseInterval = Mth.clamp(tag.getInt("ReleaseInterval"), 1, 200);
        origin = new Vec3(bounded(tag, "X", boss.getX(), -30000000, 30000000),
                bounded(tag, "Y", boss.getY(), boss.level.getMinBuildHeight(), boss.level.getMaxBuildHeight()),
                bounded(tag, "Z", boss.getZ(), -30000000, 30000000));
        arenaCenter = new Vec3(bounded(tag, "ArenaX", boss.getArenaCenter().getX(), -30000000, 30000000),
                bounded(tag, "ArenaY", boss.getArenaCenter().getY(), boss.level.getMinBuildHeight(),
                        boss.level.getMaxBuildHeight()),
                bounded(tag, "ArenaZ", boss.getArenaCenter().getZ(), -30000000, 30000000));
        arenaRadius = bounded(tag, "ArenaRadius", 50, 1, 128);
        maxRadius = bounded(tag, "MaxRadius", 50, 1, 256);
        minY = Mth.clamp(tag.getInt("MinY"), boss.level.getMinBuildHeight(), boss.level.getMaxBuildHeight() - 1);
        maxY = Mth.clamp(tag.getInt("MaxY"), minY, boss.level.getMaxBuildHeight() - 1);
        speed = bounded(tag, "Speed", 5, 0.5D, 30);
        width = bounded(tag, "Width", 0.75D, 0.1D, 4);
        hitHeight = bounded(tag, "HitHeight", 0.35D, 0.05D, 2);
        damage = (float) bounded(tag, "Damage", 2, 0, 1024);
        particleSpacing = (float) bounded(tag, "ParticleSpacing", 0.5D, 0.1D, 1);
        for (Tag value : tag.getList("Waves", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (waves.size() >= 16 || !Double.isFinite(entry.getDouble("Radius"))) {
                continue;
            }
            Wave wave = new Wave();
            wave.radius = Mth.clamp(entry.getDouble("Radius"), 0, maxRadius + width * 0.5D);
            wave.age = Mth.clamp(entry.getInt("Age"), 0, 12000);
            for (Tag hit : entry.getList("HitPlayers", Tag.TAG_COMPOUND)) {
                CompoundTag player = (CompoundTag) hit;
                if (player.hasUUID("Player")) {
                    wave.hitPlayers.add(player.getUUID("Player"));
                }
            }
            waves.add(wave);
        }
    }
}
