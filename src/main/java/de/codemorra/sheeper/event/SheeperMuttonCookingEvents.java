package de.codemorra.sheeper.event;

import de.codemorra.sheeper.SheeperMod;
import de.codemorra.sheeper.config.SheeperConfig;
import de.codemorra.sheeper.registry.ModItems;
import de.codemorra.sheeper.registry.ModSounds;
import de.codemorra.sheeper.util.SheeperMuttonExplosion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Handles unsafe preparation of Sheeper mutton.
 */
@Mod.EventBusSubscriber(modid = SheeperMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SheeperMuttonCookingEvents {
    private static final String COOKING_TICKS = SheeperMod.MODID + ":unsafe_cooking_ticks";

    private static final Set<BlockEntity> COOKING_BLOCKS = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));

    private SheeperMuttonCookingEvents() {
    }

    /**
     * Drops the furnace and its inventory before creating a small blast.
     * Removing the furnace first prevents it from shielding nearby entities.
     */
    private static void explodeFurnace(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.FURNACE)) {
            return;
        }

        level.destroyBlock(pos, true);

        SheeperMuttonExplosion.explode(level,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5, SheeperConfig.COOKING_EXPLOSION_STRENGTH.get().floatValue());
    }

    /**
     * Drops normal campfire loot and remaining food before the blast.
     */
    private static void explodeCampfire(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof CampfireBlock)) {
            return;
        }

        level.destroyBlock(pos, true);

        SheeperMuttonExplosion.explode(level,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5, SheeperConfig.COOKING_EXPLOSION_STRENGTH.get().floatValue());
    }

    /**
     * Tracks furnaces and campfires when their block entities are created.
     * No custom capability is attached.
     */
    @SubscribeEvent
    public static void onAttachCapabilities(
            AttachCapabilitiesEvent<BlockEntity> event) {
        BlockEntity blockEntity = event.getObject();

        // Track furnaces and campfires for unsafe cooking events
        if (blockEntity instanceof FurnaceBlockEntity
                || blockEntity instanceof CampfireBlockEntity) {
            COOKING_BLOCKS.add(blockEntity);
        }
    }

    /**
     * Checks tracked cooking blocks once per server-level tick.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)) {
            return;
        }

        List<BlockEntity> snapshot;

        synchronized (COOKING_BLOCKS) {
            snapshot = List.copyOf(COOKING_BLOCKS);
        }

        // Iterate over a snapshot of tracked cooking blocks to avoid concurrent
        // modification issues
        for (BlockEntity blockEntity : snapshot) {
            // Skip removed or unloaded block entities
            if (blockEntity.isRemoved()
                    || blockEntity.getLevel() != level) {
                continue;
            }

            // Skip blocks that are outside the active simulation area
            if (!level.shouldTickBlocksAt(blockEntity.getBlockPos())) {
                continue;
            }

            // Tick the furnace or campfire if it is being tracked
            if (blockEntity instanceof FurnaceBlockEntity furnace) {
                tickFurnace(level, furnace);
            } else if (blockEntity instanceof CampfireBlockEntity campfire) {
                tickCampfire(level, campfire);
            }
        }
    }

    /**
     * Counts continuous heating time while raw Sheeper mutton is present.
     * Saved progress pauses when the furnace is outside the active simulation.
     */
    private static void tickFurnace(
            ServerLevel level, FurnaceBlockEntity furnace) {
        CompoundTag data = furnace.getPersistentData();

        boolean heating = furnace.getBlockState()
                .getValue(AbstractFurnaceBlock.LIT);
        boolean hasMutton = furnace.getItem(0)
                .is(ModItems.RAW_SHEEPER_MUTTON.get());

        // Reset cooking progress if the furnace is not heating or has no raw Sheeper
        // mutton
        if (!SheeperConfig.UNSAFE_COOKING_ENABLED.get() || !heating || !hasMutton) {
            if (data.contains(COOKING_TICKS)) {
                data.remove(COOKING_TICKS);
                furnace.setChanged();
            }
            return;
        }

        int elapsed = data.getInt(COOKING_TICKS);

        // Play a sound when cooking starts for the first tick
        if (elapsed == 0) {
            level.playSound(
                    null, furnace.getBlockPos(),
                    ModSounds.WARNING_FUSE.get(),
                    SoundSource.BLOCKS,
                    1.0F, 1.0F);
        }

        elapsed++;

        // Check if cooking has reached the warning threshold
        if (elapsed >= SheeperConfig.secondsToTicks(SheeperConfig.COOKING_WARNING_SECONDS.get())) {
            data.remove(COOKING_TICKS);
            furnace.removeItem(0, 1);
            explodeFurnace(level, furnace.getBlockPos());
            return;
        }

        data.putInt(COOKING_TICKS, elapsed);
        furnace.setChanged();
    }

    /**
     * Warns while a lit campfire holds raw Sheeper mutton.
     * One piece triggers the blast; all remaining food is preserved.
     */
    private static void tickCampfire(
            ServerLevel level, CampfireBlockEntity campfire) {
        CompoundTag data = campfire.getPersistentData();
        boolean heating = campfire.getBlockState()
                .getValue(CampfireBlock.LIT);

        int muttonSlot = -1;

        // Find the first slot containing raw Sheeper mutton
        for (int slot = 0; slot < campfire.getItems().size(); slot++) {
            if (campfire.getItems().get(slot)
                    .is(ModItems.RAW_SHEEPER_MUTTON.get())) {
                muttonSlot = slot;
                break;
            }
        }

        // Reset cooking progress if the campfire is not heating or has no raw Sheeper
        // mutton
        if (!SheeperConfig.UNSAFE_COOKING_ENABLED.get() || !heating || muttonSlot == -1) {
            if (data.contains(COOKING_TICKS)) {
                data.remove(COOKING_TICKS);
                campfire.setChanged();
            }
            return;
        }

        int elapsed = data.getInt(COOKING_TICKS);

        // Play a sound when cooking starts for the first tick
        if (elapsed == 0) {
            level.playSound(
                    null, campfire.getBlockPos(),
                    ModSounds.WARNING_FUSE.get(),
                    SoundSource.BLOCKS,
                    1.0F, 1.0F);
        }

        elapsed++;

        // Check if cooking has reached the warning threshold
        if (elapsed >= SheeperConfig.secondsToTicks(SheeperConfig.COOKING_WARNING_SECONDS.get())) {
            data.remove(COOKING_TICKS);
            campfire.getItems().get(muttonSlot).shrink(1);
            explodeCampfire(level, campfire.getBlockPos());
            return;
        }

        data.putInt(COOKING_TICKS, elapsed);
        campfire.setChanged();
    }
}
