package dev.ryanhcode.sable.network.packets.tcp;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension;
import dev.ryanhcode.sable.network.packets.udp.SableUDPAuthenticationPacket;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.network.udp.AddressedSableUDPPacket;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;

import java.net.InetSocketAddress;
import java.util.UUID;

public record ClientboundSableUDPActivationPacket(UUID uuid) implements SableTCPPacket {
    public static void encode(final FriendlyByteBuf buf, final ClientboundSableUDPActivationPacket msg) {
        msg.write(buf);
    }

    public static ClientboundSableUDPActivationPacket decode(final FriendlyByteBuf buf) {
        return ClientboundSableUDPActivationPacket.read(buf);
    }
    private void write(final FriendlyByteBuf buf) {
        buf.writeUUID(this.uuid);
    }

    private static ClientboundSableUDPActivationPacket read(final FriendlyByteBuf buf) {
        return new ClientboundSableUDPActivationPacket(buf.readUUID());
    }
    @Override
    public void handle(final SablePacketContext context) {
        if (!SableClientConfig.ATTEMPT_UDP_NETWORKING.get()) {
            Sable.LOGGER.info("Received UDP authentication request, ignoring due to disabled attempt_udp_networking config");
            return;
        }

        final Connection connection = Minecraft.getInstance().getConnection().getConnection();
        final ConnectionExtension connectionExtension = (ConnectionExtension) connection;
        final Channel channel = connectionExtension.sable$getUDPChannel();

        final InetSocketAddress baseAddress = ((InetSocketAddress) connection.getRemoteAddress());
        final InetSocketAddress remoteAddress = new InetSocketAddress(baseAddress.getAddress(), baseAddress.getPort());

        Sable.LOGGER.info("Received UDP authentication request, sending response over UDP to {}", remoteAddress);

        channel.eventLoop().execute(() -> {
            final SableUDPAuthenticationPacket packet = new SableUDPAuthenticationPacket(this.uuid.toString());

            final AddressedSableUDPPacket envelope = new AddressedSableUDPPacket(packet, remoteAddress);
            final ChannelFuture writeFuture = channel.writeAndFlush(envelope);

            writeFuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        });
    }
}