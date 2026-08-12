package dev.simulated_team.simulated.network.packets.contraption_diagram;
import dev.simulated_team.simulated.backport.BackportCodecs;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.entities.diagram.DiagramEntity;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record RequestDiagramDataPacket(UUID subLevel) implements CustomPacketPayload {

    public static Type<RequestDiagramDataPacket> TYPE = new Type<>(Simulated.path("request_diagram_data"));

    public static StreamCodec<FriendlyByteBuf, RequestDiagramDataPacket> CODEC = StreamCodec.composite(
            BackportCodecs.UUID, RequestDiagramDataPacket::subLevel,
            RequestDiagramDataPacket::new);

    public void handle(final ServerPacketContext context) {
        final ServerPlayer player = context.getPlayer();
        final Level level = player.level();

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        assert container != null;

        final SubLevel subLevel = container.getSubLevel(this.subLevel);

        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            DiagramEntity.queueDiagramDataFor(serverSubLevel, player);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
