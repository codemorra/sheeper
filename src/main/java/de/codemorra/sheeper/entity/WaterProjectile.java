package de.codemorra.sheeper.entity;

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
            if (flightTicks >= SheeperConfig.WATER_PROJECTILE_LIFETIME.get()
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
            level.addParticle(ParticleTypes.SPLASH, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
        }
        // No gravity, water drag, or target updates: dodging never changes the shot.
        setPos(position().add(getDeltaMovement()));
        flightTicks++;
    }

    @Override
    protected void onHit(HitResult hit) {
        if (level.isClientSide || isRemoved()) {
            return;
        }
        super.onHit(hit);
        ((ServerLevel) level).sendParticles(ParticleTypes.SPLASH,
                getX(), getY(), getZ(), 8, 0.15D, 0.15D, 0.15D, 0.0D);
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity target = hit.getEntity();
        Vec3 previousMotion = target.getDeltaMovement();
        if (target.hurt(DamageSource.thrown(this, getOwner()),
                SheeperConfig.WATER_PROJECTILE_DAMAGE.get().floatValue())) {
            // Replace vanilla's attacker-relative impulse with the flight direction.
            target.setDeltaMovement(previousMotion.add(getDeltaMovement().normalize()
                    .scale(SheeperConfig.WATER_PROJECTILE_KNOCKBACK.get())));
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
