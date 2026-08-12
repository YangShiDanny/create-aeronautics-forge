package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableBufferUtils;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record ClientboundRecentlySplitSubLevelPacket(UUID splitSubLevelID, UUID splitFromID, Pose3d pose) implements SableTCPPacket {

    public static void encode(final FriendlyByteBuf buf, final ClientboundRecentlySplitSubLevelPacket msg) {
        buf.writeUUID(msg.splitSubLevelID());
        buf.writeUUID(msg.splitFromID());
        SableBufferUtils.write(buf, msg.pose());
    }

    public static ClientboundRecentlySplitSubLevelPacket decode(final FriendlyByteBuf buf) {
        return new ClientboundRecentlySplitSubLevelPacket(buf.readUUID(), buf.readUUID(), SableBufferUtils.read(buf, new Pose3d()));
    }

    @Override
    public void handle(final SablePacketContext context) {
        final SubLevelContainer container = SubLevelContainer.getContainer(context.level());
        if (container instanceof final ClientSubLevelContainer clientContainer) {
            final SubLevel subLevel = container.getSubLevel(this.splitSubLevelID);
            final SubLevel splitFrom = container.getSubLevel(this.splitFromID);

            if (subLevel != null && splitFrom != null) {
                ((ClientSubLevel) subLevel).wasSplitFrom(clientContainer.getInterpolation(), (ClientSubLevel) splitFrom, this.pose);
            } else {
                Sable.LOGGER.error("Attempted to handle a recently split sub-level packet for a sub-level (or origin sub-level) that does not exist!");
            }
        }
    }
}
