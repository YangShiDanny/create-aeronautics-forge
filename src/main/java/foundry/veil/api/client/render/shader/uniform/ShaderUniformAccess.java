package foundry.veil.api.client.render.shader.uniform;
/**
 * Backport shim of Veil's {@code ShaderUniformAccess}. The real Veil shader
 * uniform API does not exist on Forge 1.20.1; this shim keeps the
 * simulated/aeronautics call sites compiling. Methods are no-ops because the
 * actual shader/uniform plumbing is deferred to phase 2 (Embeddium-native
 * rewrite) — effects are dropped, gameplay logic still runs.
 */
public interface ShaderUniformAccess {
    default void setFloat(float value) {}
    default void setInt(int value) {}
    default void setVec3(float x, float y, float z) {}
    default void setVec4(float x, float y, float z, float w) {}
    default void setVector(float x, float y, float z, float w) {}
    default void setVector(float x, float y) {}
}
