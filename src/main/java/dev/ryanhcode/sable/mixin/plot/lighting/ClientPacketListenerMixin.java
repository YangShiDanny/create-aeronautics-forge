package dev.ryanhcode.sable.mixin.plot.lighting;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;
import java.util.Iterator;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @WrapOperation(method = "m_183388_", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_194171_(Ljava/lang/Runnable;)V", remap = false))
    private void sable$queueLightData(final ClientLevel instance, final Runnable task, final Operation<Void> original, @Local(argsOnly = true) final ClientboundLevelChunkWithLightPacket packet) {
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(instance);

        if (container != null && container.inBounds(packet.getX(), packet.getZ())) {
            final LevelPlot plot = container.getPlot(new ChunkPos(packet.getX(), packet.getZ()));

            if (plot != null) {
                final int x = packet.getX();
                final int z = packet.getZ();
                final LevelLightEngine plotEngine = plot.getLightEngine();
                final ClientboundLightUpdatePacketData lightData = packet.getLightData();
                final ClientLevel level = instance;

                // [BUG-33 修复] 复刻原版 ClientPacketListener.applyLightData + readSectionList + enableChunkLight，
                // 但把光照数据写入 SubLevel 独立的 lightEngine（镜像服务端），使方块光能在 plot 内部跨区块 flood-fill，
                // 不再被主世界 lightEngine 在偏移坐标处（邻居 chunk 未加载）截断。
                sable$applyLightData(plotEngine, x, z, lightData, level);

                final PlotChunkHolder holder = container.getChunkHolder(new ChunkPos(x, z));
                if (holder != null) {
                    final LevelChunk chunk = holder.getChunk();
                    if (chunk != null) {
                        sable$enableChunkLight(plotEngine, chunk, x, z, level);
                    }
                }

                do {
                    plotEngine.runLightUpdates();
                } while (plotEngine.hasLightWork());

                return;
            }
        }

        original.call(instance, task);
    }

    /**
     * 复刻原版 {@code ClientPacketListener.applyLightData}：把天空光与方块光分别按区段写入 lightEngine。
     */
    @Unique
    private static void sable$applyLightData(final LevelLightEngine engine, final int x, final int z,
                                             final ClientboundLightUpdatePacketData data, final ClientLevel level) {
        sable$readSectionList(x, z, engine, LightLayer.SKY, data.getSkyYMask(), data.getEmptySkyYMask(), data.getSkyUpdates().iterator());
        sable$readSectionList(x, z, engine, LightLayer.BLOCK, data.getBlockYMask(), data.getEmptyBlockYMask(), data.getBlockUpdates().iterator());
        engine.setLightEnabled(new ChunkPos(x, z), true);
    }

    /**
     * 复刻原版 {@code ClientPacketListener.readSectionList}：逐区段 {@code queueSectionData}。
     */
    @Unique
    private static void sable$readSectionList(final int x, final int z, final LevelLightEngine engine, final LightLayer layer,
                                              final BitSet mask, final BitSet emptyMask, final Iterator<byte[]> updates) {
        // 严格复刻原版 ClientPacketListener.applyLightData：k 从 0 遍历到 getLightSectionCount()，
        // 区段 Y = k + getMinLightSection()；mask/emptyMask 一律按 0 基索引 k 取值（绝不可用绝对区段 Y 当索引，
        // 否则 overworld 下 getMinLightSection()=-4 → BitSet.get(-4) 抛 IndexOutOfBoundsException 崩溃）。
        for (int k = 0; k < engine.getLightSectionCount(); ++k) {
            final boolean bitMask = mask.get(k);
            final boolean bitEmpty = emptyMask.get(k);
            if (!bitMask && !bitEmpty) {
                continue;
            }

            final DataLayer dataLayer;
            if (bitMask) {
                dataLayer = new DataLayer((byte[]) updates.next());
            } else {
                dataLayer = new DataLayer();
            }

            engine.queueSectionData(layer, SectionPos.of(x, k + engine.getMinLightSection(), z), dataLayer);
        }
    }

    /**
     * 复刻原版 {@code ClientPacketListener.enableChunkLight}：把每个含非空气方块的区段标记为已加载，
     * 让 lightEngine 的 flood-fill 能正确进入这些区段。
     */
    @Unique
    private static void sable$enableChunkLight(final LevelLightEngine engine, final LevelChunk chunk,
                                               final int x, final int z, final ClientLevel level) {
        final LevelChunkSection[] sections = chunk.getSections();
        final ChunkPos chunkPos = chunk.getPos();
        for (int i = 0; i < sections.length; ++i) {
            final int sectionY = level.getSectionYFromSectionIndex(i);
            engine.updateSectionStatus(SectionPos.of(chunkPos, sectionY), !sections[i].hasOnlyAir());
        }
    }
}
