package dev.simulated_team.simulated.util;
import dev.simulated_team.simulated.util.SimClientContext;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities for client behavior using common classes
 */
public class SimDistUtil {

    /**
     * @return the client player instance if it exists
     */
    
    public static Player getClientPlayer() {
        return SimClientContext.getClientPlayer();
    }

    public static float getPartialTick() {
        return Minecraft.getInstance().getFrameTime();
    }

    public static HitResult getHitResult() {
        return Minecraft.getInstance().hitResult;
    }
}
