package dev.ryanhcode.sable.sublevel.render.vanilla;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.compatibility.SableIrisCompat;
import dev.ryanhcode.sable.forge.debug.SableRenderDebug;
import dev.ryanhcode.sable.mixin.sublevel_render.RenderSectionAccessor;
import dev.ryanhcode.sable.mixinterface.sublevel_render.vanilla.RenderSectionExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.AABB;
import org.joml.*;
import org.lwjgl.opengl.GL11;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.joml.Quaternionf;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * A renderer and view area for a {@link dev.ryanhcode.sable.sublevel.SubLevel}.
 */
public class VanillaChunkedSubLevelRenderData implements SubLevelRenderData {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** [BUG-28 诊断] 一次性诊断去重集合，避免逐帧刷屏。 */
    private static final Set<String> DIAGNOSED = new HashSet<>();

    /**
     * [BUG-28 诊断·实例台账] 当前存活的区块化子关卡渲染数据（按对象身份去重）。
     *
     * <p>日志实测：同一网格原点 (2.0481008E7, 96.0, 2.0481024E7) 上并存两个渲染数据实例，
     * 其中 #1601730883 的 6 个渲染段在全部 5 个图层上非空段数都是 0（表现为整体隐形），
     * 而 #1434062347 在同一位置 solid 非空=4。若确属「同一 plot 被两份渲染数据抢占」，
     * 那么绕序 / 剔除 / 着色全是无辜的，真正要修的是渲染数据的创建与回收。
     * 这里把每个实例的出生与销毁都记下来，用来判定是重复创建还是旧实例没回收。
     */
    private static final Set<VanillaChunkedSubLevelRenderData> LIVE_INSTANCES =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /** [BUG-28 诊断·实例台账] 累计创建次数，用于日志节流（曾出现 1.2GB 日志，必须防刷屏）。 */
    private static int instanceCreations = 0;

    /** [BUG-28 诊断·实例台账] 累计销毁次数，节流规则与创建一致，两者应当成对出现。 */
    private static int instanceCloses = 0;

    private static final Matrix4f TRANSFORM = new Matrix4f();
    private static final Matrix4f MODEL_MATRIX = new Matrix4f();
    /**
     * 上一次按世界朝向烘焙方向性亮度时，子关卡 logical 朝向对应的「世界上方向」向量。
     * 用于检测刚体朝向变化：变化时整段重烘焙，使翻转后的顶面/底面亮度跟着世界转。
     */
    private Vector3d sable$lastShadeUp = null;

    /**
     * [手机端优化·A1] 视锥剔除结果缓存：仅手机端（MobilePlatform.isMobile()）在
     * updateCulling 阶段填充，保存当前帧对主世界相机可见的渲染段。PC 端永不填充，
     * renderChunkedSubLevel 因此不过滤，行为与源版完全一致。
     */
    private final java.util.Set<ChunkRenderDispatcher.RenderChunk> sable$visibleSections = new java.util.HashSet<>();

    private final Vector3d origin = new Vector3d();
    /**
     * The origin(minimum) of the render section grid
     */
    private final Vector3i chunkOrigin = new Vector3i();
    /**
     * The sub-level this renderer is for
     */
    private final ClientSubLevel subLevel;
    /**
     * The size of the render section grid
     */
    private final Vector3i size = new Vector3i();
    /**
     * All render sections this renderer stores
     */
    private final ObjectList<ChunkRenderDispatcher.RenderChunk> allRenderSections = new ObjectArrayList<>();
    /**
     * All dirty render sections this renderer stores
     */
    private final ObjectList<ChunkRenderDispatcher.RenderChunk> dirtyRenderSections = new ObjectArrayList<>();
    /**
     * The grid of render sections
     */
    private ChunkRenderDispatcher.RenderChunk[] renderSections = null;
    /**
     * The section render dispatcher to build sections through
     */
    private final ChunkRenderDispatcher sectionRenderDispatcher;

    /**
     * Creates a new renderer for the given sub-level
     *
     * @param subLevel the sub-level to render
     */
    public VanillaChunkedSubLevelRenderData(final ClientSubLevel subLevel, final ChunkRenderDispatcher sectionRenderDispatcher) {
        this.subLevel = subLevel;
        this.sectionRenderDispatcher = sectionRenderDispatcher;
        this.resize();

        LIVE_INSTANCES.add(this);
        instanceCreations++;
    }

