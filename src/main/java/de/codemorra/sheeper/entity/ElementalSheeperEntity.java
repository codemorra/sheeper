package de.codemorra.sheeper.entity;

import java.util.List;
import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Shared foundation for sheep-shaped elemental bosses. Sheep supplies the model
 * state and sounds; this class defines the boss lifecycle and interactions.
 */
public class ElementalSheeperEntity extends Sheep {
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData
            .defineId(ElementalSheeperEntity.class, EntityDataSerializers.FLOAT);

    /** Element identity is fixed by the registered entity type. */
    public enum Element {
        LAVA(BossEvent.BossBarColor.RED, DyeColor.BLACK, ParticleTypes.SMALL_FLAME),
        LIGHTNING(BossEvent.BossBarColor.YELLOW, DyeColor.GRAY, ParticleTypes.ELECTRIC_SPARK),
        WATER(BossEvent.BossBarColor.BLUE, DyeColor.CYAN, ParticleTypes.DRIPPING_WATER);

        private final BossEvent.BossBarColor barColor;
        private final DyeColor woolColor;
        private final ParticleOptions particle;

        Element(BossEvent.BossBarColor barColor, DyeColor woolColor, ParticleOptions particle) {
            this.barColor = barColor;
            this.woolColor = woolColor;
            this.particle = particle;
        }

        float configuredSize() {
            return switch (this) {
                case LAVA -> SheeperConfig.LAVA_SIZE.get().floatValue();
                case LIGHTNING -> SheeperConfig.LIGHTNING_SIZE.get().floatValue();
                case WATER -> SheeperConfig.WATER_SIZE.get().floatValue();
            };
        }

        double configuredMaxHealth() {
            return switch (this) {
                case LAVA -> SheeperConfig.LAVA_MAX_HEALTH.get();
                case LIGHTNING -> SheeperConfig.LIGHTNING_MAX_HEALTH.get();
                case WATER -> SheeperConfig.WATER_MAX_HEALTH.get();
            };
        }
    }

    private final Element element;
    private final ServerBossEvent bossEvent;
    private double strengthMultiplier = 1.0D;

    /** Creates a persistent adult boss with the registered element's bar color. */
    public ElementalSheeperEntity(EntityType<? extends Sheep> type, Level level, Element element) {
        super(type, level);
        this.element = element;
        super.setColor(element.woolColor);
        bossEvent = new ServerBossEvent(getDisplayName(), element.barColor, BossEvent.BossBarOverlay.PROGRESS);
        setPersistenceRequired();
        if (!level.isClientSide) {
            entityData.set(DATA_SIZE, element.configuredSize());
            refreshConfiguredHealth();
            setHealth(getMaxHealth());
        }
    }

    /** Each element applies its configured maximum health during construction. */
    public static AttributeSupplier.Builder createBossAttributes() {
        return Sheep.createAttributes().add(Attributes.MAX_HEALTH, 200.0D);
    }

    public Element getElement() {
        return element;
    }

    /**
     * Individual strength reserved for future scaling; currently changes no stats.
     */
    public double getStrengthMultiplier() {
        return strengthMultiplier;
    }

