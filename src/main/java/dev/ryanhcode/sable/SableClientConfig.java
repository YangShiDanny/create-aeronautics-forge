package dev.ryanhcode.sable;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-side config for the Forge 1.20.1 port.
 *
 * <p>The upstream NeoForge client config carried several Veil-only rendering
 * toggles (dynamic shading, water occlusion, skylight shadows, renderer
 * selection) whose {@code onUpdate} path depended on the Veil rendering API.
 * Those fields are intentionally omitted here because the Veil-dependent render
 * package was moved out of the compiled source set. The four values kept below
 * are the only ones actually read by the built source, and they must be real
 * {@link ForgeConfigSpec} entries (not {@code null}) or client-side ticks crash
 * with an NPE when dereferencing {@code .get()}.
 */
public final class SableClientConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue ZOOM_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue INTERPOLATION_DELAY;
    public static final ForgeConfigSpec.BooleanValue ATTEMPT_UDP_NETWORKING;
    public static final ForgeConfigSpec.BooleanValue DEBUG_DRAW_LOADED_CHUNKS;

    // ===== [手机端优化·S2] 手机端（安卓 FCL/Pojav 等加载器）优化开关 =====
    /**
     * 主开关：{@code auto}=环境是安卓则自动开、否则关；{@code on}=强制开启（PC 上测优化用）；
     * {@code off}=强制关闭（误判时手动覆盖）。
     */
    public static final ForgeConfigSpec.ConfigValue<String> MOBILE_OPTIMIZATION;
    /**
     * 性能档位覆盖：{@code auto}=按 GL 渲染器自动判定（原生 GPU 中档 / 翻译层激进）；
     * {@code native}=强制原生 GPU 中档；{@code translation}=强制翻译层激进档。
     */
    public static final ForgeConfigSpec.ConfigValue<String> MOBILE_TIER;
    /** 手机端是否强制使用轻量（Vanilla 区块化）渲染路径，跳过更重的渲染分支。 */
    public static final ForgeConfigSpec.BooleanValue MOBILE_FORCE_VANILLA_RENDERER;
    /** 手机端离屏帧缓冲（图解 / 便签）分辨率缩放，1.0=原分辨率，0.5=半分辨率。 */
    public static final ForgeConfigSpec.DoubleValue MOBILE_FBO_SCALE;
    /** 手机端是否关闭图解「蓝图线稿」描边后处理（glBlit/着色器在移动端极慢）。 */
    public static final ForgeConfigSpec.BooleanValue MOBILE_DISABLE_OUTLINE_POST;
    /** 手机端每帧最多同步编译的子层级区块数（分帧限流，防主线程卡顿）。 */
    public static final ForgeConfigSpec.IntValue MOBILE_CHUNK_COMPILE_PER_FRAME;
    /** 手机端（仅单人/自建房）玩家超过此距离（方块）的子层级进入物理休眠，不再做物理预算。 */
    public static final ForgeConfigSpec.DoubleValue MOBILE_PHYSICS_SLEEP_DISTANCE;
    /** 手机端每帧最多渲染的方块实体数量（距离裁剪后的数量上限，防主线程卡顿）。 */
    public static final ForgeConfigSpec.IntValue MOBILE_BE_MAX_PER_FRAME;
    /** 手机端方块实体渲染距离裁剪阈值（方块）：超出此距离（主世界坐标）的方块实体不渲染；0 表示不限制。 */
    public static final ForgeConfigSpec.DoubleValue MOBILE_BE_RENDER_DISTANCE;

    static {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        DEBUG_DRAW_LOADED_CHUNKS = builder
                .comment("Whether to draw loaded chunks on the client in the chunk debug renderer")
                .define("debug_draw_loaded_chunks", false);
        INTERPOLATION_DELAY = builder
                .comment("The distance back in game-ticks that the snapshot interpolation should operate")
                .defineInRange("sub_level_snapshot_interpolation_delay_ticks", 1.5, 0.0, 100.0);
        ZOOM_SENSITIVITY = builder
                .comment("The zoom sensitivity for sub-level camera types")
                .defineInRange("sub_level_zoom_sensitivity", 0.2, 0.0, 100.0);
        ATTEMPT_UDP_NETWORKING = builder
                .comment("If Sable should attempt to establish a UDP connection with the server, to receive sub-level movement data over a UDP channel")
                .define("attempt_udp_networking", true);

        // [手机端优化·S2] 以下为手机端优化专用开关，默认仅在检测到安卓时生效。
        MOBILE_OPTIMIZATION = builder
                .comment("Mobile (Android FCL/Pojav etc.) optimization master switch: 'auto' (detect android), 'on' (force enable), 'off' (force disable)")
                .define("mobile_optimization", "auto");
        MOBILE_TIER = builder
                .comment("Mobile performance tier override: 'auto' (detect from GL renderer), 'native' (mid degrade), 'translation' (aggressive degrade for gl4es/VirGL/Zink/llvmpipe/SwiftShader)")
                .define("mobile_tier", "auto");
        MOBILE_FORCE_VANILLA_RENDERER = builder
                .comment("On mobile, force the lightweight Vanilla chunked render path")
                .define("mobile_force_vanilla_renderer", true);
        MOBILE_FBO_SCALE = builder
                .comment("On mobile, scale factor for offscreen framebuffers (diagram/sticky note). 1.0 = full (default), lower values reduce resolution to help weak GPUs")
                .defineInRange("mobile_fbo_scale", 1.0, 0.25, 1.0);
        MOBILE_DISABLE_OUTLINE_POST = builder
                .comment("On mobile, disable the diagram outline (blueprint) post-processing pass")
                .define("mobile_disable_outline_post", true);
        MOBILE_CHUNK_COMPILE_PER_FRAME = builder
                .comment("On mobile, max sub-level chunk sections compiled synchronously per frame (rate-limit to avoid main-thread stalls)")
                .defineInRange("mobile_chunk_compile_per_frame", 2, 1, 64);
        MOBILE_PHYSICS_SLEEP_DISTANCE = builder
                .comment("On mobile singleplayer, sub-levels farther than this distance (blocks) from the player enter physics sleep")
                .defineInRange("mobile_physics_sleep_distance", 64.0, 0.0, 1024.0);
        MOBILE_BE_MAX_PER_FRAME = builder
                .comment("On mobile, max block entities rendered per frame (after distance culling), to avoid main-thread stalls")
                .defineInRange("mobile_be_max_per_frame", 64, 1, 2048);
        MOBILE_BE_RENDER_DISTANCE = builder
                .comment("On mobile, block entities farther than this distance (blocks, world space) from the camera are not rendered; 0 = no limit")
                .defineInRange("mobile_be_render_distance", 48.0, 0.0, 256.0);

        SPEC = builder.build();
    }

    public static void onUpdate(final boolean reloaded) {
        // [手机端优化·S2] 把配置写入 MobilePlatform，使其主开关与档位覆盖立即生效（含游戏内热重载）。
        MobilePlatform.configure(parseMobileMode(MOBILE_OPTIMIZATION.get()), parseMobileTier(MOBILE_TIER.get()));
    }

    private static MobilePlatform.Mode parseMobileMode(final String raw) {
        if (raw == null) {
            return MobilePlatform.Mode.AUTO;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "on", "true", "1" -> MobilePlatform.Mode.ON;
            case "off", "false", "0" -> MobilePlatform.Mode.OFF;
            default -> MobilePlatform.Mode.AUTO;
        };
    }

    private static MobilePlatform.Tier parseMobileTier(final String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "native" -> MobilePlatform.Tier.NATIVE;
            case "translation" -> MobilePlatform.Tier.TRANSLATION;
            default -> null;
        };
    }
}
