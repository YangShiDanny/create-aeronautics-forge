package dev.ryanhcode.sable.sublevel.render.fancy;

/**
 * Fancy sub-level shader preprocessor.
 *
 * <p>On Neoforge 1.21.1 this implemented a Veil/ocelot GLSL injection for
 * sable's dynamic sub-level vertex shader. The Veil shader pipeline is unavailable
 * on Forge 1.20.1, so this is a no-op placeholder that keeps the public
 * constant referenced elsewhere. The actual injection is a TODO for a later pass.
 */
public class FancySubLevelShaderProcessor {
    public static final String BUFFER_SIZE = "SABLE_TEXTURE_CACHE_SIZE";
}
