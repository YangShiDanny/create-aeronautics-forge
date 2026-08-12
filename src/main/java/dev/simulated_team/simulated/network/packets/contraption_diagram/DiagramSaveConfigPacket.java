package dev.simulated_team.simulated.network.packets.contraption_diagram;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.entities.diagram.DiagramConfig;
import dev.simulated_team.simulated.content.entities.diagram.DiagramEntity;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public record DiagramSaveConfigPacket(int entityID, DiagramConfig config) implements CustomPacketPayload {
    public static final Type<DiagramSaveConfigPacket> TYPE = new Type<>(Simulated.path("save_diagram"));

    public static final StreamCodec<FriendlyByteBuf, DiagramSaveConfigPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, DiagramSaveConfigPacket::entityID,
            DiagramConfig.STREAM_CODEC, DiagramSaveConfigPacket::config,
            DiagramSaveConfigPacket::new
    );

    public void handle(final ServerPacketContext context) {
        final Level level = context.level();

        final Entity entity = level.getEntity(this.entityID());

        if (entity instanceof final DiagramEntity diagram && entity.distanceToSqr(context.getPlayer()) < 64.0 * 64.0) {
            // [1.20.1 移植修复] 物理化后图解实体 getContaining 取 null；改用 getTrackingSubLevel 兜底判定，
            // 保证物理化（被追踪）图解也能保存配置（subLevel 仅作归属校验，setConfig 不依赖它）。
            final SubLevel subLevel = Sable.HELPER.getContaining(diagram);
            final SubLevel tracking = Sable.HELPER.getTrackingSubLevel(diagram);
            if (subLevel == null && tracking == null) return;

            diagram.setConfig(this.config);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
