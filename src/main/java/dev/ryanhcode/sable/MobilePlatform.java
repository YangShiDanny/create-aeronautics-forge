package dev.ryanhcode.sable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Locale;

/**
 * [手机端优化·S1] 统一的手机端（安卓 FCL/Pojav 等加载器）检测与档位判定。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>分层检测、结果只算一次</b>：阶段一为启动期纯环境检测（无需 GL 上下文），
 *       判断「是否安卓」；阶段二在 GL 就绪后由渲染侧调用一次 {@link #detectGlTier()}，
 *       依据 {@code GL_RENDERER}/{@code GL_VERSION} 把手机再细分为
 *       {@link Tier#NATIVE 原生 GPU 档}（Adreno/Mali/… 中档降级）与
 *       {@link Tier#TRANSLATION 翻译层档}（gl4es/VirGL/Zink/llvmpipe/SwiftShader 激进降级）。</li>
 *   <li><b>auto / 强制开 / 强制关</b>：主开关 {@link Mode} 由客户端配置
 *       {@code SableClientConfig} 在加载时通过 {@link #configure(Mode, Tier)} 写入；
 *       非手机端（{@code auto} 且环境非安卓）一律短路跳过优化。</li>
 *   <li><b>服务端可用</b>：{@link #isAndroid()} 与 {@link #isMobile()} 是纯环境判定，
 *       同一 JVM 内客户端与（单人/自建房的）集成服务端共享同一份静态结果；
 *       专用服务器端因环境非安卓、{@code auto} 直接判定为否，绝不会误开优化。</li>
 *   <li><b>客户端依赖惰性加载</b>：涉及 GL 的代码全部收敛在嵌套类 {@link GlInfo} 内，
 *       仅当 {@link #detectGlTier()}（只会在客户端渲染期被调用）真正执行时才加载，
 *       故本类在专用服务端被加载也不会触碰任何客户端/GL 符号。</li>
 * </ul>
 */
public final class MobilePlatform {

    private static final Logger LOGGER = LoggerFactory.getLogger("Sable/MobilePlatform");

    /** 主开关模式。 */
    public enum Mode {
        /** 自动：环境是安卓则开，否则关。 */
        AUTO,
        /** 强制开启（用于 PC 上测试手机端优化）。 */
        ON,
        /** 强制关闭（用于误判时手动覆盖）。 */
        OFF
    }

    /** 性能档位。 */
    public enum Tier {
        /** 非手机端，不启用任何优化。 */
        NONE,
        /** 手机端 + 原生 GPU（Adreno/Mali/PowerVR/Xclipse 等），中度降级。 */
        NATIVE,
        /** 手机端 + 翻译层（gl4es/VirGL/Zink/llvmpipe/SwiftShader 等），激进降级。 */
        TRANSLATION
    }

    /** 由客户端配置写入的主开关模式（默认 AUTO）。 */
    private static volatile Mode mode = Mode.AUTO;
    /** 由客户端配置写入的手动档位覆盖；null 表示「按 GL 自动判定」。 */
    private static volatile Tier tierOverride = null;

    // ===== 阶段一（环境）结果缓存 =====
    private static boolean envResolved = false;
    private static boolean envIsMobile = false;

    // ===== 阶段二（GL）结果缓存 =====
    private static boolean glResolved = false;
    private static Tier glTier = Tier.NATIVE;

    // [F1] 翻译层缺失关键 GL 特性（如浮点颜色缓冲 / 帧缓冲对象）时的安全降级标志。
    private static boolean safeFramebufferMode = false;

    private MobilePlatform() {
    }

    /**
     * [S2 回调] 客户端配置加载/重载时写入主开关模式与手动档位覆盖。
     *
     * @param newMode         主开关模式
     * @param newTierOverride 手动档位覆盖（null = 自动）
     */
    public static void configure(final Mode newMode, final Tier newTierOverride) {
        mode = (newMode == null) ? Mode.AUTO : newMode;
        tierOverride = newTierOverride;
        // 档位覆盖变化后，下一帧渲染会重新跑 detectGlTier 以刷新实际档位。
        glResolved = false;
    }

    public static Mode getMode() {
        return mode;
    }

    public static Tier getTierOverride() {
        return tierOverride;
    }

