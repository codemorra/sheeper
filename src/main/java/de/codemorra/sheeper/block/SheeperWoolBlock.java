package de.codemorra.sheeper.block;

import de.codemorra.sheeper.entity.PrimedSheeperWool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.ToolActions;
import javax.annotation.Nullable;

/**
 * Shared block type for all Sheeper wool colors.
 * Each color receives the properties of its matching vanilla wool block.
 * Block instances are shared by all placed blocks of the same registered type.
 */
public class SheeperWoolBlock extends TntBlock {
    // Color belongs to the registered variant; fuse state belongs to each primed
    // entity.
    private final DyeColor color;

    public SheeperWoolBlock(
            BlockBehaviour.Properties properties, DyeColor color) {
        super(properties);
        this.color = color;
    }

    public DyeColor getColor() {
        return color;
    }

    /**
     * Replaces the inherited TNT spawn with color-preserving primed wool.
     * The calling ignition path handles removal of the placed block.
     */
    @Override
    public void onCaughtFire(
            BlockState state, Level level, BlockPos pos,
            @Nullable Direction face, @Nullable LivingEntity igniter) {
        if (level.isClientSide) {
            return;
        }

        PrimedSheeperWool primed = new PrimedSheeperWool(level, pos, color, igniter);

        level.addFreshEntity(primed);
        level.playSound(
                null, primed.getX(), primed.getY(), primed.getZ(),
                SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(igniter, GameEvent.PRIME_FUSE, pos);
    }

    /**
     * Preserves the wool color when an explosion primes this block.
     * Uses the same shortened random fuse as vanilla TNT chain reactions.
     */
    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (level.isClientSide) {
            return;
        }

        PrimedSheeperWool primed = new PrimedSheeperWool(
                level, pos, color, explosion.getSourceMob());

        int fuse = primed.getFuse();
        primed.setFuse(level.random.nextInt(fuse / 4) + fuse / 8);

        level.addFreshEntity(primed);
    }

    /**
     * Matches the flammability of vanilla wool.
     */
    @Override
    public int getFlammability(
            BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 60;
    }

    /**
     * Matches the fire spread speed of vanilla wool.
     */
    @Override
    public int getFireSpreadSpeed(
            BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 30;
    }

    /**
     * Harvests safely with shears; other tools cause a small blast without drops
     * or surrounding block destruction. The block has already been removed.
     * Minecraft supplies a tool snapshot from before durability loss, so a
     * breaking pair of shears still counts as safe. Creative mining skips this
     * path.
     */
    @Override
    public void playerDestroy(
            Level level, Player player, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (level.isClientSide) {
            return;
        }

        if (tool.canPerformAction(ToolActions.SHEARS_DIG)) {
            super.playerDestroy(level, player, pos, state, blockEntity, tool);
            return;
        }

        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);

        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.0F,
                Explosion.BlockInteraction.NONE);
    }
}
