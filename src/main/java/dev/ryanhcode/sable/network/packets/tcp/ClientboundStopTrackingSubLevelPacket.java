package dev.ryanhcode.sable.network.packets.tcp;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public record ClientboundStopTrackingSubLevelPacket(long plotCoordinate) implements SableTCPPacket {
    public static void encode(final FriendlyByteBuf buf, final ClientboundStopTrackingSubLevelPacket msg) {
        msg.write(buf);
    }

    public static ClientboundStopTrackingSubLevelPacket decode(final FriendlyByteBuf buf) {
        return ClientboundStopTrackingSubLevelPacket.read(buf);
    }
    private static ClientboundStopTrackingSubLevelPacket read(final FriendlyByteBuf buf) {
        return new ClientboundStopTrackingSubLevelPacket(buf.readLong());
    }

    private void write(final FriendlyByteBuf buf) {
        buf.writeLong(this.plotCoordinate);
    }
    @Override
    public void handle(final SablePacketContext context) {
        final Level level = context.level();
        final SubLevelContainer container = SubLevelContainer.getContainer(level);

        if (container == null) {
            Sable.LOGGER.error("Received a sub-level tracking packet for a level without a sub-level container");
            return;
        }

        final int chunkX = ChunkPos.getX(this.plotCoordinate);
        final int chunkZ = ChunkPos.getZ(this.plotCoordinate);
        if (container.getSubLevel(chunkX, chunkZ) == null) {
            Sable.LOGGER.error("Received a sub-level tracking removal packet for unknown sub-level: {}, {}", chunkX, chunkZ);
            return;
        }
        container.removeSubLevel(chunkX, chunkZ, SubLevelRemovalReason.REMOVED);
    }
}