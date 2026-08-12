package dev.simulated_team.simulated.network.packets;
import dev.simulated_team.simulated.backport.BackportCodecs;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.util.Observable;
import foundry.veil.api.network.handler.ServerPacketContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public record BlockEntityObservedPacket(BlockPos pos) implements CustomPacketPayload {

    public static Type<BlockEntityObservedPacket> TYPE = new Type<>(Simulated.path("be_observed"));
    public static StreamCodec<ByteBuf, BlockEntityObservedPacket> CODEC = BackportCodecs.BLOCK_POS.map(BlockEntityObservedPacket::new, BlockEntityObservedPacket::pos);

    @Override
    public  Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(final ServerPacketContext context) {
        final Level level = context.level();
        final ServerPlayer player = context.getPlayer();

        // More than 4 blocks + interaction range is way too far to observe a block
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(this.pos)) > 4.0 * 4.0) {
            return;
        }

        if (level.getBlockEntity(this.pos) instanceof final Observable observable) {
            observable.onObserved(player);
        }
    }
}
