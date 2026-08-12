package dev.ryanhcode.sable.mixin.entity.entity_rendering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
    private void renderEntityOnSubLevel(final Entity entity,
                                        final double cameraX,
                                        final double cameraY,
                                        final double cameraZ,
                                        final float partialTick,
                                        final PoseStack poseStack,
                                        final MultiBufferSource multiBufferSource,
                                        final CallbackInfo ci,
                                        @Local(ordinal = 3) final LocalDoubleRef entityX,
                                        @Local(ordinal = 4) final LocalDoubleRef entityY,
                                        @Local(ordinal = 5) final LocalDoubleRef entityZ,
                                        @Share("renderPose") final LocalRef<Pose3dc> renderPoseShare) {
        final SubLevel sable$tracking = Sable.HELPER.getTrackingSubLevel(entity);

        // Render the entity on the data
        final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(entity);

        if (subLevel == null) {
            // Tracking sub-levels
            final SubLevel trackingSubLevel = sable$tracking;

            if (trackingSubLevel instanceof final ClientSubLevel clientSubLevel && !entity.isPassenger()) {
                final Vector3d oldTrackingPosLocal = trackingSubLevel.lastPose().transformPositionInverse(new Vector3d(entity.xOld, entity.yOld, entity.zOld));
                final Vector3d newTrackingPosLocal = trackingSubLevel.logicalPose().transformPositionInverse(JOMLConversion.toJOML(entity.position()));

                final Vector3d interpolatedTrackingPosLocal = new Vector3d(
                        Mth.lerp(partialTick, oldTrackingPosLocal.x, newTrackingPosLocal.x),
                        Mth.lerp(partialTick, oldTrackingPosLocal.y, newTrackingPosLocal.y),
                        Mth.lerp(partialTick, oldTrackingPosLocal.z, newTrackingPosLocal.z)
                );

                final Pose3dc renderPose = clientSubLevel.renderPose(partialTick);
                renderPose.transformPosition(interpolatedTrackingPosLocal);

                entityX.set(interpolatedTrackingPosLocal.x);
                entityY.set(interpolatedTrackingPosLocal.y);
                entityZ.set(interpolatedTrackingPosLocal.z);
            }

            return;
        }

        // [BUG36·根因] 此处曾有一段 DiagramEntity 例外（直接 return，跳过 renderPose 变换），
        // 其前提是「客户端数据包处理器已把图解实体从 plot 空间转回世界坐标」——该前提恒为假：
        // getContaining(Entity) 按 entity.chunkPosition() 查 plot，板子在世界坐标 384 时命中的是
        // chunk(24,2)（无 plot）必然返回 null，根本走不到这里；能进本分支即证明坐标已是 plot 空间
        // （约 2048 万格）。那段例外恰好在唯一需要变换的场合跳过了变换，使板子被画到 2048 万格外
        // 而隐形（物理化后不可见、去物理化即恢复）。已删除，与上游 NeoForge 1.21.1 保持一致。
        //
        // [1.20.1 图解隐形修复·二次修正] 探针实测：containing 实体的插值坐标 entityX/Y/Z 就是
        // plot 空间坐标（约 2048 万格，与方块同源），renderPose 输入即 plot 空间，
        // 与上游一致直接变换即可。此前误加的 logicalPose 逆变换把渲染位置算飞导致画不出来。
        final Pose3dc renderPose = subLevel.renderPose(partialTick);
        final Vector3d transformedPosition = renderPose.transformPosition(
                new Vector3d(entityX.get(), entityY.get(), entityZ.get()));

        renderPoseShare.set(renderPose);

        entityX.set(transformedPosition.x);
        entityY.set(transformedPosition.y);
        entityZ.set(transformedPosition.z);
    }

    @WrapOperation(method = "m_109517_", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;m_114384_(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = false))
    private void renderEntity(final EntityRenderDispatcher instance,
                              final Entity entity,
                              final double x,
                              final double y,
                              final double z,
                              final float g,
                              final float h,
                              final PoseStack poseStack,
                              final MultiBufferSource multiBufferSource,
                              final int i,
                              final Operation<Void> original,
                              @Share("renderPose") final LocalRef<Pose3dc> renderPoseShare) {
        final Pose3dc pose = renderPoseShare.get();
        if (pose != null) {
            // Forge 1.20.1: PoseStack is native; replicate Veil MatrixStack.rotateAround(quat, x, y, z)
            // which is translate(x,y,z) * rotate(quat) * translate(-x,-y,-z).
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(new Quaternionf(pose.orientation()));
            poseStack.translate(-x, -y, -z);
            original.call(instance, entity, x, y, z, g, h, poseStack, multiBufferSource, i);
            poseStack.popPose();
        } else {
            original.call(instance, entity, x, y, z, g, h, poseStack, multiBufferSource, i);
        }
    }
}
