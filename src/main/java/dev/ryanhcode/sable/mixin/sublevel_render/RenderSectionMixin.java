package dev.ryanhcode.sable.mixin.sublevel_render;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.sublevel_render.vanilla.RenderSectionExtension;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Fixes distance check used for priority and chunk building to take sublevels into account
 */
@Mixin(ChunkRenderDispatcher.RenderChunk.class)
public class RenderSectionMixin implements RenderSectionExtension {

    @Shadow
    private AABB bb;
    @Shadow
    private boolean dirty;

    @Unique
    private Set<DirtyListener> sable$listeners;
    @Unique
    private boolean sable$listening = true;

    @Inject(method = "setDirty(Z)V", at = @At("HEAD"))
    public void setDirty(final boolean playerChanged, final CallbackInfo ci) {
        if (this.sable$listening && !this.dirty && this.sable$listeners != null) {
            // Forge 1.20.1: run the dirty notification on the main (render) thread.
            Minecraft.getInstance().execute(() -> {
                for (final DirtyListener listener : this.sable$listeners) {
                    listener.markDirty((ChunkRenderDispatcher.RenderChunk) (Object) this);
                }
            });
        }
    }

    // Forge 1.20.1 专属旁路：原版 RebuildTask.doTask 开头会调 hasAllNeighbors()，
    // 距玩家平方距离 >576 时要求东西南北 4 个邻居区块存在，否则取消编译并重新标脏。
    // 子关卡渲染段位于约 2048 万格外的地块坐标，邻居永远凑不齐 -> 无声死循环 -> 隐形。
    // 1.21 (NeoForge) 已移除该门槛，这里对地块界内的渲染段直接放行，还原 1.21 语义。
    @Inject(method = "hasAllNeighbors()Z", at = @At("HEAD"), cancellable = true)
    private void sable$plotHasAllNeighbors(final CallbackInfoReturnable<Boolean> cir) {
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        final int chunkX = SectionPos.blockToSectionCoord((int) this.bb.minX);
        final int chunkZ = SectionPos.blockToSectionCoord((int) this.bb.minZ);
        if (container.inBounds(chunkX, chunkZ)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * @author RyanH
     * @reason Fixes distance check to take sublevels into account
     */
    @Overwrite
    public double getDistToPlayerSqr() {
        final ClientLevel level = Minecraft.getInstance().level;
        final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        final double x = this.bb.minX + 8.0;
        final double y = this.bb.minY + 8.0;
        final double z = this.bb.minZ + 8.0;
        return Sable.HELPER.distanceSquaredWithSubLevels(level, camera.getPosition(), x, y, z);
    }

    @Override
    public void sable$addDirtyListener(final DirtyListener listener) {
        if (this.sable$listeners == null) {
            this.sable$listeners = new ObjectArraySet<>();
        }
        this.sable$listeners.add(listener);
    }

    @Override
    public void sable$setListening(final boolean listening) {
        this.sable$listening = listening;
    }
}
