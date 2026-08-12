package dev.ryanhcode.sable.mixin.compat.shouldersurfing;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixin.accessor.OptionsCameraTypeAccessor;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SubLevelCameraCycleHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ShoulderSurfing 的 InputHandler.tick() 用 options.keyTogglePerspective.consumeClick() 把 F5 键消费掉，
// 然后调自己的 ShoulderSurfing.togglePerspective()。而 togglePerspective 只在 SS 自己的 Perspective 枚举
// （FIRST_PERSON / THIRD_PERSON_BACK / THIRD_PERSON_FRONT / SHOULDER_SURFING）里循环，根本没有 SUB_LEVEL_VIEW，
// 导致原版 F5 的 setCameraType(getCameraType().cycle())（它才调用我们 CameraTypeMixin.cycle() 的 @Overwrite 产出 SUB_LEVEL_VIEW）
// 永远不被执行 —— 这就是「开着 SS 切不进子层级视角、禁用 SS 就正常」的根因。
//
// 修复：当玩家骑在子层级载具（物理化气球）上时，把这次 F5 改走原版 cycle（= setCameraType(getCameraType().cycle())），
// 从而走我们的 cycle() -> SUB_LEVEL_VIEW，让 F5 在气球上能完整循环进子层级视角。
//
// 关键细节：ShoulderSurfing 的 OptionsMixin.setCameraType 有介入条件「if (cameraType != 当前字段)」，
// 会把不认识的 SUB_LEVEL_VIEW 降级回 FIRST_PERSON。由于本 MixinGradle 版本 @Inject 没有 priority 元素，
// 无法用「抢优先级」让本模组先写字段。改用确定性方案：在调用 setCameraType(next) 之前，
// 若 next 是子层级视角，先用 OptionsCameraTypeAccessor 把 options.cameraType 字段预置成 next，
// 使 SS 的介入条件不成立、完全不介入，原版字段赋值得以保留 SUB_LEVEL_VIEW。
// 非气球场景下不拦截，SS 的越肩视角切换照常工作。
@Mixin(targets = "com.github.exopandora.shouldersurfing.client.ShoulderSurfing", remap = false)
public class ShoulderSurfingToggleCompatMixin {

    @Inject(method = "togglePerspective", at = @At("HEAD"), cancellable = true, remap = false)
    private void sable$togglePerspective(final CallbackInfo ci) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Entity cameraEntity = minecraft.cameraEntity;
        // 仅当相机实体确实关联着一个子层级（即骑在物理化气球上）时才接管 F5，
        // 把这次切换改走原版 cycle，由我们的 CameraTypeMixin.cycle() 产出 SUB_LEVEL_VIEW。
        if (cameraEntity != null && Sable.HELPER.getTrackingOrVehicleSubLevel(cameraEntity) != null) {
            ci.cancel();
            // 切换前校正（重置缩放、按子层级朝向校正视线）——此时 cameraType 仍是切换前的，
            // 行为与原版 MinecraftMixin 在 handleKeybinds 的 setCameraType 之前注入一致。
            SubLevelCameraCycleHelper.onPreCycle(minecraft);
            final CameraType next = minecraft.options.getCameraType().cycle();
            // 子层级视角（SUB_LEVEL_VIEW / UNLOCKED）是 ShoulderSurfing 不认识的 CameraType，
            // 其 OptionsMixin.setCameraType 会把它们降级回 FIRST_PERSON。
            // 因此在调用 setCameraType 之前，先把 options.cameraType 字段预置成 next，
            // 让 SS 的「cameraType != 当前字段」介入条件不成立、完全不介入，
            // 原版字段赋值保留子层级视角。此预置完全本地可控，不依赖任何 mixin 间优先级。
            if (next == SableCameraTypes.SUB_LEVEL_VIEW || next == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
                ((OptionsCameraTypeAccessor) minecraft.options).sable$setCameraTypeField(next);
            }
            minecraft.options.setCameraType(next);
            // 切换后：显示左下角聊天栏提示 + 子层级朝向校正——原版 handleKeybinds 的 setCameraType 之后注入，
            // 因 F5 被 SS 消费而 handleKeybinds 不执行，故在此补回（与禁用 SS 时互斥触发，不会重复提示）。
            SubLevelCameraCycleHelper.onPostCycle(minecraft);
        }
    }
}
