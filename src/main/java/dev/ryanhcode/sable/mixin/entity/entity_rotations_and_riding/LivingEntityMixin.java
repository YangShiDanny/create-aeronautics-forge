package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity{

    @Shadow protected abstract float getJumpPower();

    public LivingEntityMixin(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    public void sable$jumpFromGround(final CallbackInfo ci) {
        final Quaterniondc orientation = EntitySubLevelUtil.getCustomEntityOrientation(this, 1.0f);
        if (orientation == null) return;

        final float power = this.getJumpPower();
        if (!(power <= 1.0E-5F)) {
            final Vector3d deltaMovement = JOMLConversion.toJOML(this.getDeltaMovement());
            final Vector3d up = orientation.transform(OrientedBoundingBox3d.UP, new Vector3d());
            deltaMovement.fma(-up.dot(deltaMovement), up).fma(power, up);
            this.setDeltaMovement(deltaMovement.x, deltaMovement.y, deltaMovement.z);

            if (this.isSprinting()) {
                final float yRot = this.getYRot() * (float) (Math.PI / 180.0);
                final Vec3 horizontalImpulse = new Vec3((double) (-Mth.sin(yRot)) * 0.2, 0.0, (double) Mth.cos(yRot) * 0.2);
                this.addDeltaMovement(JOMLConversion.toMojang(orientation.transform(JOMLConversion.toJOML(horizontalImpulse))));
            }

            this.hasImpulse = true;
        }

        ci.cancel();
    }

    // 【BUG-12 修复 · 下座坐标回投】
    // 1.20.1 的 LivingEntity.dismountVehicle(m_21028_) 内部调用的是 dismountTo(DDD)V（SRG m_142098_），
    // 与源版 1.21.1 一致，只是 SRG 名不同。移植时源版写的是 "Lnet/.../LivingEntity;dismountTo(DDD)V"（干净名+remap），
    // 这里必须用运行时 SRG 名 m_142098_ 且 remap=false，否则编译能过、运行时注入静默失败（defaultRequire=0）。
    // 坐垫实体存于 plot 坐标（约 2048 万），getDismountLocationForPassenger 返回的是 plot 坐标，
    // 必须整体经 logicalPose().transformPosition 回投到世界坐标，否则玩家下座后落在 plot 虚空处。
    @WrapOperation(method = "m_21028_(Lnet/minecraft/world/entity/Entity;)V", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_142098_(DDD)V"))
    public void sable$onDismountVehicle(final LivingEntity instance, final double x, final double y, final double z, final Operation<Void> original) {
        final Vec3 dismountPosition = new Vec3(x, y, z);
        final SubLevel subLevel = Sable.HELPER.getContaining(instance.level(), dismountPosition);

        if (subLevel == null) {
            original.call(instance, x, y, z);
            return;
        }

        final Vec3 pos = subLevel.logicalPose().transformPosition(dismountPosition);
        original.call(instance, pos.x, pos.y, pos.z);
    }

    // 1.20.1 下座坐标已由上面的 sable$onDismountVehicle（包 dismountTo）统一回投到世界坐标，
    // 此处仅对传送门/实体已移除分支里的 Math.max 做透传，避免对 Y 轴做二次变换导致坐标错乱。
    @Redirect(remap = false, method = "m_21028_(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D"))
    public double sable$maxAltitude(final double a, final double b, @Local(argsOnly = true) final Entity vehicle) {
        return Math.max(a, b);
    }

}
