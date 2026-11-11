package de.codemorra.sheeper.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * A damageable server player whose normal player ticks run without a connected
 * client. Forge's FakePlayer deliberately disables ticking, damage, and death.
 */
final class TestPlayer extends ServerPlayer {
    private Vec3 anchor;
    final List<String> damageEvents = new ArrayList<>();
    final List<ClientboundSetEntityMotionPacket> motionPackets = new ArrayList<>();
    final List<ClientboundBossEventPacket> bossPackets = new ArrayList<>();
    final List<ClientboundLevelParticlesPacket> particlePackets = new ArrayList<>();
    float foodDamage;

    TestPlayer(ServerLevel level, Vec3 position) {
        super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "SheeperTest"), null);
        anchor = position;
        Connection sink = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void send(Packet<?> packet, PacketSendListener listener) {
                // Retain packets used to check client-side launches and boss bars.
                // Other packets are discarded because this player has no real client.
                if (packet instanceof ClientboundSetEntityMotionPacket motion) {
                    motionPackets.add(motion);
                } else if (packet instanceof ClientboundLevelParticlesPacket particles) {
                    particlePackets.add(particles);
                } else if (packet instanceof ClientboundBossEventPacket boss) {
                    bossPackets.add(boss);
                }
            }
        };
        connection = new ServerGamePacketListenerImpl(level.getServer(), sink, this);
        setGameMode(GameType.SURVIVAL);
        moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        // Expire vanilla's initial login protection through its ordinary countdown.
        // No world/player-effect ticks are advanced during this preparation.
        for (int tick = 0; tick < 60; tick++) {
            super.tick();
        }
    }

    @Override
    public void tick() {
        super.tick();
        // A real connection normally drives doTick from its network listener.
        doTick();
        // Keep AI scenes reproducible while preserving the velocity for bump checks.
        setPos(anchor.x, anchor.y, anchor.z);
    }

    /** Completes normal item use, including Forge's finish event, without a GUI. */
    void consume(Item item) {
        setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        startUsingItem(InteractionHand.MAIN_HAND);
        if (!isUsingItem() || !getUseItem().is(item)) {
            throw new GameTestAssertException("Test player could not start using " + item);
        }
        completeUsingItem();
        if (isUsingItem()) {
            throw new GameTestAssertException("Test player did not finish using " + item);
        }
    }

    /** Moves the stationary test client to another position for range tests. */
    void standAt(Vec3 position) {
        anchor = position;
        setPos(position.x, position.y, position.z);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float before = getHealth() + getAbsorptionAmount();
        boolean accepted = super.hurt(source, amount);
        if (accepted && getHealth() + getAbsorptionAmount() < before) {
            damageEvents.add(source.msgId + " at tick " + tickCount + ": " + before + " -> "
                    + (getHealth() + getAbsorptionAmount()));
            // Internal food explosions use GENERIC damage; environmental hits have
            // separate sources and must not be mistaken for a food-effect failure.
            if (source == DamageSource.GENERIC) {
                foodDamage += before - getHealth() - getAbsorptionAmount();
            }
        }
        return accepted;
    }
}
