package dev.ryanhcode.offroad.network;

import dev.ryanhcode.offroad.Offroad;
import dev.ryanhcode.offroad.network.borehead_bearing.ClientboundMultiMiningSync;
import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/**
 * [1.20.1 移植·修复] 仅客户端加载的 Veil 包管理器实现。
 * 专用服务器不会加载本类（仅在客户端经 OffroadPacketManager 的 isClient 委托进入），
 * 因此 VeilPacketManager 对 LocalPlayer 的引用不会在服务器上触发类加载。
 */
public class OffroadPacketManagerClient {

    private static VeilPacketManager INSTANCE;

    public static void init() {
        INSTANCE = VeilPacketManager.create(Offroad.MOD_ID, "0.1");
        INSTANCE.registerClientbound(ClientboundMultiMiningSync.class, ClientboundMultiMiningSync.TYPE, ClientboundMultiMiningSync.CODEC, ClientboundMultiMiningSync::handle);
    }

    public static void sendMultiMiningSync(final ServerLevel level, final BlockPos pos, final ClientboundMultiMiningSync packet) {
        INSTANCE.tracking(level, pos).sendPacket(packet);
    }
}
