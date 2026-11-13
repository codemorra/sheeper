package de.codemorra.sheeper.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Timed storm phase with independent player/arena centers and two immediately
 * expanding rounds.
 */
final class LightningStrikeAttack {
    private static final class Strike {
        private final Vec3 pos;
        private final double radius;
        private final double height;
        private final float damage;
        private final boolean primary;
        private final int warningDuration;
        private int remaining;

        Strike(Vec3 pos, double radius, double height, float damage, boolean primary, int warningDuration,
                int remaining) {
            this.pos = pos;
            this.radius = radius;
            this.height = height;
            this.damage = damage;
            this.primary = primary;
            this.warningDuration = warningDuration;
            this.remaining = remaining;
        }

        CompoundTag save() {
            CompoundTag tag = positionTag(pos);
            tag.putDouble("Radius", radius);
            tag.putDouble("Height", height);
            tag.putFloat("Damage", damage);
            tag.putBoolean("Primary", primary);
            tag.putInt("WarningDuration", warningDuration);
            tag.putInt("Remaining", remaining);
            return tag;
        }
    }

    private static final class Followup {
        private final Vec3 center;
        private final int innerCount;
        private final int outerCount;
        private final double innerMin;
        private final double innerMax;
        private final double outerMin;
        private final double outerMax;
        private final int warning;
        private int round;
        private int remaining;

        Followup(Vec3 center, int innerCount, int outerCount, double innerMin, double innerMax,
                double outerMin, double outerMax, int warning, int round, int remaining) {
            this.center = center;
            this.innerCount = innerCount;
            this.outerCount = outerCount;
            this.innerMin = innerMin;
            this.innerMax = innerMax;
            this.outerMin = outerMin;
            this.outerMax = outerMax;
            this.warning = warning;
            this.round = round;
            this.remaining = remaining;
        }

        CompoundTag save() {
            CompoundTag tag = positionTag(center);
            tag.putInt("InnerCount", innerCount);
            tag.putInt("OuterCount", outerCount);
            tag.putDouble("InnerMin", innerMin);
            tag.putDouble("InnerMax", innerMax);
            tag.putDouble("OuterMin", outerMin);
            tag.putDouble("OuterMax", outerMax);
            tag.putInt("Warning", warning);
            tag.putInt("Round", round);
            tag.putInt("Remaining", remaining);
            return tag;
        }
    }

    private final LightningSheeperEntity boss;
    private final LightningAftershockZones aftershocks;
    private final List<Strike> strikes = new ArrayList<>();
    private final List<Followup> followups = new ArrayList<>();
    private final Map<UUID, Integer> playerDelays = new HashMap<>();
    private final Map<UUID, Integer> playerCooldowns = new HashMap<>();
    private int remaining;
    private int arenaDelay;
    @Nullable
    private UUID focusId;

    LightningStrikeAttack(LightningSheeperEntity boss, LightningAftershockZones aftershocks) {
        this.boss = boss;
        this.aftershocks = aftershocks;
    }

