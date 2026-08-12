package foundry.veil.platform;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.forge.event.ForgeVeilRegisterBlockLayersEvent;
import foundry.veil.forge.event.ForgeVeilRegisterFixedBuffersEvent;
import org.joml.Matrix4fc;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
public final class VeilEventPlatform {
    public static final VeilEventPlatform INSTANCE = new VeilEventPlatform();
    private VeilEventPlatform() {}

    @FunctionalInterface
    public interface VeilRenderLevelStageHandler {
        void accept(VeilRenderLevelStageEvent.Stage stage, Object levelRenderer,
                    Object bufferSource, Object matrixStack, Matrix4fc frustumMatrix,
                    Matrix4fc projectionMatrix, int renderTick, Object deltaTracker,
                    Object camera, Object frustum);
    }
    public void onVeilRenderLevelStage(VeilRenderLevelStageHandler handler) {}

    public void onVeilRegisterBlockLayers(Consumer<ForgeVeilRegisterBlockLayersEvent> callback) {}
    public void onVeilRegisterFixedBuffers(Consumer<ForgeVeilRegisterFixedBuffersEvent> callback) {}
    public void onVeilAddShaderProcessors(BiConsumer<Object, Object> callback) {}
}
