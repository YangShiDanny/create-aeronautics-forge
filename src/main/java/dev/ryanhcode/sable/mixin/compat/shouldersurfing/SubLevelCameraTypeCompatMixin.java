package dev.ryanhcode.sable.mixin.compat.shouldersurfing;

import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 兼容「越肩视角重制」ShoulderSurfing。
// 真正的修复在 ShoulderSurfingToggleCompatMixin：当玩家骑在子层级载具（物理化气球）上按 F5 时，
// 先把 options.cameraType 字段预置成目标视角（SUB_LEVEL_VIEW / UNLOCKED），再调 setCameraType(next)，
// 使 SS 的 OptionsMixin「if (cameraType != 当前字段)」介入条件不成立、完全不介入，
// 原版字段赋值保留子层级视角。此预置完全本地可控，不依赖 mixin 间优先级
// （本 MixinGradle 版本的 @Inject 没有 priority 元素，@Mixin(priority) 跨 mixin 排序也不可靠）。
//
// 本 Mixin 仅作为双保险：若 setCameraType(SUB) 真的被调到，则抢先把字段写回 SUB 并 cancel，
// 阻止任何漏网的 SS 降级。非子层级视角完全不干预，SS 照常工作。
@Mixin(value = Options.class)
public abstract class SubLevelCameraTypeCompatMixin {
    @Shadow
    private CameraType cameraType;

    @Inject(method = "setCameraType", at = @At("HEAD"), cancellable = true)
    private void sable$setCameraType(final CameraType cameraType, final CallbackInfo ci) {
        if (cameraType == SableCameraTypes.SUB_LEVEL_VIEW || cameraType == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            this.cameraType = cameraType;
            ci.cancel();
        }
    }
}
