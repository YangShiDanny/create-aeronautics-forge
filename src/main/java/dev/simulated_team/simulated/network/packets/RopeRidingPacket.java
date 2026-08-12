package dev.simulated_team.simulated.network.packets;
import dev.simulated_team.simulated.backport.BackportCodecs;

import com.simibubi.create.content.kinetics.chainConveyor.ServerChainConveyorHandler;
import dev.simulated_team.simulated.Simulated;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public record RopeRidingPacket(UUID uuid, boolean stop) implements CustomPacketPayload {
    public static Type<RopeRidingPacket> TYPE = new Type<>(Simulated.path("ride_rope"));

    public static StreamCodec<FriendlyByteBuf, RopeRidingPacket> CODEC = StreamCodec.composite(
            BackportCodecs.UUID, RopeRidingPacket::uuid,
            ByteBufCodecs.BOOL, RopeRidingPacket::stop,
            RopeRidingPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(final ServerPacketContext ctx) {
        final ServerPlayer player = ctx.getPlayer();

        player.connection.aboveGroundTickCount = 0;
        player.connection.aboveGroundVehicleTickCount = 0;
        player.fallDistance = 0.0f;

        if (this.stop)
            ServerChainConveyorHandler.handleStopRidingPacket(player);
        else
            ServerChainConveyorHandler.handleTTLPacket(player);

    }
}
