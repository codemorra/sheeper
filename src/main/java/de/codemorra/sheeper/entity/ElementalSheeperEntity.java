package de.codemorra.sheeper.entity;

import java.util.List;
import javax.annotation.Nullable;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.EntityType;
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

/**
 * Shared foundation for sheep-shaped elemental bosses. Sheep supplies the model
 * state and sounds; this class defines the boss lifecycle and interactions.
 */
public class ElementalSheeperEntity extends Sheep {
    /** Element identity is fixed by the registered entity type. */
    public enum Element {
        LAVA(BossEvent.BossBarColor.RED),
        LIGHTNING(BossEvent.BossBarColor.YELLOW),
        WATER(BossEvent.BossBarColor.BLUE);

        private final BossEvent.BossBarColor barColor;

        Element(BossEvent.BossBarColor barColor) {
            this.barColor = barColor;
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

    /** Creates a persistent adult boss with the registered element's bar color. */
    public ElementalSheeperEntity(EntityType<? extends Sheep> type, Level level, Element element) {
        super(type, level);
        this.element = element;
        bossEvent = new ServerBossEvent(getDisplayName(), element.barColor, BossEvent.BossBarOverlay.PROGRESS);
        setPersistenceRequired();
        if (!level.isClientSide) {
            refreshConfiguredHealth();
            setHealth(getMaxHealth());
        }
    }

    /** Health is a technical placeholder pending the later combat design. */
    public static AttributeSupplier.Builder createBossAttributes() {
        return Sheep.createAttributes().add(Attributes.MAX_HEALTH, 100.0D);
    }

    public Element getElement() {
        return element;
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

    /** Updates the bar from authoritative health and the current display name. */
    @Override
    public void tick() {
        super.tick();
        if (!level.isClientSide) {
            if (isAlive()) {
                refreshConfiguredHealth();
            }
            bossEvent.setName(getDisplayName());
            bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
            bossEvent.setVisible(isAlive());
        }
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
