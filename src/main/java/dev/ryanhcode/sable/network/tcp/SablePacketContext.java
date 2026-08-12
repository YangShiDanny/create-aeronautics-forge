package dev.ryanhcode.sable.network.tcp;
import dev.ryanhcode.sable.network.tcp.SableClientContext;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import foundry.veil.api.network.handler.PacketContext;

/**
 * Forge 1.20.1 replacement for Veil's {@code PacketContext}.
 * Wraps the Forge {@link NetworkEvent.Context} and exposes the level / player /
 * server the packet was handled on.
 */
public class SablePacketContext implements PacketContext {

    private final NetworkEvent.Context ctx;

    public SablePacketContext(final NetworkEvent.Context ctx) {
        this.ctx = ctx;
    }

    /**
     * The level the packet is being handled for. On the server this is the sender's
     * level; on the client it is the integrated client level.
     */
    public Level level() {
        final ServerPlayer sender = this.ctx.getSender();
        if (sender != null) {
            return sender.level();
        }
        return SableClientContext.getClientLevel();
    }

    /**
     * The player that sent the packet (server-bound handling only).
     */
    public Player player() {
        return this.ctx.getSender();
    }

    @Override
    public Player getPlayer() {
        return this.ctx.getSender();
    }

    @Override
    public Level getLevel() {
        return this.level();
    }

    /**
     * The server the packet arrived on (server-bound handling only).
     */
    public MinecraftServer server() {
        final ServerPlayer sender = this.ctx.getSender();
        return sender != null ? sender.server : null;
    }

    /**
     * The client level, used by client-bound packets that need to defer work to the
     * render / main thread via {@code Minecraft.getInstance().execute(...)}.
     */
    public Level clientLevel() {
        return SableClientContext.getClientLevel();
    }
}
