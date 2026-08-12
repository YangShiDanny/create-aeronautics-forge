package dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes view area calls redirect to data renderers
 */
@Mixin(ViewArea.class)
public class ViewAreaMixin {

    @Inject(method = "setDirty", at = @At("HEAD"), cancellable = true)
    private void sable$setDirty(final int x, final int y, final int z, final boolean playerChanged, final CallbackInfo ci) {
        final SubLevelContainer plotContainer = ((SubLevelContainerHolder) Minecraft.getInstance().level).sable$getPlotContainer();
        final LevelPlot plot = plotContainer.getPlot(x, z);

        if (plot != null) {
            final ClientSubLevel subLevel = (ClientSubLevel) plot.getSubLevel();

            // renderData may be null in the window between the sub-level being
            // added to the container and its render data being built by the
            // StartTracking/Finalize packet. Skip the dirty-mark this frame
            // rather than crash (compileSections lazily rebuilds it).
            final SubLevelRenderData renderData = subLevel.getRenderData();
            if (renderData != null) {
                renderData.setDirty(x, y, z, playerChanged);
                // [1.20.1 port 修复] 原版 ViewAreaMixin 只标脏包含 (x,y,z) 的那一段。
                // 但子关卡的 setBlock 路径没有走 LevelChunk.setBlockState 的"段落边界邻居段标脏"，
                // 于是当一个块落在段落顶/底/侧面边界、其相邻段落里正对它的那一面需要重新生成时，
                // 相邻段落没被标脏 -> 露出面保持剔除（典型表现：破坏上方块后，下方块只有最上面的面隐形）。
                // 这里补上与 ClientChunk.setBlockState 一致的边界邻居标脏：
                // 块在段落边界 (i==0 / i==15) 时，把该轴向上/下相邻的段落也标脏。
                // renderData.setDirty 内部有 inBounds 保护，越界的相邻段落会被安全忽略。
                final int ix = x & 15;
                final int iy = y & 15;
                final int iz = z & 15;
                if (iy == 0) {
                    renderData.setDirty(x, y - 1, z, playerChanged);
                } else if (ix == 15) {
                    renderData.setDirty(x + 1, y, z, playerChanged);
                }
                if (iz == 0) {
                    renderData.setDirty(x, y, z - 1, playerChanged);
                } else if (iz == 15) {
                    renderData.setDirty(x, y, z + 1, playerChanged);
                }
            }
            ci.cancel();
        }
    }
}