    /**
     * [BUG-28 诊断·实例台账] 列出除自己之外、网格原点完全相同的其他存活实例。
     *
     * <p>返回非空字符串即代表「同一 plot 上并存多份渲染数据」这一硬伤成立，
     * 此时隐形的直接解释就是：被渲染的那份不是真正持有方块网格的那份。
     */
    private static String describeOriginConflict(final VanillaChunkedSubLevelRenderData self) {
        final StringBuilder sb = new StringBuilder();
        for (final VanillaChunkedSubLevelRenderData other : LIVE_INSTANCES) {
            if (other == self || !other.origin.equals(self.origin)) {
                continue;
            }
            sb.append(sb.length() == 0 ? "" : "、")
                    .append('#').append(System.identityHashCode(other))
                    .append("(子关卡").append(other.subLevel.getUniqueId()).append(')');
        }
        return sb.length() == 0 ? "" : "；【原点冲突】同一网格原点上还活着：" + sb;
    }

    /**
     * Gets a section in global section coordinates
     *
     * @param sections the section array
     * @param size     the dimensions of the section grid
     * @param origin   the origin of the section grid
     * @param x        the global x coordinate
     * @param y        the global y coordinate
     * @param z        the global z coordinate
     * @return the section if it exists
     */
    private static ChunkRenderDispatcher.RenderChunk getSection(final ChunkRenderDispatcher.RenderChunk[] sections, final Vector3i size, final Vector3i origin, final int x, final int y, final int z) {
        final int relX = (x - origin.x());
        final int relY = (y - origin.y());
        final int relZ = (z - origin.z());

        if (relX < 0 || relY < 0 || relZ < 0) {
            return null;
        }

        if (relX >= size.x() || relY >= size.y() || relZ >= size.z()) {
            return null;
        }

        return sections[relX + relY * size.x() + relZ * size.x() * size.y()];
    }

    /**
     * Gets an index in the render section grid from a global position
     */
    private int getIndex(final int x, final int y, final int z) {
        return (x - this.chunkOrigin.x()) + (y - this.chunkOrigin.y()) * this.size.x() + (z - this.chunkOrigin.z()) * this.size.x() * this.size.y();
    }

    /**
     * Checks if a global section coordinate is in bounds
     */
    private boolean inBounds(final int x, final int y, final int z) {
        final int localX = x - this.chunkOrigin.x();
        final int localY = y - this.chunkOrigin.y();
        final int localZ = z - this.chunkOrigin.z();
        return localX >= 0 && localY >= 0 && localZ >= 0 &&
                localX < this.size.x() && localY < this.size.y() && localZ < this.size.z();

    }

    public void resize() {
        final ChunkRenderDispatcher.RenderChunk[] oldRenderSections = this.renderSections;
        final Collection<ChunkRenderDispatcher.RenderChunk> oldRenderSectionsList = new ObjectArrayList<>(this.allRenderSections);

        this.renderSections = null;
        this.allRenderSections.clear();
        this.dirtyRenderSections.clear();

        final BoundingBox3ic bounds = this.subLevel.getPlot().getBoundingBox();


        if (bounds != null && !bounds.equals(BoundingBox3i.EMPTY) && bounds.volume() > 0.0) {
            final Vector3i minChunkPos = new Vector3i(bounds.minX() >> 4, bounds.minY() >> 4, bounds.minZ() >> 4);
            final Vector3i maxChunkPos = new Vector3i(bounds.maxX() >> 4, bounds.maxY() >> 4, bounds.maxZ() >> 4);

            final Vector3i oldSize = new Vector3i(this.size);
            final Vector3i oldOrigin = new Vector3i(this.chunkOrigin);

            this.size.set(maxChunkPos.x() - minChunkPos.x() + 1, maxChunkPos.y() - minChunkPos.y() + 1, maxChunkPos.z() - minChunkPos.z() + 1);
            this.chunkOrigin.set(minChunkPos);
            this.origin.set(minChunkPos.x() << 4, minChunkPos.y() << 4, minChunkPos.z() << 4);

            this.renderSections = new ChunkRenderDispatcher.RenderChunk[this.size.x() * this.size.y() * this.size.z()];

            for (int x = minChunkPos.x(); x <= maxChunkPos.x(); x++) {
                for (int y = minChunkPos.y(); y <= maxChunkPos.y(); y++) {
                    for (int z = minChunkPos.z(); z <= maxChunkPos.z(); z++) {
                        final ChunkRenderDispatcher.RenderChunk oldSection = getSection(oldRenderSections, oldSize, oldOrigin, x, y, z);
                        final ChunkRenderDispatcher.RenderChunk newSection;

                        if (oldRenderSections != null && oldSection != null) {
                            newSection = oldSection;
                        } else {
                            newSection = this.sectionRenderDispatcher.new RenderChunk(-1, x << 4, y << 4, z << 4);
                            ((RenderSectionExtension) newSection).sable$addDirtyListener(this.dirtyRenderSections::add);
                        }

                        if (newSection.isDirty()) {
                            this.dirtyRenderSections.add(newSection);
                        }
                        this.renderSections[this.getIndex(x, y, z)] = newSection;
                        this.allRenderSections.add(newSection);
                    }
                }
            }

            // free old chunks
            if (oldRenderSections != null) {
                // [1.20.1 缓冲泄漏修复] 释放旧渲染段前先冲刷待上传队列，
                // 防止尚未上传的 RenderedBuffer 因顶点缓冲失效被跳过、永久滞留在 BufferBuilder 里。
                this.sectionRenderDispatcher.uploadAllPendingUploads();
                for (final ChunkRenderDispatcher.RenderChunk oldSection : oldRenderSectionsList) {
                    // if not in bounds
                    final SectionPos oldSectionPos = SectionPos.of(oldSection.getOrigin());
                    if (oldSectionPos.getX() < minChunkPos.x() || oldSectionPos.getX() > maxChunkPos.x() ||
                            oldSectionPos.getY() < minChunkPos.y() || oldSectionPos.getY() > maxChunkPos.y() ||
                            oldSectionPos.getZ() < minChunkPos.z() || oldSectionPos.getZ() > maxChunkPos.z()) {

                        oldSection.releaseBuffers();
                        oldSection.updateGlobalBlockEntities(Set.of());
                        oldSection.compiled.set(ChunkRenderDispatcher.CompiledChunk.UNCOMPILED);
                    }
                }
            }
        }
    }

