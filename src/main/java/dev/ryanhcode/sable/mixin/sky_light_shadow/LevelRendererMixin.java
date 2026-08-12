package dev.ryanhcode.sable.mixin.sky_light_shadow;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    // TODO: neo dies
/*
    @Inject(method = "renderChunkLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;close()V", shift = At.Shift.AFTER))
    private void sable$onRenderSectionLayer(final RenderType renderType, final double d, final double e, final double f, final Matrix4f matrix4f, final Matrix4f matrix4f2, final CallbackInfo ci, @Local final ShaderInstance shader) {
        SableSkyLightShadows.bindShadowMapTexture(shader);
    }

    @WrapOperation(method = "m_172993_", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher$CompiledChunk;m_112758_(Lnet/minecraft/client/renderer/RenderType;)Z", remap = false))
    private boolean sable$wrapRenderSectionLayer(final ChunkRenderDispatcher.CompiledChunk instance, final RenderType renderType, final Operation<Boolean> original) {
        return SableSkyLightShadows.renderingShadowMap() || original.call(instance, renderType);
    }

    *//**
     * Don't render entities if we're rendering the shadow map
     *//*
    @WrapOperation(method = "m_109089_", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;m_114397_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z", remap = false))
    private boolean sable$wrapRenderLevel(final EntityRenderDispatcher instance, final Entity entity, final Frustum frustum, final double d, final double e, final double f, final Operation<Boolean> original) {
        return !SableSkyLightShadows.renderingShadowMap() && original.call(instance, entity, frustum, d, e, f);
    }

    @WrapWithCondition(method = "m_109089_", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;m_102316_(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V", remap = false))
    private boolean sable$wrapRenderParticles(final ParticleEngine instance, final LightTexture lightTexture, final Camera camera, final float f) {
        return !SableSkyLightShadows.renderingShadowMap();
    }*/
}
