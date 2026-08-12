package dev.ryanhcode.sable.sublevel.render;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import org.joml.*;

import java.io.Closeable;
import org.joml.Quaternionf;

public interface SubLevelRenderData extends Closeable {

    @Override
    void close();

    /**
     * Forces the sections in this renderer to rebuild.
     */
    void rebuild();

    /**
     * Checks if a section in global section coordinates is compiled
     *
     * @param x the global x coordinate
     * @param y the global y coordinate
     * @param z the global z coordinate
     * @return if the section exists and is compiled
     */
    boolean isSectionCompiled(int x, int y, int z);

    /**
     * Sets a section in global section coordinates as dirty
     *
     * @param x             the global x coordinate
     * @param y             the global y coordinate
     * @param z             the global z coordinate
     * @param playerChanged if the section is dirty from a player action
     */
    void setDirty(final int x, final int y, final int z, final boolean playerChanged);

    /**
     * Compiles all dirty sections in this renderer.
     *
     * @param chunkUpdates      The chunk update mode
     * @param renderRegionCache The render region cache instance for compiling sections
     * @param camera            The camera instance
     */
    void compileSections(PrioritizeChunkUpdates chunkUpdates, final RenderRegionCache renderRegionCache, Camera camera);

    int getVisibleSectionCount();

    default Matrix4f getTransformation(final double camX, final double camY, final double camZ) {
        return this.getTransformation(camX, camY, camZ, new Matrix4f());
    }

    default Matrix4f getTransformation(final double camX, final double camY, final double camZ, final Matrix4f store) {
        store.identity();

        final Pose3dc pose = this.getSubLevel().renderPose();

        final Vector3dc pos = pose.position();
        final Vector3dc scale = pose.scale();
        final Quaterniondc orientation = pose.orientation();

        // [BUG-03 真凶修复 v10·回归上游] 必须减去相机坐标，与上游 NeoForge 2.0.3 一字不差。
        //
        // 第 5 轮曾误删此处的 -camX，理由是「MESH 路径用 fogOffset 抵消了平移，方块实体同理」——
        // 该类比是错的：MESH 路径（renderChunkedSubLevel）的平移之所以能抵消，是因为平移改由
        // 着色器 uniform CHUNK_OFFSET 提供（见该方法注释「平移完全交给着色器 uniform CHUNK_OFFSET」）；
        // 而方块实体走 MultiBufferSource 顶点管线，根本没有 CHUNK_OFFSET uniform，
        // 平移只能留在矩阵里。删掉 -camX 后平移无处提供，方块实体被画到相机外（看不见）。
        //
        // 与地形（当前显示正确的黄金参照）严格同口径：
        //   地形：  finalModelView = [poseStack.last().pose() 纯相机旋转] · [translate(renderPos-cam) · rotate]
        //   方块实体：finalModelView = [poseStack.last().pose() 纯相机旋转] · [translate(pos-cam) · rotate · scale]
        // 相机「平移」由本方法提供，相机「旋转」由 SABLE_BE_BASE_POSE 提供，各司其职、互不重复。
        store.translate((float) (pos.x() - camX), (float) (pos.y() - camY), (float) (pos.z() - camZ));
        store.rotate(new Quaternionf(orientation));
        store.scale((float) scale.x(), (float) scale.y(), (float) scale.z());

        return store;
    }

    ClientSubLevel getSubLevel();

    default Vector3d getChunkOffset() {
        return this.getChunkOffset(new Vector3d());
    }

    default Vector3d getChunkOffset(final Vector3d dest) {
        return this.getSubLevel().renderPose().rotationPoint().negate(dest);
    }
}
