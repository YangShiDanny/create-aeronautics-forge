package dev.simulated_team.simulated.content.entities.diagram.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.ryanhcode.sable.MobilePlatform;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.simulated_team.simulated.content.entities.diagram.DiagramConfig;
import dev.simulated_team.simulated.index.SimGUITextures;
import dev.simulated_team.simulated.index.SimSoundEvents;
import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.*;

import java.lang.Math;
import org.joml.Quaternionf;

public class DiagramStickyNote extends DiagramButton {

    private static final SimGUITextures NOTE_TEXTURE = SimGUITextures.DIAGRAM_STICKY_NOTE;

    private static final int SUBLEVEL_RENDER_WIDTH_PIXELS = 88;
    private static final int SUBLEVEL_RENDER_HEIGHT_PIXELS = 88;

    private static final int SUBLEVEL_RENDER_X_OFFSET = 8;
    private static final int SUBLEVEL_RENDER_Y_OFFSET = 7;

    public static final int MAX_OFFSET = NOTE_TEXTURE.width;
    public static final int MIN_OFFSET = 9;

    private static final Vector3d NOTE_LOCAL_CAM_POS = new Vector3d();
    private static final Vector3d NOTE_CAMERA_POS = new Vector3d();
    private static final Matrix4f NOTE_PROJ_MAT = new Matrix4f();

    private static final Quaternionf NOTE_ORIENTATION = new Quaternionf();
    private DiagramScreen parent;

    private float lastOffset = MIN_OFFSET;
    private float currentOffset = MIN_OFFSET;

    private AdvancedFbo fbo;
    private AdvancedFbo outlineFbo;
    private AdvancedFbo finalFbo;

    // [手机端优化·B1] 便签离屏 FBO 的**实际像素尺寸**。
    // GUI 上的显示尺寸恒为 SUBLEVEL_RENDER_WIDTH/HEIGHT_PIXELS（88），
    // renderFBO 用 UV 0~1 全图贴到 88×88 的四边形上，因此这里把像素尺寸调小
    // 只会降低便签缩略图的清晰度，**不会改变任何界面布局与鼠标命中区**。
    private int fboWidth = SUBLEVEL_RENDER_WIDTH_PIXELS;
    private int fboHeight = SUBLEVEL_RENDER_HEIGHT_PIXELS;

    private float renderTime = 0;
    private int renderXStart;

    // [1.20.1 移植] 便签鼠标拖拽移动：左键在便签上拖动即可自由摆放。
    // 注意：与原版"在便签内框选子结构"交互共用拖动手势，此处拖拽移动优先。
    private boolean dragging = false;
    private boolean dragMoved = false;
    private int dragGrabX = 0;
    private int dragGrabY = 0;
    private int dragX = 0;
    private int dragY = 0;

    public DiagramStickyNote(final DiagramScreen parent, final int diagramX, final int diagramY, final Component message, final Runnable onClick) {
        super(NOTE_TEXTURE, 0, diagramY + 5, message, onClick);

        // [1.20.1 移植] 仅做下限保护（防止负坐标跑到屏幕左侧外）。
        // 上限不限制：原版设计即允许便签激活时右侧微超出屏幕边界。
        final int wantedX = (diagramX + SimGUITextures.DIAGRAM.width) - NOTE_TEXTURE.width + MIN_OFFSET;
        this.renderXStart = java.lang.Math.max(wantedX, 0);
        this.setX(this.renderXStart);

        this.parent = parent;
    }


    public void tick() {
        this.lastOffset = this.currentOffset;

        float target = MIN_OFFSET;
        if (this.active) {
            target = MAX_OFFSET - 8;
        }

        // [1.20.1 移植] 拖拽时不跳过滑入动画：renderXStart 平滑趋向拖拽目标位置，
        // currentOffset 仍照常向 target 收敛，保留动画质感（不再瞬间贴到鼠标）。
        if (this.dragging) {
            final float targetBaseX = this.dragX - this.currentOffset;
            this.renderXStart = (int) Mth.lerp(DiagramScreen.PAPER_SLIDE_SPEED, this.renderXStart, targetBaseX);
            this.setY((int) Mth.lerp(DiagramScreen.PAPER_SLIDE_SPEED, this.getY(), this.dragY));
        }

        this.currentOffset = Mth.lerp(DiagramScreen.PAPER_SLIDE_SPEED, this.currentOffset, target);
        this.setX((int) (this.renderXStart + this.currentOffset));
    }

