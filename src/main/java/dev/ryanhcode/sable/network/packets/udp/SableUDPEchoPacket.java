package dev.ryanhcode.sable.network.packets.udp;

import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public record SableUDPEchoPacket(String text) implements SableUDPPacket {

    public SableUDPEchoPacket(final FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void write(final FriendlyByteBuf buf) {
        buf.writeUtf(this.text);
    }

    @Override
    public SableUDPPacketType getType() {
        return SableUDPPacketType.PING;
    }

    @Override
    public void handleClient(final Level level) {
        Minecraft.getInstance().player.sendSystemMessage(Component.literal("Received UDP Test Ping: " + this.text));
    }
}
