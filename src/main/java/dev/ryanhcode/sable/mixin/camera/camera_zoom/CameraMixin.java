package dev.ryanhcode.sable.mixin.camera.camera_zoom;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.ryanhcode.sable.mixinterface.camera.camera_zoom.CameraZoomExtension;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(Camera.class)
public abstract class CameraMixin implements CameraZoomExtension {

    @Shadow
    private BlockGetter level;
    @Shadow
    private Vec3 position;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    private Entity entity;
    @Unique
    private boolean sable$pushed = false;
    @Unique
    private float sable$zoomAmount;
    @Unique
    private float sable$interpolatedZoom;
    @Unique
    private float sable$lastInterpolatedZoom;
    @Shadow
    protected abstract void setPosition(double d, double e, double f);

    @Inject(method = "tick", at = @At("HEAD"))
    private void sable$preTick(final CallbackInfo ci) {
        this.sable$lastInterpolatedZoom = this.sable$interpolatedZoom;
        this.sable$interpolatedZoom = Mth.lerp(0.725f, this.sable$interpolatedZoom, this.sable$zoomAmount);
    }

    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", shift = At.Shift.AFTER))
    private void sable$setup(final BlockGetter blockGetter, final Entity entity, final boolean bl, final boolean bl2, final float f, final CallbackInfo ci) {
        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW || minecraft.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            final Entity cameraEntity = minecraft.cameraEntity;
            final Entity vehicle = cameraEntity.getVehicle();

            // [BUG-34·修复] 物理化气球靠 tracking 系统把骑乘实体映射到 SubLevel；必须用 getTrackingOrVehicleSubLevel
            // （先 tracking 后 vehicle），不能用 getVehicleSubLevel（只走世界坐标区块查偏移 plot → 必取 null）。
            if (vehicle != null) {
                final SubLevel subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(cameraEntity);

                if (subLevel instanceof final ClientSubLevel clientSubLevel) {
                    final Vector3dc pos = clientSubLevel.renderPose().position();
                    this.setPosition(pos.x(), pos.y(), pos.z());
                }
            }
        }
    }

    @Unique
    private float sable$clampZoom(final float maxZoom, final SubLevel ignoredSubLevel) {
        float zoom = maxZoom;

        final float partialTick = Minecraft.getInstance().getPartialTick();

        final Level level = this.entity.level();
        final LevelPoseProviderExtension extension = ((LevelPoseProviderExtension) this.level);
        assert extension != null;

        final Collection<SubLevel> ignoredChain = SubLevelHelper.getConnectedChain(ignoredSubLevel);

        extension.sable$pushPoseSupplier((subLevel) -> ((ClientSubLevel) subLevel).renderPose(partialTick));

        for (int i = 0; i < 8; i++) {
            final float offsetX = (float) ((i & 1) * 2 - 1);
            final float offsetY = (float) ((i >> 1 & 1) * 2 - 1);
            final float offsetZ = (float) ((i >> 2 & 1) * 2 - 1);

            final Vec3 vec3 = this.position.add(offsetX * 0.1F, offsetY * 0.1F, offsetZ * 0.1F);
            final Vec3 vec32 = vec3.add(new Vec3(this.forwards).scale(-zoom));

            final ClipContext clipContext = new ClipContext(vec3, vec32, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity);
            ((ClipContextExtension) clipContext).sable$setSubLevelIgnoring(ignoredChain::contains);
            final HitResult hitResult = this.level.clip(clipContext);

            if (hitResult.getType() != HitResult.Type.MISS) {
                final float l = (float) Sable.HELPER.distanceSquaredWithSubLevels(level, hitResult.getLocation(), this.position);
                if (l < Mth.square(zoom)) {
                    zoom = Mth.sqrt(l);
                }
            }
        }

        extension.sable$popPoseSupplier();

        return zoom;
    }

    // [1.20.1 修正] 1.20.1 的 getMaxZoom() 无参且只返回字段，真正的「带循环 raycast 缩放回退」是 getZoom(double) = m_90566_。
    // 源版 NeoForge 1.21.1 的 getMaxZoom(float) 角色等同于本版 getZoom，refmap 按 (D)D 把它错配到了 m_90566_，
    // 但回调用了 CallbackInfoReturnable<Float> 注入到返回 double 的 getZoom，导致 setReturnValue(float) 后
    // getReturnValueD() 转型崩溃（切到子层级相机视角即触发）。此处显式钉死 m_90566_ + remap=false，
    // 回调改用 CallbackInfoReturnable<Double>，彻底消除 Float/ Double 错配。
    @Inject(method = "m_90566_", at = @At(value = "HEAD"), cancellable = true, remap = false)
    private void sable$getMaxZoomHead(final double f, final CallbackInfoReturnable<Double> cir) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Entity cameraEntity = minecraft.cameraEntity;
        final Entity vehicle = cameraEntity != null ? cameraEntity.getVehicle() : null;

        if (minecraft.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW || minecraft.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            if (vehicle != null) {
                // [BUG-34·修复] 物理化气球靠 tracking 系统；必须用 getTrackingOrVehicleSubLevel（先 tracking 后 vehicle）。
                final SubLevel subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(cameraEntity);

                if (subLevel != null) {
                    final float partialTick = Minecraft.getInstance().getPartialTick();
                    final float zoomAmount = Mth.lerp(partialTick, this.sable$lastInterpolatedZoom, this.sable$interpolatedZoom);

                    final BoundingBox3ic boundingBox = subLevel.getPlot().getBoundingBox();
                    final Vec3 extents = new Vec3(boundingBox.maxX() - boundingBox.minX(), boundingBox.maxY() - boundingBox.minY(), boundingBox.maxZ() - boundingBox.minZ());
                    final double maxDist = extents.scale(0.5).length();
                    final float desiredDistance = (float) Math.max(f, maxDist) * (1.75f + zoomAmount);
                    cir.setReturnValue((double) this.sable$clampZoom(desiredDistance, subLevel));
                    // [BUG-34·修复] 必须 cancel 才能让自定义返回值替换原方法体，否则原版 getZoom 用第三人称默认距离覆盖。
                    cir.cancel();
                    this.sable$pushed = false;
                    return;
                }
            }
        }

        final LevelPoseProviderExtension extension = ((LevelPoseProviderExtension) minecraft.level);
        assert extension != null;
        extension.sable$pushPoseSupplier((subLevel) -> ((ClientSubLevel) subLevel).renderPose(minecraft.getPartialTick()));
        this.sable$pushed = true;
    }

    // [1.20.1 修正] getZoom 内部调用 Vec3.distanceTo（SRG m_82554_）。method 与 target 均用 SRG + remap=false，
    // 避免 refmap 把 getMaxZoom 错配成 m_90566_ 的同时又保留干净名导致歧义。
    @Redirect(method = "m_90566_", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;m_82554_(Lnet/minecraft/world/phys/Vec3;)D", remap = false))
    private double sable$getMaxZoom(final Vec3 instance, final Vec3 vec3) {
        return Sable.HELPER.distanceSquaredWithSubLevels((Level) this.level, instance, vec3);
    }

    @Inject( method = "m_90566_", at = @At(value = "RETURN"), remap = false)
    private void sable$getMaxZoomTail(final double f, final CallbackInfoReturnable<Double> cir) {
        if (this.sable$pushed) {
            final LevelPoseProviderExtension extension = ((LevelPoseProviderExtension) Minecraft.getInstance().level);
            assert extension != null;
            extension.sable$popPoseSupplier();
            this.sable$pushed = false;
        }
    }

    @Override
    public float sable$getZoomAmount() {
        return this.sable$zoomAmount;
    }

    @Override
    public void sable$setZoomAmount(final float sable$zoomAmount) {
        this.sable$zoomAmount = Mth.clamp(sable$zoomAmount, 0.0f, 4.0f);
    }
}
