package de.codemorra.sheeper.entity;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.NetworkHooks;

/** Constant-velocity water shot with one server-authoritative impact. */
public class WaterProjectile extends Projectile {
    private int flightTicks;

    public WaterProjectile(EntityType<? extends WaterProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity && !ownedBy(entity) && super.canHitEntity(entity);
    }

    @Override
    public void tick() {
        if (isRemoved()) {
            return;
        }
        super.tick();
        if (!level.isClientSide) {
            if (flightTicks >= SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_PROJECTILE_LIFETIME.get())
                    || !level.getChunkSource().hasChunk(chunkPosition().x, chunkPosition().z)) {
                discard();
                return;
            }
            HitResult hit = ProjectileUtil.getHitResult(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
                onHit(hit);
                return;
            }
        } else {
            spawnBubbleTrail();
        }
        // No gravity, water drag, or target updates: dodging never changes the shot.
        setPos(position().add(getDeltaMovement()));
        flightTicks++;
    }

    /**
     * Samples the travelled segment so fast shots retain a continuous, bounded
     * trail.
     */
    private void spawnBubbleTrail() {
        Vec3 motion = getDeltaMovement();
        int samples = Math.max(1, Math.min(8, (int) Math.ceil(motion.length() / 0.25D)));
        for (int index = 0; index < samples; index++) {
            Vec3 point = position().subtract(motion.scale((double) index / samples));
            level.addParticle(ParticleTypes.BUBBLE, point.x, point.y, point.z,
                    motion.x * 0.15D, motion.y * 0.15D, motion.z * 0.15D);
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        if (level.isClientSide || isRemoved()) {
            return;
        }
        super.onHit(hit);
        // Keep block-hit bubbles on the incoming water side of the collision face.
        Vec3 impact = hit.getLocation().subtract(getDeltaMovement().normalize().scale(0.12D));
        ((ServerLevel) level).sendParticles(ParticleTypes.BUBBLE,
                impact.x, impact.y, impact.z, 12, 0.1D, 0.1D, 0.1D, 0.25D);
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity target = hit.getEntity();
        Vec3 previousMotion = target.getDeltaMovement();
        if (target.hurt(DamageSource.thrown(this, getOwner()),
                WaterSheeperConfig.WATER_PROJECTILE_DAMAGE.get().floatValue())) {
            // Replace vanilla's attacker-relative impulse with the flight direction.
            target.setDeltaMovement(previousMotion.add(getDeltaMovement().normalize()
                    .scale(WaterSheeperConfig.WATER_PROJECTILE_KNOCKBACK.get())));
            target.hurtMarked = true;
            if (target instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                player.hurtMarked = false;
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FlightTicks", flightTicks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        flightTicks = Math.max(0, tag.getInt("FlightTicks"));
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
