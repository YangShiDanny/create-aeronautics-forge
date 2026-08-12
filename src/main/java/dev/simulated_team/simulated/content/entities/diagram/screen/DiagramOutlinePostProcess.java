package dev.simulated_team.simulated.content.entities.diagram.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.logging.LogUtils;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * [1.20.1 移植] 图解"蓝图线稿"描边后处理。
 * 源版（NeoForge 1.21.1）由 Veil 后处理管线 simulated:diagram 完成：
 * 读取子关卡离屏渲染结果（颜色 + 深度），用 outline_diagram 着色器做
 * 深度描边 + 调色板抖动，输出蓝图线稿风格图样到 diagram_final。
 * 本工程 Veil 管线是空壳，这里用原生 GL 等价复刻同一步 blit：
 * 复用打包在 assets/simulated/shaders/program/contraption_diagram/ 下的
 * 原版着色器源码（全屏三角形 gl_VertexID 技巧，无顶点属性）。
 */
public final class DiagramOutlinePostProcess {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation VSH = new ResourceLocation("simulated", "shaders/program/contraption_diagram/outline_diagram.vsh");
    private static final ResourceLocation FSH = new ResourceLocation("simulated", "shaders/program/contraption_diagram/outline_diagram.fsh");
    private static final ResourceLocation PALETTE = new ResourceLocation("simulated", "textures/effects/diagram_palette.png");
    private static final ResourceLocation DITHER = new ResourceLocation("simulated", "textures/effects/dither.png");

    private static int program = 0;
    private static int vao = 0;
    private static boolean broken = false;

    private static int uInSize = -1;
    private static int uLineColor = -1;
    private static int uLineShadowColor = -1;
    private static int uPaletteOffset = -1;
    private static int uFadeScale = -1;

    private DiagramOutlinePostProcess() {}

    /**
     * 把 inFbo（颜色+深度）经描边着色器输出到 outFbo。
     * 结束时恢复主帧缓冲绑定；着色器编译失败时降级为直接拷贝颜色（保底可见）。
     */
    public static void run(final AdvancedFbo inFbo, final AdvancedFbo outFbo, final float inWidth, final float inHeight,
                           final float paletteOffset, final float fadeScale, final int lineColor, final int lineShadowColor) {
        if (!broken && program == 0) {
            try {
                init();
            } catch (final Exception e) {
                LOGGER.error("[图解描边] 着色器初始化失败，降级为直拷贝", e);
                broken = true;
            }
        }

        if (broken) {
            blitFallback(inFbo, outFbo);
            // [1.20.1 移植·修复频闪] 降级路径同样需关闭深度测试，避免 GUI 被世界深度剔除。
            RenderSystem.disableDepthTest();
            return;
        }

        // RenderTarget.clear 结束时会解绑回 0 号帧缓冲，故先清再绑
        outFbo.clear();
        outFbo.bind(true);

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        GlStateManager._glUseProgram(program);

        GL20C.glUniform2f(uInSize, inWidth, inHeight);
        GL20C.glUniform4f(uLineColor,
                ((lineColor >> 16) & 0xFF) / 255.0f, ((lineColor >> 8) & 0xFF) / 255.0f, (lineColor & 0xFF) / 255.0f, 1.0f);
        GL20C.glUniform4f(uLineShadowColor,
                ((lineShadowColor >> 16) & 0xFF) / 255.0f, ((lineShadowColor >> 8) & 0xFF) / 255.0f, (lineShadowColor & 0xFF) / 255.0f, 1.0f);
        GL20C.glUniform1f(uPaletteOffset, paletteOffset);
        GL20C.glUniform1f(uFadeScale, fadeScale);

        // 纹理单元：0=颜色 1=深度 2=调色板 3=抖动
        GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
        GlStateManager._bindTexture(inFbo.getColorTextureAttachment(0));
        GlStateManager._activeTexture(GL13C.GL_TEXTURE1);
        GlStateManager._bindTexture(inFbo.getDepthTextureId());
        GlStateManager._activeTexture(GL13C.GL_TEXTURE2);
        GlStateManager._bindTexture(textureId(PALETTE));
        GlStateManager._activeTexture(GL13C.GL_TEXTURE3);
        GlStateManager._bindTexture(textureId(DITHER));
        GlStateManager._activeTexture(GL13C.GL_TEXTURE0);

        GL30C.glBindVertexArray(vao);
        GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
        GL30C.glBindVertexArray(0);

        GlStateManager._glUseProgram(0);

        // 原生 VAO 绑定绕过了缓冲上传器缓存，重置以防后续 GUI 绘制状态错乱
        BufferUploader.reset();

        // [1.20.1 移植·修复配置纸选项卡频闪] 此处必须还原为 GUI 渲染期望的状态：关闭深度测试。
        // 原函数误把深度测试重新开启，使其后绘制的所有 GUI（配置纸选项卡、主图解板等）
        // 与主帧缓冲里残留的世界深度做比较，被身后世界几何按帧随机剔除 → 频闪。
        // NeoForge 1.21.1 的 Veil 管线会自动还原为关闭，自写 run 需手动对齐。
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        AdvancedFbo.unbind();
    }

