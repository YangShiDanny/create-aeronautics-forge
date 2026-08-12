package dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.client.renderer.LevelRenderer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;

public class VanillaSubLevelBlockEntityRenderer implements SubLevelRenderDispatcher.BlockEntityRenderer {

    private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
    private final RenderBuffers renderBuffers;
    private final Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

    public VanillaSubLevelBlockEntityRenderer(final BlockEntityRenderDispatcher blockEntityRenderDispatcher, final RenderBuffers renderBuffers, final Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress) {
        this.blockEntityRenderDispatcher = blockEntityRenderDispatcher;
        this.renderBuffers = renderBuffers;
        this.destructionProgress = destructionProgress;
    }

    @Override
    public BlockEntityRenderDispatcher getBlockEntityRenderDispatcher() {
        return this.blockEntityRenderDispatcher;
    }

    /** Expose the buffer source for direct dispatcher.render() calls that bypass renderSingleBE. */
    public MultiBufferSource getBufferSource() {
        return this.renderBuffers.bufferSource();
    }

    @Override
    public void renderSingleBE(final BlockEntity blockEntity, final PoseStack poseStack, final float partialTick, final double cameraX, final double cameraY, final double cameraZ) {
        final BlockPos pos = blockEntity.getBlockPos();
        MultiBufferSource source = this.renderBuffers.bufferSource();

        poseStack.pushPose();
        poseStack.translate((double) pos.getX() - cameraX, (double) pos.getY() - cameraY, (double) pos.getZ() - cameraZ);

        final SortedSet<BlockDestructionProgress> destructionProgresses = this.destructionProgress.get(pos.asLong());
        if (destructionProgresses != null && !destructionProgresses.isEmpty()) {

            final int progress = destructionProgresses.last().getProgress();
            if (progress >= 0) {
                final PoseStack.Pose posestack$pose = poseStack.last();
                final VertexConsumer vertexconsumer = new SheetedDecalTextureGenerator(this.renderBuffers.crumblingBufferSource().getBuffer(ModelBakery.DESTROY_TYPES.get(progress)), posestack$pose.pose(), posestack$pose.normal(), 1.0F);
                source = type -> {
                    final VertexConsumer consumer = this.renderBuffers.bufferSource().getBuffer(type);
                    return type.affectsCrumbling() ? VertexMultiConsumer.create(vertexconsumer, consumer) : consumer;
                };
            }
        }

        // [照明修复] 方块实体取光须与地形一致：地形靠 SableSkyLightScale uniform
        // (= subLevel.getLatestSkyLightScale()/15) 缩放子层级内部烘焙光，故夜间仍亮。
        // 原版 dispatcher 用百万格坐标向主世界光照引擎取光会越界归零 -> 全黑；把局部坐标映射成
        // 真实世界坐标再去主世界取光同样夜间归零。正确做法与
        // BlockEntityRenderDispatcherMixin.sable$getLightColor 的 @Redirect 完全同口径：
        // 用子层级「局部百万格坐标」直接向主世界光照引擎取子层级内部光，再经
        // subLevel.scaleLightColor(...) 按子层级天空光比例缩放。
        final int packedLight;
        final ClientSubLevel sableSubLevel = Sable.HELPER.getContainingClient(blockEntity);
        if (sableSubLevel != null) {
            // 与 BlockEntityRenderDispatcherMixin.sable$getLightColor 的 @Redirect 完全同口径：
            // 用子层级「局部百万格坐标」直接向主世界光照引擎取子层级内部光，
            // 再经 subLevel.scaleLightColor(...) 按子层级天空光比例缩放（地形靠 SableSkyLightScale 同理）。
            int sableLight = sableSubLevel.scaleLightColor(LevelRenderer.getLightColor(sableSubLevel.getLevel(), pos));
            // 兜底：若子层级光照引擎尚未就绪（取光为 0），至少按子层级天空光比例给满光，
            // 与「夜间地形仍亮」保持一致，避免方块实体黑掉。
            if (sableLight == 0) {
                // 兜底：子层级内部取光为 0 时，按子层级天空光比例给满光（与地形夜间仍亮一致）。
                // 满光打包格式 = (blockLight<<4)|(skyLight<<20)，与 scaleLightColor 期望一致，无需依赖 LightTexture。
                sableLight = sableSubLevel.scaleLightColor((15 << 4) | (15 << 20));
            }
            packedLight = sableLight;
        } else {
            packedLight = LevelRenderer.getLightColor(blockEntity.getLevel(), pos);
        }

        final BlockEntityRenderer<BlockEntity> sableRenderer = this.blockEntityRenderDispatcher.getRenderer(blockEntity);
        if (sableRenderer != null) {
            sableRenderer.render(blockEntity, partialTick, poseStack, source, packedLight, 0);
        }

        poseStack.popPose();
    }

    @Override
    public void renderBlockEntities(final List<BlockEntity> blockEntities, final PoseStack poseStack, final float partialTick, final double cameraX, final double cameraY, final double cameraZ) {
        // 1.20.1 移植修复：香草渲染契约要求调 dispatcher.render 前先按「方块坐标-相机」平移。
        // 上游原版此处直接循环 render、丢弃偏移参数（flywheel 时代该分支从未实际运行），
        // 导致子关卡方块实体画在姿态栈原点、跟随视角漂浮。统一委托给已含正确平移的 renderSingleBE。
        for (final BlockEntity blockEntity : blockEntities) {
            this.renderSingleBE(blockEntity, poseStack, partialTick, cameraX, cameraY, cameraZ);
        }
    }
}
