package dev.ryanhcode.sable.network.packets;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;

public final class ClientboundSableSnapshotInfoDualPacket implements SableUDPPacket, SableTCPPacket {
    public static void encode(final FriendlyByteBuf buf, final ClientboundSableSnapshotInfoDualPacket msg) {
        msg.encode(buf);
    }

    public static ClientboundSableSnapshotInfoDualPacket decode(final FriendlyByteBuf buf) {
        return new ClientboundSableSnapshotInfoDualPacket(buf);
    }
    private final int msSinceLast;
    private final int gameTick;
    private final boolean stopped;

    public ClientboundSableSnapshotInfoDualPacket(final int msSinceLast, final int gameTick, final boolean stopped) {
        this.msSinceLast = msSinceLast;
        this.gameTick = gameTick;
        this.stopped = stopped;
    }

    public ClientboundSableSnapshotInfoDualPacket(final ByteBuf buf) {
        this(buf.readInt(), buf.readInt(), buf.readBoolean());
    }

    @Override
    public void handle(final SablePacketContext context) {
        this.handleClient(context.level());
    }
    public void encode(final ByteBuf byteBuf) {
        final FriendlyByteBuf buf = ((FriendlyByteBuf) byteBuf);

        buf.writeInt(this.msSinceLast);
        buf.writeInt(this.gameTick);
        buf.writeBoolean(this.stopped);
    }

    @Override
    public SableUDPPacketType getType() {
        return SableUDPPacketType.SNAPSHOT_INFO;
    }

    @Override
    public void handleClient(final Level level) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);

        if (container == null) {
            Sable.LOGGER.error("Received a sub-level movement packet for a level without a sub-level container");
            return;
        }

        ((ClientSubLevelContainer) container).getInterpolation()
                .receiveInfo(this.msSinceLast, this.gameTick, this.stopped);
    }
}