    /**
     * Stores strength independently of installation-wide base settings.
     *
     * @param multiplier finite, positive strength multiplier
     * @throws IllegalArgumentException if the multiplier is not finite and positive
     */
    public void setStrengthMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 0.0D) {
            throw new IllegalArgumentException("Strength multiplier must be finite and positive");
        }
        strengthMultiplier = multiplier;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("StrengthMultiplier", strengthMultiplier);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        double multiplier = tag.getDouble("StrengthMultiplier");
        // Older saves and malformed summon data retain neutral strength.
        strengthMultiplier = Double.isFinite(multiplier) && multiplier > 0.0D ? multiplier : 1.0D;
    }

    /** Shared scale for the server hitbox and client renderer. */
    public float getSizeMultiplier() {
        return entityData.get(DATA_SIZE);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SIZE, 1.0F);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getSizeMultiplier());
    }

    /**
     * Rebuilds collision bounds and eye height on both sides after a size update.
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key)) {
            refreshDimensions();
        }
    }

    /**
     * Keeps the current health fraction when the server's configured maximum
     * changes.
     */
    private void refreshConfiguredHealth() {
        double maximum = element.configuredMaxHealth();
        AttributeInstance attribute = getAttribute(Attributes.MAX_HEALTH);
        if (attribute.getBaseValue() != maximum) {
            float fraction = getHealth() / getMaxHealth();
            attribute.setBaseValue(maximum);
            setHealth(fraction * getMaxHealth());
        }
    }

    @Override
    protected void registerGoals() {
        // Elemental AI will be designed separately from sheep and ordinary Sheeper AI.
    }

    @Override
    protected void customServerAiStep() {
        // Sheep's implementation expects a grazing goal registered by registerGoals.
    }

    /**
     * Element-specific preparation may postpone combat until its arena is ready.
     */
    public boolean isCombatReady() {
        return true;
    }

    /**
     * Synchronizes server-controlled boss state and emits local elemental
     * particles.
     */
    @Override
    public void tick() {
        super.tick();
        if (!level.isClientSide) {
            entityData.set(DATA_SIZE, element.configuredSize());
            if (isAlive()) {
                refreshConfiguredHealth();
            }
            bossEvent.setName(getDisplayName());
            bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
            bossEvent.setVisible(isAlive());
        } else if (isAlive() && !isInvisible() && (tickCount + getId()) % 6 == 0) {
            spawnElementParticle();
        }
    }

    /**
     * Alternates between the flanks, ends, back, and head of the visible fleece.
     * Coordinates approximate the vanilla sheep model in unscaled blocks, then
     * follow the boss's size and body rotation. The wool above the head extends
     * higher than the collision box, so particle placement follows the model.
     */
    private void spawnElementParticle() {
        double side;
        double forward;
        double height = 0.7D + random.nextDouble() * 0.48D;
        int surface = (tickCount + getId()) / 6 % 4;
        switch (surface) {
            case 0 -> {
                side = random.nextBoolean() ? 0.52D : -0.52D;
                forward = (random.nextDouble() * 2.0D - 1.0D) * 0.6D;
            }
            case 1 -> {
                side = (random.nextDouble() * 2.0D - 1.0D) * 0.34D;
                forward = random.nextBoolean() ? 0.77D : -0.77D;
            }
            case 2 -> {
                side = (random.nextDouble() * 2.0D - 1.0D) * 0.34D;
                forward = (random.nextDouble() * 2.0D - 1.0D) * 0.58D;
                height = 1.24D + random.nextDouble() * 0.005D;
            }
            default -> {
                side = (random.nextDouble() * 2.0D - 1.0D) * 0.21D;
                forward = 0.56D + (random.nextDouble() * 2.0D - 1.0D) * 0.2D;
                height = 1.418D + random.nextDouble() * 0.005D;
            }
        }
        Vec3 offset = new Vec3(side, height, forward).scale(getSizeMultiplier())
                .yRot(-yBodyRot * Mth.DEG_TO_RAD);
        // Cosmetic particles are local; vanilla client particle settings apply.
        double upwardSpeed = surface >= 2 ? 0.0D : 0.005D;
        level.addParticle(element.particle, getX() + offset.x, getY() + offset.y, getZ() + offset.z,
                0.0D, upwardSpeed, 0.0D);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    /** Cleans up the bar for every removal path, including chunk unloading. */
    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // DyeItem would otherwise consume dye after calling our fixed-color setter.
        return player.getItemInHand(hand).getItem() instanceof DyeItem
                ? InteractionResult.CONSUME
                : InteractionResult.PASS;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canMate(Animal other) {
        return false;
    }

    @Override
    @Nullable
    public Sheep getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public void setInLove(@Nullable Player player) {
        // The boss lifecycle is independent of animal breeding.
    }

    @Override
    public void setAge(int age) {
        // Keep the adult model, including when reading Age from summon or saved NBT.
    }

    @Override
    public boolean readyForShearing() {
        return false;
    }

    @Override
    public void shear(SoundSource source) {
        // Preserve the fleece for direct calls as well as ordinary interactions.
    }

    @Override
    public List<ItemStack> onSheared(
            @Nullable Player player, ItemStack item, Level world, BlockPos pos, int fortune) {
        return List.of();
    }

    @Override
    public void setSheared(boolean sheared) {
        // Preserve the fleece when reading sheep state from NBT.
    }

    @Override
    public void setColor(DyeColor color) {
        // Elemental appearance is controlled by the entity type.
    }

    /**
     * Covers items, equipment, and experience together, including custom loot NBT.
     */
    @Override
    protected void dropAllDeathLoot(DamageSource source) {
    }
}
