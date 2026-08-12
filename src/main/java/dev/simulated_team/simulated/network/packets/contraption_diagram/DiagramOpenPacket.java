package dev.simulated_team.simulated.network.packets.contraption_diagram;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.entities.diagram.DiagramConfig;
import dev.simulated_team.simulated.content.entities.diagram.DiagramEntity;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import foundry.veil.api.network.handler.ClientPacketContext;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public record DiagramOpenPacket(int entityID, DiagramConfig config) implements CustomPacketPayload {
    public static final Type<DiagramOpenPacket> TYPE = new Type<>(Simulated.path("open_diagram"));

    public static final StreamCodec<FriendlyByteBuf, DiagramOpenPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, DiagramOpenPacket::entityID,
            DiagramConfig.STREAM_CODEC, DiagramOpenPacket::config,
            DiagramOpenPacket::new
    );

    public void handle(final ClientPacketContext context) {
        final Level level = context.level();
        assert level != null;

        final Entity entity = level.getEntity(this.entityID());

        if (entity instanceof final DiagramEntity diagram) {
            // [1.20.1 移植修复] 物理化后图解实体坐标进入 plot 空间，客户端 getContaining 可能取 null；
            // 回退 getTrackingSubLevel（与渲染/交互判定一致），保证被追踪的物理化图解仍能打开界面。
            SubLevel subLevel = Sable.HELPER.getContaining(diagram);
            if (subLevel == null) subLevel = Sable.HELPER.getTrackingSubLevel(diagram);
            if (subLevel == null) return;

            DiagramScreen.open(diagram, this.config, subLevel);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
