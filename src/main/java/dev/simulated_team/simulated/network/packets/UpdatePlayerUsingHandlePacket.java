package dev.simulated_team.simulated.network.packets;
import dev.simulated_team.simulated.backport.BackportCodecs;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.handle.HandleBlockEntity;
import dev.simulated_team.simulated.content.blocks.handle.ServerHandleHoldingHandler;
import dev.simulated_team.simulated.data.advancements.SimAdvancements;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public record UpdatePlayerUsingHandlePacket(float desiredRange, boolean remove, BlockPos interactionPos) implements CustomPacketPayload {

    public static StreamCodec<FriendlyByteBuf, UpdatePlayerUsingHandlePacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, UpdatePlayerUsingHandlePacket::desiredRange,
            ByteBufCodecs.BOOL, UpdatePlayerUsingHandlePacket::remove,
            BackportCodecs.BLOCK_POS, UpdatePlayerUsingHandlePacket::interactionPos,
            UpdatePlayerUsingHandlePacket::new);

    public static Type<UpdatePlayerUsingHandlePacket> TYPE = new Type<>(Simulated.path("update_player_handle"));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(final ServerPacketContext ctx) {
        final ServerPlayer player = ctx.getPlayer();
        final Level level = ctx.level();

        final BlockEntity be = level.getBlockEntity(this.interactionPos);

        if (this.remove) {
            ServerHandleHoldingHandler.stopHolding(player);
        }

        if (be instanceof final HandleBlockEntity hbe) {
            if (this.remove) {
                hbe.stopGrabbingServer(player.getUUID());
            } else {
                ServerHandleHoldingHandler.startHolding(player);
                hbe.startGrabbingServer(player.getUUID(), this.desiredRange);

                SimAdvancements.GET_A_GRIP.awardTo(player);
                if (player.fallDistance > 64) {
                    SimAdvancements.GOT_A_GRIP.awardTo(player);
                }
            }
        }
    }
}
