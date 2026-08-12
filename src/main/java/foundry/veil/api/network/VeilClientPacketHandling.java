package foundry.veil.api.network;

import foundry.veil.api.network.handler.ClientPacketContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.BiConsumer;

/**
 * [1.20.1 专用服务端兼容] 客户端隔离处理器。
 *
 * <p>本类把所有 {@code net.minecraft.client.*} 引用（Minecraft、LocalPlayer）全部封闭在此，
 * 并标注 {@code @OnlyIn(Dist.CLIENT)}。{@link VeilPacketManager} 的 clientbound 处理只通过
 * {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VeilClientPacketHandling.handle(...))}
 * 的内层合成 lambda 以 invokestatic 引用本类。invokestatic 属延迟解析：
 * 专用服务端在校验 VeilPacketManager 时不会加载本类，运行期又因 dist 判定永不执行内层 lambda，
 * 故本类在服务端永不被加载，规避 RuntimeDistCleaner 的 invalid dist 崩溃。
 */
@OnlyIn(Dist.CLIENT)
public final class VeilClientPacketHandling {

    private VeilClientPacketHandling() {}

    public static <T> void handle(final BiConsumer<T, ClientPacketContext> handler, final T msg) {
        final LocalPlayer player = Minecraft.getInstance().player;
        final Level level = player != null ? player.level() : null;
        handler.accept(msg, new ClientPacketContext(player, level));
    }
}
