package dev.ryanhcode.sable.sublevel.render.dispatcher;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.forge.debug.SableRenderDebug;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.mixinterface.dynamic_directional_shading.ModelBlockRendererCacheExtension;
import dev.ryanhcode.sable.render.sky_light_shadow.SableDynamicSkyLightShadowPreProcessor;
import dev.ryanhcode.sable.render.sky_light_shadow.SableSkyLightShadows;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.MobilePlatform;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
// (SequencedSet removed: JDK17 has no SequencedSet; LinkedHashSet preserves insertion order)
import java.util.function.Consumer;

public class VanillaSubLevelRenderDispatcher implements SubLevelRenderDispatcher {
    private final LinkedHashSet<RenderType> singleBlockLayers;

    public VanillaSubLevelRenderDispatcher() {
        this.singleBlockLayers = new LinkedHashSet<>();
    }

    public static void setupDynamicEffects(final ShaderInstance shader, final boolean onSubLevel, final boolean upload) {
        final Uniform sableEnableNormalLighting = shader.getUniform("SableEnableNormalLighting");
        final Uniform sableEnableSkyLightShadows = shader.getUniform(SableDynamicSkyLightShadowPreProcessor.ENABLE_UNIFORM);

        if (sableEnableNormalLighting != null) {
            sableEnableNormalLighting.set(onSubLevel ? 1.0F : 0.0F);
            if (upload) {
                sableEnableNormalLighting.upload();
            }
        }

        if (sableEnableSkyLightShadows != null) {
            sableEnableSkyLightShadows.set(onSubLevel || !SableSkyLightShadows.isEnabled() ? 0.0F : 1.0F);
            if (upload) {
                sableEnableSkyLightShadows.upload();
            }
        }

        final Uniform sableSkyLightScale = shader.getUniform("SableSkyLightScale");

        if (sableSkyLightScale != null) {
            sableSkyLightScale.set(1.0f);
            if (upload) {
                sableSkyLightScale.upload();
            }
        }
    }

    /**
     * 依据姿态栈当前的 pose 矩阵，重算并写回法线矩阵。
     *
     * <p><b>为什么必须显式做这件事（1.20.1 字节码实证）：</b>
     * <ul>
     *   <li>{@code PoseStack.setIdentity()}（映射 {@code eij.e}）把 pose 与 normal
     *       <b>一起</b>清成单位阵；</li>
     *   <li>{@code PoseStack.mulPoseMatrix(Matrix4f)}（映射 {@code eij.a(Matrix4f)}）
     *       整个方法体只有一句 {@code pose.mul(matrix)} 后直接 return，
     *       <b>完全不碰 normal 矩阵</b>。</li>
     * </ul>
     * 子层级方块实体的姿态栈恰好是「setIdentity → mulPoseMatrix(相机旋转基座)
     * → mulPoseMatrix(子层级变换)」这样搭起来的，于是 normal <b>恒为单位阵</b> ——
     * 连相机旋转都没有，更不用说子层级自身的旋转。
     *
     * <p><b>后果：</b>方块实体走的是带法线的顶点格式（ENTITY_SOLID / ENTITY_CUTOUT 等），
     * 而着色器里的 {@code Light0_Direction / Light1_Direction} 是<b>视图空间</b>的常量方向，
     * 前提是顶点法线也已被变换到视图空间。normal 停在单位阵时法线仍停在模型空间，
     * 方向性漫反射整体算错：部件某些朝向的面被压成近乎全黑（肉眼就是「只剩一个面」），
     * 且明暗不随相机 / 子层级旋转改变，转到某些角度反而「碰巧正常」。
     *
     * <p><b>为什么固定结构没事、只有可动部件出问题：</b>区块地形的顶点格式不带法线，
     * 方向明暗是在烘焙期直接算进顶点色的（见 {@code SableDynamicDirectionalShading}），
     * 完全不经过 normal 矩阵；而齿轮、传动轴、螺旋桨这类 Create 动能部件全部由
     * 方块实体渲染器 + SuperByteBuffer 绘制，吃的正是 normal 矩阵。这条分界线
     * 与「固定结构显示正常、可动部分异常」的现象完全吻合。
     *
     * @param poseStack 待修正的姿态栈，调用时其栈顶 pose 必须已经构造完毕
     */
    public static void syncNormalMatrix(final PoseStack poseStack) {
        // 现场诊断开关：/sabledbg normal 可实时关掉本修复，回到「normal 恒为单位阵」的旧行为，
        // 让玩家对着同一个部件来回切换、当场判定法线到底是不是主因，省掉一轮重新构建。
        if (!dev.ryanhcode.sable.forge.debug.SableRenderDebug.normalFixEnabled) {
            return;
        }
        final PoseStack.Pose pose = poseStack.last();
        // 取模型视图矩阵的左上 3x3（平移分量在第 4 列，天然被排除，
        // 所以样板区那种 2000 万量级的大平移不会污染法线）。
        final Matrix3f normal = new Matrix3f(pose.pose());

        // 法线矩阵 = 左上 3x3 的逆转置。纯旋转时结果就等于自身，含缩放时也能给出正确法线。
        // 行列式为 0 的退化变换求逆会得到 NaN，进而让整个部件消失，故先判定、退化就保持原样。
        if (java.lang.Math.abs(normal.determinant()) > 1.0e-9f) {
            normal.invert().transpose();
            pose.normal().set(normal);
        }
    }

