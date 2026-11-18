package de.codemorra.sheeper.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.WaterArenaData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

/**
 * Continuous beam timing and contact damage; the special scheduler owns
 * selection.
 * Target distance changes direction only, never the configured maximum range.
 */
final class WaterBeamAttack {
    private final WaterSheeperEntity boss;
    private final Map<UUID, Integer> damageCooldowns = new HashMap<>();
    @Nullable
    private Vec3 aimPosition;
    private int preparationTicks;
    private int preparationDuration;
    private int remainingAttackTicks;
    private int sightLossTicks;
    private double beamLength;

    /**
     * @param boss owner providing the mouth origin and synchronized collision
     *             segment
     */
    WaterBeamAttack(WaterSheeperEntity boss) {
        this.boss = boss;
    }

    /** Begins a stationary warning and samples the subsequent firing duration. */
    void start() {
        preparationDuration = SheeperConfig.secondsToTicks(SheeperConfig.WATER_BEAM_PREPARATION.get());
        preparationTicks = preparationDuration;
        int minimum = SheeperConfig.secondsToTicks(SheeperConfig.WATER_BEAM_DURATION_MIN.get());
        int maximum = Math.max(minimum, SheeperConfig.secondsToTicks(SheeperConfig.WATER_BEAM_DURATION_MAX.get()));
        remainingAttackTicks = minimum + boss.getRandom().nextInt(maximum - minimum + 1);
        sightLossTicks = 0;
        beamLength = 0.0D;
        damageCooldowns.clear();
        aimPosition = null;
        boss.setBeamOffset(Vec3.ZERO);
        boss.setBeamEyeIntensity(0.0F);
    }

    /**
     * @return delayed aim position used for both head tracking and beam direction
     */
    Vec3 getAimPosition() {
        return aimPosition == null ? boss.getEyePosition().add(boss.getLookAngle()) : aimPosition;
    }

    /**
     * Advances warning, extension and contact damage without rechecking entry
     * distance.
     *
     * @param target current eligible target; replacements retain gradual tracking
     * @return false when firing completes or sight is lost for too long
     */
    boolean tick(ServerPlayer target) {
        boss.setBeamOffset(Vec3.ZERO);
        if (remainingAttackTicks == 0) {
            return false;
        }
        // Cooldowns advance even while a player is outside the beam or arena.
        damageCooldowns.replaceAll((id, ticks) -> ticks - 1);
        damageCooldowns.values().removeIf(ticks -> ticks <= 0);
        boolean visible = boss.hasLineOfSight(target);
        sightLossTicks = visible ? 0 : sightLossTicks + 1;
        if (sightLossTicks >= SheeperConfig.secondsToTicks(SheeperConfig.WATER_BEAM_SIGHT_TIMEOUT.get())) {
            return false;
        }
        if (aimPosition == null) {
            aimPosition = target.getEyePosition();
        } else if (visible) {
            // A gradual response leaves room to dodge without abrupt aiming steps.
            int trackingTicks = Math.max(1,
                    SheeperConfig.secondsToTicks(SheeperConfig.WATER_BEAM_TRACKING_RESPONSE.get()));
            aimPosition = aimPosition.lerp(target.getEyePosition(), 1.0D / trackingTicks);
        }
        if (preparationTicks > 0) {
            preparationTicks--;
            boss.setBeamEyeIntensity(1.0F - (float) preparationTicks / preparationDuration);
            // Keep the full warning visible before activating the beam.
            return true;
        }
        boss.setBeamEyeIntensity(1.0F);
        double range = SheeperConfig.WATER_BEAM_RANGE.get();
        int extensionTicks = Math.max(1,
                SheeperConfig.secondsToTicks(SheeperConfig.WATER_BEAM_EXTENSION.get()));
        beamLength = Math.min(range, beamLength + range / extensionTicks);
        Vec3 start = boss.getBeamOrigin();
        Vec3 direction = aimPosition.subtract(start);
        if (direction.lengthSqr() < 1.0E-8D) {
            direction = boss.getLookAngle();
        }
        // Target movement controls direction only; length grows to a fixed range.
        Vec3 tip = loadedTip(start, direction.normalize(), beamLength);
        Vec3 end = boss.level.clip(new ClipContext(start, tip,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss)).getLocation();
        boss.setBeamOffset(end.subtract(start));
        for (ServerPlayer player : boss.getCombatParticipants()) {
            var bounds = player.getBoundingBox().inflate(WaterSheeperEntity.BEAM_RADIUS);
            if (!bounds.contains(start) && bounds.clip(start, end).isEmpty()) {
                continue;
            }
            // First contact is immediate. Only accepted damage starts the next
            // one-second interval; invulnerability must not consume that interval.
            if (!damageCooldowns.containsKey(player.getUUID())
                    && player.hurt(DamageSource.mobAttack(boss),
                            SheeperConfig.WATER_BEAM_DAMAGE_PER_SECOND.get().floatValue())) {
                damageCooldowns.put(player.getUUID(), 20);
            }
        }
        return --remainingAttackTicks > 0;
    }

