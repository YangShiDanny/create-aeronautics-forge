package foundry.veil.api.client.render;

import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.post.PostProcessingManager;

public final class VeilRenderer {
    public PostProcessingManager getPostProcessingManager() { return new PostProcessingManager(); }
    public ShaderManager getShaderManager() { return new ShaderManager(); }
    public ShaderDefinitions getShaderDefinitions() { return new ShaderDefinitions(); }

    // [1.20.1 移植] NeoForge 1.21 的 VeilRenderer 提供摄像机矩阵与动态帧缓冲管理。
    // 1.20.1 无对应底层；这里对接 RenderSystem 全局矩阵状态（见 CameraMatrices），
    // 动态 fbo 即传入的 fbo 本身（已包装 RenderTarget）。
    public CameraMatrices getCameraMatrices() { return new CameraMatrices(); }

    public DynamicBufferManager getDynamicBufferManger() { return new DynamicBufferManager(); }

    public static final class DynamicBufferManager {
        public AdvancedFbo getDynamicFbo(final AdvancedFbo fbo) { return fbo; }
    }

    public static final class ShaderManager {
        public java.util.concurrent.CompletableFuture<foundry.veil.api.client.render.shader.program.ShaderProgram> createDynamicProgram(Object... args) { return java.util.concurrent.CompletableFuture.completedFuture(null); }
    }
    public static final class ShaderDefinitions {
        public void set(Object key, String value) {}
    }
}