    /**
     * Checks if this sub-level is a single block, and therefore can use simpler batched rendering
     */
    public static boolean isSingleBlock(final ClientSubLevel subLevel) {
        // [1.20.1 port] 单方块渲染路径（VanillaSingleSubLevelRenderData）在 1.20.1 上不可见：
        // 破坏组装器后子关卡只剩 1 格，自动切到该路径即整体隐形（日志：
        // renderChunkedSubLevel 探针消失、只剩 inworld single BE 刷屏）。
        // 区块化路径已完全修好，强制永远走区块化。
        if (true) {
            return false;
        }
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        final boolean isSingle = bounds != null && bounds.minX() == bounds.maxX() && bounds.minY() == bounds.maxY() && bounds.minZ() == bounds.maxZ();
        if (!isSingle) {
            return false;
        }

        final BlockState blockState = subLevel.getLevel().getBlockState(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()));
        return !blockState.is(SableTags.ALWAYS_CHUNK_RENDERING);
    }

    @Override
    public void onResourceManagerReload( final ResourceManager resourceManager) {
    }

    @Override
    public SubLevelRenderData resize(final ClientSubLevel subLevel, final SubLevelRenderData renderData) {
        if (renderData instanceof VanillaSingleSubLevelRenderData ^ isSingleBlock(subLevel)) {
            renderData.close();

            // Force-rebuild the data
            final SubLevelRenderData data = this.createRenderData(subLevel);
            if (data instanceof VanillaChunkedSubLevelRenderData chunkedRenderData) {
                chunkedRenderData.compileSections(PrioritizeChunkUpdates.NEARBY, new RenderRegionCache(), Minecraft.getInstance().gameRenderer.getMainCamera());
            }

            return data;
        }

        if (renderData instanceof final VanillaChunkedSubLevelRenderData chunkedRenderData) {
            chunkedRenderData.resize();
            chunkedRenderData.compileSections(PrioritizeChunkUpdates.NEARBY, new RenderRegionCache(), Minecraft.getInstance().gameRenderer.getMainCamera());
        }
        return renderData;
    }

    @Override
    public SubLevelRenderData createRenderData(final ClientSubLevel subLevel) {
        if (isSingleBlock(subLevel)) {
            return new VanillaSingleSubLevelRenderData(subLevel);
        }

        final ChunkRenderDispatcher sectionRenderDispatcher = Minecraft.getInstance().levelRenderer.getChunkRenderDispatcher();
        return new VanillaChunkedSubLevelRenderData(subLevel, sectionRenderDispatcher);
    }

    @Override
    public void updateCulling(final Iterable<ClientSubLevel> sublevels, final double cameraX, final double cameraY, final double cameraZ, final Frustum cullFrustum, final boolean isSpectator) {
        // [手机端优化·A1] 仅手机端启用子层级区块视锥剔除：把每个区块的 plot 空间 AABB
        // 变换到主世界空间，用 LevelRenderer 传入的【主世界空间】Frustum 做可见性测试，
        // 结果存进 VanillaChunkedSubLevelRenderData，后续 renderChunkedSubLevel 只画可见区块。
        // PC 端保持源版空实现（早退），行为完全不变。
        if (!MobilePlatform.isMobile()) {
            return;
        }
        for (final ClientSubLevel sublevel : sublevels) {
            final SubLevelRenderData data = sublevel.getRenderData();
            if (data instanceof final VanillaChunkedSubLevelRenderData chunkedRenderData) {
                chunkedRenderData.sable$updateMobileCulling(cullFrustum);
            }
        }
    }

    @Override
    public void renderSectionLayer(final Iterable<ClientSubLevel> sublevels, final RenderType renderType, final ShaderInstance shader, final double cameraX, final double cameraY, final double cameraZ, final Matrix4f modelView, final Matrix4f projection, final float partialTicks) {
        final FogShape fogShape = RenderSystem.getShaderFogShape();

        if (shader.FOG_SHAPE != null && fogShape != FogShape.SPHERE) {
            shader.FOG_SHAPE.set(FogShape.SPHERE.getIndex());
            shader.FOG_SHAPE.upload();
        }

        VanillaSubLevelRenderDispatcher.setupDynamicEffects(shader, true, true);

        for (final ClientSubLevel sublevel : sublevels) {
            final SubLevelRenderData data = sublevel.getRenderData();

            // We'll render the single block sub-levels in a pass afterward
            if (!(data instanceof final VanillaChunkedSubLevelRenderData chunkedRenderData)) {
                this.singleBlockLayers.add(renderType);
                continue;
            }

            chunkedRenderData.renderChunkedSubLevel(renderType, shader, modelView, cameraX, cameraY, cameraZ);
        }

        if (shader.FOG_SHAPE != null && fogShape != FogShape.SPHERE) {
            shader.FOG_SHAPE.set(fogShape.getIndex());
        }

        VanillaSubLevelRenderDispatcher.setupDynamicEffects(shader, false, false);
    }

    @Override
    public void renderAfterSections(final Iterable<ClientSubLevel> sublevels, final double cameraX, final double cameraY, final double cameraZ, final Matrix4f modelView, final Matrix4f projection, final float partialTicks) {
        if (this.singleBlockLayers.isEmpty()) {
            return;
        }

        final ModelBlockRendererCacheExtension ext = (ModelBlockRendererCacheExtension) ModelBlockRenderer.CACHE.get();
        ext.sable$setOnSubLevel(true);

        for (final RenderType layer : this.singleBlockLayers) {
            final BufferBuilder consumer = Tesselator.getInstance().getBuilder();
            consumer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

            for (final ClientSubLevel sublevel : sublevels) {
                final SubLevelRenderData data = sublevel.getRenderData();

                if (!(data instanceof final VanillaSingleSubLevelRenderData singleRenderData)) {
                    continue;
                }

                singleRenderData.renderSingleBlock(layer, consumer, modelView, cameraX, cameraY, cameraZ);
            }

            final BufferBuilder.RenderedBuffer meshData = consumer.end();
            if (meshData != null && !meshData.isEmpty()) {
                // Set up the state so the shader instance is updated
                layer.setupRenderState();

                final ShaderInstance shader = Objects.requireNonNull(RenderSystem.getShader());
                // [BUG-28] 单方块子关卡路径已被 isSingleBlock() 强制禁用（永远返回 false），
                // 当前所有子关卡（含可动部件）均走 VanillaChunkedSubLevelRenderData 区块网格路径。
                // 此处保留原 modelView 行为即可；真正的问题在 renderChunkedSubLevel。
                shader.MODEL_VIEW_MATRIX.set(modelView);
                shader.MODEL_VIEW_MATRIX.upload();
                shader.PROJECTION_MATRIX.set(projection);
                shader.PROJECTION_MATRIX.upload();
                shader.apply();
                setupDynamicEffects(shader, true, true);

                final VertexBuffer singleBlockBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                singleBlockBuffer.bind();
                singleBlockBuffer.upload(meshData);
                // [BUG-28] 单方块子关卡走的是与区块网格完全不同的绘制路径，
                // 同样接入运行时诊断开关，保证 /sabledbg 切换模式时两条路径行为一致，
                // 否则会出现「大结构变了、单方块没变」的误判。
                SableRenderDebug.apply(shader);
                try {
                    singleBlockBuffer.draw();
                } finally {
                    SableRenderDebug.restore(shader);
                }

                // Match every setup with a clear
                singleBlockBuffer.close();
                layer.clearRenderState();

                setupDynamicEffects(shader, false, false);
                shader.clear();
            }
        }

        ext.sable$setOnSubLevel(false);

        this.singleBlockLayers.clear();
    }

    @Override
    public void renderBlockEntities(final Iterable<ClientSubLevel> sublevels, final SubLevelRenderDispatcher.BlockEntityRenderer blockEntityRenderer, final double cameraX, final double cameraY, final double cameraZ, final float partialTick) {
        final Vector3f cameraPosition = new Vector3f();
        final Vector3d chunkOffset = new Vector3d();
        final Matrix4f transformation = new Matrix4f();
        final Matrix4f transformationInverse = new Matrix4f();
        final BlockEntityRenderDispatcherExtension dispatcher = (BlockEntityRenderDispatcherExtension) blockEntityRenderer.getBlockEntityRenderDispatcher();
        final BlockEntityRenderDispatcher vanillaDispatcher = blockEntityRenderer.getBlockEntityRenderDispatcher();
        final PoseStack matrices = new PoseStack();

        int subLevelIndex = -1;

        // [手机端优化·A2] 仅手机端启用「方块实体距离裁剪 + 每帧数量上限」。
        // PC 端：距离阈值置为「不限制」(0)，预算置为无上限(Integer.MAX_VALUE)，行为与源版完全一致。
        final boolean sable$mobileBE = MobilePlatform.isMobile();
        final int sable$beBudgetCap = sable$mobileBE ? java.lang.Math.max(1, SableClientConfig.MOBILE_BE_MAX_PER_FRAME.get()) : Integer.MAX_VALUE;
        final double sable$beDistLimit = sable$mobileBE ? SableClientConfig.MOBILE_BE_RENDER_DISTANCE.get() : 0.0;
        final double sable$beDistLimitSq = sable$beDistLimit > 0.0 ? sable$beDistLimit * sable$beDistLimit : -1.0;
        // 跨所有子层级共享的每帧渲染预算（距离裁剪后仍受此上限约束）。
        int sable$beRemaining = sable$beBudgetCap;

        for (final ClientSubLevel sublevel : sublevels) {
            subLevelIndex++;
            final SubLevelRenderData data = sublevel.getRenderData();

            sublevel.renderPose().rotationPoint().negate(chunkOffset.zero());
            data.getTransformation(cameraX, cameraY, cameraZ, transformation);

            // [v10 回归上游] getTransformation 已恢复「减相机」，逆变换作用于原点即得相机在子层级局部坐标系的位置，
            // 与上游 NeoForge 2.0.3 一字不差。
            transformation.invert(transformationInverse).transformPosition(cameraPosition.zero());
            dispatcher.sable$setCameraPosition(new Vec3(cameraPosition.x - chunkOffset.x(), cameraPosition.y - chunkOffset.y(), cameraPosition.z - chunkOffset.z()));

            // [第22轮·真凶] 1.20.1 的 PoseStack.clear() 不是清空而是只读查询(返回「栈是否只剩一层」)，
            // 矩阵从不复位 -> 多个子关卡循环时变换逐个叠乘：相机旋转基座与「减相机平移」被乘两遍以上，
            // 从第 2 个子关卡起其拉杆被画在相机相关位置 = 「拉杆随玩家移动」。第20轮 plot 轮转分配后
            // 多子关卡同时存活，此上游潜伏缺陷（flywheel 时代该分支从未运行）被引爆。改用 setIdentity() 真复位。
            matrices.setIdentity();
            // 1.20.1 移植修复：renderLevel 姿态栈自带相机旋转，1.21 是绘制时全局注入。
            // 新建姿态栈会丢旋转，顶点被记录成屏幕固定 -> 跟随视角漂浮。先乘回相机旋转基座。
            if (dev.ryanhcode.sable.Sable.SABLE_BE_BASE_POSE != null) {
                matrices.mulPoseMatrix(dev.ryanhcode.sable.Sable.SABLE_BE_BASE_POSE);
            }
            matrices.mulPoseMatrix(transformation);
            // [BUG-28 第二阶段·可动部件方向性光照] 上面两次 mulPoseMatrix 与开头的 setIdentity
            // 都不会维护 normal 矩阵（1.20.1 字节码实证，详见 syncNormalMatrix 的说明），
            // 到这里 normal 仍是单位阵。方块实体渲染器吃的就是它，不补就等于把法线留在模型空间，
            // 方向性漫反射整体算错 —— 可动部件某些面被压成全黑，看着像「只显示一个面」。
            syncNormalMatrix(matrices);

            // ======================================================================
            // [BUG-30 第三十一轮·真正的修复] 「离屏方块实体直通道」
            //
            // 背景（前三十轮踩过的坑，务必读完再改）：
            //   激光发射器的渲染器 shouldRenderOffScreen() 返回 true。原版 1.20.1 的
            //   ChunkRenderDispatcher$RebuildTask#handleBlockEntity（字节码实证，反编译产物见
            //   _bug30/rebuild.txt）确实会把它「同时」放进 renderable 与 global 两个列表，
            //   所以下面那段基于 getRenderableBlockEntities() 的老代码在区块编译产物完好时是能画出光的
            //   —— 这也解释了为什么光「有时候有」。
            //
            //   但编译产物是会瞬时失效的：section 被置脏、重新装配、或 RebuildTask 尚未回填时，
            //   getCompiledChunk() 会读到 UNCOMPILED / 旧空对象，getRenderableBlockEntities() 直接变空列表。
            //   原版世界里这不要紧，因为「离屏方块实体」原本就有一条独立于区块编译产物的全局通道
            //   （LevelRenderer 的 globalBlockEntities）在兜底；而子层级这条渲染路径把两者
            //   合并成了「只读编译产物」一条腿，兜底通道整个丢失。
            //   现象就是玩家看到的：光束毫无规律地出现 / 消失，日志里能连打七组正常探针，
            //   紧接着整整十一秒一条不打（渲染器压根没被调用）。
            //
            // 修复思路：给离屏方块实体补回那条兜底通道。数据源不取区块编译产物，
            //   而是直接枚举 plot 的已加载区块（LevelChunk#getBlockEntities），
            //   与 ClientSubLevel#getRestBounds 中已被验证可用的做法完全一致。
            //   这样只要方块还在、区块还加载着，激光每帧必被渲染，与地形编译状态彻底解耦。
            //
            // 防重复绘制：直通道画过的位置记进 offScreenRendered 集合，
            //   下面遍历编译产物时按位置剔除。半透明光束画两遍会明显加深、边缘发硬，必须避免。
            // ======================================================================
            final java.util.Set<BlockPos> offScreenRendered = new java.util.HashSet<>();
            int loadedChunkCount = 0;
            int totalBlockEntities = 0;
            for (final PlotChunkHolder holder : sublevel.getPlot().getLoadedChunks()) {
                final LevelChunk chunk = holder.getChunk();
                if (chunk == null) {
                    continue;
                }
                loadedChunkCount++;
                for (final BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    totalBlockEntities++;
                    final net.minecraft.client.renderer.blockentity.BlockEntityRenderer<BlockEntity> renderer = vanillaDispatcher.getRenderer(blockEntity);
                    // 只接管「离屏」方块实体。普通方块实体继续走原来的编译产物路径：
                    // 它们数量大、且天然受区块可见性约束，全量直通会白白吃掉性能。
                    if (renderer == null || !renderer.shouldRenderOffScreen(blockEntity)) {
                        continue;
                    }
                    // [手机端优化·A2] 离屏方块实体（如激光发射器）也受距离裁剪 + 每帧预算约束。
                    // 命中预算/距离则跳过渲染，但仍记入 offScreenRendered，避免后续编译产物路径重复绘制。
                    if (sable$mobileBE && (sable$beRemaining <= 0
                            || (sable$beDistLimitSq >= 0.0 && sable$beDistanceSq(sublevel, blockEntity.getBlockPos(), cameraX, cameraY, cameraZ) > sable$beDistLimitSq))) {
                        offScreenRendered.add(blockEntity.getBlockPos());
                        continue;
                    }
                    offScreenRendered.add(blockEntity.getBlockPos());
                    if (sable$mobileBE) {
                        sable$beRemaining--;
                    }
                    blockEntityRenderer.renderSingleBE(blockEntity, matrices, partialTick, -chunkOffset.x, -chunkOffset.y, -chunkOffset.z);
                }
            }

            int sectionCount = 0;
            int compiledSectionCount = 0;
            int compiledBlockEntityCount = 0;

            if (data instanceof final VanillaChunkedSubLevelRenderData chunkedRenderData) {
                for (final ChunkRenderDispatcher.RenderChunk renderSection : chunkedRenderData.allRenderSections()) {
                    sectionCount++;
                    final ChunkRenderDispatcher.CompiledChunk compiled = renderSection.getCompiledChunk();
                    if (compiled != ChunkRenderDispatcher.CompiledChunk.UNCOMPILED) {
                        compiledSectionCount++;
                    }
                    final List<BlockEntity> blockEntities = compiled.getRenderableBlockEntities();
                    compiledBlockEntityCount += blockEntities.size();
                    if (blockEntities.isEmpty()) {
                        continue;
                    }
                    // [手机端优化·A2] 距离裁剪 + 每帧数量上限：过滤掉过远或超出预算的方块实体。
                    // PC 端（sable$mobileBE=false）保持源版行为：仅剔除离屏直通道已画的，其余全画。
                    final List<BlockEntity> toRender;
                    if (sable$mobileBE) {
                        toRender = new java.util.ArrayList<>(blockEntities.size());
                        for (final BlockEntity blockEntity : blockEntities) {
                            if (offScreenRendered.contains(blockEntity.getBlockPos())) {
                                continue; // 离屏已在直通道处理（含预算判定）
                            }
                            if (sable$beRemaining <= 0) {
                                break; // 预算用尽，本区块剩余方块实体全部跳过
                            }
                            if (sable$beDistLimitSq >= 0.0 && sable$beDistanceSq(sublevel, blockEntity.getBlockPos(), cameraX, cameraY, cameraZ) > sable$beDistLimitSq) {
                                continue; // 过远，跳过
                            }
                            toRender.add(blockEntity);
                            sable$beRemaining--;
                        }
                    } else if (offScreenRendered.isEmpty()) {
                        toRender = blockEntities;
                    } else {
                        // 剔除已由直通道画过的离屏方块实体，避免同一帧画两遍。
                        toRender = new java.util.ArrayList<>(blockEntities.size());
                        for (final BlockEntity blockEntity : blockEntities) {
                            if (!offScreenRendered.contains(blockEntity.getBlockPos())) {
                                toRender.add(blockEntity);
                            }
                        }
                    }
                    if (!toRender.isEmpty()) {
                        blockEntityRenderer.renderBlockEntities(toRender, matrices, partialTick, -chunkOffset.x, -chunkOffset.y, -chunkOffset.z);
                    }
                }
            } else if (data instanceof final VanillaSingleSubLevelRenderData singleRenderData) {
                final BlockEntity renderBlockEntity = singleRenderData.getRenderBlockEntity();
                if (renderBlockEntity != null && !offScreenRendered.contains(renderBlockEntity.getBlockPos())) {
                    blockEntityRenderer.renderSingleBE(renderBlockEntity, matrices, partialTick, -chunkOffset.x, -chunkOffset.y, -chunkOffset.z);
                }
            }
        }

        dispatcher.sable$setCameraPosition(null);
    }

    /**
     * [手机端优化·A2] 计算方块实体（plot 局部坐标）到主世界相机的距离平方。
     * 方块实体坐标在子层级 plot 空间（约 2048 万），须经 renderPose 变换到主世界坐标
     * 才能与传入的（主世界）相机坐标比较。
     */
    private static double sable$beDistanceSq(final ClientSubLevel sublevel, final BlockPos bePos, final double camX, final double camY, final double camZ) {
        final Vector3d world = sublevel.renderPose().transformPosition(new Vector3d(bePos.getX() + 0.5, bePos.getY() + 0.5, bePos.getZ() + 0.5));
        final double dx = world.x - camX, dy = world.y - camY, dz = world.z - camZ;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public void rebuild(final Iterable<ClientSubLevel> sublevels) {
        for (final ClientSubLevel sublevel : sublevels) {
            final SubLevelRenderData data = sublevel.getRenderData();
            if (data instanceof final VanillaChunkedSubLevelRenderData chunked) {
                chunked.compileSections(PrioritizeChunkUpdates.NEARBY, new RenderRegionCache(), Minecraft.getInstance().gameRenderer.getMainCamera());
            }
        }
    }

    @Override
    public void preRenderChunks(final Camera camera) {
        // Vanilla fallback has no separate reach-around dispatcher to pre-upload; nothing to do.
    }

    @Override
    public void addDebugInfo(final Consumer<String> consumer) {
    }

    @Override
    public void free() {
    }
}
