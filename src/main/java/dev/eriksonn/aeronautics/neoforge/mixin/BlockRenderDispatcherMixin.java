package dev.eriksonn.aeronautics.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.eriksonn.aeronautics.index.AeroTags;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockRenderDispatcher.class)
public class BlockRenderDispatcherMixin {

    @Dynamic
    @WrapMethod(method = "m_110918_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V", remap = false)
    public void renderBreakingTexture(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack, VertexConsumer consumer, Operation<Void> original) {
        if (state.is(AeroTags.BlockTags.LEVITITE)) {
            poseStack.pushPose();
            float s = 0.0001f;
            poseStack.translate(-s, -s, -s);
            s *= 2;
            poseStack.scale(1 + s, 1 + s, 1 + s);
            original.call(state, pos, level, poseStack, consumer);
            poseStack.popPose();
        } else {
            original.call(state, pos, level, poseStack, consumer);
        }
    }

}
