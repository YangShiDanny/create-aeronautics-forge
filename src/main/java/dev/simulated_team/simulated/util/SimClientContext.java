package dev.simulated_team.simulated.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * [1.20.1 专用服务端兼容] 客户端隔离类，与 sable 的 SableClientContext 对称。
 *
 * <p>{@code SimDistUtil.getClientPlayer()} 原本直接返回 {@code Minecraft.getInstance().player}
 * （LocalPlayer 赋给 Player 变量，跨类型赋值）。当 {@code SimDistUtil} 这类公共工具类在
 * 服务端被 JVM 校验器加载校验时，会强制解析 LocalPlayer 类而崩溃。
 *
 * <p>本类把 {@code net.minecraft.client.Minecraft} 引用封闭在自己内部，标注 {@code @OnlyIn(Dist.CLIENT)}。
 * {@code SimDistUtil} 只通过 invokestatic 延迟引用 {@code SimClientContext.getClientPlayer()}，
 * 服务端加载 SimDistUtil 时不解析 LocalPlayer，运行期也只在客户端执行到该调用。
 */
@OnlyIn(Dist.CLIENT)
public final class SimClientContext {

    private SimClientContext() {}

    public static Player getClientPlayer() {
        return Minecraft.getInstance().player;
    }
}
