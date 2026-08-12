package dev.ryanhcode.sable.render.sky_light_shadow;

import net.minecraft.client.renderer.ShaderInstance;

/**
 * Ported from Veil 4.x sky-light shadow renderer.
 *
 * <p>Upstream Sable rendered an off-screen depth FBO (Veil {@code AdvancedFbo}
 * + {@code VeilLevelPerspectiveRenderer}) and sampled it inside the chunk
 * shader through GLSL injection ({@code ocelot.glslprocessor}). Forge 1.20.1
 * has no Veil and no global shader-injection API, so the shadow depth map
 * is never produced — rendering falls back to vanilla lighting. The vanilla
 * chunk shaders simply don't expose the {@code SableShadows*} uniforms, so
 * {@code bindShadowMapTexture} is a no-op and {@code renderingShadowMap()}
 * is always false. The class is kept so the vanilla render path's uniform-setup
 * hook ({@code VanillaSubLevelRenderDispatcher#setupDynamicEffects}) and the
 * {@code sky_light_shadow.LevelRendererMixin} redirects stay wired.
 */
public class SableSkyLightShadows {

    public static final float SHADOW_VOLUME_SIZE = 256f / 2f;

    private static boolean isEnabled = false;

    public static boolean isEnabled() {
        return isEnabled;
    }

    public static void setIsEnabled(final boolean isEnabled) {
        SableSkyLightShadows.isEnabled = isEnabled;
    }

    /**
     * Upstream returned whether the off-screen shadow map was mid-render.
     * No shadow map is ever rendered on Forge 1.20.1, so this is always false.
     */
    public static boolean renderingShadowMap() {
        return false;
    }

    /**
     * No-op on Forge 1.20.1: vanilla chunk shaders expose no
     * {@code SableShadows*} uniforms and no shadow depth texture is produced,
     * so there is nothing to bind.
     */
    public static void bindShadowMapTexture(final ShaderInstance shader) {
        if (!SableSkyLightShadows.isEnabled()) {
            return;
        }
    }
}
