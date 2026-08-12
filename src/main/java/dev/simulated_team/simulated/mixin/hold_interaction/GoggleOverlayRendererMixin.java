package dev.simulated_team.simulated.mixin.hold_interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;
import dev.simulated_team.simulated.index.SimClickInteractions;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GoggleOverlayRenderer.class)
public class GoggleOverlayRendererMixin {
    @Shadow public static int hoverTicks;

    @Inject(remap = false, method = "renderOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;m_14036_(FFF)F", shift = At.Shift.BEFORE))
    private static void decrementRenderTicks(final CallbackInfo ci) {
        if (SimClickInteractions.STEERING_WHEEL_MANAGER.isActive()) {
            hoverTicks = Mth.clamp(hoverTicks - 2, 0, 24);
        }
    }

    @WrapOperation(method = "renderOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;m_14036_(FFF)F", remap = false), remap = false)
    private static float fixPartialTicks(final float value, final float min, final float max, final Operation<Float> original, @Local(argsOnly = true) final float partialTicks) {
        if (SimClickInteractions.STEERING_WHEEL_MANAGER.isActive()) {
            return Mth.clamp(hoverTicks - partialTicks, 0, 24) / 24;
        }
        return original.call(value, min, max);
    }

    @Inject(remap = false, method = "renderOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;m_14036_(FFF)F"), cancellable = true)
    private static void dontRenderTheText(final CallbackInfo ci, @Local(argsOnly = true) final float partialTicks) {
        if (hoverTicks - partialTicks <= 0) {
            ci.cancel();
        }
    }
}