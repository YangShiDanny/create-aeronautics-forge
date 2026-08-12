package foundry.veil.api.client.render.post;
import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;
public class PostPipeline {
    public static final class Context {
    }

    public ShaderUniformAccess getUniformSafe(final String name) {
        return new ShaderUniformAccess() {};
    }
}
