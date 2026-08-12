package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ClientboundFloatingBlockMaterialPacket(ResourceLocation name, FloatingBlockMaterial material) implements SableTCPPacket {

    public static void encode(final FriendlyByteBuf buf, final ClientboundFloatingBlockMaterialPacket msg) {
        buf.writeResourceLocation(msg.name());
        FloatingBlockMaterial.encode(buf, msg.material());
    }

    public static ClientboundFloatingBlockMaterialPacket decode(final FriendlyByteBuf buf) {
        return new ClientboundFloatingBlockMaterialPacket(buf.readResourceLocation(), FloatingBlockMaterial.decode(buf));
    }

    @Override
    public void handle(final SablePacketContext context) {
        Minecraft.getInstance().execute(() -> {
            FloatingBlockMaterialDataHandler.addMaterial(this.name, this.material);
        });
    }
}
