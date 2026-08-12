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
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * [BUG-29 修复] 物理化飞行结构上的放置/破坏，服务端经
 * {@code ChunkHolder.broadcastChanges} 对区段内多个方块变更会发
 * {@code ClientboundSectionBlocksUpdatePacket}（批量区段更新包），而不是多个单方块
 * {@code ClientboundBlockUpdatePacket}。原工程只在 {@code BlockUpdateDiagnosticMixin} 拦了单方块包，
 * 漏掉了这条批量路径——用户的放置/破坏增量包根本没被捕获，于是既无画面刷新也无声音/碎屑
 * （只有机械部件 velocity_sensor/navigation_table 的每秒单方块刷新会走单方块包，掩盖了这条缺口）。
 *
 * <p>这里在批量包路径上：① 标脏子层级渲染数据（触发重烘焙，修复可见性）；
 * ② 把每个变更位写进子关卡的「上次确认状态」映射，供单方块路径正确判定真实变更
 * （批量路径本身不补播声音：一次区段更新可能含上百方块，直接补播会刷屏；
 * 用户放置/破坏这种单方块动作走 {@code BlockUpdateDiagnosticMixin} 补播）。
 *
 * <p>坐标换算与 {@code BlockUpdateDiagnosticMixin} 一致。
 *
 * <p>注意：本类位于 mixin 包内，不能定义任何被引用到的非-Mixin 内部类（否则触发
 * IllegalClassLoadError），故用 {@code Map<BlockPos, BlockState>} 暂存上次确认状态，TAIL 再比对/写回。
 */
@Mixin(ClientPacketListener.class)
public class SectionBlocksUpdateMixin {

    /** HEAD 时捕获的「该坐标上次服务端确认状态」，来自子关卡自身维护的映射，TAIL 比对/写回用。 */
    @Unique
    private static final Map<BlockPos, BlockState> sable$lastKnown = new HashMap<>();

    /** 上一次打印诊断的时刻，限流用。 */
    @Unique
    private static long sable$lastLogTime = 0L;

    /** TAIL 内累计：真正发生变更的方块数（上次确认状态 != 新状态）。 */
    @Unique
    private static int sable$changedCount = 0;

    @Inject(method = "handleChunkBlocksUpdate(Lnet/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket;)V",
            at = @At("HEAD"))
    private void sable$capture(final ClientboundSectionBlocksUpdatePacket packet, final CallbackInfo ci) {
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        // HEAD：原版尚未应用。读取子关卡自身维护的「上次确认状态」（非 level.getBlockState，
        // 后者会被本地预测污染）。只关心地块坐标（约 2048 万格外）。
        sable$lastKnown.clear();
        packet.runUpdates((BiConsumer<BlockPos, BlockState>) (pos, newState) -> {
            if (Math.abs(pos.getX()) < 1_000_000 && Math.abs(pos.getZ()) < 1_000_000) {
                return;
            }
            final LevelPlot plot = container.getPlot(pos.getX() >> 4, pos.getZ() >> 4);
            if (plot != null && plot.getSubLevel() instanceof ClientSubLevel) {
                sable$lastKnown.put(pos, ((ClientSubLevel) plot.getSubLevel()).sable$getLastKnownState(pos));
            }
        });
    }

    @Inject(method = "handleChunkBlocksUpdate(Lnet/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket;)V",
            at = @At("TAIL"))
    private void sable$apply(final ClientboundSectionBlocksUpdatePacket packet, final CallbackInfo ci) {
        if (sable$lastKnown.isEmpty()) {
            return;
        }

        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            sable$lastKnown.clear();
            return;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            sable$lastKnown.clear();
            return;
        }

        sable$changedCount = 0;

        // TAIL：原版已应用，重新遍历包取 (pos, newState) 与 HEAD 存的上次确认状态比对。
        packet.runUpdates((BiConsumer<BlockPos, BlockState>) (pos, newState) -> {
            final BlockState lastKnown = sable$lastKnown.get(pos);
            if (lastKnown != null && lastKnown.equals(newState)) {
                // 无变化（机械部件每秒无操作刷新等），仍写回映射保持同步，但不标脏/不计变更。
                final LevelPlot plot = container.getPlot(pos.getX() >> 4, pos.getZ() >> 4);
                if (plot != null && plot.getSubLevel() instanceof ClientSubLevel) {
                    ((ClientSubLevel) plot.getSubLevel()).sable$setLastKnownState(pos, newState);
                }
                return;
            }
            sable$changedCount++;

            final LevelPlot plot = container.getPlot(pos.getX() >> 4, pos.getZ() >> 4);
            if (plot == null || !(plot.getSubLevel() instanceof ClientSubLevel)) {
                return;
            }
            final ClientSubLevel subLevel = (ClientSubLevel) plot.getSubLevel();
            final SubLevelRenderData renderData = subLevel.getRenderData();
            if (renderData == null) {
                return;
            }

            // [BUG-29 修复] 标脏受影响段落及其边界邻居段落（与单方块路径一致）。
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

            // [BUG-29 修复] 把变更位写回子关卡的「上次确认状态」映射，供单方块路径判定真实变更。
            subLevel.sable$setLastKnownState(pos, newState);
        });

        sable$lastKnown.clear();
    }
}
