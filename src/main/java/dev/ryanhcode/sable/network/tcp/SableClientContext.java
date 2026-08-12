package dev.ryanhcode.sable.network.tcp;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * [1.20.1 专用服务端兼容] 客户端隔离类。
 *
 * <p>原 {@code SablePacketContext.level()/clientLevel()} 与 {@code SableDistUtil.getClientLevel()}
 * 直接写 {@code Minecraft.getInstance().level}，该方法返回 {@code ClientLevel} 并赋给 {@code Level}
 * 变量（跨类型赋值）。当这些公共类在服务端被 JVM 校验器加载校验时，会强制解析 {@code ClientLevel}
 * 类，触发 RuntimeDistCleaner 的 invalid dist 崩溃（日志表现为处理 sable 包时
 * Attempted to load class ClientLevel）。
 *
 * <p>本类把所有 {@code net.minecraft.client.Minecraft} 引用封闭在自己内部，并标注
 * {@code @OnlyIn(Dist.CLIENT)}。调用方（SablePacketContext / SableDistUtil）只通过
 * invokestatic 延迟引用 {@code SableClientContext.getClientLevel()}：
 * 延迟解析在运行期才发生，而服务端走 sender 分支永不执行到该调用，故 ClientLevel 永不被加载。
 */
@OnlyIn(Dist.CLIENT)
public final class SableClientContext {

    private SableClientContext() {}

    public static Level getClientLevel() {
        return Minecraft.getInstance().level;
    }

    public static Player getClientPlayer() {
        return Minecraft.getInstance().player;
    }
}
