package dev.simulated_team.simulated.network.packets.rope;
import dev.simulated_team.simulated.backport.BackportCodecs;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import foundry.veil.api.network.handler.ClientPacketContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public record ClientboundRopeStoppedPacket(BlockPos ownerPos) implements CustomPacketPayload {
    public static final StreamCodec<ByteBuf, ClientboundRopeStoppedPacket> CODEC = BackportCodecs.BLOCK_POS.map(ClientboundRopeStoppedPacket::new, ClientboundRopeStoppedPacket::ownerPos);
    public static Type<ClientboundRopeStoppedPacket> TYPE = new Type<>(Simulated.path("rope_stopped"));

    public void handle(final ClientPacketContext context) {
        final Player player = context.getPlayer();
        final Level level = player.level();

        final BlockEntity blockEntity = level.getBlockEntity(this.ownerPos);

        if (!(blockEntity instanceof final SmartBlockEntity smartBlockEntity)) {
            return;
        }

        final RopeStrandHolderBehavior ropeHolder = smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);

        if (ropeHolder == null) {
            return;
        }

        ropeHolder.receiveClientStrandStopped();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