    @Override
    public void rebuild() {
        for (final ChunkRenderDispatcher.RenderChunk renderSection : this.allRenderSections) {
            renderSection.setDirty(true);
            ((RenderSectionAccessor) renderSection).getGlobalBlockEntities().clear();
        }
    }

    @Override
    public void compileSections(final PrioritizeChunkUpdates chunkUpdates, final RenderRegionCache renderRegionCache, final Camera camera) {
        // [1.20.1 动态方向着色] 刚体旋转后，区块是局部烘焙的，方向性亮度（顶亮底暗）
        // 不会跟着转。这里检测子关卡 logical 朝向变化，变化时整段重烘焙，
        // 使烘焙期 getShade 能用「世界朝向」算出正确亮度（见 ModelBlockRendererMixin / AmbientOcclusionFaceMixin）。
        // [1.20.1 动态方向着色] 用 renderPose（与 renderSection 网格旋转完全一致，L409 同款）
        // 作为烘焙朝向，使烘焙期 getShade 算出的方向性亮度跟可见网格朝向一致；
        // 物理翻转后 renderPose 含翻转 -> 旋转生效、亮度跟随世界朝向。
        final Quaterniondc shadeOri = this.subLevel.renderPose().orientation();
        final Vector3d shadeUp = new Vector3d(0.0, 1.0, 0.0);
        shadeOri.transform(shadeUp);
        if (this.sable$lastShadeUp == null || this.sable$lastShadeUp.distance(shadeUp) > 2.0e-3) {
            if (this.sable$lastShadeUp == null) {
                this.sable$lastShadeUp = new Vector3d();
            }
            this.sable$lastShadeUp.set(shadeUp);
            this.rebuild();
        }

        // [BUG-28 诊断] 玩家敲了 /sabledbg rebuild 时整段重烘焙一次，
        // 好让面朝向统计能随时重新产出，不必退出重进世界。
        if (SableRenderDebug.consumeRebuildRequest()) {
            this.rebuild();
        }

        if (this.dirtyRenderSections.isEmpty()) {
            return;
        }


        final ProfilerFiller profiler = Minecraft.getInstance().getProfiler();
        final Vector3d cameraPos = JOMLConversion.atCenterOf(camera.getBlockPosition()).sub(8, 8, 8);
        this.subLevel.logicalPose().transformPositionInverse(cameraPos);

        // [BUG-28 诊断] 本轮烘焙的面朝向统计从零开始。
        SableDynamicDirectionalShading.resetQuadStats();

        // [1.20.1 动态方向着色] 标记当前线程正在构建子关卡区块 + 带上世界朝向，
        // 使烘焙期 getShade 把面方向旋转到世界方向（近处同步构建在渲染线程上，标志对当前线程可见）。
        // [手机端优化·A3] 分帧限流：手机端每帧最多同步编译 MOBILE_CHUNK_COMPILE_PER_FRAME 个脏区块，
        // 其余留到后续帧（从 dirtyRenderSections 移出已处理的，未处理的保留），PC 端不限流一次全量编译。
        final int sable$compileBudget = dev.ryanhcode.sable.MobilePlatform.isMobile()
                ? java.lang.Math.max(1, dev.ryanhcode.sable.SableClientConfig.MOBILE_CHUNK_COMPILE_PER_FRAME.get())
                : Integer.MAX_VALUE;
        int sable$compiledThisFrame = 0;

        SableDynamicDirectionalShading.beginSubLevelBuild(shadeOri);
        try {
            for (final java.util.Iterator<ChunkRenderDispatcher.RenderChunk> it = this.dirtyRenderSections.iterator(); it.hasNext(); ) {
                if (sable$compiledThisFrame >= sable$compileBudget) {
                    break;
                }
                final ChunkRenderDispatcher.RenderChunk renderSection = it.next();
                it.remove(); // 已处理，移出脏列表；未处理的留待后续帧
                ((RenderSectionExtension) renderSection).sable$setListening(false);

                // [1.20.1 动态方向着色·关键] 子关卡渲染段一律【同步】烘焙（在当前渲染线程上）。
                // 原分支依赖视频设置「区块构建优先级」（NEARBY/PLAYER_AFFECTED 才同步），
                // 默认设置 NONE 时全部走 rebuildChunkAsync -> Worker 线程烘焙，
                // 而 beginSubLevelBuild 的 ThreadLocal 标记只在渲染线程可见，
                // 导致 getShade 旋转逻辑从未触发（日志铁证：AO 类在 Worker-Main-19 首次加载）。
                // 子关卡渲染段数量极少（通常 1~2 个），同步烘焙无性能风险。
                profiler.push("sublevel_build_sync");
                this.sectionRenderDispatcher.rebuildChunkSync(renderSection, renderRegionCache);
                // 关键点：Forge 1.20.1 的 rebuildChunkSync 内部 compileSync 会把「提交 future」丢弃，
                // 顶点虽在主线程同步建好（ThreadLocal 生效），但 setCompiledChunk + 顶点缓冲上传
                // 永远不执行 -> getCompiledChunk() 读到旧空对象 -> 方块隐形。
                // RebuildTaskSyncCommitMixin 在 doTask 返回处、子关卡烘焙 ThreadLocal 激活时强制 join，
                // 等提交完成后再返回，因此此处返回时本段已可绘制。
                profiler.pop();

                renderSection.setNotDirty();
                ((RenderSectionExtension) renderSection).sable$setListening(true);
                sable$compiledThisFrame++;
            }
        } finally {
            SableDynamicDirectionalShading.endSubLevelBuild();
        }

        // [1.20.1 缓冲泄漏修复·根因] rebuildChunkSync 烘焙出的 RenderedBuffer 是排队等下一帧上传的，
        // 若上传执行前渲染段先被 releaseBuffers（边界频繁变化时会发生），
        // 原版上传逻辑检测到 VertexBuffer 已失效会直接 return 而【不释放】RenderedBuffer，
        // 导致与 GUI/实体渲染共享的 fixedBufferPack BufferBuilder 永不复位、单调膨胀到 2GB
        // （扩容目标整数溢出为负 → OutOfMemoryError → 后续写入 IndexOutOfBoundsException 崩溃）。
        // 此处在渲染线程立即冲刷上传队列：此刻顶点缓冲必然有效，上传即释放，杜绝泄漏。
        // [手机端优化·A3] 即便本帧只编译了部分脏区块，也要冲刷这批已建好的顶点缓冲，避免泄漏。
        this.sectionRenderDispatcher.uploadAllPendingUploads();
    }

