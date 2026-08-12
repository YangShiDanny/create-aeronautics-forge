package dev.eriksonn.aeronautics.mixin.levitite;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import dev.eriksonn.aeronautics.service.AeroLevititeService;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WaterWheelBlockEntity.class)
public class WaterWheelBlockEntityMixin {

    @ModifyReturnValue(remap = false, method = "getFlowVectorAtPosition", at = @At("RETURN"))
    private Vec3 aeronautics$getFlowVectorAtPosition(Vec3 original, @Local(name = "fluid") FluidState fluidstate) {
        // [1.20.1 移植·防御] 桶条目在 1.20.1 下可能未进注册表，getBucket() 会返回 null，
        // 直接 .equals(null) 会 NPE 崩游戏（此 mixin 每 tick 执行）。先判空再比。
        final net.minecraft.world.item.Item levititeBucket = AeroLevititeService.INSTANCE.getBucket();
        if (levititeBucket != null && fluidstate.getType().getBucket().equals(levititeBucket)) {
            original = original.reverse();
        }
        return original;
    }
}
