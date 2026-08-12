package foundry.veil.api.network.handler;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Backport shim of NeoForge 1.21.1 {@code foundry.veil.api.network.handler.ClientPacketContext}.
 *
 * <p>[1.20.1 专用服务端兼容] 原本持有 {@code net.minecraft.client.player.LocalPlayer}。
 * 由于所有 clientbound 包的 {@code handle(ClientPacketContext)} 方法在服务端注册时
 * 会随包类一起被 JVM 校验器加载，若本类字段/返回类型是 LocalPlayer（仅客户端类），
 * 服务端校验期即会尝试加载 LocalPlayer 而崩溃（RuntimeDistCleaner: invalid dist DEDICATED_SERVER）。
 * 因此改为持有公共类 {@code Player}，服务端可安全加载；客户端仍传入真实的 LocalPlayer 实例。
 */
public class ClientPacketContext implements PacketContext {

    private final Player player;
    private final Level level;

    public ClientPacketContext(final Player player, final Level level) {
        this.player = player;
        this.level = level;
    }

    @Override
    public Player player() {
        return this.player;
    }

    @Override
    public Level level() {
        return this.level;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public Level getLevel() {
        return this.level;
    }
}
