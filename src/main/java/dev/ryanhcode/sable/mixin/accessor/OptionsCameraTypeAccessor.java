package dev.ryanhcode.sable.mixin.accessor;

import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// 暴露 Options.cameraType 字段的写权限，供 ShoulderSurfing 兼容层在调用 setCameraType 之前
// 预置字段（让 SS 的 OptionsMixin 介入条件「cameraType != 当前字段」不成立，从而不降级子层级视角）。
// 用接口注入式 @Accessor：调用方 ((OptionsCameraTypeAccessor) options).sable$setCameraTypeField(value) 直接写字段。
@Mixin(Options.class)
public interface OptionsCameraTypeAccessor {
    @Accessor("cameraType")
    void sable$setCameraTypeField(CameraType value);
}
