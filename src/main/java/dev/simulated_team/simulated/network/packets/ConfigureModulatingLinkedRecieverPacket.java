package dev.simulated_team.simulated.network.packets;
import dev.simulated_team.simulated.backport.BackportCodecs;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.redstone.modulating_receiver.ModulatingLinkedReceiverBlockEntity;
import dev.simulated_team.simulated.network.packets.helpers.SimBlockEntityConfigurationPacket;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

public class ConfigureModulatingLinkedRecieverPacket extends SimBlockEntityConfigurationPacket<ModulatingLinkedReceiverBlockEntity> {
    public static final Type<ConfigureModulatingLinkedRecieverPacket> TYPE = new Type<>(Simulated.path("configure_modulating_linked_reciever"));
    public static final StreamCodec<ByteBuf, ConfigureModulatingLinkedRecieverPacket> CODEC = StreamCodec.composite(
            BackportCodecs.BLOCK_POS, SimBlockEntityConfigurationPacket::getPos,
            ByteBufCodecs.INT, ConfigureModulatingLinkedRecieverPacket::getMinRange,
            ByteBufCodecs.INT, ConfigureModulatingLinkedRecieverPacket::getMaxRange,
            ConfigureModulatingLinkedRecieverPacket::new);

    private final int minRange;
    private final int maxRange;

    public ConfigureModulatingLinkedRecieverPacket(final BlockPos pos, final int minRange, final int maxRange) {
        super(pos);
        this.minRange = minRange;
        this.maxRange = maxRange;
    }

    public int getMinRange() {
        return this.minRange;
    }

    public int getMaxRange() {
        return this.maxRange;
    }

    @Override
    public  Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    protected void applySettings(final ServerPlayer serverPlayer, final ModulatingLinkedReceiverBlockEntity be) {
        be.minRange = this.minRange;
        be.maxRange = this.maxRange;

        be.notifyUpdate();
    }
}
