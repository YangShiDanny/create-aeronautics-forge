package foundry.veil.api.client.render;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.block.ShaderBlock;
import java.util.concurrent.Executor;
import net.minecraft.resources.ResourceLocation;
public final class VeilRenderSystem {
    private VeilRenderSystem() {}
    private static final VeilRenderer RENDERER = new VeilRenderer();
    public static ShaderProgram setShader(ResourceLocation id) { return null; }
    public static boolean tessellationSupported() { return false; }
    public static CullFrustum getCullingFrustum() { return null; }
    public static void bind(String name, ShaderBlock block) {}
    public static void unbind(ShaderBlock block) {}
    public static Executor renderThreadExecutor() { return Runnable::run; }
    public static VeilRenderer renderer() { return RENDERER; }
}
