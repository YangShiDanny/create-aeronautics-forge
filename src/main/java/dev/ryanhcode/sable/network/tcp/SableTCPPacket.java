package dev.ryanhcode.sable.network.tcp;

/**
 * Forge 1.20.1 network packet interface (replaces the NeoForge 1.21.1 packet-payload abstraction)
 * based packet contract. Messages are registered on {@link SableTCPPackets#INSTANCE}
 * and handled with a {@link SablePacketContext}.
 */
public interface SableTCPPacket {

    void handle(SablePacketContext context);
}