    @Override
    public int getVisibleSectionCount() {
        return this.allRenderSections.size();
    }

    @Override
    public ClientSubLevel getSubLevel() {
        return this.subLevel;
    }

    @Override
    public boolean isSectionCompiled(final int x, final int y, final int z) {
        if (this.renderSections == null) {
            return false;
        }

        if (!this.inBounds(x, y, z)) {
            return true;
        }

        final int index = this.getIndex(x, y, z);
        return index >= 0 && index < this.renderSections.length && this.renderSections[index].compiled.get() != ChunkRenderDispatcher.CompiledChunk.UNCOMPILED;
    }

    @Override
    public void setDirty(final int x, final int y, final int z, final boolean playerChanged) {
        if (this.renderSections == null) {
            return;
        }

        if (!this.inBounds(x, y, z)) {
            return;
        }

        final int index = this.getIndex(x, y, z);
        if (index >= 0 && index < this.renderSections.length) {
            this.renderSections[index].setDirty(playerChanged);
        }
    }

    /**
     * @return all render sections this renderer stores
     */
    public ObjectList<ChunkRenderDispatcher.RenderChunk> allRenderSections() {
        return this.allRenderSections;
    }

    /**
     * [手机端优化·A1] 依据主世界相机的视锥，对本子关卡每个区块做可见性测试。
     * 区块在 plot 局部空间（约 2048 万坐标），须先经 renderPose 变换到主世界坐标，
     * 才能用 LevelRenderer 传入的【主世界空间】Frustum 判定。结果写入 sable$visibleSections。
     *
     * <p>旋转子关卡下，16×16×16 的 plot 方块经 renderPose 旋转后不再是轴对齐，
     * 这里取变换后两角的极值得到保守的世界空间 AABB（无旋转时精确），保证绝不会误剔可见区块。
     *
     * @param frustum 主世界空间视锥（来自 LevelRenderer.setupRender）
     */
    public void sable$updateMobileCulling(final net.minecraft.client.renderer.culling.Frustum frustum) {
        this.sable$visibleSections.clear();
        final Pose3dc renderPose = this.subLevel.renderPose();
        final Vector3d min = new Vector3d();
        final Vector3d max = new Vector3d();
        for (final ChunkRenderDispatcher.RenderChunk renderSection : this.allRenderSections) {
            final BlockPos origin = renderSection.getOrigin();
            // plot 空间 16×16×16 区块包围盒两角
            renderPose.transformPosition(min.set(origin.getX(), origin.getY(), origin.getZ()));
            renderPose.transformPosition(max.set(origin.getX() + 16, origin.getY() + 16, origin.getZ() + 16));
            // 旋转后取两角极值得到保守的世界空间 AABB
            final AABB worldAabb = new AABB(
                    java.lang.Math.min(min.x, max.x), java.lang.Math.min(min.y, max.y), java.lang.Math.min(min.z, max.z),
                    java.lang.Math.max(min.x, max.x), java.lang.Math.max(min.y, max.y), java.lang.Math.max(min.z, max.z));
            if (frustum.isVisible(worldAabb)) {
                this.sable$visibleSections.add(renderSection);
            }
        }
    }

