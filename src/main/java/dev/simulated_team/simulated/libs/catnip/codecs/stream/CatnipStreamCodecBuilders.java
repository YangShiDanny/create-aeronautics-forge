package dev.simulated_team.simulated.libs.catnip.codecs.stream;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Backport shim of Create's {@code dev.simulated_team.simulated.libs.catnip.codecs.stream.CatnipStreamCodecBuilders}.
 * Provides the {@code list}/{@code ofEnum} builders the merged aeronautics build references.
 */
public final class CatnipStreamCodecBuilders {

    private CatnipStreamCodecBuilders() {
    }

    public static <E> StreamCodec<ByteBuf, List<E>> list(final StreamCodec<ByteBuf, E> elementCodec) {
        return StreamCodec.of((b, l) -> {
            ((FriendlyByteBuf) b).writeVarInt(l.size());
            for (final E e : l)
                elementCodec.encode(b, e);
        }, b -> {
            final int n = ((FriendlyByteBuf) b).readVarInt();
            final List<E> r = new ArrayList<>(n);
            for (int i = 0; i < n; i++)
                r.add(elementCodec.decode(b));
            return r;
        });
    }

    public static <E extends Enum<E>> StreamCodec<ByteBuf, E> ofEnum(final Class<E> enumClass) {
        return StreamCodec.of(
                (b, v) -> ((FriendlyByteBuf) b).writeEnum(v),
                b -> ((FriendlyByteBuf) b).readEnum(enumClass));
    }
}
