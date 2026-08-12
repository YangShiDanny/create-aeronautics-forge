package foundry.veil.api.client.render.framebuffer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;

public final class AdvancedFbo {
    private RenderTarget target;

    private AdvancedFbo() {}

    // [1.20.1 移植] NeoForge 1.21 的 AdvancedFbo 对应 1.20.1 的 RenderTarget（离屏帧缓冲）。
    // 这里用 TextureTarget 真实创建/绑定/清理，使子世界图表渲染真正画到离屏缓冲。
    public static AdvancedFbo withSize(final int width, final int height) {
        final AdvancedFbo fbo = new AdvancedFbo();
        fbo.target = new TextureTarget(width, height, true, false);
        fbo.target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        return fbo;
    }

    public static void unbind() {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }

    public void bind() { this.target.bindWrite(true); }
    public void bind(final boolean withRestore) { this.target.bindWrite(withRestore); }
    public void bindRead() { this.target.bindRead(); }

    public AdvancedFbo addColorTextureBuffer() { return this; }
    public AdvancedFbo setDepthTextureBuffer() { return this; }

    public AdvancedFbo build(final boolean restore) { return this; }

    public void clear(final float r, final float g, final float b, final float a, final int mask) {
        this.target.setClearColor(r, g, b, a);
        // [1.20.1 移植] RenderTarget.clear(boolean) 无位掩码语义，按 mask 含颜色位则清颜色缓冲处理。
        this.target.clear((mask & 0x4000) != 0 || mask == 0);
    }

    // [1.20.1 移植] NeoForge 1.21 的 fbo.clear() 无参；1.20.1 的 RenderTarget.clear 需 boolean。子世界图表渲染用全清。
    public void clear() {
        this.target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        this.target.clear(true);
    }

    public void free() { this.target.destroyBuffers(); }

    public int getWidth() { return this.target.width; }
    public int getHeight() { return this.target.height; }

    // [1.20.1 移植] 返回颜色纹理 id（供贴图到 GUI）。1.20.1 RenderTarget.getColorTextureId() 直接返回纹理 id（int）。
    public int getColorTextureAttachment(final int index) {
        return this.target.getColorTextureId();
    }

    // [1.20.1 移植] 返回深度纹理 id（供图解描边后处理采样深度做轮廓检测）。
    public int getDepthTextureId() {
        return this.target.getDepthTextureId();
    }

    // [1.20.1 移植] 返回帧缓冲对象 id（供 glBlitFramebuffer 兜底拷贝）。
    public int getId() {
        return this.target.frameBufferId;
    }
}
