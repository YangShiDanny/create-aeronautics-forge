package foundry.veil.api.network;

import foundry.veil.api.network.handler.ClientPacketContext;
import foundry.veil.api.network.handler.ServerPacketContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import dev.simulated_team.simulated.libs.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Backport shim of NeoForge 1.21.1 {@code foundry.veil.api.network.VeilPacketManager}.
 * The merged aeronautics build wires every packet through this single facade; internally it is a
 * real Forge {@link SimpleChannel}, so the sub-level physics network actually transports at runtime.
 *
 * <p>Forge 1.20.1 has no {@code CustomPacketPayload} registry type and no {@code IMessage}
 * constraint on {@link SimpleChannel}: its {@code send} takes a {@link PacketDistributor.PacketTarget}
 * (the result of {@code PacketDistributor.X.with(...)}) plus an arbitrary message object, while
 * {@code messageBuilder} requires the concrete message {@link Class}. We therefore keep the NeoForge
 * call sites (which already pass {@code XxxPacket.class}) and translate them onto the Forge API here.
 *
 * <p>The {@code CustomPacketPayload} and {@code StreamCodec} types are themselves backport shims
 * declared elsewhere (net.minecraft.network.*) so every packet record compiles and (de)serializes
 * over a {@link FriendlyByteBuf} at runtime via the {@code CODEC} fields.
 */
public final class VeilPacketManager {

    private final SimpleChannel channel;
    private int discriminator = 0;

    private VeilPacketManager(final SimpleChannel channel) {
        this.channel = channel;
    }

    public static VeilPacketManager create(final String modId, final String version) {
        final ResourceLocation name = new ResourceLocation(modId, "network/" + version);
        final SimpleChannel ch = NetworkRegistry.newSimpleChannel(
                name, () -> version, s -> true, s -> true);
        final VeilPacketManager mgr = new VeilPacketManager(ch);
        return mgr;
    }

    public <T extends CustomPacketPayload> void registerServerbound(
            final Class<T> clazz,
            final CustomPacketPayload.Type<T> type,
            final StreamCodec<? extends io.netty.buffer.ByteBuf, T> codec,
            final BiConsumer<T, ServerPacketContext> handler) {
        final int id = ++this.discriminator;
        @SuppressWarnings("unchecked")
        final StreamCodec<ByteBuf, T> c = (StreamCodec<ByteBuf, T>) (Object) codec;
        this.channel.messageBuilder(clazz, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> c.encode(buf, msg))
                .decoder(buf -> c.decode(buf))
                .consumerMainThread((msg, ctxSup) -> {
                    final NetworkEvent.Context ctx = ctxSup.get();
                    final ServerPlayer sender = ctx.getSender();
                    final Level level = sender != null ? sender.level() : null;
                    handler.accept(msg, new ServerPacketContext(sender, level));
                    ctx.setPacketHandled(true);
                })
                .add();
    }

    public <T extends CustomPacketPayload> void registerClientbound(
            final Class<T> clazz,
            final CustomPacketPayload.Type<T> type,
            final StreamCodec<? extends io.netty.buffer.ByteBuf, T> codec,
            final BiConsumer<T, ClientPacketContext> handler) {
        final int id = ++this.discriminator;
        @SuppressWarnings("unchecked")
        final StreamCodec<ByteBuf, T> c = (StreamCodec<ByteBuf, T>) (Object) codec;
        this.channel.messageBuilder(clazz, id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> c.encode(buf, msg))
                .decoder(buf -> c.decode(buf))
                .consumerMainThread((msg, ctxSup) -> {
                    final NetworkEvent.Context ctx = ctxSup.get();
                    net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                            net.minecraftforge.api.distmarker.Dist.CLIENT,
                            () -> () -> VeilClientPacketHandling.handle(handler, msg));
                    ctx.setPacketHandled(true);
                })
                .add();
    }

    public <T extends CustomPacketPayload> void send(final PacketDistributor.PacketTarget target, final T packet) {
        this.channel.send(target, packet);
    }

    public <T extends CustomPacketPayload> void sendToServer(final T packet) {
        this.channel.sendToServer(packet);
    }

    /** Backport shim of Veil's {@code PacketSink}. */
    public interface PacketSink {
        void sendPacket(CustomPacketPayload packet);
    }

    /** Instance method: create a packet sink that sends to a specific player. */
    public PacketSink player(final ServerPlayer player) {
        return packet -> this.channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Backport alias: NeoForge 1.21.1 call sites use {@code VeilPacketManager.getPlayer(player)}. */
    public PacketSink getPlayer(final ServerPlayer player) {
        return player(player);
    }

    /** Instance method: create a packet sink that sends to every player on the server. */
    public PacketSink all(final MinecraftServer server) {
        return packet -> this.channel.send(PacketDistributor.ALL.with(() -> null), packet);
    }

    /** Instance method: create a packet sink that sends from client to server. */
    public PacketSink server() {
        return packet -> this.channel.sendToServer(packet);
    }

    /**
     * Backport shim of Veil's {@code VeilPacketManager.tracking(entity)}: send to every client
     * currently tracking the given entity.
     */
    public PacketSink tracking(final net.minecraft.world.entity.Entity entity) {
        return packet -> this.channel.send(
                net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }

    /**
     * Backport shim of Veil's {@code VeilPacketManager.tracking(blockEntity)}: send to every client
     * tracking the chunk that contains the given block entity.
     */
    public PacketSink tracking(final net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        final net.minecraft.server.level.ServerLevel level =
                (net.minecraft.server.level.ServerLevel) blockEntity.getLevel();
        return packet -> this.channel.send(
                net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(
                        () -> level.getChunkAt(blockEntity.getBlockPos())), packet);
    }

    /**
     * Backport shim of Veil's {@code VeilPacketManager.tracking(level, pos)}: send to every client
     * tracking the chunk at the given position.
     */
    public PacketSink tracking(final net.minecraft.server.level.ServerLevel level,
                                      final net.minecraft.core.BlockPos pos) {
        return packet -> this.channel.send(
                net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(
                        () -> level.getChunkAt(pos)), packet);
    }
}
