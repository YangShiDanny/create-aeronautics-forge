package foundry.veil.api.client.render.post;

import net.minecraft.resources.ResourceLocation;

public final class PostProcessingManager {
    public void add(PostPipeline pipeline) {}
    public void remove(PostPipeline pipeline) {}
    public PostPipeline getPipeline(ResourceLocation id) { return new PostPipeline(); }
    public PostPipeline.Context getPostPipelineContext() { return new PostPipeline.Context(); }
    public void runPipeline(PostPipeline pipeline) {}
    public void runPipeline(PostPipeline pipeline, final boolean bl) {}
}