    public void renderChunkedSubLevel(final RenderType layer, final ShaderInstance shader, final Matrix4f modelView, final double camX, final double camY, final double camZ) {
        final Pose3dc renderPose = this.subLevel.renderPose();
        final Vector3d renderPos = new Vector3d(renderPose.position());
        final Quaterniondc renderRot = renderPose.orientation();
        final Vector3d renderCOR = renderRot.transform(new Vector3d(renderPose.rotationPoint()).sub(this.origin));

        float[] oldFogColor = null;

        if (shader.FOG_COLOR != null) {
            final WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(this.subLevel.getLevel());

            final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            final WaterOcclusionRegion occludingRegion = container.getOccludingRegion(camera.getPosition());

            // TODO: Redo to swap to main fog instead of just getting rid of it
            if (occludingRegion != null && Sable.HELPER.getContaining(this.subLevel.getLevel(), occludingRegion.getVolume().getMinBlockPos()) == this.subLevel) {
                oldFogColor = RenderSystem.getShaderFogColor();
                shader.FOG_COLOR.set(0.0f, 0.0f, 0.0f, 0.0f);
                shader.FOG_COLOR.upload();
            }
        }

        final Uniform sableSkyLightScale = shader.getUniform("SableSkyLightScale");
        if (sableSkyLightScale != null) {
            final int skyLight = this.subLevel.getLatestSkyLightScale();
            sableSkyLightScale.set(skyLight / 15.0f);
            sableSkyLightScale.upload();
        }


        // 还原原版 sable-forge 1.20.1 方案：transform 仅做旋转，
        // translate 一项因 fogOffset 抵消而恒为 0；平移完全交给着色器 uniform CHUNK_OFFSET。
        renderPos.sub(renderCOR);

        final Matrix4f transform = TRANSFORM.identity();

        // convert the camera pos to local to the origin / rotated
        final Vector3d fogOffset = new Vector3d(camX, camY, camZ).sub(renderPos).mul(-1.0);
        transform.translate((float) (renderPos.x() - camX - fogOffset.x), (float) (renderPos.y() - camY - fogOffset.y), (float) (renderPos.z() - camZ - fogOffset.z));
        transform.rotate(new Quaternionf(renderRot));

        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(modelView.mul(transform, MODEL_MATRIX));
            shader.MODEL_VIEW_MATRIX.upload();
            // 与单方块路径(renderAfterSections)对齐：apply() 重新上传全部 uniform，
            // 避免 mixin 在 shader.clear() 前注入导致部分 uniform 状态缺失而整块隐形。
            shader.apply();
        }

