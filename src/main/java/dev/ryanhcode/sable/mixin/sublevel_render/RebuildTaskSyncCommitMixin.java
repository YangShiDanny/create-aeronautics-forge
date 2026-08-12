package dev.ryanhcode.sable.mixin.sublevel_render;

import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * BUG-28 根因修复。
 *
 * <p>Forge 1.20.1 的 {@code ChunkRenderDispatcher.RenderChunk.compileSync(RenderRegionCache)}
 * （即 {@code rebuildChunkSync} 内部转调的方法）在调用 {@code RebuildTask.doTask(pack)} 后，
 * 把返回的 {@link CompletableFuture} 直接丢弃（字节码里是 {@code pop}）。而真正把编译结果
 * （{@code compiledChunk} 元数据 + 顶点缓冲）提交回 {@code RenderChunk} 的逻辑，
 * 就包在这个 future 内部（{@code ac.c(uploadTasks).handle(...)} 里调用 {@code setCompiledChunk}）。</p>
 *
 * <p>子关卡走主渲染线程的「同步烘焙」路径（{@code VanillaChunkedSubLevelRenderData.compileSections}
 * 在调用 {@code rebuildChunkSync} 前设了 {@code beginSubLevelBuild} ThreadLocal，
 * 烘焙本身确实在主线程同步完成、变换也已套用），但因为 future 被丢弃、提交从未发生，
 * 紧接着 {@code getCompiledChunk()} 读到的是旧空对象 → 物理化后方块整体隐形。</p>
 *
 * <p>修复：在 {@code doTask} 返回处，当子关卡烘焙 ThreadLocal 激活时，
 * 把 future 同步 {@code join()} 住，等上传线程把结果写回 {@code RenderChunk} 后再放行。
 * 普通（非子关卡）区块重建时 ThreadLocal 未激活，保持原行为不变。</p>
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
public abstract class RebuildTaskSyncCommitMixin {

    @Inject(method = "doTask", at = @At("RETURN"))
    private void sable$forceSyncCommit(final CallbackInfoReturnable<CompletableFuture<?>> cir) {
        if (!SableDynamicDirectionalShading.isBuildingSubLevel()) {
            return;
        }
        final CompletableFuture<?> future = cir.getReturnValue();
        if (future == null) {
            return;
        }
        // 死锁修复（javap 实锤）：doTask 返回的 future 内部通过 Util.combineFutures 聚合多个
        // 上传 future，而每个上传 future 用 RenderSystem.recordRenderCall 把 GPU 上传排到
        // 「主线程渲染循环」执行，future 在主线程跑完上传回调（含 setCompiledChunk）后才完成。
        // 若在主线程上 future.join()，主线程会等自己跑上传 → 卡死在加载地形。
        // 改为在后台线程等待完成：主线程照常跑渲染循环、执行上传、调用 setCompiledChunk，
        // 后台线程等 future 完成后自然退出。这正是原版异步烘焙路径（worker 线程 join、主线程跑上传）的模式。
        CompletableFuture.runAsync(() -> {
            try {
                future.join();
            } catch (final Throwable t) {
                LogManager.getLogger("SableBug28").warn("[BUG28·修复] 子关卡区块同步提交被中断：{}", t.getMessage());
            }
        });
    }
}
