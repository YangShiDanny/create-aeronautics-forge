package dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom;

import net.minecraft.resources.ResourceLocation;

/**
 * Backport shim of the vanilla 1.21 {@code dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload}.
 * Forge 1.20.1 has no such type; the merged aeronautics build uses this interface so the
 * packet records (which {@code implements CustomPacketPayload}) compile. {@link VeilPacketManager}
 * (foundry.veil.api.network) registers these through a real Forge {@code SimpleChannel} at runtime.
 */
public interface CustomPacketPayload {

    Type<? extends CustomPacketPayload> type();

    /**
     * Packet type key. In vanilla 1.21 this is {@code record Type<T>(ResourceLocation id)};
     * we mirror it exactly so {@code new Type<>(location)} and {@code Type<X>} usages compile.
     */
    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }
}
