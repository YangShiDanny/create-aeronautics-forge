package dev.ryanhcode.sable.compatibility;

import net.minecraftforge.fml.ModList;
import dev.ryanhcode.sable.Sable;

/**
 * 光影兼容性检测工具。
 *
 * <p>Forge 1.20.1 上光影由 Oculus（mod id = {@code oculus}）提供，其 API 包名沿用
 * {@code net.irisshaders.iris.api.v0.IrisApi}。本模组 jar 不内含任何 {@code net.irisshaders.iris}
 * 包，仅通过反射在运行时查询，避免与 Oculus 产生 JPMS 分包冲突（{@code ResolutionException}）。
 *
 * <p>判定「光影是否启用」用 {@code IrisApi.getInstance().getConfig().areShadersEnabled()} 再与
 * {@code isShaderPackInUse()} 取交集，比单看其一更稳：前者是「光影功能总开关」，后者是「当前确有着色器包在跑」。
 */
public final class SableShaderCompat {

    /** 上一次检测光影状态的时间戳，用于缓存避免每帧反射。 */
    private static long sable$lastCheckTime = 0L;
    /** 上一次检测到的光影状态。 */
    private static boolean sable$lastResult = false;

    private SableShaderCompat() {
    }

    /**
     * @return 当前客户端是否启用了光影包（Oculus 已加载、光影功能开启、且正在使用着色器包）。
     */
    public static boolean areShadersActive() {
        final long now = System.currentTimeMillis();
        if (now - sable$lastCheckTime < 1000L) {
            return sable$lastResult;
        }
        sable$lastCheckTime = now;
        sable$lastResult = computeAreShadersActive();
        return sable$lastResult;
    }

    private static boolean computeAreShadersActive() {
        // Forge 1.20.1 上 Iris 模组本身不存在，实际光影由 Oculus 提供。
        if (!ModList.get().isLoaded("oculus")) {
            return false;
        }
        try {
            final Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            final Object instance = irisApi.getMethod("getInstance").invoke(null);
            // areShadersEnabled() 在 IrisApiConfig 上（需经 getConfig() 取）。
            final Object config = irisApi.getMethod("getConfig").invoke(instance);
            final boolean enabled = (Boolean) config.getClass().getMethod("areShadersEnabled").invoke(config);
            final boolean inUse = (Boolean) irisApi.getMethod("isShaderPackInUse").invoke(instance);
            return enabled && inUse;
        } catch (final Throwable ignored) {
            // API 不可用或调用失败时保守返回 false，走原版渲染路径。
            return false;
        }
    }
}
