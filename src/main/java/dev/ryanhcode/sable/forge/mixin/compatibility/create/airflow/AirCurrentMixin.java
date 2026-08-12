package dev.ryanhcode.sable.forge.mixin.compatibility.create.airflow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;

@Mixin(AirCurrent.class)
public abstract class AirCurrentMixin {

    @Shadow
    @Final
    public IAirCurrentSource source;

    @Unique
    private WeakReference<SubLevel> sable$subLevelReference;

    @Inject(remap = false, method = "tick", at = @At("HEAD"))
    public void sable$updateSubLevel(final CallbackInfo ci) {
        if (this.sable$subLevelReference == null) {
            this.sable$subLevelReference = new WeakReference<>(Sable.HELPER.getContaining(this.source.getAirCurrentWorld(), this.source.getAirCurrentPos()));
        }
    }

    @Redirect(remap = false, method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20191_()Lnet/minecraft/world/phys/AABB;"))
    public AABB sable$reverseProjectEntityBB(final Entity instance) {
        final SubLevel subLevel = this.sable$subLevelReference.get();
        if (subLevel != null) {
            return new BoundingBox3d(instance.getBoundingBox()).transformInverse(subLevel.logicalPose(), new BoundingBox3d()).toMojang();
        }

        return instance.getBoundingBox();
    }

    @WrapOperation(method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20256_(Lnet/minecraft/world/phys/Vec3;)V", remap = false))
    public void sable$transformFlowVector(final Entity instance, final Vec3 vec3, final Operation<Void> original, @Local final Vec3i flow, @Local(ordinal = 2) final float acceleration, @Local final Vec3 previousMotion, @Local(ordinal = 3) final float maxAcceleration) {
        final SubLevel subLevel = this.sable$subLevelReference.get();
        if (subLevel != null) {
            final Vector3d nonIntFlow = JOMLConversion.atLowerCornerOf(flow);
            subLevel.logicalPose().transformNormal(nonIntFlow);

            final double xIn = Mth.clamp((double) ((float) nonIntFlow.get(0) * acceleration) - previousMotion.x, -maxAcceleration, maxAcceleration);
            final double yIn = Mth.clamp((double) ((float) nonIntFlow.get(1) * acceleration) - previousMotion.y, -maxAcceleration, maxAcceleration);
            final double zIn = Mth.clamp((double) ((float) nonIntFlow.get(2) * acceleration) - previousMotion.z, -maxAcceleration, maxAcceleration);

            original.call(instance, previousMotion.add(new Vec3(xIn, yIn, zIn).scale(0.125f)));
            return;
        }

        original.call(instance, vec3);
    }

    @Redirect(remap = false, method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20182_()Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 sable$reverseProjectAllPositions(final Entity instance) {
        final SubLevel subLevel = this.sable$subLevelReference.get();
        if (subLevel != null) {
            return subLevel.logicalPose().transformPositionInverse(instance.position());
        }

        return instance.position();
    }

    // ── 子层级内的风扇加工类型识别（原 FanProcessingTypeMixin 的功能，1.20.1 移植改写）──
    //
    // 源版 NeoForge 1.21.1 是对接口 FanProcessingType 做 Mixin，用 @WrapMethod 直接
    // 包装它的 static 方法 getAt。这个写法在 Forge 1.20.1 的 mixin-0.8.5 下无解，
    // 因为库里有两条互相矛盾的校验（均已 javap 反编译核实）：
    //   1) MixinPreProcessorInterface.prepareMethod（PREPARE 阶段）：
    //      接口 Mixin 里的方法只要不是 public 且不是 synthetic，就抛
    //      InvalidInterfaceMixinException「Interface mixin contains a non-public method」。
    //      → 写 private static 会被这条拦下（整个 Mixin 静默失效，只打 ERROR 不崩）。
    //   2) MixinApplicatorStandard.checkMethodVisibility（APPLY 阶段）：
    //      方法是 static、不是 private、不是 synthetic、且没有 @Overwrite 注解，就抛
    //      InvalidMixinException「contains non-private static method」。
    //      → 改成 public static 会被这条拦下，而且这次是致命的，直接崩在
    //        FanProcessingTypeRegistry.init（Bootstrap 阶段），游戏根本进不去。
    // 也就是说 private / public 两条路都是死的，接口 Mixin 在 0.8.5 下承载不了
    // MixinExtras 的 static 包装方法。
    //
    // 改用等价方案：不包装 getAt 本身，改为在它的全部调用点上拦截。
    // 已用 javap 扫过整个 Create 6.0.8，调用 FanProcessingType.getAt 的只有本类
    // AirCurrent 的两处 —— rebuild() 与 findAffectedHandlers()，覆盖完整。
    // 本类是普通类 Mixin，处理方法可以是实例方法，不受上面两条 static 规则约束。
    // 语义与源版完全一致：在子层级中用换算后的内部坐标重新查一次加工类型。
    @WrapOperation(method = {"rebuild", "findAffectedHandlers"}, at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;getAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;", remap = false))
    public FanProcessingType sable$getFanProcessingTypeInSubLevels(final Level level, final BlockPos pos, final Operation<FanProcessingType> original) {
        final ActiveSableCompanion helper = Sable.HELPER;
        return helper.runIncludingSubLevels(level, pos.getCenter(), true, helper.getContaining(level, pos),
                (subLevel, internalPos) -> original.call(level, internalPos));
    }

}
