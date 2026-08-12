package dev.simulated_team.simulated.network.packets.physics_staff;
import dev.simulated_team.simulated.backport.BackportCodecs;

import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffAction;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import dev.simulated_team.simulated.index.SimItems;
import dev.simulated_team.simulated.util.SimCodecUtil;
import foundry.veil.api.network.VeilPacketManager;
import foundry.veil.api.network.handler.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;
import dev.simulated_team.simulated.network.SimPacketManager;

public class PhysicsStaffActionPacket implements CustomPacketPayload {

    public static CustomPacketPayload.Type<PhysicsStaffActionPacket> TYPE = new CustomPacketPayload.Type<>(Simulated.path("physics_staff_action"));

    public static final StreamCodec<FriendlyByteBuf, PhysicsStaffActionPacket> CODEC = StreamCodec.composite(
            PhysicsStaffAction.STREAM_CODEC, packet -> packet.action,
            BackportCodecs.UUID, packet -> packet.subLevel,
            SimCodecUtil.STREAM_VECTOR3D, packet -> packet.location,
            PhysicsStaffActionPacket::new
    );

    protected final PhysicsStaffAction action;
    protected final UUID subLevel;
    protected final Vector3d location;

    public PhysicsStaffActionPacket(final PhysicsStaffAction action, final UUID subLevel, final Vector3dc location) {
        this.action = action;
        this.subLevel = subLevel;
        this.location = new Vector3d(location);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(final PacketContext context) {
        final ServerLevel level = (ServerLevel) context.level();
        final Player player = context.getPlayer();

        if (!player.getMainHandItem().is(SimItems.PHYSICS_STAFF.get()) &&
                !player.getOffhandItem().is(SimItems.PHYSICS_STAFF.get())) {
            ((ServerPlayer) context.getPlayer()).connection.disconnect(Component.literal("Invalid packet"));
            return;
        }

        if (this.action == PhysicsStaffAction.LOCK) {
            PhysicsStaffServerHandler.get(level).toggleLock(this.subLevel);
        }

        if (this.action == PhysicsStaffAction.STOP_DRAG) {
            PhysicsStaffServerHandler.get(level).stopDragging(player.getUUID());
        }

        if (this.action == PhysicsStaffAction.LOCK) {
            final Vector3d beamStart = JOMLConversion.toJOML(player.getEyePosition());
            final Vector3d beamEnd = new Vector3d(this.location);

            final ChunkPos chunk = new ChunkPos(BlockPos.containing(this.location.x(), this.location.y(), this.location.z()));
            final PhysicsStaffBeamPacket beamPacket = new PhysicsStaffBeamPacket(player.getUUID(), beamStart, beamEnd);

            for (final ServerPlayer otherPlayer : level.getChunkSource().chunkMap.getPlayers(chunk, false)) {
                if (otherPlayer == player) {
                    continue;
                }
                dev.simulated_team.simulated.network.SimPacketManager.INSTANCE.getPlayer(otherPlayer).sendPacket(beamPacket);
            }
        }
    }
}
