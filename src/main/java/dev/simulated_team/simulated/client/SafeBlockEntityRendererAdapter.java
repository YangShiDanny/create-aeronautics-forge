package dev.simulated_team.simulated.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Adapter for SafeBlockEntityRenderer in Create 6.0.8.
 *
 * Problem: Create's SafeBlockEntityRenderer.m_6922_ (SRG name for BlockEntityRenderer.render)
 * is final and not recognized as implementing render() under official mappings.
 * Subclasses that extend SafeBlockEntityRenderer get "does not override abstract method render".
 *
 * This adapter implements BlockEntityRenderer directly, providing a non-final render()
 * that delegates to renderSafe(). Subclasses should extend this instead of SafeBlockEntityRenderer.
 */
public abstract class SafeBlockEntityRendererAdapter<T extends BlockEntity> implements BlockEntityRenderer<T> {

    @Override
    public void render(final T blockEntity, final float partialTicks, final PoseStack poseStack,
                       final MultiBufferSource bufferSource, final int packedLight, final int packedOverlay) {
        if (blockEntity.isRemoved()) {
            return;
        }
        this.renderSafe(blockEntity, partialTicks, poseStack, bufferSource, packedLight, packedOverlay);
    }

    protected abstract void renderSafe(final T blockEntity, final float partialTicks, final PoseStack poseStack,
                                       final MultiBufferSource bufferSource, final int packedLight, final int packedOverlay);
}
