package foundry.veil.api.client.render.shader.program;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;
import org.joml.Matrix4fc;
/**
 * Backport shim of Veil's {@code ShaderProgram}. No-op on Forge 1.20.1
 * (shader plumbing deferred to phase 2). {@link #getUniformSafe} returns a
 * no-op {@link ShaderUniformAccess} so call sites compile and run without NPE.
 */
public class ShaderProgram {
    public void free() {}
    public static void unbind() {}

    public ShaderUniformAccess getUniformSafe(final String name) {
        return new ShaderUniformAccess() {};
    }

    public void setDefaultUniforms(final VertexFormat.Mode mode) {}
    public void setDefaultUniforms(final VertexFormat.Mode mode, final Matrix4fc modelView, final Matrix4fc projection) {}
    public void setDefaultUniforms(final VertexFormat.Mode mode, final PoseStack pose, final Matrix4fc modelView, final Matrix4fc projection) {}
    public void setDefaultUniforms(final VertexFormat.Mode mode, final Matrix4fc modelView, final Matrix4fc projection, final Window window) {}
}