    /**
     * Stops before an unloaded chunk instead of letting a long collision ray load
     * terrain. At chunk corners, both adjacent columns must also be loaded.
     */
    private Vec3 loadedTip(Vec3 start, Vec3 direction, double length) {
        Vec3 end = start;
        for (double distance = Math.min(0.5D, length); distance <= length; distance += 0.5D) {
            Vec3 candidate = start.add(direction.scale(distance));
            if (!isSegmentLoaded(end, candidate)) {
                return end;
            }
            end = candidate;
        }
        Vec3 tip = start.add(direction.scale(length));
        return isSegmentLoaded(end, tip) ? tip : end;
    }

    /**
     * Checks every chunk touched by one short ray step, including diagonal
     * crossings.
     */
    private boolean isSegmentLoaded(Vec3 from, Vec3 to) {
        BlockPos first = new BlockPos(from);
        BlockPos second = new BlockPos(to);
        for (int x = Math.min(first.getX() >> 4, second.getX() >> 4); x <= Math.max(first.getX() >> 4,
                second.getX() >> 4); x++) {
            for (int z = Math.min(first.getZ() >> 4, second.getZ() >> 4); z <= Math.max(first.getZ() >> 4,
                    second.getZ() >> 4); z++) {
                if (!WaterArenaData.isLoaded((ServerLevel) boss.level, new BlockPos(x * 16, 0, z * 16))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Clears all visible and damaging beam state when completed, aborted or reset.
     */
    void stop() {
        boss.setBeamOffset(Vec3.ZERO);
        remainingAttackTicks = 0;
        damageCooldowns.clear();
    }

    /**
     * @return beam extension, tracking and remaining per-player damage cooldowns;
     *         unloaded time does not advance the attack
     */
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("PreparationDuration", preparationDuration);
        tag.putInt("PreparationTicks", preparationTicks);
        tag.putInt("RemainingAttackTicks", remainingAttackTicks);
        tag.putInt("SightLossTicks", sightLossTicks);
        tag.putDouble("BeamLength", beamLength);
        if (aimPosition != null) {
            tag.putDouble("AimX", aimPosition.x);
            tag.putDouble("AimY", aimPosition.y);
            tag.putDouble("AimZ", aimPosition.z);
        }
        ListTag contacts = new ListTag();
        damageCooldowns.forEach((id, ticks) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Ticks", ticks);
            contacts.add(entry);
        });
        tag.put("DamageCooldowns", contacts);
        return tag;
    }

    /**
     * @param tag saved beam state, validated before it can affect tracking or
     *            damage
     */
    void load(CompoundTag tag) {
        preparationDuration = Math.max(1, Math.min(1200, tag.getInt("PreparationDuration")));
        preparationTicks = Math.max(0, Math.min(preparationDuration, tag.getInt("PreparationTicks")));
        remainingAttackTicks = Math.max(0, Math.min(1200, tag.getInt("RemainingAttackTicks")));
        sightLossTicks = Math.max(0, Math.min(1200, tag.getInt("SightLossTicks")));
        double savedLength = tag.getDouble("BeamLength");
        beamLength = Double.isFinite(savedLength) ? Math.max(0.0D, Math.min(128.0D, savedLength)) : 0.0D;
        aimPosition = null;
        if (tag.contains("AimX", Tag.TAG_DOUBLE) && tag.contains("AimY", Tag.TAG_DOUBLE)
                && tag.contains("AimZ", Tag.TAG_DOUBLE)) {
            Vec3 savedAim = new Vec3(tag.getDouble("AimX"), tag.getDouble("AimY"), tag.getDouble("AimZ"));
            if (Double.isFinite(savedAim.x) && Double.isFinite(savedAim.y) && Double.isFinite(savedAim.z)) {
                aimPosition = savedAim;
            }
        }
        damageCooldowns.clear();
        boolean legacyContacts = !tag.contains("DamageCooldowns", Tag.TAG_LIST);
        // Old saves stored elapsed contact time rather than the remaining delay.
        // Convert it once so loading cannot grant an extra immediate hit.
        for (Tag value : tag.getList(legacyContacts ? "ContactTicks" : "DamageCooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                int remaining = legacyContacts ? 20 - Math.max(0, Math.min(19, entry.getInt("Ticks")))
                        : Math.max(0, Math.min(20, entry.getInt("Ticks")));
                if (remaining > 0) {
                    damageCooldowns.put(entry.getUUID("Player"), remaining);
                }
            }
        }
    }
}
