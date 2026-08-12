package dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(value = LevelRenderer.class, priority = 1002) // Higher priority to go after Flywheel
public abstract class LevelRendererMixin {

    @Shadow
    private  ClientLevel level;
    private static int sublevelCount(final Iterable<ClientSubLevel> sublevels) {
        int c = 0;
        for (final ClientSubLevel ignored : sublevels) {
            c++;
        }
        return c;
    }


    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "compileChunks", at = @At("TAIL"))
    private void sable$compileSections(final Camera camera, final CallbackInfo ci) {
        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer) ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
        final RenderRegionCache renderRegionCache = new RenderRegionCache();
        final PrioritizeChunkUpdates chunkUpdates = Minecraft.getInstance().options.prioritizeChunkUpdates().get();

        for (final ClientSubLevel sublevel : sublevels) {
            SubLevelRenderData renderData = sublevel.getRenderData();
            if (renderData == null) {
                // Render data not built yet (e.g. startTracking packet not applied this frame).
                // Lazily (re)build it so the sub-level still renders; skip on failure.
                try {
                    sublevel.updateRenderData();
                    renderData = sublevel.getRenderData();
                } catch (final Throwable t) {
                    continue;
                }
            }
            if (renderData != null) {
                renderData.compileSections(chunkUpdates, renderRegionCache, camera);
            }
        }
    }

    @Inject(method = "setupRender", at = @At("TAIL"))
    public void sable$cull(final Camera camera, final Frustum frustum, final boolean hasCapturedFrustum, final boolean isSpectator, final CallbackInfo ci) {
        if (hasCapturedFrustum) {
            return;
        }

        final SubLevelRenderDispatcher dispatcher = SubLevelRenderDispatcher.get();
        dispatcher.preRenderChunks(camera);

        final ProfilerFiller profiler = this.minecraft.getProfiler();
        profiler.push("sub_level_section_occlusion_graph");

        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer) ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
        final Vec3 cameraPosition = camera.getPosition();
        dispatcher.updateCulling(sublevels, cameraPosition.x, cameraPosition.y, cameraPosition.z, frustum, isSpectator);

        profiler.pop();
    }

    @Inject(method = "isChunkCompiled", at = @At("HEAD"), cancellable = true)
    private void sable$isSectionCompiled(final BlockPos blockPos, final CallbackInfoReturnable<Boolean> cir) {
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container == null) {
            return;
        }

        if (container.inBounds(blockPos)) {
            final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(this.level, blockPos);

            if (subLevel == null) {
                cir.setReturnValue(false);
            } else {
            final SubLevelRenderData renderData = subLevel.getRenderData();
            if (renderData == null) {
                cir.setReturnValue(false);
                return;
            }
            final SectionPos sectionPos = SectionPos.of(blockPos);
            cir.setReturnValue(renderData.isSectionCompiled(sectionPos.x(), sectionPos.y(), sectionPos.z()));
            }
        }
    }

    @Inject(method = "renderChunkLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;clear()V"))
    public void sable$renderSubLevels(final RenderType renderType, final PoseStack poseStack, final double x, final double y, final double z, final Matrix4f modelView, final CallbackInfo ci, @Local ShaderInstance shader) {
        // Embeddium / Sodium 会完全接管原版 renderChunkLayer，使本注入点永不触发。
        // 当检测到 Sodium 时，绘制逻辑改由 Forge RenderLevelStageEvent 处理（SubLevelRenderStageHandler）。
        if (dev.ryanhcode.sable.forge.compat.EmbeddiumCompat.isLoaded()) {
            return;
        }

        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer) ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
        // 【1.20.1 关键修正·NDC 探针实锤】Forge 1.20.1 的 renderChunkLayer(RenderType, PoseStack, x, y, z, Matrix4f)
        // 中的 Matrix4f 参数是【投影矩阵】，不是模型视图矩阵（NeoForge 1.21 同位参数才是模型视图）！
        // vanilla 源码：MODEL_VIEW_MATRIX.set(poseStack.last().pose())、PROJECTION_MATRIX.set(该参数)。
        // 此前把投影矩阵当模型视图传下去，着色器算成 投影×投影×顶点 → ndc.z=1.095>1 整段被 GPU 静默裁掉（隐形根因）。
        final Matrix4f projection = modelView; // 该参数实为投影矩阵
        final Matrix4f realModelView = new Matrix4f(poseStack.last().pose()); // 真正的模型视图
        SubLevelRenderDispatcher.get().renderSectionLayer(sublevels, renderType, shader, x, y, z, realModelView, projection, Minecraft.getInstance().getPartialTick());
        // Forge 1.20.1: draw the single-block sub-levels here while a section-layer
        // shader is guaranteed to be bound (inside renderSectionLayer, before clear()).
        // This replaces the original NeoForge hook that fired at DimensionSpecialEffects.constantAmbientLight(),
        // which does not exist in 1.20.1.
        SubLevelRenderDispatcher.get().renderAfterSections(sublevels, x, y, z, realModelView, projection, Minecraft.getInstance().getPartialTick());
    }

    @Inject(method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V", at = @At("TAIL"))
    public void sable$renderSubLevelLayers(final RenderType renderType, final PoseStack poseStack, final double x, final double y, final double z, final Matrix4f modelView, final CallbackInfo ci) {
        // Forge 1.20.1: Veil's layered RenderType wrappers are unavailable.
        // The primary sub-level render path (sable$renderSubLevels) already
        // handles the base render type. Per-layer sub-level passes are
        // deferred to a later phase; this is the simplified/downgraded mode.
    }
}
