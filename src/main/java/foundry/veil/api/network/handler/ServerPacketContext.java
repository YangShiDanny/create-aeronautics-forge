package foundry.veil.api.network.handler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Backport shim of NeoForge 1.21.1 {@code foundry.veil.api.network.handler.ServerPacketContext}.
 * A real, functional wrapper around the Forge {@code NetworkEvent.Context} so handlers
 * ({@code XxxPacket::handle}) keep their NeoForge signature.
 */
public class ServerPacketContext implements PacketContext {

    private final ServerPlayer player;
    private final Level level;

    public ServerPacketContext(final ServerPlayer player, final Level level) {
        this.player = player;
        this.level = level;
    }

    @Override
    public ServerPlayer player() {
        return this.player;
    }

    @Override
    public Level level() {
        return this.level;
    }

    @Override
    public ServerPlayer getPlayer() {
        return this.player;
    }

    @Override
    public Level getLevel() {
        return this.level;
    }
}