        final Uniform chunkOffsetUniform = shader.CHUNK_OFFSET;

        // ==============================================================================
        // [BUG-28 诊断] 一次性输出「方向性隐形」的两个关键判据，每个子关卡 + 图层只打一次：
        //   1) 旋转矩阵 3x3 行列式 —— 若为负数，说明变换含镜像，
        //      三角形绕序被翻转，GL 背面剔除会把**正面剔掉、只留背面**，
        //      表现正是「只显示一面 / 换个方向看才正常」。
        //   2) 非空渲染段数量 —— 若远小于总段数，说明是区块编译缺失而非剔除问题。
        // 另外顺带打印四元数模长（物理引擎漂移会让它偏离 1，导致附带缩放）。
        // ==============================================================================
        // RenderType.toString() 会把整个 CompositeState 展开成数百字符，
        // 直接打进日志会把后面真正有用的数值挤出可读范围，这里压成短名。
        final String layerName = shortLayerName(layer);
        // ==============================================================================
        // 诊断 key 必须带上当前 /sabledbg 模式。
        //
        // 早先的 key 只有「子关卡 + 图层」，于是进世界头一分钟就把所有组合打印完、
        // 此后永久静音。结果玩家后来切到模式 1~6 做的全部实验，
        // 一条 GL 回读日志都没留下 —— 「模式 4 到底有没有真的把绕序改成 CW」
        // 这种最基本的问题都无法回答，判读只能靠猜。
        // 加上模式后，每切换一次模式都会重新打印一轮，实验才有据可依。
        // ==============================================================================
        final int debugMode = SableRenderDebug.mode;
        final String diagKey = "chunked:" + System.identityHashCode(this) + ":" + layerName + ":模式" + debugMode;
        final boolean doDiagnose = DIAGNOSED.add(diagKey);
        int nonEmptySections = 0;

        // 在关闭剔除之前先把「进来时」的真实 GL 状态抓下来 —— 这是判断
        // Embeddium / Oculus 是否留下了非原版绕序（GL_CW = 2304，GL_CCW = 2305）的唯一依据。
        // 原版全程使用 GL_CCW；一旦这里读到 GL_CW，所有按原版绕序烘焙的子关卡网格
        // 都会被反向剔除，正面被丢弃、只剩背面 —— 即「只显示一面」。
        int glFrontFace = -1;
        int glCullMode = -1;
        boolean glCullEnabled = false;
        boolean glDepthEnabled = false;
        int glDepthFunc = -1;
        boolean glDepthMask = false;
        if (doDiagnose) {
            glFrontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
            glCullMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
            glCullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            glDepthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            glDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            glDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        }