    private static void init() throws Exception {
        final String vshSrc = "#version 150\n" + load(VSH);
        final String fshSrc = "#version 150\n" + load(FSH);

        final int v = compile(GL20C.GL_VERTEX_SHADER, vshSrc);
        final int f = compile(GL20C.GL_FRAGMENT_SHADER, fshSrc);

        final int p = GL20C.glCreateProgram();
        GL20C.glAttachShader(p, v);
        GL20C.glAttachShader(p, f);
        GL30C.glBindFragDataLocation(p, 0, "fragColor");
        GL20C.glLinkProgram(p);
        if (GL20C.glGetProgrami(p, GL20C.GL_LINK_STATUS) == 0) {
            final String log = GL20C.glGetProgramInfoLog(p);
            GL20C.glDeleteProgram(p);
            GL20C.glDeleteShader(v);
            GL20C.glDeleteShader(f);
            throw new IllegalStateException("链接失败: " + log);
        }
        GL20C.glDeleteShader(v);
        GL20C.glDeleteShader(f);

        GlStateManager._glUseProgram(p);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(p, "DiffuseSampler0"), 0);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(p, "DiffuseDepthSampler"), 1);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(p, "Palette"), 2);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(p, "Dither"), 3);
        uInSize = GL20C.glGetUniformLocation(p, "InSize");
        uLineColor = GL20C.glGetUniformLocation(p, "LineColor");
        uLineShadowColor = GL20C.glGetUniformLocation(p, "LineShadowColor");
        uPaletteOffset = GL20C.glGetUniformLocation(p, "PaletteOffset");
        uFadeScale = GL20C.glGetUniformLocation(p, "FadeScale");
        GlStateManager._glUseProgram(0);

        vao = GL30C.glGenVertexArrays();
        program = p;
        LOGGER.info("[图解描边] outline_diagram 着色器编译链接成功");
    }

    private static int compile(final int type, final String src) {
        final int id = GL20C.glCreateShader(type);
        GL20C.glShaderSource(id, src);
        GL20C.glCompileShader(id);
        if (GL20C.glGetShaderi(id, GL20C.GL_COMPILE_STATUS) == 0) {
            final String log = GL20C.glGetShaderInfoLog(id);
            GL20C.glDeleteShader(id);
            throw new IllegalStateException("编译失败(" + (type == GL20C.GL_VERTEX_SHADER ? "顶点" : "片元") + "): " + log);
        }
        return id;
    }

    private static String load(final ResourceLocation rl) throws Exception {
        try (final InputStream in = Minecraft.getInstance().getResourceManager().open(rl)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int textureId(final ResourceLocation rl) {
        return Minecraft.getInstance().getTextureManager().getTexture(rl).getId();
    }

    /**
     * [手机端优化·B2 / F1] 描边后处理的「廉价替代」：直接把输入颜色缓冲原样拷贝到输出。
     *
     * <p>描边这一步是一个全屏片元着色器，逐像素采样 4 张纹理（颜色 / 深度 / 调色板 / 抖动）
     * 并做邻域深度比较，在手机 GPU、尤其 gl4es / VirGL 这类翻译层上是图解界面最贵的固定开销。
     * 关闭后图样退化为「无蓝图线稿描边的实色缩略图」，结构轮廓仍然可辨，但省下整趟后处理。
     *
     * <p>GL 状态收尾与 {@link #run} 完全一致（关闭深度测试等），避免界面频闪。
     */
    public static void runCheapCopy(final AdvancedFbo inFbo, final AdvancedFbo outFbo) {
        outFbo.clear();
        blitFallback(inFbo, outFbo);

        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        AdvancedFbo.unbind();
    }

    /** 着色器不可用时的保底：把颜色缓冲原样拷到输出（等价旧降级行为，至少可见）。 */
    private static void blitFallback(final AdvancedFbo inFbo, final AdvancedFbo outFbo) {
        GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, inFbo.getId());
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, outFbo.getId());
        GL30C.glBlitFramebuffer(0, 0, inFbo.getWidth(), inFbo.getHeight(),
                0, 0, outFbo.getWidth(), outFbo.getHeight(),
                GL11C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);
        AdvancedFbo.unbind();
    }
}
