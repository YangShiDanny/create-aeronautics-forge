package dev.ryanhcode.sable.network.udp;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.flow.FlowControlHandler;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.net.InetSocketAddress;

public interface SableUDPPacket {

    static void configureSerialization(final ChannelPipeline pipeline, final PacketFlow flow, final boolean memoryOnly) {
        if (memoryOnly) {
            // In-memory channels need no length framing; the Sable encoders/decoders operate on raw payloads.
            pipeline.addLast(new FlowControlHandler())
                    .addLast("decoder", new SableUDPPacketDecoder())
                    .addLast("encoder", new SableUDPPacketEncoder());
        } else {
            pipeline.addLast("splitter", new Varint21FrameDecoder())
                    .addLast(new FlowControlHandler())
                    .addLast("decoder", new SableUDPPacketDecoder())
                    .addLast("prepender", new Varint21LengthFieldPrepender())
                    .addLast("encoder", new SableUDPPacketEncoder());
        }
    }

    static void configureInMemoryPipeline(final ChannelPipeline channelPipeline, final PacketFlow arg) {
        configureSerialization(channelPipeline, arg, true);
    }

    SableUDPPacketType getType();

    default void handleClient(final Level level) {

    }

    default void handleServer(final MinecraftServer server, final InetSocketAddress sender) {

    }
}