        // ==============================================================================
        // [BUG-28 现场诊断开关]
        //
        // 此前这里写死了 RenderSystem.disableCull()（用于验证绕序假设）。实测结论：
        //   * translucent 图层（流体）关剔除后恢复可见 —— 该图层确有朝向问题；
        //   * solid 图层关剔除后**依然不可见** —— 证明不透明方块的隐形与背面剔除无关；
        //   * 且写死关剔除会让实心结构的内壁参与绘制，与外壁共面产生深度冲突，
        //     表现为「黑色与原贴图交替频闪」的重影。
        //
        // 因此改为运行时可切换：默认 0（完全不改状态），游戏内 /sabledbg <模式> 切换。
        // 具体模式与判定含义见 SableRenderDebug 类文档。
        // ==============================================================================
        SableRenderDebug.apply(shader);
        // apply 之后立刻回读一次，确认诊断模式究竟有没有真正落到 GL 上。
        // 只有拿到这个回读值，模式 4（改绕序）、模式 5（剔除正面）之类的实验结论才可采信。
        final String glAfterApply = doDiagnose ? SableRenderDebug.snapshotGlState() : null;
        // 诊断用：记录首个非空渲染段实际写入的 CHUNK_OFFSET 平移量。
        // 子关卡的平移完全依赖这个 uniform（transform 里的 translate 因 fogOffset 抵消恒为 0），
        // 一旦着色器没有该 uniform 或数值算错，整个结构就会被平移到错误位置甚至视锥体之外，
        // 表现就是「几何存在、draw 也调用了，但屏幕上什么都没有」。
        float firstCox = Float.NaN;
        float firstCoy = Float.NaN;
        float firstCoz = Float.NaN;
        try {
            for (final ChunkRenderDispatcher.RenderChunk renderSection : this.allRenderSections) {
                if (renderSection.getCompiledChunk().isEmpty(layer)) {
                    continue;
                }
                // [手机端优化·A1] 手机端：跳过视锥外的区块。PC 端 isMobile() 为 false，
                // 且 sable$visibleSections 当帧不会被填充（updateCulling 早退），故恒不过滤。
                if (dev.ryanhcode.sable.MobilePlatform.isMobile() && !this.sable$visibleSections.contains(renderSection)) {
                    continue;
                }
                nonEmptySections++;

                final BlockPos pos = renderSection.getOrigin();

                if (chunkOffsetUniform != null) {
                    final Vector3d fogOffsetRot = renderRot.transformInverse(fogOffset, new Vector3d());
                    final float cox = (float) (pos.getX() - this.origin.x() + fogOffsetRot.x);
                    final float coy = (float) (pos.getY() - this.origin.y() + fogOffsetRot.y);
                    final float coz = (float) (pos.getZ() - this.origin.z() + fogOffsetRot.z);
                    if (nonEmptySections == 1) {
                        firstCox = cox;
                        firstCoy = coy;
                        firstCoz = coz;
                    }
                    chunkOffsetUniform.set(cox, coy, coz);
                    chunkOffsetUniform.upload();
                }

                final VertexBuffer buffer = renderSection.getBuffer(layer);


                buffer.bind();
                buffer.draw();
            }
        } finally {
            // 与原版 renderChunkLayer 收尾一致：解绑顶点数组对象。
            // 原代码缺这一步，会把子关卡最后一个渲染段的 VAO 一直留在绑定状态，
            // 干扰 Embeddium 之后的绘制批次。
            VertexBuffer.unbind();
            SableRenderDebug.restore(shader);
        }

        if (doDiagnose) {
            final float det = new Matrix3f(
                    transform.m00(), transform.m01(), transform.m02(),
                    transform.m10(), transform.m11(), transform.m12(),
                    transform.m20(), transform.m21(), transform.m22()).determinant();
            // 注意：本文件同时 import 了 JOML 的类，直接写 Math 会与 org.joml.Math 撞名
            // （编译报「对 Math 的引用不明确」），这里必须用全限定名 java.lang.Math。
            final double quatLen = java.lang.Math.sqrt(renderRot.x() * renderRot.x() + renderRot.y() * renderRot.y()
                    + renderRot.z() * renderRot.z() + renderRot.w() * renderRot.w());
            LOGGER.info("[Sable诊断·BUG28] 诊断模式={} · 子关卡#{} 图层{}：渲染段 总数={} 非空={}，旋转矩阵行列式={}（负数=绕序翻转），四元数模长={}",
                    debugMode, System.identityHashCode(this), layerName, this.allRenderSections.size(), nonEmptySections, det, quatLen);
            LOGGER.info("[Sable诊断·BUG28] 模式{} · 进入时GL状态：正面绕序={}（2305=GL_CCW原版正常 / 2304=GL_CW已被篡改），剔除开启={} 剔除面={}（1029=BACK），深度测试={} 深度函数={}（515=LEQUAL）深度写入={}",
                    debugMode, glFrontFace, glCullEnabled, glCullMode, glDepthEnabled, glDepthFunc, glDepthMask);
            // 这一条才是验证诊断开关本身是否生效的依据：若切了模式 4 而这里仍是 CCW，
            // 说明 glFrontFace 根本没落到 GL 上，之前基于模式 4 得出的所有结论一律作废。
            LOGGER.info("[Sable诊断·BUG28] 模式{} · apply后实际生效的GL状态：{}", debugMode, glAfterApply);
            // 实际提交了几次绘制。若非空段数量正常、GL 状态也正常，却依旧看不见，
            // 那么问题必然出在**网格烘焙阶段**（面压根没写进去）或**着色输出**（算成了纯黑），
            // 而不在这一层的图形状态上。
            LOGGER.info("[Sable诊断·BUG28] 模式{} · 图层{} 本次实际提交绘制 {} 次（0 表示什么都没画）",
                    debugMode, layerName, nonEmptySections);

            // ==========================================================================
            // 平移链路诊断 —— 这是「几何存在、draw 也调用了，却什么都看不见」时
            // 唯一还没被验证过的环节。
            //
            // 子关卡的平移【完全】由着色器 uniform CHUNK_OFFSET 承担
            // （transform 里的 translate 因与 fogOffset 相减而恒为 0）。
            // 因此只要出现下面任一情况，整个结构就会跑到屏幕外：
            //   * CHUNK_OFFSET 为 null（着色器根本没有这个 uniform，平移直接丢失，
            //     所有渲染段被堆到相机原点，玩家处于几何内部，正面朝外全被剔除
            //     —— 这与「关剔除后流体才现形」的现象高度吻合）；
            //   * CHUNK_OFFSET 数值异常（例如出现 2048 万量级的局部坐标残留，
            //     说明 pos 与 origin 不在同一坐标系，相减没有抵消掉 plot 偏移）。
            // ==========================================================================
            // [BUG36] 离屏相机位置（camX/camY/camZ 是 DiagramScreen 经 renderGroup 传入的真实相机，
            // 之前错打印主世界玩家相机导致无法判读离屏对齐情况）。
            LOGGER.info("[Sable诊断·BUG28] 平移链路：CHUNK_OFFSET是否存在={}，首个非空段偏移=({}, {}, {})，"
                            + "子关卡原点origin=({}, {}, {})，渲染位置renderPos=({}, {}, {})，离屏相机=({}, {}, {})",
                    chunkOffsetUniform != null, firstCox, firstCoy, firstCoz,
                    this.origin.x(), this.origin.y(), this.origin.z(),
                    renderPos.x(), renderPos.y(), renderPos.z(),
                    camX, camY, camZ);
        }

