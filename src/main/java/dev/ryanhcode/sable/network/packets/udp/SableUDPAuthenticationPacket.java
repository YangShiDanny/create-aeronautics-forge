package dev.ryanhcode.sable.network.packets.udp;

import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;
import dev.ryanhcode.sable.network.udp.SableUDPServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;

import java.net.InetSocketAddress;
import java.util.UUID;

public record SableUDPAuthenticationPacket(String token) implements SableUDPPacket {

    public SableUDPAuthenticationPacket(final FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(final FriendlyByteBuf buf) {
        buf.writeUtf(this.token);
    }

    @Override
    public SableUDPPacketType getType() {
        return SableUDPPacketType.AUTH;
    }

    @Override
    public void handleServer(final MinecraftServer server, final InetSocketAddress sender) {
        final SableUDPServer udpServer = SableUDPServer.getServer(server);

        if (udpServer != null) {
            udpServer.receiveAuthenticationPacket(UUID.fromString(this.token), sender);
        }
    }
}
