package dev.eriksonn.aeronautics.network.packets;
import dev.simulated_team.simulated.backport.BackportCodecs;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.api.levitite_blend_crystallization.CrystalPropagationContext;
import dev.eriksonn.aeronautics.api.levitite_blend_crystallization.LevititeBlendHelper;
import dev.eriksonn.aeronautics.index.AeroLevititeBlendPropagationContexts;
import dev.eriksonn.aeronautics.index.AeroTags;
import dev.eriksonn.aeronautics.util.CatalyzerHelper;
import foundry.veil.api.network.handler.ServerPacketContext;
import dev.simulated_team.simulated.libs.catnip.codecs.stream.CatnipStreamCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public record LevititeCatalystCrystallizationPacket(BlockPos pos, InteractionHand hand) implements CustomPacketPayload {
	public static final Type<LevititeCatalystCrystallizationPacket> TYPE = new Type<>(Aeronautics.path("levitite_blend_crystallize"));

	public static final StreamCodec<FriendlyByteBuf, LevititeCatalystCrystallizationPacket> STREAM_CODEC = StreamCodec.composite(
			BackportCodecs.BLOCK_POS, LevititeCatalystCrystallizationPacket::pos,
			CatnipStreamCodecs.HAND, LevititeCatalystCrystallizationPacket::hand,
			LevititeCatalystCrystallizationPacket::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(final ServerPacketContext context) {
		final ServerPlayer player = context.getPlayer();

		final ItemStack item = player.getItemInHand(this.hand);

		if (!CatalyzerHelper.isCatalyzer(item)) {
			return;
		}

		if (!item.is(AeroTags.ItemTags.LEVITITE_CATALYZER_NO_CONSUME)) {
			if (item.isDamageableItem()) {
				item.hurtAndBreak(1, player, (LivingEntity p) -> {});
			} else if (item.isStackable() && !context.getPlayer().isCreative()) {
				item.shrink(1);
			}
		}
		player.swing(this.hand);
		final CrystalPropagationContext itemContext = item.is(AeroTags.ItemTags.LEVITITE_SOUL_CATALYZER) ?
				AeroLevititeBlendPropagationContexts.SOUL_CONTEXT.get() :
				AeroLevititeBlendPropagationContexts.STANDARD_CONTEXT.get();
		LevititeBlendHelper.addLevititeBlendTicker(context.level(), this.pos, false, false,
				itemContext.getContextForSpread(context.level(), this.pos)
		);
	}
}