    // [1.20.1 移植] 便签拖拽由 DiagramScreen 亲自接管（右键），绕开 Minecraft
    // ContainerEventHandler 的 isDragging 限制（仅左键 mouseClicked 成功消费后才会置
    // isDragging，导致右键/中键拖拽的 mouseDragged 不转发）。以下为对外接口，由 Screen 直接调用。

    public boolean isDragging() {
        return this.dragging;
    }

    public void beginDrag(final double mouseX, final double mouseY) {
        this.dragging = true;
        this.dragMoved = false;
        this.dragGrabX = (int) mouseX - this.getX();
        this.dragGrabY = (int) mouseY - this.getY();
        this.dragX = this.getX();
        this.dragY = this.getY();
    }

    public void dragTo(final double mouseX, final double mouseY) {
        // [1.20.1 移植] 用户要求便签可无限制拖拽（允许拖出屏幕），不再 clamp 到屏幕内。
        this.dragX = (int) mouseX - this.dragGrabX;
        this.dragY = (int) mouseY - this.dragGrabY;
        this.dragMoved = true;
    }

    public void endDrag() {
        this.dragging = false;
        this.renderXStart = this.dragX - (int) this.currentOffset;
        this.setY(this.dragY);
    }

    private float lerpedOffset(final float pt) {
        return Mth.lerp(pt, this.lastOffset, this.currentOffset);
    }

    // [1.20.1 移植] 滑入动画是否已完成：已激活且 currentOffset 已收敛到目标偏移。
    public boolean isSlideDone() {
        if (!this.active) {
            return false;
        }
        return Math.abs(this.currentOffset - (MAX_OFFSET - 8)) < 0.5F;
    }

