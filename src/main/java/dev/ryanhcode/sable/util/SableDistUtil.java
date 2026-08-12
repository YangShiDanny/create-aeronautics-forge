package dev.ryanhcode.sable.util;
import dev.ryanhcode.sable.network.tcp.SableClientContext;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

public class SableDistUtil {

    /**
     * @return the client level
     */
    public static Level getClientLevel() {
        return SableClientContext.getClientLevel();
    }

    /**
     * @return if level inherits from {@link ClientLevel}
     */
    public static boolean isClientLevel(final Level level) {
        return level instanceof ClientLevel;
    }
}
