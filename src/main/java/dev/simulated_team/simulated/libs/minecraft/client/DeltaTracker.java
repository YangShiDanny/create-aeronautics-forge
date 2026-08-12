package dev.simulated_team.simulated.libs.minecraft.client;

import net.minecraft.client.Minecraft;

/**
 * Backport shim of NeoForge 1.21.1's {@code DeltaTracker}. Forge 1.20.1 has no
 * such class; the delta-time helpers used by the (now no-op) Veil render hooks are
 * approximated via {@link Minecraft#getFrameTime()} so the call sites compile and run.
 * Actual frame-time semantics are irrelevant here because the Veil rendering pipeline is
 * deferred to phase 2 — these values only feed dropped visual effects.
 */
public class DeltaTracker {
    public float getFrameTime() {
        return Minecraft.getInstance().getDeltaFrameTime();
    }

    public float getGameTimeDeltaTicks() {
        return Minecraft.getInstance().getDeltaFrameTime();
    }

    public float getGameTimeDeltaPartialTick(final boolean bl) {
        return Minecraft.getInstance().getDeltaFrameTime();
    }

    public float getRealtimeDeltaTicks() {
        return Minecraft.getInstance().getDeltaFrameTime();
    }

    public float partialTick() {
        return Minecraft.getInstance().getDeltaFrameTime();
    }

    public float getRenderTime() {
        return Minecraft.getInstance().getDeltaFrameTime();
    }
}
