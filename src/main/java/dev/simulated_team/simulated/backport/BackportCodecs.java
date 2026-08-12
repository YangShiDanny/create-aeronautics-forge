package dev.simulated_team.simulated.backport;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.UUID;

/**
 * Backport shims for the vanilla 1.21 {@code X.STREAM_CODEC} static fields that do not exist on
 * Forge 1.20.1. On 1.20.1 the corresponding (de)serialization lives in
 * {@link FriendlyByteBuf} helpers ({@code writeBlockPos/readBlockPos}, {@code writeEnum/readEnum},
 * {@code writeResourceLocation/readResourceLocation}, {@code writeUUID/readUUID}), so each codec is
 * built on top of those. The packet {@code CODEC} fields and {@code StreamCodec.composite} calls
 * reference these instead of the missing vanilla fields.
 */
public final class BackportCodecs {

    private BackportCodecs() {
    }

    public static final StreamCodec<ByteBuf, BlockPos> BLOCK_POS =
            StreamCodec.of(
                    (b, v) -> ((FriendlyByteBuf) b).writeBlockPos(v),
                    b -> ((FriendlyByteBuf) b).readBlockPos());

    public static final StreamCodec<ByteBuf, Direction> DIRECTION =
            StreamCodec.of(
                    (b, v) -> ((FriendlyByteBuf) b).writeEnum(v),
                    b -> ((FriendlyByteBuf) b).readEnum(Direction.class));

    public static final StreamCodec<ByteBuf, ResourceLocation> RESOURCE_LOCATION =
            StreamCodec.of(
                    (b, v) -> ((FriendlyByteBuf) b).writeResourceLocation(v),
                    b -> ((FriendlyByteBuf) b).readResourceLocation());

    public static final StreamCodec<ByteBuf, UUID> UUID =
            StreamCodec.of(
                    (b, v) -> ((FriendlyByteBuf) b).writeUUID(v),
                    b -> ((FriendlyByteBuf) b).readUUID());

    /**
     * Backport of the vanilla 1.21 {@code ResourceKey.streamCodec(Registry)} helper, which does
     * not exist on Forge 1.20.1. Encodes a {@link ResourceKey} as its {@link ResourceLocation}
     * and rebuilds it via {@link ResourceKey#create(ResourceKey, ResourceLocation)}.
     */
    public static <T> StreamCodec<FriendlyByteBuf, ResourceKey<T>> resourceKeyStreamCodec(
            final ResourceKey<Registry<T>> registryKey) {
        return StreamCodec.of(
                (b, v) -> ((FriendlyByteBuf) b).writeResourceLocation(v.location()),
                b -> ResourceKey.create(registryKey, ((FriendlyByteBuf) b).readResourceLocation()));
    }
}
