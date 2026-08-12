package dev.ryanhcode.sable.forge.compat;

/**
 * 软依赖检测：Embeddium / Sodium 会接管原版地形渲染管线，
 * 使 sable 挂在 {@code LevelRenderer.renderChunkLayer} 上的子层级绘制注入点失效。
 * 检测到后改用 Forge {@code RenderLevelStageEvent} 作为绘制时机。
 */
public final class EmbeddiumCompat {
    private static final boolean LOADED;

    static {
        boolean loaded = false;
        try {
            // Embeddium 1.20.1 与 Rubidium/Sodium 0.5 共用 me.jellysquid.mods.sodium 包名。
            // 1.21 上游使用 net.caffeinemc.mods.sodium，但本 Forge 1.20.1 移植不处理该分支。
            Class.forName("me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", false, EmbeddiumCompat.class.getClassLoader());
            loaded = true;
        } catch (final ClassNotFoundException ignored) {
        }
        LOADED = loaded;
    }

    private EmbeddiumCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }
}
