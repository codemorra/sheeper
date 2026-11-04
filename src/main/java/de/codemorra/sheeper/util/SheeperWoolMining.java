package de.codemorra.sheeper.util;

import de.codemorra.sheeper.config.SheeperConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Shared unsafe-harvesting behavior for Sheeper wool and carpets. */
public final class SheeperWoolMining {
    /** Identifies mining blasts so the event handler can protect dropped items. */
    public static final DamageSource DAMAGE = DamageSource.explosion((LivingEntity) null);

    private SheeperWoolMining() {
    }

    /**
     * Accounts for an unsafe survival harvest and explodes without dropping the
     * harvested block. Call on the server after removal and after checking shears.
     * Surrounding blocks and dropped items survive; zero strength suppresses the
     * blast but still yields no harvest.
     */
    public static void unsafeHarvest(Level level, Player player, BlockPos pos, Block block) {
        player.awardStat(Stats.BLOCK_MINED.get(block));
        player.causeFoodExhaustion(0.005F);

        float strength = SheeperConfig.WOOL_MINING_STRENGTH.get().floatValue();
        if (strength > 0.0F) {
            level.explode(null, DAMAGE, null,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    strength, false, Explosion.BlockInteraction.NONE);
        }
    }
}
