package dev.ryanhcode.sable.network.packets.tcp;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.network.tcp.SableClientContext;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.util.SableBufferUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

public record ClientboundFreezePlayerPacket(UUID subLevelID, Vector3dc localPosition) implements SableTCPPacket {
    public static void encode(final FriendlyByteBuf buf, final ClientboundFreezePlayerPacket msg) {
        msg.write(buf);
    }

    public static ClientboundFreezePlayerPacket decode(final FriendlyByteBuf buf) {
        return ClientboundFreezePlayerPacket.read(buf);
    }
    private static ClientboundFreezePlayerPacket read(final FriendlyByteBuf buf) {
        return new ClientboundFreezePlayerPacket(buf.readUUID(), SableBufferUtils.read(buf, new Vector3d()));
    }

    private void write(final FriendlyByteBuf buf) {
        buf.writeUUID(this.subLevelID);
        SableBufferUtils.write(buf, this.localPosition);
    }
    @Override
    public void handle(final SablePacketContext context) {
        // [修复] 本包是 PLAY_TO_CLIENT（客户端收）。SablePacketContext.getPlayer() 返回
        // ctx.getSender()，客户端收包时发件人恒为 null → 原写法 NPE 并触发"异常已吞，已断开连接"。
        // 客户端须取本地玩家。
        final Player player = SableClientContext.getClientPlayer();
        if (player == null) {
            return;
        }
        ((PlayerFreezeExtension) player).sable$freezeTo(this.subLevelID, this.localPosition);
    }
}