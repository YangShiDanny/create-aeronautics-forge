package foundry.veil.api.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

public final class CameraMatrices {
    private final Matrix4f modelView = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();

    public Matrix4f projection() { return RenderSystem.getProjectionMatrix(); }
    public Matrix4f view() { return RenderSystem.getModelViewMatrix(); }

    // [1.20.1 移植] NeoForge 1.21 的 CameraMatrices.backup(slot) 把"当前"矩阵存入 slot，
    // restore(slot) 从 slot 恢复。1.20.1 的矩阵是 RenderSystem 全局状态，这里直接对接。
    public void backup(final CameraMatrices slot) {
        slot.modelView.set(RenderSystem.getModelViewMatrix());
        slot.projection.set(RenderSystem.getProjectionMatrix());
    }

    public void restore(final CameraMatrices slot) {
        final PoseStack stack = RenderSystem.getModelViewStack();
        stack.setIdentity();
        stack.mulPoseMatrix(slot.modelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(slot.projection, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
    }
}