    void start() {
        clear();
        int min = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_STRIKE_DURATION_MIN.get());
        int max = Math.max(min, SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_STRIKE_DURATION_MAX.get()));
        remaining = min + boss.getRandom().nextInt(max - min + 1);
        arenaDelay = initialDelay();
    }

    /**
     * Advances even between phases so leaving the arena cannot bypass or freeze a
     * player's reservation.
     */
    void tickCooldowns() {
        playerCooldowns.replaceAll((id, ticks) -> ticks - 1);
        playerCooldowns.values().removeIf(ticks -> ticks <= 0);
    }

    boolean isFinished() {
        return remaining <= 0;
    }

    private int initialDelay() {
        return 1 + boss.getRandom()
                .nextInt(SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_STRIKE_INTERVAL_MIN.get()));
    }

    private int nextDelay() {
        int min = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_STRIKE_INTERVAL_MIN.get());
        int max = Math.max(min, SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_STRIKE_INTERVAL_MAX.get()));
        return min + boss.getRandom().nextInt(max - min + 1);
    }

    @Nullable
    ServerPlayer tick(List<ServerPlayer> players) {
        ServerLevel level = (ServerLevel) boss.level;
        var pending = strikes.iterator();
        while (pending.hasNext()) {
            Strike strike = pending.next();
            if (!isValidPosition(level, strike.pos)) {
                pending.remove();
            } else if (--strike.remaining <= 0) {
                impact(level, strike, players);
                pending.remove();
            } else if (strike.remaining % 4 == 0) {
                showWarning(level, strike);
            }
        }
        // Announce waves after advancing existing warnings, preserving the full warning
        // duration.
        var waves = followups.iterator();
        while (waves.hasNext()) {
            Followup wave = waves.next();
            if (!isValidPosition(level, wave.center)) {
                waves.remove();
                continue;
            }
            if (--wave.remaining <= 0) {
                int count = wave.round == 0 ? wave.innerCount : wave.outerCount;
                for (int index = 0; index < count; index++) {
                    Vec3 pos = randomFollowupPosition(level, wave);
                    if (pos != null) {
                        announce(level, pos, false, wave.warning);
                    }
                }
                if (wave.round == 1) {
                    waves.remove();
                } else {
                    wave.round = 1;
                    wave.remaining = wave.warning;
                }
            }
        }
        int warning = SheeperConfig.secondsToTicks(SheeperConfig.LIGHTNING_STRIKE_WARNING.get());
        // A newly announced center needs time for all three fully warned rounds.
        if (remaining > warning * 3) {
            playerDelays.keySet().removeIf(id -> players.stream().noneMatch(player -> player.getUUID().equals(id)));
            for (ServerPlayer player : players) {
                if (playerCooldowns.containsKey(player.getUUID())) {
                    continue;
                }
                int delay = playerDelays.computeIfAbsent(player.getUUID(), id -> initialDelay()) - 1;
                if (delay <= 0) {
                    int x = Mth.floor(player.getX());
                    int z = Mth.floor(player.getZ());
                    if (level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                        Double surface = boss.findArenaSurface(level, x, z, Mth.floor(player.getY()));
                        if (surface != null && announceCenter(level,
                                new Vec3(player.getX(), surface, player.getZ()), warning)) {
                            focusId = player.getUUID();
                            // All rounds use this captured warning time, so the last impact occurs
                            // no later than three warning periods after the initial announcement.
                            playerCooldowns.put(player.getUUID(), warning * 3
                                    + SheeperConfig
                                            .secondsToTicks(SheeperConfig.LIGHTNING_STRIKE_PLAYER_COOLDOWN.get()));
                        }
                    }
                    delay = nextDelay();
                }
                playerDelays.put(player.getUUID(), delay);
            }
            if (--arenaDelay <= 0) {
                for (int index = 0; index < SheeperConfig.LIGHTNING_STRIKE_ARENA_COUNT.get(); index++) {
                    Vec3 pos = randomArenaPosition(level);
                    if (pos != null) {
                        announceCenter(level, pos, warning);
                    }
                }
                arenaDelay = nextDelay();
            }
        }
        remaining--;
        return players.stream().filter(player -> player.getUUID().equals(focusId)).findFirst().orElse(null);
    }

    private void announce(ServerLevel level, Vec3 pos, boolean primary, int warning) {
        Strike strike = new Strike(pos, SheeperConfig.LIGHTNING_STRIKE_RADIUS.get(),
                SheeperConfig.LIGHTNING_STRIKE_HEIGHT.get(), SheeperConfig.LIGHTNING_STRIKE_DAMAGE.get().floatValue(),
                primary, warning, warning);
        strikes.add(strike);
        showWarning(level, strike);
    }

    private boolean announceCenter(ServerLevel level, Vec3 pos, int warning) {
        if (!isValidPosition(level, pos) || strikes.stream().anyMatch(strike -> strike.primary
                && strike.pos.distanceToSqr(pos) < 1.0D)) {
            return false;
        }
        announce(level, pos, true, warning);
        return true;
    }

    @Nullable
    private Vec3 randomArenaPosition(ServerLevel level) {
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = Math.sqrt(boss.getRandom().nextDouble()) * boss.getCombatRadius();
            int x = Mth.floor(center.x + Math.cos(angle) * radius);
            int z = Mth.floor(center.z + Math.sin(angle) * radius);
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            Double surface = boss.findArenaSurface(level, x, z);
            if (surface != null) {
                Vec3 pos = new Vec3(x + 0.5D, surface, z + 0.5D);
                if (isValidPosition(level, pos) && strikes.stream().noneMatch(strike -> strike.primary
                        && strike.pos.distanceToSqr(pos) < 1.0D)) {
                    return pos;
                }
            }
        }
        return null;
    }

    @Nullable
    private Vec3 randomFollowupPosition(ServerLevel level, Followup wave) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2.0D;
            double min = wave.round == 0 ? wave.innerMin : wave.outerMin;
            double max = wave.round == 0 ? wave.innerMax : wave.outerMax;
            double radius = Math.sqrt(min * min + boss.getRandom().nextDouble() * (max * max - min * min));
            double x = wave.center.x + Math.cos(angle) * radius;
            double z = wave.center.z + Math.sin(angle) * radius;
            int blockX = Mth.floor(x);
            int blockZ = Mth.floor(z);
            if (!level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
                continue;
            }
            Double surface = boss.findArenaSurface(level, blockX, blockZ, Mth.floor(wave.center.y));
            if (surface != null) {
                Vec3 pos = new Vec3(x, surface, z);
                if (isValidPosition(level, pos)
                        && strikes.stream().noneMatch(strike -> strike.pos.distanceToSqr(pos) < 1.0D)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private boolean isValidPosition(ServerLevel level, Vec3 pos) {
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        return pos.subtract(center).horizontalDistanceSqr() <= boss.getCombatRadius() * boss.getCombatRadius()
                && Math.abs(pos.y - center.y) <= SheeperConfig.LIGHTNING_COMBAT_HEIGHT_RANGE.get()
                && pos.y >= level.getMinBuildHeight() && pos.y < level.getMaxBuildHeight()
                && level.getChunkSource().hasChunk(Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4);
    }

    private void showWarning(ServerLevel level, Strike strike) {
        LightningStrikeEffect.showWarning(level, strike.pos, strike.radius);
    }

    private void impact(ServerLevel level, Strike strike, List<ServerPlayer> players) {
        LightningStrikeEffect.spawn(boss, strike.pos);
        LightningAreaDamage.hurtPlayers(boss, players, strike.pos, strike.radius, strike.height, strike.damage);
        if (strike.primary) {
            aftershocks.add(strike.pos);
            double innerMin = SheeperConfig.LIGHTNING_FOLLOWUP_INNER_RADIUS_MIN.get();
            double innerMax = Math.max(innerMin, SheeperConfig.LIGHTNING_FOLLOWUP_INNER_RADIUS_MAX.get());
            double outerMin = Math.max(innerMax + 0.1D, SheeperConfig.LIGHTNING_FOLLOWUP_OUTER_RADIUS_MIN.get());
            double outerMax = Math.max(outerMin, SheeperConfig.LIGHTNING_FOLLOWUP_OUTER_RADIUS_MAX.get());
            int innerCount = randomCount(SheeperConfig.LIGHTNING_FOLLOWUP_INNER_COUNT_MIN.get(),
                    SheeperConfig.LIGHTNING_FOLLOWUP_INNER_COUNT_MAX.get());
            int outerCount = randomCount(SheeperConfig.LIGHTNING_FOLLOWUP_OUTER_COUNT_MIN.get(),
                    SheeperConfig.LIGHTNING_FOLLOWUP_OUTER_COUNT_MAX.get());
            followups.add(new Followup(strike.pos, innerCount, outerCount, innerMin, innerMax,
                    outerMin, outerMax, strike.warningDuration, 0, 0));
        }
    }

    private int randomCount(int min, int max) {
        return min + boss.getRandom().nextInt(Math.max(min, max) - min + 1);
    }

    void clear() {
        strikes.clear();
        followups.clear();
        playerDelays.clear();
        remaining = 0;
        arenaDelay = 0;
        focusId = null;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Remaining", remaining);
        tag.putInt("ArenaDelay", arenaDelay);
        ListTag delays = new ListTag();
        playerDelays.forEach((id, ticks) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Delay", ticks);
            delays.add(entry);
        });
        tag.put("PlayerDelays", delays);
        ListTag cooldowns = new ListTag();
        playerCooldowns.forEach((id, ticks) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Remaining", ticks);
            cooldowns.add(entry);
        });
        tag.put("PlayerCooldowns", cooldowns);
        if (focusId != null) {
            tag.putUUID("Focus", focusId);
        }
        ListTag pending = new ListTag();
        strikes.forEach(strike -> pending.add(strike.save()));
        tag.put("Strikes", pending);
        ListTag waves = new ListTag();
        followups.forEach(wave -> waves.add(wave.save()));
        tag.put("Followups", waves);
        return tag;
    }

    private static CompoundTag positionTag(Vec3 pos) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", pos.x);
        tag.putDouble("Y", pos.y);
        tag.putDouble("Z", pos.z);
        return tag;
    }

    @Nullable
    private static Vec3 readPosition(CompoundTag tag) {
        Vec3 pos = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z) ? pos : null;
    }

    void load(CompoundTag tag) {
        clear();
        playerCooldowns.clear();
        for (Tag value : tag.getList("PlayerCooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player") && entry.getInt("Remaining") > 0) {
                playerCooldowns.put(entry.getUUID("Player"), Mth.clamp(entry.getInt("Remaining"), 1, 15600));
            }
        }
        remaining = Mth.clamp(tag.getInt("Remaining"), 0, 12000);
        arenaDelay = Mth.clamp(tag.getInt("ArenaDelay"), 0, 12000);
        for (Tag value : tag.getList("PlayerDelays", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                playerDelays.put(entry.getUUID("Player"), Mth.clamp(entry.getInt("Delay"), 1, 12000));
            }
        }
        focusId = tag.hasUUID("Focus") ? tag.getUUID("Focus") : null;
        for (Tag value : tag.getList("Strikes", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            Vec3 pos = readPosition(entry);
            double radius = entry.getDouble("Radius");
            double height = entry.getDouble("Height");
            float damage = entry.getFloat("Damage");
            if (pos != null && Double.isFinite(radius) && radius > 0
                    && Double.isFinite(height) && height > 0 && Float.isFinite(damage)) {
                strikes.add(new Strike(pos, Math.min(radius, 16.0D), Math.min(height, 32.0D),
                        Mth.clamp(damage, 0.0F, 1024.0F), entry.getBoolean("Primary"),
                        Mth.clamp(entry.getInt("WarningDuration"), 1, 1200),
                        Mth.clamp(entry.getInt("Remaining"), 1, 1200)));
            }
        }
        for (Tag value : tag.getList("Followups", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            Vec3 center = readPosition(entry);
            double innerMin = entry.getDouble("InnerMin");
            double innerMax = entry.getDouble("InnerMax");
            double outerMin = entry.getDouble("OuterMin");
            double outerMax = entry.getDouble("OuterMax");
            if (center != null && Double.isFinite(innerMin) && Double.isFinite(innerMax)
                    && Double.isFinite(outerMin) && Double.isFinite(outerMax) && innerMin >= 0
                    && innerMax >= innerMin && outerMin > innerMax && outerMax >= outerMin) {
                followups.add(new Followup(center, Mth.clamp(entry.getInt("InnerCount"), 1, 16),
                        Mth.clamp(entry.getInt("OuterCount"), 1, 16), Math.min(innerMin, 32.0D),
                        Math.min(innerMax, 32.0D), Math.min(outerMin, 64.0D), Math.min(outerMax, 64.0D),
                        Mth.clamp(entry.getInt("Warning"), 1, 1200), Mth.clamp(entry.getInt("Round"), 0, 1),
                        Mth.clamp(entry.getInt("Remaining"), 0, 1200)));
            }
        }
    }
}
