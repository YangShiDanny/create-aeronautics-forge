package dev.simulated_team.simulated.mixin.hold_interaction;

import com.llamalad7.mixinextras.sugar.Local;
import dev.simulated_team.simulated.events.SimulatedCommonClientEvents;
import dev.simulated_team.simulated.util.SimDistUtil;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    // [1.20.1 移植修正] 拉杆/方向盘拖拽的鼠标增量捕获，已从本类（turnPlayer 内 @Local 槽位猜测，
    // 1.20.1 混淆 jar 下抓错导致「拉杆拉不动」）迁移到
    // dev.simulated_team.simulated.mixin.hold_interaction.TurnInputMixin，
    // 改为直接注入 LocalPlayer.turn(DD) 的方法自身参数（yaw/pitch = 真实鼠标增量，无槽位歧义）。本方法已删除。

    @Inject(method = "onPress(JIII)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0),
            cancellable = true)
    private void simulated$preOnPress(final long windowPointer, final int button, final int action, final int modifiers, final CallbackInfo ci, @Local(ordinal = 1, argsOnly = true) final int i, @Local(argsOnly = true, ordinal = 0) final long l) {
        if (SimDistUtil.getClientPlayer() != null && !SimDistUtil.getClientPlayer().isSpectator()) {
            final InteractCallback.Result status = SimulatedCommonClientEvents.onBeforeMouseInput(InteractCallback.Input.mouse(button), modifiers, action);
            if (status.cancelled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "onScroll(JDD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0),
            cancellable = true)
    private void simulated$preOnScroll(final long l, final double d, final double e, final CallbackInfo ci) {
        if (SimDistUtil.getClientPlayer() != null && !SimDistUtil.getClientPlayer().isSpectator()) {
            final InteractCallback.Result status = SimulatedCommonClientEvents.onMouseScroll(d, e);
            if (status.cancelled()) {
                ci.cancel();
            }
        }
    }
}