    public void activate() {
        if (!this.active) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
            this.active = true;
        }
    }

    public void deactivate() {
        this.active = false;
    }

    /**
     * [手机端优化·B1 / F1] 便签离屏 FBO 的缩放系数。
     *
     * <p>非手机端恒为 1.0（行为与源版完全一致，PC 端零影响）。
     * 手机端取客户端配置 {@code MOBILE_FBO_SCALE}；若处于翻译层（gl4es/VirGL/Zink 等）
     * 或探测到关键 GL 特性缺失的安全帧缓冲模式，则在此基础上再压一半。
     * 最终夹在 [0.125, 1.0] 之间，避免配置写了极端值把 FBO 压成 0 像素。
     */
    private static double mobileFboScale() {
        if (!MobilePlatform.isMobile()) {
            return 1.0;
        }
        double scale;
        try {
            scale = SableClientConfig.MOBILE_FBO_SCALE.get();
        } catch (final Throwable t) {
            // 配置尚未加载完成时保守取 1.0，绝不让优化路径把界面搞崩。
            scale = 1.0;
        }
        if (MobilePlatform.isSafeFramebufferMode()) {
            scale *= 0.5;
        }
        return java.lang.Math.max(0.125, java.lang.Math.min(1.0, scale));
    }

    /** [手机端优化·B1] 便签离屏 FBO 实际宽度（像素），最低 16 像素保证描边着色器仍有意义。 */
    private static int mobileFboWidth() {
        return java.lang.Math.max(16, (int) java.lang.Math.round(SUBLEVEL_RENDER_WIDTH_PIXELS * mobileFboScale()));
    }

    /** [手机端优化·B1] 便签离屏 FBO 实际高度（像素）。 */
    private static int mobileFboHeight() {
        return java.lang.Math.max(16, (int) java.lang.Math.round(SUBLEVEL_RENDER_HEIGHT_PIXELS * mobileFboScale()));
    }

    /**
     * [手机端优化·B1] 便签离屏重渲染的间隔倍数。
     *
     * <p>便签本质是一张小缩略预览，却要把整个子关卡完整重绘一遍（地形层 + 方块实体 + 实体），
     * 是手机端最重的单点开销之一。这里把重绘间隔按档位拉长：
     * 原生 GPU 档 ×2，翻译层档 ×3；非手机端 ×1（行为不变）。
     */
    private static float mobileRenderIntervalScale() {
        if (!MobilePlatform.isMobile()) {
            return 1.0f;
        }
        return MobilePlatform.isTranslationLayer() ? 3.0f : 2.0f;
    }

    public void create(final DiagramConfig.NoteConfigs noteConfigs) {
        // [手机端优化·B1] 先算出本次要用的离屏分辨率（非手机端恒为原始 88×88）。
        this.fboWidth = mobileFboWidth();
        this.fboHeight = mobileFboHeight();

        this.fbo = AdvancedFbo.withSize(this.fboWidth, this.fboHeight).addColorTextureBuffer().setDepthTextureBuffer().build(true);
        this.outlineFbo = AdvancedFbo.withSize(this.fboWidth, this.fboHeight).addColorTextureBuffer().build(true);
        this.finalFbo = AdvancedFbo.withSize(this.fboWidth, this.fboHeight).addColorTextureBuffer().build(true);

        this.active = noteConfigs.isActive();
        this.updateOrientation();

        if (this.active) {
            this.currentOffset = MAX_OFFSET - 8;
            this.lastOffset = this.currentOffset;
        }

        this.visible = true;
    }

    public void free() {
        this.deactivate();

        NOTE_ORIENTATION.set(0, 0, 0, 0);
        if (this.fbo != null) {
            this.fbo.free();
            this.fbo = null;

            this.outlineFbo.free();
            this.outlineFbo = null;

            this.finalFbo.free();
            this.finalFbo = null;
        }

        this.parent = null;
    }

    public void updateCurrentScope(final Vector2dc start, final Vector2dc end, final Vector3dc localPosition, final Matrix4fc projMatrix) {
        this.updateOrientation();

        final int width = DiagramScreen.DIAGRAM_TEXTURE.width;
        final int height = DiagramScreen.DIAGRAM_TEXTURE.height;
        final Vector3d startPlotSpace = DiagramScreen.getPlotCoords(start, NOTE_ORIENTATION, localPosition, projMatrix, width, height);
        final Vector3d endPlotSpace = DiagramScreen.getPlotCoords(end, NOTE_ORIENTATION, localPosition, projMatrix, width, height);

        this.parent.config.getNoteConfigs().getNoteScope().set(startPlotSpace.x, startPlotSpace.y, startPlotSpace.z, endPlotSpace.x, endPlotSpace.y, endPlotSpace.z);
    }

    public void handleInternalUpdate(final Vector2d magnifyingTarget, final Vector2d inverseTarget) {
        magnifyingTarget.sub(this.getSublevelRenderX(), this.getSublevelRenderY());
        inverseTarget.sub(this.getSublevelRenderX(), this.getSublevelRenderY());

        final int width = SUBLEVEL_RENDER_WIDTH_PIXELS;
        final int height = SUBLEVEL_RENDER_HEIGHT_PIXELS;
        final Vector3d startPlotSpace = DiagramScreen.getPlotCoords(magnifyingTarget, NOTE_ORIENTATION, NOTE_LOCAL_CAM_POS, NOTE_PROJ_MAT, width, height);
        final Vector3d endPlotSpace = DiagramScreen.getPlotCoords(inverseTarget, NOTE_ORIENTATION, NOTE_LOCAL_CAM_POS, NOTE_PROJ_MAT, width, height);

        this.parent.config.getNoteConfigs().getNoteScope().set(startPlotSpace.x, startPlotSpace.y, startPlotSpace.z, endPlotSpace.x, endPlotSpace.y, endPlotSpace.z);
    }

    private void updateOrientation() {
        this.renderTime = 100;
        NOTE_ORIENTATION.identity().rotateY((float) Math.toRadians(this.parent.config.getNoteConfigs().getNoteYaw())).rotateX((float) Math.toRadians(this.parent.config.getNoteConfigs().getNotePitch()));
    }

    public boolean contains(double x, double y) {
        if (!this.active) {
            return false;
        }

        x -= this.getSublevelRenderX();
        y -= this.getSublevelRenderY();
        return x > 0 && x < SUBLEVEL_RENDER_WIDTH_PIXELS && y > 0  && y < SUBLEVEL_RENDER_HEIGHT_PIXELS;
    }

    public Vector2d clamp(final Vector2d dest) {
        final float minX = this.getSublevelRenderX();
        final float minY = this.getSublevelRenderY();
        dest.max(new Vector2d(minX, minY));
        dest.min(new Vector2d(minX + SUBLEVEL_RENDER_WIDTH_PIXELS, minY + SUBLEVEL_RENDER_HEIGHT_PIXELS));
        return dest;
    }

    private float getSublevelRenderX() {
        return this.renderXStart + this.currentOffset + SUBLEVEL_RENDER_X_OFFSET;
    }

    private float getSublevelRenderY() {
        return this.getY() + SUBLEVEL_RENDER_Y_OFFSET;
    }

    @Override
    protected void renderWidget(final GuiGraphics guiGraphics, final int mouseX, final int mouseY, final float partialTicks) {
        final PoseStack ps = guiGraphics.pose();
        ps.pushPose();

        final float currentX = this.renderXStart + this.lerpedOffset(partialTicks);
        final int currentY = this.getY();
        ps.translate(currentX, currentY, 0);
        SimGUITextures.DIAGRAM_STICKY_NOTE.render(guiGraphics, 0, 0);

        if (this.active) {
            ps.pushPose();
            ps.translate(SUBLEVEL_RENDER_X_OFFSET, SUBLEVEL_RENDER_Y_OFFSET, 0);
            if (this.fbo != null) {
                this.populateFBO(partialTicks);
                DiagramScreen.renderFBO(guiGraphics, this.finalFbo, SUBLEVEL_RENDER_WIDTH_PIXELS, SUBLEVEL_RENDER_HEIGHT_PIXELS);
            }

            this.parent.renderArrows(guiGraphics,
                    mouseX,
                    mouseY,
                    (int) currentX + SUBLEVEL_RENDER_X_OFFSET,
                    currentY + SUBLEVEL_RENDER_Y_OFFSET,
                    NOTE_ORIENTATION,
                    NOTE_LOCAL_CAM_POS,
                    NOTE_PROJ_MAT,
                    SUBLEVEL_RENDER_WIDTH_PIXELS,
                    SUBLEVEL_RENDER_HEIGHT_PIXELS);

            final MultiBufferSource.BufferSource bufferSource = guiGraphics.bufferSource();
            bufferSource.endBatch();

            this.renderCustomCOM(guiGraphics, ps);
            ps.popPose();

        }

        ps.popPose();

    }

    public void populateFBO(final float partialTicks) {
        // [手机端优化·B1] 手机端拉长便签离屏重绘间隔（非手机端倍数为 1，判据与源版完全一致）。
        final float sable$renderInterval = (20.0f / DiagramScreen.FPS) * mobileRenderIntervalScale();
        if (this.renderTime >= sable$renderInterval) {
            this.renderTime = 0.0f;
        } else {
            this.renderTime += Minecraft.getInstance().getFrameTime();
            return;
        }

        final float zNear = 0.1f;
        final LevelPlot plot = this.parent.subLevel.getPlot();

        final BoundingBox3ic scopeBounds = new BoundingBox3i(this.parent.config.getNoteConfigs().getNoteScope());

        // [BUG40·修复] 便签相机必须与世界(渲染)空间中的几何包围盒对准。
        // 原写法把相机放在 plot 空间 scope 中心 + 整个 plot 跨度后退，再经 renderPose 变换到世界空间。
        // 当子关卡有物理旋转时，plot 空间后退方向与世界空间包围盒中心错位，导致结构只落在便签一角、其余像素透明。
        // 修正思路与主图解 renderContents 一致：在世界空间以包围盒中心为基准放置相机。
        // 1) 先把 noteScope 八个角点变换到世界空间，得到真实 AABB 与中心；
        // 2) 在世界空间以 worldCenter 为基准，沿 NOTE_ORIENTATION 的 z 轴后退 worldExtent 放置相机；
        // 3) 把世界空间相机反投影回 plot 空间，保持 NOTE_LOCAL_CAM_POS 与 NOTE_CAMERA_POS 是同一相机，
        //    供 handleInternalUpdate / getScreenCoords 反投影用。
        final Pose3dc renderPose = this.parent.subLevel.renderPose(partialTicks);
        double sable$worldMinX = Double.POSITIVE_INFINITY, sable$worldMinY = Double.POSITIVE_INFINITY, sable$worldMinZ = Double.POSITIVE_INFINITY;
        double sable$worldMaxX = Double.NEGATIVE_INFINITY, sable$worldMaxY = Double.NEGATIVE_INFINITY, sable$worldMaxZ = Double.NEGATIVE_INFINITY;
        for (int cx = 0; cx < 2; cx++) {
            for (int cy = 0; cy < 2; cy++) {
                for (int cz = 0; cz < 2; cz++) {
                    final Vector3d w = renderPose.transformPosition(new Vector3d(
                            cx == 0 ? scopeBounds.minX() : scopeBounds.maxX(),
                            cy == 0 ? scopeBounds.minY() : scopeBounds.maxY(),
                            cz == 0 ? scopeBounds.minZ() : scopeBounds.maxZ()), new Vector3d());
                    sable$worldMinX = java.lang.Math.min(sable$worldMinX, w.x);
                    sable$worldMinY = java.lang.Math.min(sable$worldMinY, w.y);
                    sable$worldMinZ = java.lang.Math.min(sable$worldMinZ, w.z);
                    sable$worldMaxX = java.lang.Math.max(sable$worldMaxX, w.x);
                    sable$worldMaxY = java.lang.Math.max(sable$worldMaxY, w.y);
                    sable$worldMaxZ = java.lang.Math.max(sable$worldMaxZ, w.z);
                }
            }
        }
        final double worldExtent = java.lang.Math.max(java.lang.Math.max(sable$worldMaxX - sable$worldMinX, sable$worldMaxY - sable$worldMinY), sable$worldMaxZ - sable$worldMinZ);
        final float sable$worldExtentF = (float) worldExtent;

        // 相机后退距离 = 结构最大跨度，使结构中心位于正交视锥 z 方向正中；
        // 半径 = 结构最大跨度，视锥直径 2*worldExtent 可覆盖任意旋转长方体在等距视角下的投影（最大对角线 sqrt(3)*worldExtent < 2*worldExtent）。
        final float sable$camOffset = sable$worldExtentF > 0.0f ? sable$worldExtentF : 1.0f;
        float radius = sable$camOffset * 1.0f;
        radius = Math.max(radius, 1.0f);

        // [手机端优化·B1] 宽高比按 FBO 实际像素尺寸算（等比缩放时数值与原来一致，不改变构图）。
        final float aspect = (float) this.fboWidth / this.fboHeight;
        NOTE_PROJ_MAT.identity().ortho(-radius * aspect, radius * aspect, -radius, radius, zNear, sable$camOffset * 2.0f);

        // account for the smaller screen size
        final Vector3d worldCenter = new Vector3d((sable$worldMinX + sable$worldMaxX) / 2.0, (sable$worldMinY + sable$worldMaxY) / 2.0, (sable$worldMinZ + sable$worldMaxZ) / 2.0);
        NOTE_CAMERA_POS.set(worldCenter.add(NOTE_ORIENTATION.transform(new Vector3d(0, 0, sable$camOffset))));
        renderPose.transformPositionInverse(NOTE_LOCAL_CAM_POS.set(NOTE_CAMERA_POS));


        // [手机端优化·B1] 描边着色器的 InSize 必须是 FBO 的**真实像素尺寸**（用于逐 texel 采样求边缘），
        // 因此这里传 fboWidth/fboHeight 而非 GUI 显示尺寸；GUI 显示尺寸仍在 renderWidget 里保持 88×88。
        DiagramScreen.draw(this.parent.subLevel, partialTicks, NOTE_ORIENTATION, NOTE_PROJ_MAT, NOTE_CAMERA_POS, this.fboWidth, this.fboHeight, this.fbo, this.outlineFbo, this.finalFbo, 0.75f, 1.15f, 0x6e684d, 0x59543e);

    }


    @Override
    public void playDownSound(final SoundManager handler) {

    }

    private void renderCustomCOM(final GuiGraphics guiGraphics, final PoseStack stack) {
        if (this.parent.config.displayCenterOfMass()) {
            stack.pushPose();
            final Vector3d centerOfMass = new Vector3d(this.parent.subLevel.logicalPose().rotationPoint());
            final Vector2d screenCoords = DiagramScreen.getScreenCoords(centerOfMass, NOTE_ORIENTATION, NOTE_LOCAL_CAM_POS, NOTE_PROJ_MAT, SUBLEVEL_RENDER_WIDTH_PIXELS, SUBLEVEL_RENDER_HEIGHT_PIXELS);

            SimGUITextures tex = SimGUITextures.DIAGRAM_ICON_COM_TINY;
            final double comOffsetX = (screenCoords.x) - 8;
            final double comOffsetY = (screenCoords.y) - 8;

            if (comOffsetY > 0 && comOffsetX > 0 && comOffsetY < SUBLEVEL_RENDER_HEIGHT_PIXELS && comOffsetX < SUBLEVEL_RENDER_WIDTH_PIXELS) {
                stack.translate(comOffsetX, comOffsetY, 0);
                guiGraphics.blit(tex.location, 0, 0, 5, tex.startX, tex.startY, tex.width, tex.height, tex.texWidth, tex.texHeight);
            } else {
                final float centerX = SUBLEVEL_RENDER_WIDTH_PIXELS / 2f;
                final float centerY = SUBLEVEL_RENDER_HEIGHT_PIXELS / 2f;

                final Vector2d target = new Vector2d(screenCoords.x() - centerX, screenCoords.y - centerY).normalize();
                TransformStack.of(stack)
                        .translate(centerX, centerY, 0)
                        .rotate((float) Math.atan2(target.x, -target.y), Direction.Axis.Z)
                        .translate(-8, -8, 0)
                        .translate(0, -40, 0);

                tex = SimGUITextures.DIAGRAM_ICON_COM_ARROW;
                guiGraphics.blit(tex.location, 0, 0, 5, tex.startX, tex.startY, tex.width, tex.height, tex.texWidth, tex.texHeight);
            }
            stack.popPose();
        }
    }
}
