package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.network.tcp.SablePacketContext;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinition;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public record ClientboundPhysicsPropertyPacket(PhysicsBlockPropertiesDefinition definition) implements SableTCPPacket {

    public static void encode(final FriendlyByteBuf buf, final ClientboundPhysicsPropertyPacket msg) {
        PhysicsBlockPropertiesDefinition.encode(buf, msg.definition());
    }

    public static ClientboundPhysicsPropertyPacket decode(final FriendlyByteBuf buf) {
        return new ClientboundPhysicsPropertyPacket(PhysicsBlockPropertiesDefinition.decode(buf));
    }

    @Override
    public void handle(final SablePacketContext context) {
        Minecraft.getInstance().execute(() -> {
            PhysicsBlockPropertiesDefinitionLoader.applyToBlocks(this.definition);
        });
    }
}
