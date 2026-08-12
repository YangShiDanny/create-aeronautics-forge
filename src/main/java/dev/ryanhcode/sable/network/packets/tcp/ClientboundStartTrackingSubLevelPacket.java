package dev.ryanhcode.sable.network.packets.tcp;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.network.client.ClientSableInterpolationState;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.util.SableBufferUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record ClientboundStartTrackingSubLevelPacket(long plotCoordinate, UUID subLevelID, Pose3dc lastPose, Pose3d pose,
                                                     BoundingBox3ic bounds,  String name, int gameTick) implements SableTCPPacket {
    public static void encode(final FriendlyByteBuf buf, final ClientboundStartTrackingSubLevelPacket msg) {
        msg.write(buf);
    }

    public static ClientboundStartTrackingSubLevelPacket decode(final FriendlyByteBuf buf) {
        return ClientboundStartTrackingSubLevelPacket.read(buf);
    }

    private void write(final FriendlyByteBuf buf) {
        buf.writeLong(this.plotCoordinate);
        buf.writeUUID(this.subLevelID);

        SableBufferUtils.write(buf, this.lastPose);
        SableBufferUtils.write(buf, this.pose);
        SableBufferUtils.write(buf, this.bounds);

        buf.writeBoolean(this.name != null);
        if (this.name != null) {
            buf.writeUtf(this.name);
        }

        buf.writeInt(this.gameTick);
    }

    private static ClientboundStartTrackingSubLevelPacket read(final FriendlyByteBuf buf) {
        return new ClientboundStartTrackingSubLevelPacket(buf.readLong(), buf.readUUID(), SableBufferUtils.read(buf, new Pose3d()), SableBufferUtils.read(buf, new Pose3d()), SableBufferUtils.read(buf, new BoundingBox3i()), buf.readBoolean() ? buf.readUtf() : null, buf.readInt());
    }
    @Override
    public void handle(final SablePacketContext context) {
        final Level level = context.level();

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (!(container instanceof final ClientSubLevelContainer clientContainer)) {
            Sable.LOGGER.error("Received a sub-level tracking packet for a level without a sub-level container");
            return;
        }

        final ClientSubLevel subLevel = (ClientSubLevel) clientContainer.allocateSubLevel(this.subLevelID, ChunkPos.getX(this.plotCoordinate), ChunkPos.getZ(this.plotCoordinate), new Pose3d(this.lastPose));

        // [1.20.1 移植修正] 子关卡创建后的插值 / 边界 / 渲染数据初始化若抛异常，
        // 原流程会让异常经 Forge 网络派发捕获并断开客户端连接（"拉动后连接中断"根因之一）。
        // 此处包安全网：记录完整堆栈、保留已分配的子关卡，避免连接被断开。
        try {
            final SubLevelSnapshotInterpolator interpolator = subLevel.getInterpolator();

            interpolator.receiveSnapshot(this.gameTick - 1, this.lastPose);
            interpolator.receiveSnapshot(this.gameTick, this.pose);

            final ClientSableInterpolationState interpolationState = clientContainer.getInterpolation();

            if (!interpolationState.isStopped()) {
                subLevel.setInitialPosesFrom(interpolationState);
            }

            interpolator.setFirstPoses(this.pose, this.lastPose);

            subLevel.getPlot().setBoundingBox(this.bounds);
            subLevel.forceUpdateBounds();
            // [BUG-03 v25] 注册发射位条目（仅记录子层级引用 + 发射位姿态）。
            // 真正的发射位包围盒冻结推迟到首次进入 P4 判定且子层级已升空时
            // （那时 chunk 已加载、枚举能拿到全部方块实体，框才准确，不会漏掉东南向后期放置的方块）。
            Sable.SABLE_REST_ENTRIES.add(new Sable.Sable$RestEntry(subLevel));
            // Create the initial render data after
            subLevel.updateRenderData();

            if (this.name != null) {
                subLevel.setName(this.name);
            }
        } catch (final Throwable t) {
            Sable.LOGGER.error("客户端初始化子关卡数据失败（已忽略，子关卡仍可用）：", t);
        }
    }
}