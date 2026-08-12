package foundry.veil.api.network.handler;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Backport shim of NeoForge 1.21.1 {@code foundry.veil.api.network.handler.PacketContext}.
 * Declares both the NeoForge 1.21 shorthand accessors ({@code player()}/{@code level()})
 * and the Forge 1.20.1 style ({@code getPlayer()}/{@code getLevel()}) so call sites
 * ported from either naming still compile. Concrete {@code ServerPacketContext} /
 * {@code ClientPacketContext} / {@code SablePacketContext} implement these.
 */
public interface PacketContext {

    Player getPlayer();

    Player player();

    Level getLevel();

    Level level();
}
