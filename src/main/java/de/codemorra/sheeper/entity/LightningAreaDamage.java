package de.codemorra.sheeper.entity;

import java.util.List;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.LightningSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.LightningStormData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Shared cylindrical hit area and solid-cover checks for bolts and electrical
 * zones.
 */
public final class LightningAreaDamage {
    private LightningAreaDamage() {
    }

    static void hurtPlayers(LightningSheeperEntity boss, List<ServerPlayer> players, Vec3 pos,
            double radius, double height, float damage) {
        if (damage > 0) {
            for (ServerPlayer player : players) {
                hurtPlayer(boss, player, pos, radius, height, damage);
            }
        }
    }

    static boolean hurtPlayer(LightningSheeperEntity boss, ServerPlayer player, Vec3 pos,
            double radius, double height, float damage) {
        return damage > 0 && contains(boss, player, pos, radius, height)
                && hurtPlayer(boss, player, damage);
    }

    /** Applies lightning damage after the caller has verified range and cover. */
    static boolean hurtPlayer(LightningSheeperEntity boss, ServerPlayer player, float damage) {
        return hurtPlayer(boss, player, new EntityDamageSource("lightningBolt", boss), damage);
    }

    /** Preserves each attack's damage source and grants half the speed duration on a successful hit. */
    static boolean hurtPlayer(@Nullable LightningSheeperEntity boss, ServerPlayer player, DamageSource source,
            float damage) {
        if (damage <= 0 || isSheltered(player) || !player.hurt(source, damage)) {
            return false;
        }
        grantSpeedBonus(boss, player, speedBonusTicks(0.5));
        return true;
    }

    /** Tests the target's hitbox center against active protection spheres in this dimension. */
    static boolean isSheltered(LivingEntity target) {
        return LightningStormData.get((ServerLevel) target.level).isSheltered(target.getBoundingBox().getCenter());
    }

    /** Applies ordinary attack damage while leaving player admission and bonuses to the player paths. */
    static boolean hurtMob(LivingEntity target, DamageSource source, float damage) {
        return damage > 0 && target.isAlive() && !isSheltered(target) && target.hurt(source, damage);
    }

    static boolean hurtMob(LightningSheeperEntity boss, LivingEntity target, float damage) {
        return hurtMob(target, new EntityDamageSource("lightningBolt", boss), damage);
    }

    /**
     * Runs vanilla and Forge reactions at a direct impact with the configured damage.
     * The context bolt is never added to the world, so it cannot deal a second strike.
     */
    static boolean strikeMob(LivingEntity target, Vec3 point, float damage) {
        if (damage <= 0 || !target.isAlive() || target.isInvulnerableTo(DamageSource.LIGHTNING_BOLT)
                || isSheltered(target)) {
            return false;
        }
        ServerLevel level = (ServerLevel) target.level;
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return false;
        }
        bolt.setPos(point);
        bolt.setDamage(damage);
        if (ForgeEventFactory.onEntityStruckByLightning(target, bolt)) {
            return false;
        }
        target.thunderHit(level, bolt);
        return true;
    }

    /** Gets candidates within one strike or effect's bounding box, restricted to this arena. */
    static List<LivingEntity> mobsIn(LightningSheeperEntity boss, AABB bounds) {
        return boss.level.getEntitiesOfClass(LivingEntity.class, bounds, target -> target != boss
                && !(target instanceof Player) && target.isAlive() && boss.isInsideArena(target));
    }

    /** Gets candidates for effects that can reach the whole arena. */
    static List<LivingEntity> arenaMobs(LightningSheeperEntity boss) {
        Vec3 center = Vec3.atBottomCenterOf(boss.getArenaCenter());
        double radius = boss.getCombatRadius();
        return mobsIn(boss, new AABB(center.x - radius, boss.getArenaMinimumY(), center.z - radius,
                center.x + radius, boss.getArenaMaximumY(), center.z + radius));
    }

    /** Gets candidates for the columns used by ordinary ground strikes. */
    static List<LivingEntity> mobsAt(LightningSheeperEntity boss, Vec3 pos, double radius, double height) {
        return mobsIn(boss, new AABB(pos.x - radius, pos.y, pos.z - radius,
                pos.x + radius, pos.y + height, pos.z + radius));
    }

    static int speedBonusTicks(double durationMultiplier) {
        return SheeperConfig.secondsToTicks(
                LightningSheeperConfig.LIGHTNING_AMBIENT_SPEED_DURATION.get() * durationMultiplier);
    }

    /** Also used by the ambient edge and ground sparks, which can grant speed without damage. */
    static void grantSpeedBonus(@Nullable LightningSheeperEntity boss, ServerPlayer player, int ticks) {
        if (ticks > 0 && player.isAlive()) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0), boss);
        }
    }

    static boolean contains(LightningSheeperEntity boss, LivingEntity target, Vec3 pos, double radius, double height) {
        return contains((ServerLevel) boss.level, boss, target, pos, radius, height);
    }

    /**
     * Ambient arena strikes remain functional while their boss entity is unloaded.
     */
    public static void hurtAmbientPlayers(ServerLevel level, List<ServerPlayer> players, Vec3 pos,
            double radius, double height, float damage) {
        if (damage <= 0) {
            return;
        }
        for (ServerPlayer player : players) {
            if (player.isAlive() && !player.isRemoved() && !player.isCreative() && !player.isSpectator()
                    && contains(level, player, player, pos, radius, height)) {
                hurtPlayer(null, player, DamageSource.LIGHTNING_BOLT, damage);
            }
        }
    }

    private static boolean contains(ServerLevel level, Entity collisionContext, LivingEntity target,
            Vec3 pos, double radius, double height) {
        AABB bounds = target.getBoundingBox();
        double dx = Mth.clamp(pos.x, bounds.minX, bounds.maxX) - pos.x;
        double dz = Mth.clamp(pos.z, bounds.minZ, bounds.maxZ) - pos.z;
        if (dx * dx + dz * dz > radius * radius || bounds.maxY <= pos.y || bounds.minY >= pos.y + height) {
            return false;
        }
        Vec3 from = pos.add(0.0D, 0.1D, 0.0D);
        Vec3 to = bounds.getCenter();
        return chunksLoaded(level, from, to)
                && level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, collisionContext)).getType() == HitResult.Type.MISS;
    }

    /** Checks the ray's bounding chunks before tracing cover without loading terrain. */
    static boolean chunksLoaded(ServerLevel level, Vec3 from, Vec3 to) {
        AABB rayBounds = new AABB(from, to).inflate(1);
        for (int x = Mth.floor(rayBounds.minX) >> 4; x <= Mth.floor(rayBounds.maxX) >> 4; x++) {
            for (int z = Mth.floor(rayBounds.minZ) >> 4; z <= Mth.floor(rayBounds.maxZ) >> 4; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