    /**
     * 判定当前 JVM 是否运行在安卓（FCL/Pojav/Zalith 等加载器）上。
     * 纯环境检测，无需 GL 上下文，结果缓存只算一次。
     */
    public static boolean isAndroid() {
        if (envResolved) {
            return envIsMobile;
        }
        envResolved = true;
        boolean android = false;
        try {
            // 1) os.name 含 android
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("android")) {
                android = true;
            }
            // 2) 存在 /system/build.prop（安卓系统标志文件）
            if (!android) {
                try {
                    if (new File("/system/build.prop").exists()) {
                        android = true;
                    }
                } catch (final Throwable ignored) {
                }
            }
            // 3) 加载器私有目录 / 环境变量特征
            if (!android) {
                final String javaHome = System.getProperty("java.home", "");
                final String tmpdir = System.getProperty("java.io.tmpdir", "");
                if (javaHome.contains("/data/") || javaHome.contains("/storage/emulated")
                        || tmpdir.contains("/data/") || tmpdir.contains("/data/user")) {
                    android = true;
                }
                if (!android) {
                    for (final String key : new String[]{"POJAV_VERSION", "FCL_VERSION", "ZALITH", "POJAV_NATIVEDIR", "POJAV_RENDERER"}) {
                        if (System.getenv(key) != null) {
                            android = true;
                            break;
                        }
                    }
                }
            }
        } catch (final Throwable t) {
            android = false;
        }
        envIsMobile = android;
        return android;
    }

    /**
     * 主开关：是否启用手机端优化。
     */
    public static boolean isMobile() {
        switch (mode) {
            case ON:
                return true;
            case OFF:
                return false;
            default:
                return isAndroid();
        }
    }

    /**
     * 阶段二：GL 上下文就绪后由渲染侧（每客户端进程仅一次）调用，
     * 依据 {@code GL_RENDERER}/{@code GL_VERSION} 判定原生 GPU 还是翻译层，
     * 并顺带做 [F1] 关键 GL 特性探测。
     */
    public static void detectGlTier() {
        if (glResolved) {
            return;
        }
        glResolved = true;

        Tier computed = Tier.NATIVE;
        boolean safeFb = false;
        try {
            final String renderer = GlInfo.getRenderer();
            final String version = GlInfo.getVersion();
            computed = classifyGl(renderer, version);
            safeFb = GlInfo.probeSafeFramebuffer();
        } catch (final Throwable t) {
            computed = Tier.NATIVE;
            safeFb = false;
        }

        safeFramebufferMode = safeFb;
        glTier = (tierOverride != null) ? tierOverride : computed;

        if (isAndroid()) {
            LOGGER.info("[手机端优化] 检测到安卓环境，GL 档位={}（override={}），安全帧缓冲模式={}", glTier, tierOverride, safeFb);
        }
    }

    private static Tier classifyGl(final String renderer, final String version) {
        final String r = ((renderer == null ? "" : renderer) + " " + (version == null ? "" : version)).toLowerCase(Locale.ROOT);
        // 翻译层：软件/兼容栈，需要最激进降级
        if (r.contains("gl4es") || r.contains("holy-gl4es") || r.contains("virgl")
                || r.contains("zink") || r.contains("llvmpipe") || r.contains("swiftshader")
                || r.contains("mesa offscreen") || r.contains("software")) {
            return Tier.TRANSLATION;
        }
        // 原生移动 GPU：中档降级即可
        if (r.contains("adreno") || r.contains("mali") || r.contains("powervr")
                || r.contains("xclipse") || r.contains("vulkan") || r.contains("opengl es")) {
            return Tier.NATIVE;
        }
        // 取不到明确信息时默认原生中档（保守，不误伤）
        return Tier.NATIVE;
    }

    /**
     * 当前生效档位：手动覆盖优先；否则用 GL 阶段结果；
     * GL 阶段尚未跑过时，手机端默认 NATIVE，否则 NONE。
     */
    public static Tier tier() {
        if (tierOverride != null) {
            return tierOverride;
        }
        if (glResolved) {
            return glTier;
        }
        return isMobile() ? Tier.NATIVE : Tier.NONE;
    }

    /** 是否处于翻译层档（gl4es/VirGL/Zink/llvmpipe/SwiftShader 等）。 */
    public static boolean isTranslationLayer() {
        return tier() == Tier.TRANSLATION;
    }

    /**
     * [F1] 是否为「缺失关键 GL 特性」的安全帧缓冲模式。
     * 该模式下 FBO 分辨率进一步下压、描边后处理强制跳过，避免翻译层崩溃。
     */
    public static boolean isSafeFramebufferMode() {
        return safeFramebufferMode || isTranslationLayer();
    }

    /**
     * 供渲染侧安全调用 GL 字符串/特性探测——嵌套类隔离客户端与 LWJGL 依赖，
     * 仅在 {@link #detectGlTier()} 真正执行时才被加载。
     */
    private static final class GlInfo {
        static String getRenderer() {
            return org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_RENDERER);
        }

        static String getVersion() {
            return org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_VERSION);
        }

        /**
         * [F1] 粗粒度探测翻译层常缺失的特性：若连基本帧缓冲对象/颜色附件都不完整，
         * 则进入安全帧缓冲模式。探测失败（取不到）一律按「正常」处理，绝不误伤。
         */
        static boolean probeSafeFramebuffer() {
            try {
                // 颜色附件数量：翻译层（如部分 gl4es 构建）可能异常低或为 0。
                final int maxColor = org.lwjgl.opengl.GL30C.glGetInteger(org.lwjgl.opengl.GL30C.GL_MAX_COLOR_ATTACHMENTS);
                if (maxColor <= 0) {
                    return true;
                }
                return false;
            } catch (final Throwable t) {
                return false;
            }
        }
    }
}
