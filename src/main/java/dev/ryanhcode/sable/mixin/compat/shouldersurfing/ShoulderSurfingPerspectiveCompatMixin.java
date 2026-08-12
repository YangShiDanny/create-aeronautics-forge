package dev.ryanhcode.sable.mixin.compat.shouldersurfing;

import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 兼容「越肩视角重制」ShoulderSurfing（Forge 1.20.1 5.x）：
// ShoulderSurfing 在自己的 OptionsMixin.setCameraType 里拦截所有视角切换并 ci.cancel()，
// 再用 Perspective.of(cameraType, isShoulderSurfing) 决定视角。而 Perspective.of 的 switch 表达式
// 只有 FIRST_PERSON / THIRD_PERSON_BACK / THIRD_PERSON_FRONT 三个分支，没有我们新增的
// SUB_LEVEL_VIEW / SUB_LEVEL_VIEW_UNLOCKED，传入会抛 MatchException，并且 ShoulderSurfing 会把
// 子层级视角整个吞掉（相机被接管成第三人称背面，getMaxZoom 拉远逻辑永不触发）。
// 这里在 Perspective.of 的 HEAD 注入：当 cameraType 是我们的子层级视角时，直接返回 FIRST_PERSON。
// 关键：ShoulderSurfing 只有在 Perspective 为 SHOULDER_SURFING 时才会重定向 Camera.move 接管相机位置；
// 而 Perspective.of(THIRD_PERSON_BACK, true) 恰好等于 SHOULDER_SURFING，若映射成 THIRD_PERSON_BACK，
// 在 SS 激活时会触发 SS 接管相机、把我们的 getMaxZoom 拉远覆盖掉（表现就是「只像第三人称背面」）。
// FIRST_PERSON 分支不依赖 isShoulderSurfing 参数，永远安全，且绝不会变成 SHOULDER_SURFING，
// 这样 SS 不接管相机，原版第三人称走 + 我们的拉远逻辑正常生效。CameraType 字段仍由
// SubLevelCameraTypeCompatMixin 设回 SUB_LEVEL_VIEW，只用于驱动 sable 的拉远与渲染判定。
// ShoulderSurfing 不存在时，@Mixin(targets=...) 指向的类缺失，Mixin 因 targets 本身就是「可选目标」
// 而自动跳过该 Mixin，无任何副作用（本 Mixin 版本不支持 require 元素，故不写）。
@Mixin(targets = "com.github.exopandora.shouldersurfing.api.client.Perspective", remap = false)
public class ShoulderSurfingPerspectiveCompatMixin {

    // ShoulderSurfing 用官方映射编译，运行时 Perspective.of 描述符为 of(net.minecraft.client.CameraType, boolean)
    @Inject(
        method = "of(Lnet/minecraft/client/CameraType;Z)Lcom/github/exopandora/shouldersurfing/api/client/Perspective;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void sable$of(final CameraType cameraType, final boolean shoulderSurfing, final CallbackInfoReturnable cir) {
        if (cameraType == SableCameraTypes.SUB_LEVEL_VIEW || cameraType == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            try {
                final Class<?> perspectiveClass = Class.forName("com.github.exopandora.shouldersurfing.api.client.Perspective");
                cir.setReturnValue(Enum.valueOf((Class) perspectiveClass, "FIRST_PERSON"));
                cir.cancel();
            } catch (final Throwable ignored) {
                // ShoulderSurfing 不存在或版本不兼容：交给原逻辑（此时本 Mixin 本不应加载）
            }
        }
    }
}
