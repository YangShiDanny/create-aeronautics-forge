package dev.simulated_team.simulated.network.packets.linked_typewriter;
import dev.simulated_team.simulated.backport.BackportCodecs;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import dev.simulated_team.simulated.index.SimStats;
import foundry.veil.api.network.handler.ServerPacketContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lwjgl.glfw.GLFW;

public record TypewriterKeyInteractionPacket(BlockPos interactionPos, int key, int scanCode, int action /*If it's being pressed etc*/) implements CustomPacketPayload {
    public static final Type<TypewriterKeyInteractionPacket> TYPE = new Type<>(Simulated.path("key_interaction"));

    public static final StreamCodec<ByteBuf, TypewriterKeyInteractionPacket> CODEC = StreamCodec.composite(
            BackportCodecs.BLOCK_POS, TypewriterKeyInteractionPacket::interactionPos,
            ByteBufCodecs.INT, TypewriterKeyInteractionPacket::key,
            ByteBufCodecs.INT, TypewriterKeyInteractionPacket::scanCode,
            ByteBufCodecs.INT, TypewriterKeyInteractionPacket::action,
            TypewriterKeyInteractionPacket::new);

    public void handle(final ServerPacketContext context) {
        final Level level = context.level();
        final BlockEntity be = level.getBlockEntity(this.interactionPos);

        if (be instanceof final LinkedTypewriterBlockEntity typeWriter) {
            final boolean pressed = this.action == GLFW.GLFW_PRESS;
            if (pressed) {
                SimStats.TYPEWRITER_KEY_PRESSES.awardTo(context.getPlayer());
            }
            typeWriter.onKeyInteraction(context.getPlayer().getUUID(), null, this.key, pressed);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
