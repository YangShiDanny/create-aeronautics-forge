package dev.ryanhcode.offroad.network;

import dev.ryanhcode.offroad.Offroad;
import dev.ryanhcode.offroad.network.borehead_bearing.ClientboundMultiMiningSync;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * [1.20.1 移植·修复] Veil 是客户端渲染库，其 VeilPacketManager 类被加载时即引用
 * 客户端专属类 LocalPlayer，专用服务器上 RuntimeDistCleaner 会拒绝加载并导致模组构造崩溃。
 * 故把 Veil 相关的实例创建与网络调用全部隔离到仅在客户端加载的
 * OffroadPacketManagerClient，本类只负责「仅客户端才执行」的委托，自身不触碰 Veil。
 */
public class OffroadPacketManager {

    public static void init() {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        OffroadPacketManagerClient.init();
    }

    /**
     * 服务端向追踪该区块的玩家下发多挖同步包。专用服务器上 Veil 不可用，
     * 直接跳过（多挖客户端可视化在专用服务器不刷新，属已知限制，不影响服务端破块逻辑）。
     */
    public static void sendMultiMiningSync(final ServerLevel level, final BlockPos pos, final ClientboundMultiMiningSync packet) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        OffroadPacketManagerClient.sendMultiMiningSync(level, pos, packet);
    }
}
