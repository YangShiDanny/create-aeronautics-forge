package dev.simulated_team.simulated.network.packets.handle;
import dev.simulated_team.simulated.backport.BackportCodecs;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.handle.PlayerHoldingHandleRenderer;
import foundry.veil.api.network.handler.ClientPacketContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

public record ClientboundPlayersHoldingHandlePacket(Collection<UUID> uuids) implements CustomPacketPayload {
	public static Type<ClientboundPlayersHoldingHandlePacket> TYPE = new Type<>(Simulated.path("players_holding_handles"));
	public static final StreamCodec<ByteBuf, ClientboundPlayersHoldingHandlePacket> CODEC = StreamCodec.composite(
		ByteBufCodecs.collection(HashSet::new, BackportCodecs.UUID), ClientboundPlayersHoldingHandlePacket::uuids,
	    ClientboundPlayersHoldingHandlePacket::new
	);

	public void handle(final ClientPacketContext context) {
		PlayerHoldingHandleRenderer.updatePlayerList(this.uuids);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
