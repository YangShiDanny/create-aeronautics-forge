package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.network.FriendlyByteBuf;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record ClientboundChangeSubLevelNamePacket(UUID subLevelID,  String name) implements SableTCPPacket {

    public static void encode(final FriendlyByteBuf buf, final ClientboundChangeSubLevelNamePacket msg) {
        buf.writeUUID(msg.subLevelID());
        buf.writeBoolean(msg.name() != null);
        if (msg.name() != null) {
            buf.writeUtf(msg.name());
        }
    }

    public static ClientboundChangeSubLevelNamePacket decode(final FriendlyByteBuf buf) {
        final UUID id = buf.readUUID();
        final String name = buf.readBoolean() ? buf.readUtf() : null;
        return new ClientboundChangeSubLevelNamePacket(id, name);
    }

    @Override
    public void handle(final SablePacketContext context) {
        final SubLevelContainer container = SubLevelContainer.getContainer(context.level());
        if (container != null) {
            final SubLevel subLevel = container.getSubLevel(this.subLevelID);

            if (subLevel != null) {
                subLevel.setName(this.name);
            } else {
                Sable.LOGGER.error("Attempted to set name for a client sub-level that does not exist!");
            }
        }
    }
}
