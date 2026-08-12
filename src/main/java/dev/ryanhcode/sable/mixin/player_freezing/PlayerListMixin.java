package dev.ryanhcode.sable.mixin.player_freezing;

import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.mixinterface.respawn_point.ServerPlayerRespawnExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.network.tcp.SableTCPPackets;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(PlayerList.class)
public class PlayerListMixin {

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void sable$player(final Connection connection, final ServerPlayer serverPlayer, final CallbackInfo ci) {
        if (serverPlayer instanceof final PlayerFreezeExtension extension) {
            final UUID uuid = extension.sable$getFrozenToSubLevel();

            if (uuid != null) {
                SableTCPPackets.sendToPlayer(serverPlayer, new ClientboundFreezePlayerPacket(uuid, extension.sable$getFrozenToSubLevelAnchor()));
            }
        }
    }

    @Inject(method = "respawn", at = @At("TAIL"))
    private void sable$respawn(final ServerPlayer oldPlayer, final boolean bl, final CallbackInfoReturnable<ServerPlayer> cir) {
        final ServerPlayer newPlayer = cir.getReturnValue();
        ((ServerPlayerRespawnExtension) newPlayer).sable$takeQueuedFreezeFrom(oldPlayer);
    }
}
