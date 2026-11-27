package de.codemorra.sheeper.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import de.codemorra.sheeper.config.WaterSheeperConfig;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.world.WaterArenaBoundary;
import de.codemorra.sheeper.world.WaterArenaData;
import de.codemorra.sheeper.world.WaterArenaData.VolumeBounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.IndirectEntityDamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/** Constant-velocity water shot with one server-authoritative impact. */
public class WaterProjectile extends Projectile implements IEntityAdditionalSpawnData {
    private int flightTicks;
    @Nullable
    private UUID arenaOwner;
    @Nullable
    private VolumeBounds clientVolume;

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
    public void setOwner(@Nullable Entity owner) {
        super.setOwner(owner);
        arenaOwner = owner instanceof WaterSheeperEntity ? owner.getUUID() : null;
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
        VolumeBounds volume = attackVolume();
        if (volume == null) {
            discard();
            return;
        }
        Vec3 start = position();
        Vec3 motion = getDeltaMovement();
        double fraction = WaterArenaBoundary.attackFraction(volume, start, motion);
        if (fraction == 0) {
            discard();
            return;
        }
        Vec3 step = motion.scale(fraction);
        if (!level.isClientSide) {
            // Retire boss shots when the attack is disabled or the boss is defeated.
            if (arenaOwner != null && !WaterSheeperConfig.WATER_PROJECTILE_ENABLED.get()
                    || getOwner() instanceof WaterSheeperEntity boss && !boss.isAlive()
                    || flightTicks >= SheeperConfig.secondsToTicks(WaterSheeperConfig.WATER_PROJECTILE_LIFETIME.get())
                    || !level.getChunkSource().hasChunk(chunkPosition().x, chunkPosition().z)) {
                discard();
                return;
            }
            // Recheck the same flight segment after each removed leaf, so players
            // and solid blocks behind foliage can still be hit during this tick.
            for (int cleared = 0; cleared <= 16; cleared++) {
                HitResult hit = findImpact(volume, start, step);
                if (hit.getType() == HitResult.Type.MISS || ForgeEventFactory.onProjectileImpact(this, hit)) {
                    break;
                }
                if (cleared < 16 && hit instanceof BlockHitResult block && getOwner() instanceof WaterSheeperEntity boss
                        && WaterAttackBlocks.clearLeaves(boss, block.getBlockPos())) {
                    continue;
                }
                onHit(hit);
                return;
            }
        } else {
            spawnBubbleTrail(volume);
        }
        // No gravity, water drag, or target updates: dodging never changes the shot.
        setPos(start.add(step));
        if (fraction < 1) {
            discard();
        }
        flightTicks++;
    }

    /** Clips terrain and entity queries before any Forge impact event or foliage removal. */
    private HitResult findImpact(VolumeBounds volume, Vec3 start, Vec3 step) {
        HitResult terrain = level.clip(new ClipContext(start, start.add(step), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, this, start, terrain.getLocation(),
                getBoundingBox().expandTowards(step).inflate(1), candidate ->
                        canHitEntity(candidate) && volume.contains(candidate.position()));
        return entity == null ? terrain : entity;
    }

    @Nullable
    private VolumeBounds attackVolume() {
        if (level instanceof ServerLevel server) {
            return arenaOwner == null ? null : WaterArenaData.get(server).getVolume(arenaOwner);
        }
        return clientVolume;
    }

    /**
     * Samples the travelled segment so fast shots retain a continuous, bounded
     * trail.
     */
    private void spawnBubbleTrail(VolumeBounds volume) {
        Vec3 motion = getDeltaMovement();
        int samples = Math.max(1, Math.min(8, (int) Math.ceil(motion.length() / 0.25D)));
        for (int index = 0; index < samples; index++) {
            Vec3 point = position().subtract(motion.scale((double) index / samples));
            if (volume.contains(point)) {
                level.addParticle(ParticleTypes.BUBBLE, point.x, point.y, point.z,
                        motion.x * 0.15D, motion.y * 0.15D, motion.z * 0.15D);
            }
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
        level.playSound(null, impact.x, impact.y, impact.z,
                SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.HOSTILE, 0.8F, 1.1F);
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Entity target = hit.getEntity();
        Vec3 previousMotion = target.getDeltaMovement();
        if (target.hurt(damageSource(),
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

    /**
     * Retains thrown-projectile armor, shield and owner attribution while applying
     * ordinary mob difficulty scaling. Vanilla checks the nonliving projectile
     * instead of its shooter here; its override also ignores the scaling flag.
     */
    private DamageSource damageSource() {
        return new IndirectEntityDamageSource("thrown", this, getOwner()) {
            @Override
            public boolean scalesWithDifficulty() {
                return getEntity() instanceof LivingEntity && !(getEntity() instanceof Player);
            }
        }.setProjectile();
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
        // Retain the arena lookup even when the projectile reloads before its owner.
        arenaOwner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    /** Sends the owner's fixed bounds so flight prediction stops locally at the same edge. */
    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        VolumeBounds volume = attackVolume();
        buffer.writeBoolean(volume != null);
        if (volume != null) {
            buffer.writeBlockPos(volume.center());
            buffer.writeDouble(volume.radius());
            buffer.writeInt(volume.minimumY());
            buffer.writeDouble(volume.maximumY());
        }
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        if (buffer.readBoolean()) {
            var volume = new VolumeBounds(buffer.readBlockPos(), buffer.readDouble(), buffer.readInt(),
                    buffer.readDouble());
            clientVolume = volume.isValidFor(level) ? volume : null;
        }
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
