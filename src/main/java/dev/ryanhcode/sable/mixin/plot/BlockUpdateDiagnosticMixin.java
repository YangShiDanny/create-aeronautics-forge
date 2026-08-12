package dev.ryanhcode.sable.mixin.plot;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [BUG-29 修复] 客户端收到「单方块增量更新包」时，原版会把方块写进子关卡在
 * 主世界 chunk 缓存里（约 2048 万格外的地块坐标）的 backing chunk，但**从不触发
 * 子关卡渲染数据的标脏**——原版 {@code ViewArea.setDirty} 对地块坐标根本不会被调用
 * （实测 0 次）。于是服务端即使把增量广播下来、客户端也收到了包，画面依旧不刷新
 * （放置隐形 / 破坏不消失），只有当区块被整块重发（如解除物理化）才恢复。
 *
 * <p>这里在收包路径（{@code handleBlockUpdate} 已由 {@code PacketUtils.ensureRunningOnSameThread}
 * 调度到主线程，可安全触碰世界与渲染状态）上，按地块坐标定位子关卡并标脏其渲染数据，
 * 触发下个渲染帧重新烘焙对应段落。坐标换算：{@code renderData.setDirty} 使用段落坐标（方块 >> 4）。
 *
 * <p>此外，物理化飞行结构在客户端**没有本地预测**（其方块操作完全由服务端增量包驱动），
 * 因此放置/破坏的「操作反馈」（破坏碎屑 + 放置/破坏声音）在本应出现的时刻从未产生。
 * 静态结构靠客户端本地预测掩盖了这点，物理化飞行态一暴露就全缺。
 * 本 Mixin 在检测到方块确实变化、且该子层级正处于物理化飞行时，把反馈补播出来；
 * 静态结构有本地预测、不补播，避免双播。物理化判定见 {@code ClientSubLevel.sable$isPhysicsActive()}。
 *
 */
@Mixin(ClientPacketListener.class)
public class BlockUpdateDiagnosticMixin {

    @Inject(method = "handleBlockUpdate(Lnet/minecraft/network/protocol/game/ClientboundBlockUpdatePacket;)V", at = @At("HEAD"), require = 0)
    private void sable$onBlockUpdate(final ClientboundBlockUpdatePacket packet, final CallbackInfo ci) {
        final BlockPos pos = packet.getPos();

        // 只关心地块区（远离主世界原点约 2048 万格）的方块更新，主世界的日常更新由原版处理。
        if (Math.abs(pos.getX()) < 1_000_000 && Math.abs(pos.getZ()) < 1_000_000) {
            return;
        }

        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        // 按地块坐标定位子关卡。getPlot 吃区块坐标（方块 >> 4）。
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        final LevelPlot plot = container.getPlot(pos.getX() >> 4, pos.getZ() >> 4);
        if (plot == null) {
            return;
        }
        final ClientSubLevel subLevel = (ClientSubLevel) plot.getSubLevel();
        final SubLevelRenderData renderData = subLevel.getRenderData();
        if (renderData == null) {
            return;
        }

        // [BUG-29 修复] 用「子关卡自己维护的上次服务端确认状态」比对，而非 level.getBlockState——
        // 物理化结构客户端本地预测会把 plot chunk 预先写成新状态，收包时 level.getBlockState 读到的
        // 旧状态已被污染（恒等于新状态），无法据此判断真实变更。lastKnown 来自 sable$lastKnownStates，
        // 它在包应用后(TAIL)才写入，故 HEAD 读到的是「上一个服务端确认状态」。
        final BlockState newState = packet.getBlockState();
        final BlockState lastKnown = subLevel.sable$getLastKnownState(pos);
        final boolean realChange = lastKnown == null || !lastKnown.equals(newState);

        // [BUG-29 修复] 标脏受影响段落及其边界邻居段落。
        // 块落在段落顶/底/侧边界时，相邻段落正对它的那一面也需要重烘，否则破坏上方块后
        // 下方块只有最上面的面隐形（与 ViewAreaMixin 的边界邻居标脏对齐）。
        // setDirty 内部有 inBounds 保护，越界的相邻段落会被安全忽略。
        final int cx = pos.getX() >> 4;
        final int cy = pos.getY() >> 4;
        final int cz = pos.getZ() >> 4;
        final boolean playerChanged = false;
        renderData.setDirty(cx, cy, cz, playerChanged);
        if ((pos.getY() & 15) == 0) {
            renderData.setDirty(cx, cy - 1, cz, playerChanged);
        }
        if ((pos.getY() & 15) == 15) {
            renderData.setDirty(cx, cy + 1, cz, playerChanged);
        }
        if ((pos.getX() & 15) == 0) {
            renderData.setDirty(cx - 1, cy, cz, playerChanged);
        }
        if ((pos.getX() & 15) == 15) {
            renderData.setDirty(cx + 1, cy, cz, playerChanged);
        }
        if ((pos.getZ() & 15) == 0) {
            renderData.setDirty(cx, cy, cz - 1, playerChanged);
        }
        if ((pos.getZ() & 15) == 15) {
            renderData.setDirty(cx, cy, cz + 1, playerChanged);
        }

        // [BUG-29 修复] 物理化飞行结构无本地预测（放置/破坏的反馈从不由客户端产生），
        // 故在收到增量包时补播反馈；静态结构有本地预测、跳过避免双播。
        // 判定：sable$isPhysicsActive()(最近 1 秒内收过运动快照=飞行中)。
        // 坐标投影：plot 坐标 (pos) 经 logicalPose().transformPosition(方块中心) 得到飞行结构当前所在世界坐标播放。
        // [BUG-30 衍生·破坏反馈缺失修复] 破坏特效/声音不再依赖 lastKnown（上一次确认状态）：
        // 机械动力(Create)等联动方块在物理化飞行结构上会被客户端本地预测/联动先置成空气，
        // 破坏包到达时 lastKnown 已被污染为 air，原判断(lastKnown 非空气)整体跳过 → 无碎屑+无声音。
        // 改用 sable$lastNonAirStates（仅由非空气包刷新、破坏播放后清空）携带"被破坏的真实方块"。
        if (subLevel.sable$isPhysicsActive()) {
            final Vec3 world = subLevel.logicalPose().transformPosition(
                    new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            final boolean newAir = newState.isAir();
            // [BUG-32] 破坏碎屑+声音已统一下沉到 LevelChunkMixin（子层级 chunk 方块置空那一层）补播，
            // 这里不再重复播放，避免与那条路径双播。本处仅负责标脏(上方已完成)与刷新"最近非空气状态"兜底。
            if (!newAir) {
                // 新状态非空气：刷新"最近非空气状态"，并在确有变更时补一声放置音（原版放置本就无碎屑）。
                if (realChange) {
                    level.playLocalSound(world.x, world.y, world.z,
                            newState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F, false);
                }
                subLevel.sable$setLastNonAirState(pos, newState);
            }
        }

        // [BUG-29 修复] 包应用前(HEAD)把本包的新状态记进「上次确认状态」映射，供后续增量包比对真实变更。
        // 写在 HEAD 末尾：原版 setKnownState 在 HEAD 之后才执行，这里先记录期望结果，主线程顺序执行无竞争。
        subLevel.sable$setLastKnownState(pos, newState);
    }
}
