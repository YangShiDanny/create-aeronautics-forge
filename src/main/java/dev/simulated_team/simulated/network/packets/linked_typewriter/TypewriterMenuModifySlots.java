package dev.simulated_team.simulated.network.packets.linked_typewriter;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterMenuCommon;
import foundry.veil.api.network.handler.ServerPacketContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record TypewriterMenuModifySlots(ItemStack first, ItemStack second) implements CustomPacketPayload {

    public static Type<TypewriterMenuModifySlots> TYPE = new Type<>(Simulated.path("entry_modify"));

    public static StreamCodec<ByteBuf, TypewriterMenuModifySlots> CODEC = StreamCodec.composite(
            ByteBufCodecs.OPTIONAL_ITEM, TypewriterMenuModifySlots::first,
            ByteBufCodecs.OPTIONAL_ITEM, TypewriterMenuModifySlots::second,
            TypewriterMenuModifySlots::new
    );

    public void handle(final ServerPacketContext context) {
        final ServerPlayer player = context.getPlayer();

        if (player.containerMenu instanceof final LinkedTypewriterMenuCommon menu) {
            menu.ghostInventory.setStackInSlot(0, this.first);
            menu.ghostInventory.setStackInSlot(1, this.second);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
