package dev.ryanhcode.sable.network.packets.tcp;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SableBufferUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Gizmo movement packet
 *
 * @param subLevel sub-level id
 * @param position position
 */
public record ServerboundGizmoMoveSubLevelPacket(UUID subLevel, Vector3d position) implements SableTCPPacket {
    public static void encode(final FriendlyByteBuf buf, final ServerboundGizmoMoveSubLevelPacket msg) {
        msg.write(buf);
    }

    public static ServerboundGizmoMoveSubLevelPacket decode(final FriendlyByteBuf buf) {
        return ServerboundGizmoMoveSubLevelPacket.read(buf);
    }
    private static ServerboundGizmoMoveSubLevelPacket read(final FriendlyByteBuf buf) {
        return new ServerboundGizmoMoveSubLevelPacket(
                buf.readUUID(),
                SableBufferUtils.read(buf, new Vector3d())
        );
    }

    private void write(final FriendlyByteBuf buf) {
        buf.writeUUID(this.subLevel);
        SableBufferUtils.write(buf, this.position);
    }
    @Override
    public void handle(final SablePacketContext context) {
        final ServerLevel level = (ServerLevel) context.level();

        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);

        if (!context.getPlayer().hasPermissions(1)) {
            Sable.LOGGER.warn("Player {} tried to move a sub-level with gizmo without permission", context.getPlayer().getGameProfile().getName());
            return;
        }

        if (container == null) {
            Sable.LOGGER.error("Received a gizmo movement packet for a level without a sub-level container");
            return;
        }

        final SubLevel subLevel = container.getSubLevel(this.subLevel);
        container.physicsSystem().getPipeline().teleport((ServerSubLevel) subLevel, this.position, subLevel.logicalPose().orientation());
    }
}