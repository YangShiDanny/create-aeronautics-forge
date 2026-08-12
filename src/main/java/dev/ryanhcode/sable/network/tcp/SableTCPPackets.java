package dev.ryanhcode.sable.network.tcp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotInfoDualPacket;
import dev.ryanhcode.sable.network.packets.tcp.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SableTCPPackets {

    public static final String PROTOCOL = "1";

    // [1.20.1 跨 Forge 小版本兼容] 客户端与服务端 Forge 小版本不同（如 47.4.x 客户端连 47.3.x 服务端）
    // 是常态：Forge 47.4.0 移植回了新网络系统，握手时通道版本的传输表示与 47.3.x 不一致，
    // 严格相等校验（PROTOCOL::equals）会误判拒绝，日志表现为
    // Channels [sable:main] rejected their server side version number，玩家无法进服。
    // 两端装的是同一个模组 jar，包格式必然一致，故版本校验放宽为接受任意版本
    //（含对端未报版本的占位符），与本工程 Veil 通道的做法一致。
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Sable.MOD_ID, "main"),
            () -> PROTOCOL,
            s -> true,
            s -> true
    );

    private static int index = 0;

    public static void init() {
        registerClient(ClientboundSableSnapshotDualPacket.class, ClientboundSableSnapshotDualPacket::encode, ClientboundSableSnapshotDualPacket::decode, ClientboundSableSnapshotDualPacket::handle);
        registerClient(ClientboundSableSnapshotInfoDualPacket.class, ClientboundSableSnapshotInfoDualPacket::encode, ClientboundSableSnapshotInfoDualPacket::decode, ClientboundSableSnapshotInfoDualPacket::handle);
        registerClient(ClientboundStopMovingSubLevelPacket.class, ClientboundStopMovingSubLevelPacket::encode, ClientboundStopMovingSubLevelPacket::decode, ClientboundStopMovingSubLevelPacket::handle);
        registerClient(ClientboundChangeSubLevelNamePacket.class, ClientboundChangeSubLevelNamePacket::encode, ClientboundChangeSubLevelNamePacket::decode, ClientboundChangeSubLevelNamePacket::handle);
        registerClient(ClientboundStartTrackingSubLevelPacket.class, ClientboundStartTrackingSubLevelPacket::encode, ClientboundStartTrackingSubLevelPacket::decode, ClientboundStartTrackingSubLevelPacket::handle);
        registerClient(ClientboundFinalizeSubLevelPacket.class, ClientboundFinalizeSubLevelPacket::encode, ClientboundFinalizeSubLevelPacket::decode, ClientboundFinalizeSubLevelPacket::handle);
        registerClient(ClientboundStopTrackingSubLevelPacket.class, ClientboundStopTrackingSubLevelPacket::encode, ClientboundStopTrackingSubLevelPacket::decode, ClientboundStopTrackingSubLevelPacket::handle);
        registerClient(ClientboundChangeBoundsSubLevelPacket.class, ClientboundChangeBoundsSubLevelPacket::encode, ClientboundChangeBoundsSubLevelPacket::decode, ClientboundChangeBoundsSubLevelPacket::handle);
        registerClient(ClientboundFreezePlayerPacket.class, ClientboundFreezePlayerPacket::encode, ClientboundFreezePlayerPacket::decode, ClientboundFreezePlayerPacket::handle);
        registerClient(ClientboundPhysicsPropertyPacket.class, ClientboundPhysicsPropertyPacket::encode, ClientboundPhysicsPropertyPacket::decode, ClientboundPhysicsPropertyPacket::handle);
        registerClient(ClientboundFloatingBlockMaterialPacket.class, ClientboundFloatingBlockMaterialPacket::encode, ClientboundFloatingBlockMaterialPacket::decode, ClientboundFloatingBlockMaterialPacket::handle);
        registerClient(ClientboundRecentlySplitSubLevelPacket.class, ClientboundRecentlySplitSubLevelPacket::encode, ClientboundRecentlySplitSubLevelPacket::decode, ClientboundRecentlySplitSubLevelPacket::handle);
        registerClient(ClientboundSableUDPActivationPacket.class, ClientboundSableUDPActivationPacket::encode, ClientboundSableUDPActivationPacket::decode, ClientboundSableUDPActivationPacket::handle);
        registerClient(ClientboundEnterGizmoPacket.class, ClientboundEnterGizmoPacket::encode, ClientboundEnterGizmoPacket::decode, ClientboundEnterGizmoPacket::handle);

        registerServer(ServerboundPunchSubLevelPacket.class, ServerboundPunchSubLevelPacket::encode, ServerboundPunchSubLevelPacket::decode, ServerboundPunchSubLevelPacket::handle);
        registerServer(ServerboundGizmoMoveSubLevelPacket.class, ServerboundGizmoMoveSubLevelPacket::encode, ServerboundGizmoMoveSubLevelPacket::decode, ServerboundGizmoMoveSubLevelPacket::handle);
    }

    private static <MSG extends SableTCPPacket> void registerClient(final Class<MSG> type,
                                                                  final BiConsumer<FriendlyByteBuf, MSG> encoder,
                                                                  final Function<FriendlyByteBuf, MSG> decoder,
                                                                  final BiConsumer<MSG, SablePacketContext> handler) {
        INSTANCE.registerMessage(index++, type,
                (msg, buf) -> encoder.accept(buf, msg),
                decoder,
                (msg, ctxSupplier) -> {
                    final NetworkEvent.Context ctx = ctxSupplier.get();
                    // [1.20.1 移植修正] NeoForge 1.21 的包处理默认在主线程；Forge 1.20.1 SimpleChannel 在网络线程直呼 handler，
                    // 会触发 "Rendersystem called from wrong thread"（如 StartTracking 建渲染数据）。统一 enqueueWork 排队主线程。
                    ctx.enqueueWork(() -> {
                        try {
                            handler.accept(msg, new SablePacketContext(ctx));
                        } catch (final Throwable t) {
                            Sable.LOGGER.error("客户端/服务端处理 sable 包 {} 时异常（已忽略，不断开连接）：", msg.getClass().getName(), t);
                        }
                    });
                    ctx.setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static <MSG extends SableTCPPacket> void registerServer(final Class<MSG> type,
                                                                  final BiConsumer<FriendlyByteBuf, MSG> encoder,
                                                                  final Function<FriendlyByteBuf, MSG> decoder,
                                                                  final BiConsumer<MSG, SablePacketContext> handler) {
        INSTANCE.registerMessage(index++, type,
                (msg, buf) -> encoder.accept(buf, msg),
                decoder,
                (msg, ctxSupplier) -> {
                    final NetworkEvent.Context ctx = ctxSupplier.get();
                    // [1.20.1 移植修正] NeoForge 1.21 的包处理默认在主线程；Forge 1.20.1 SimpleChannel 在网络线程直呼 handler，
                    // 会触发 "Rendersystem called from wrong thread"（如 StartTracking 建渲染数据）。统一 enqueueWork 排队主线程。
                    ctx.enqueueWork(() -> {
                        try {
                            handler.accept(msg, new SablePacketContext(ctx));
                        } catch (final Throwable t) {
                            Sable.LOGGER.error("客户端/服务端处理 sable 包 {} 时异常（已忽略，不断开连接）：", msg.getClass().getName(), t);
                        }
                    });
                    ctx.setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /**
     * Sends a client-bound packet to a specific player.
     */
    public static void sendToPlayer(final ServerPlayer player, final SableTCPPacket packet) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Sends a server-bound packet from the client.
     */
    public static void sendToServer(final SableTCPPacket packet) {
        INSTANCE.sendToServer(packet);
    }
}
