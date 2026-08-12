package dev.ryanhcode.sable.render.water_occlusion;

/**
 * Water occlusion shader preprocessor.
 *
 * <p>On Neoforge 1.21.1 this class implemented a Veil/ocelot GLSL injection
 * that discarded translucent fragments hidden behind sub-level water. The Veil
 * shader pipeline is unavailable on Forge 1.20.1, so the preprocessor is a
 * no-op placeholder that keeps the public constants used by the rest of the
 * water-occlusion code. The actual effect is re-enabled in a later pass.
 */
public class SableWaterOcclusionPreProcessor {
    public static final String CLOSE_SAMPLER_NAME = "SableCloseSampler";
    public static final String FAR_SAMPLER_NAME = "SableFarSampler";
    public static final String ENABLE_UNIFORM = "SableWaterOcclusionEnabled";
}
