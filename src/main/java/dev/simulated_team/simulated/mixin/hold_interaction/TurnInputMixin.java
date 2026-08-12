package dev.simulated_team.simulated.mixin.hold_interaction;

import dev.simulated_team.simulated.events.SimulatedCommonClientEvents;
import dev.simulated_team.simulated.util.SimDistUtil;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public class TurnInputMixin {

    // [1.20.1 移植修正·第三版] 拉杆/方向盘等「按住右键拖拽」依赖鼠标增量驱动。
    // 历史教训：
    //   ① @Local(ordinal=...) 在 1.20.1 下 double 局部次序与 1.21 不同，ordinal 越界 -> 启动即崩（12:10）；
    //   ② @Local(index=...) 语义 ≠ 字节码槽位，抓到错值 -> 拉杆拉不动（12:23）；
    //   ③ @Mixin(LocalPlayer) 注入 turn(DD)V -> LocalPlayer 自身不声明该方法（只继承自 Entity），
    //     "could not find any targets" -> 启动即崩（12:38）。javap 实锤：m_19884_ 声明在 Entity。
    // 最终打法：turnPlayer 字节码里那条调用指令是 invokevirtual LocalPlayer.m_19884_(DD)V（属主就是 LocalPlayer），
    // 用 @Redirect 包住这条【调用指令】——yaw/pitch 就是 turnPlayer 传给 turn 的真实鼠标增量，
    // 由重定向方法按参数位置直接接收，零槽位猜测；目标匹配的是调用点属主，与方法声明在哪个类无关。
    // 若交互处理器（如油门拉杆，只取 pitch = 前后位移）消费了这次位移，则不调用 turn（相机冻结）；
    // 否则原样放行 player.turn(yaw, pitch)。
    @Redirect(method = "turnPlayer()V",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void simulated$onTurn(final LocalPlayer player, final double yaw, final double pitch) {
        if (SimDistUtil.getClientPlayer() != null && !SimDistUtil.getClientPlayer().isSpectator()) {
            final InteractCallback.Result status = SimulatedCommonClientEvents.onMouseMove(yaw, pitch);
            if (status.cancelled()) {
                return; // 位移被拉杆等交互消费：取消本次转身（相机不转）
            }
        }
        player.turn(yaw, pitch);
    }
}
