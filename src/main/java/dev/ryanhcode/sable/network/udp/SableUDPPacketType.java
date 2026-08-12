package dev.ryanhcode.sable.network.udp;

import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotInfoDualPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPAuthenticationPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPClientboundKeepAlivePacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPEchoPacket;
import dev.ryanhcode.sable.network.packets.udp.SableUDPServerboundAlivePacket;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.BiConsumer;
import java.util.function.Function;

public enum SableUDPPacketType {
    PING((buf, p) -> ((SableUDPEchoPacket) p).write(buf), buf -> new SableUDPEchoPacket(buf)),
    SNAPSHOT((buf, p) -> ((ClientboundSableSnapshotDualPacket) p).encode(buf), buf -> new ClientboundSableSnapshotDualPacket(buf)),
    SNAPSHOT_INFO((buf, p) -> ((ClientboundSableSnapshotInfoDualPacket) p).encode(buf), buf -> new ClientboundSableSnapshotInfoDualPacket(buf)),
    AUTH((buf, p) -> ((SableUDPAuthenticationPacket) p).write(buf), buf -> new SableUDPAuthenticationPacket(buf)),
    KEEP_ALIVE_CLIENTBOUND((buf, p) -> ((SableUDPClientboundKeepAlivePacket) p).write(buf), buf -> new SableUDPClientboundKeepAlivePacket(buf)),
    ALIVE_SERVERBOUND((buf, p) -> ((SableUDPServerboundAlivePacket) p).write(buf), buf -> new SableUDPServerboundAlivePacket(buf));

    public static final SableUDPPacketType[] VALUES = SableUDPPacketType.values();

    private final BiConsumer<FriendlyByteBuf, SableUDPPacket> encoder;
    private final Function<FriendlyByteBuf, ? extends SableUDPPacket> decoder;

    SableUDPPacketType(final BiConsumer<FriendlyByteBuf, SableUDPPacket> encoder, final Function<FriendlyByteBuf, ? extends SableUDPPacket> decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    public SableUDPPacket create(final FriendlyByteBuf buf) {
        return this.decoder.apply(buf);
    }

    public void write(final FriendlyByteBuf buf, final SableUDPPacket packet) {
        this.encoder.accept(buf, packet);
    }
}