        if (chunkOffsetUniform != null) {
            // [v36 修复] 原先只 set 不 upload：Uniform.set 只改 CPU 缓存并标记脏，
            // 必须 upload 才真正推到 GPU（对比上面循环内是 set+upload 配对）。
            // 漏 upload 会让「最后一个区块的 CHUNK_OFFSET」残留在 GPU 上，
            // 污染此后所有走 solid/cutout 着色器族的绘制。
            chunkOffsetUniform.set(0f, 0f, 0f);
            chunkOffsetUniform.upload();
        }


        if (oldFogColor != null) {
            shader.FOG_COLOR.set(oldFogColor[0], oldFogColor[1], oldFogColor[2], oldFogColor[3]);
        }
    }

    @Override
    public void close() {
        // [1.20.1 缓冲泄漏修复] 同 resize：先冲刷待上传队列再释放，避免 RenderedBuffer 泄漏。
        this.sectionRenderDispatcher.uploadAllPendingUploads();
        for (final ChunkRenderDispatcher.RenderChunk section : this.allRenderSections) {
            section.releaseBuffers();
            section.updateGlobalBlockEntities(Set.of());
            section.compiled.set(ChunkRenderDispatcher.CompiledChunk.UNCOMPILED);
        }
        this.allRenderSections.clear();
        this.renderSections = null;

        LIVE_INSTANCES.remove(this);
    }

    public ChunkRenderDispatcher.RenderChunk getRenderSection(final SectionPos sectionPos) {
        if (this.renderSections == null) {
            return null;
        }

        final int index = this.getIndex(sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());

        if (index < 0 || index >= this.renderSections.length) {
            return null;
        }

        return this.renderSections[index];
    }

    /**
     * 取渲染图层的短名（solid / cutout / translucent …）。
     *
     * <p>{@code RenderType.toString()} 会把整个 CompositeState 连同所有
     * 着色器 lambda 名一起展开，长达数百字符，直接写进日志会把后面真正
     * 需要看的数值挤掉。1.20.1 的 {@code RenderType#name} 是受保护字段，
     * 无法直接读取，这里从 {@code RenderType[<名字>:CompositeState[...]]}
     * 的字符串形式里截出名字部分。
     *
     * @param layer 渲染图层
     * @return 图层短名；格式不符合预期时原样返回
     */
    private static String shortLayerName(final RenderType layer) {
        final String raw = layer.toString();
        final int start = raw.indexOf('[');
        if (start < 0) {
            return raw;
        }
        final int end = raw.indexOf(':', start);
        if (end < 0) {
            return raw;
        }
        return raw.substring(start + 1, end);
    }
}
