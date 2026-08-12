package dev.ryanhcode.sable.render.sky_light_shadow;

/**
 * Ported from Veil 4.x {@code ShaderPreProcessor} to Forge 1.20.1.
 *
 * <p>Upstream Sable used Veil's GLSL-injection ({@code ocelot.glslprocessor}) to add the
 * {@code SableShadowsEnabled} / {@code SableShadowVolumeSize} / {@code SableShadowOrigin}
 * uniforms and rewrite the chunk shader's lightmap sampling. Forge 1.20.1 has no equivalent
 * global shader-injection API, so the actual injection is dropped. The uniform <em>names</em>
 * are preserved as constants so {@code VanillaSubLevelRenderDispatcher#setupDynamicEffects}
 * still compiles and (no-op) sets them — under vanilla the shader simply has no such
 * uniform, so {@code getUniform(...)} returns null and rendering falls back to vanilla lighting.
 */
public class SableDynamicSkyLightShadowPreProcessor {
    public static final String SAMPLER_NAME = "SableShadowSampler";
    public static final String SHADOW_VOLUME_SIZE_UNIFORM = "SableShadowVolumeSize";
    public static final String ENABLE_UNIFORM = "SableShadowsEnabled";
    public static final String SHADOW_ORIGIN_UNIFORM = "SableShadowOrigin";
}
