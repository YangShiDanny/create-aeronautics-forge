package dev.ryanhcode.sable.network.packets.tcp;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundEnterGizmoPacket() implements SableTCPPacket {
    public static void encode(final FriendlyByteBuf buf, final ClientboundEnterGizmoPacket msg) {
        msg.write(buf);
    }

    public static ClientboundEnterGizmoPacket decode(final FriendlyByteBuf buf) {
        return ClientboundEnterGizmoPacket.read(buf);
    }
    private static ClientboundEnterGizmoPacket read(final FriendlyByteBuf buf) {
        return new ClientboundEnterGizmoPacket();
    }

    private void write(final FriendlyByteBuf buf) {

    }
    @Override
    public void handle(final SablePacketContext context) {
        SableClient.GIZMO_HANDLER.start();
    }
}