package dev.ryanhcode.sable.mixin.respawn_point;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.mixinterface.respawn_point.ServerPlayerRespawnExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.network.tcp.SableTCPPackets;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements ServerPlayerRespawnExtension {

    @Shadow
    @Final
    public MinecraftServer server;
    @Shadow
    public ServerGamePacketListenerImpl connection;
    @Shadow
    private  BlockPos respawnPosition;
    @Shadow
    private ResourceKey<Level> respawnDimension;
    @Shadow
    private float respawnAngle;
    @Shadow
    private boolean respawnForced;
    @Unique
    
    private UUID sable$respawnPoint = null;
    @Unique
    private Pair<UUID, Vector3d> sable$queuedFreeze = null;

    // 1.20.1: findRespawnAndUseSpawnBlock (the NeoForge 1.21 private helper this mixin
    // shadowed/redirected) does NOT exist. In 1.20.1 the respawn position is computed by
    // PlayerRespawnLogic.getOverworldRespawnPos (m_183928_), which is static and has no
    // access to the ServerPlayer instance (this), so the sub-level respawn override has no
    // drop-in equivalent here. DISABLED for 1.20.1: sable$respawnPoint is still registered
    // (setRespawnPosition) and saved/loaded, but not consumed on respawn yet.
    @Shadow
    public abstract ServerLevel serverLevel();

    @Shadow
    public abstract void sendSystemMessage(Component component);

    @Override
    public  UUID sable$getRespawnPoint() {
        return this.sable$respawnPoint;
    }

    @Inject(method = "setRespawnPosition", at = @At("HEAD"), cancellable = true)
    private void sable$setRespawnPosition(final ResourceKey<Level> resourceKey,  final BlockPos blockPos, final float f, final boolean bl, final boolean sendMessage, final CallbackInfo ci) {
        final ServerLevel level = this.serverLevel();
        final SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(level);

        if (this.sable$respawnPoint != null) {
            data.removeTrackingPoint(this.sable$respawnPoint);
            this.sable$respawnPoint = null;
        }

        if (blockPos != null) {
            final SubLevel trackingSubLevel = Sable.HELPER.getContaining(level, blockPos);

            if (trackingSubLevel instanceof final ServerSubLevel serverSubLevel) {
                this.sable$respawnPoint = data.generateTrackingPoint(Vec3.atCenterOf(blockPos), serverSubLevel);

                if (this.sable$respawnPoint != null) {
                    final boolean theSame = blockPos.equals(this.respawnPosition) && resourceKey.equals(this.respawnDimension);
                    if (sendMessage && !theSame) {
                        this.sendSystemMessage(Component.translatable("block.minecraft.set_spawn"));
                    }

                    this.respawnPosition = blockPos;
                    this.respawnDimension = resourceKey;
                    this.respawnAngle = f;
                    this.respawnForced = bl;
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void sable$addRespawnPoint(final CompoundTag compoundTag, final CallbackInfo ci) {
        if (this.sable$respawnPoint != null) {
            compoundTag.putUUID("RespawnPoint", this.sable$respawnPoint);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void sable$readRespawnPoint(final CompoundTag compoundTag, final CallbackInfo ci) {
        if (compoundTag.hasUUID("RespawnPoint")) {
            this.sable$respawnPoint = compoundTag.getUUID("RespawnPoint");
        }
    }

    /**
     * @author RyanH
     * @reason Respawning on sub-levels
     */
    @Unique
    public void copyRespawnPosition(final ServerPlayer serverPlayer) {
        if (serverPlayer.getRespawnPosition() != null) {
            this.sable$respawnPoint = ((ServerPlayerRespawnExtension) serverPlayer).sable$getRespawnPoint();
            this.respawnPosition = serverPlayer.getRespawnPosition();
            this.respawnDimension = serverPlayer.getRespawnDimension();
            this.respawnAngle = serverPlayer.getRespawnAngle();
            this.respawnForced = serverPlayer.isRespawnForced();
        } else {
            this.sable$respawnPoint = null;
            this.respawnPosition = null;
            this.respawnDimension = Level.OVERWORLD;
            this.respawnAngle = 0.0F;
            this.respawnForced = false;
        }
    }

    @Override
    public void sable$takeQueuedFreezeFrom(final ServerPlayer oldPlayer) {
        final ServerPlayerRespawnExtension extension = (ServerPlayerRespawnExtension) oldPlayer;
        final Pair<UUID, Vector3d> queuedFreeze = extension.sable$getQueuedFreeze();

        if (queuedFreeze != null) {
            ((PlayerFreezeExtension) this).sable$freezeTo(queuedFreeze.first(), queuedFreeze.second());
            SableTCPPackets.sendToPlayer((ServerPlayer) (Object) this, new ClientboundFreezePlayerPacket(queuedFreeze.first(), queuedFreeze.second()));
        }
    }

    @Override
    public  Pair<UUID, Vector3d> sable$getQueuedFreeze() {
        return this.sable$queuedFreeze;
    }

    // 1.20.1: sub-level respawn override DISABLED (see note on findRespawnAndUseSpawnBlock above).
    // To re-enable, hook PlayerRespawnLogic.getOverworldRespawnPos (m_183928_) via a
    // ServerPlayer-instance @Redirect that reads this.sable$respawnPoint — a real rework,
    // not a drop-in, because the 1.21 hook target no longer exists in 1.20.1.
}
