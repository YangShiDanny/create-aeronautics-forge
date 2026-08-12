package dev.simulated_team.simulated.libs.minecraft.network.codec;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Backport shim of the vanilla 1.21 {@code dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec<S, V>}.
 * Forge 1.20.1 has no such type; the merged aeronautics build uses this shim so the packet
 * {@code CODEC} fields (declared as {@code StreamCodec<ByteBuf, X>}) compile and actually
 * (de)serialize over a {@link net.minecraft.network.FriendlyByteBuf} at runtime via the
 * {@code foundry.veil.api.network.VeilPacketManager} facade.
 *
 * <p>This is the CANONICAL type (not an alias): the static factory methods {@link #of},
 * {@code #unit} and {@code #composite} are declared here so that the 56 source files which
 * {@code import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec} can invoke them.
 */
public interface StreamCodec<S, V> {

    V decode(S buffer);

    void encode(S buffer, V value);

    static <S, V> StreamCodec<S, V> of(BiConsumer<S, V> encoder, Function<S, V> decoder) {
        return new StreamCodec<>() {
            public V decode(S b) {
                return decoder.apply(b);
            }

            public void encode(S b, V v) {
                encoder.accept(b, v);
            }
        };
    }

    static <S, V> StreamCodec<S, V> unit(V value) {
        return of((b, v) -> {
        }, b -> value);
    }

    /**
     * Backport of NeoForge 1.21's {@code StreamCodec.apply(Function)} bridge: applies an
     * element codec to a list/compound codec factory. Used as {@code BYTEBUFCODEC.apply(ByteBufCodecs.list(n))}.
     */
    default <R> StreamCodec<S, R> apply(final Function<StreamCodec<S, V>, StreamCodec<S, R>> combinator) {
        return combinator.apply(this);
    }

    /** Backport of NeoForge 1.21's {@code StreamCodec.map(to, from)}. */
    default <R> StreamCodec<S, R> map(final Function<V, R> to, final Function<R, V> from) {
        return StreamCodec.of(
                (b, v) -> this.encode(b, from.apply(v)),
                b -> to.apply(this.decode(b)));
    }

    /* ---- helper: encode with wildcard capture via unchecked cast ---- */
    @SuppressWarnings("unchecked")
    private static <S, X> void encodeImpl(StreamCodec<? super S, ?> codec, S buf, Object val) {
        ((StreamCodec<S, X>) codec).encode(buf, (X) val);
    }

    @SuppressWarnings("unchecked")
    private static <S, X> X decodeImpl(StreamCodec<? super S, ?> codec, S buf) {
        return ((StreamCodec<S, X>) codec).decode(buf);
    }

    /* ---- composite arity 1 ---- */
    @SuppressWarnings("unchecked")
    static <S, B, A> StreamCodec<S, B> composite(
            StreamCodec<? super S, ? extends A> c1, Function<? super B, ? extends A> g1,
            Function<? super A, ? extends B> combiner) {
        return of(
                (b, v) -> encodeImpl(c1, b, g1.apply(v)),
                b -> combiner.apply(decodeImpl(c1, b)));
    }

    /* ---- composite arity 2 ---- */
    @SuppressWarnings("unchecked")
    static <S, B, A, C> StreamCodec<S, B> composite(
            StreamCodec<? super S, ? extends A> c1, Function<? super B, ? extends A> g1,
            StreamCodec<? super S, ? extends C> c2, Function<? super B, ? extends C> g2,
            BiFunction<? super A, ? super C, ? extends B> combiner) {
        return of(
                (b, v) -> {
                    encodeImpl(c1, b, g1.apply(v));
                    encodeImpl(c2, b, g2.apply(v));
                },
                b -> combiner.apply(decodeImpl(c1, b), decodeImpl(c2, b)));
    }

    /* ---- composite arity 3 ---- */
    @SuppressWarnings("unchecked")
    static <S, B, A, C, D> StreamCodec<S, B> composite(
            StreamCodec<? super S, ? extends A> c1, Function<? super B, ? extends A> g1,
            StreamCodec<? super S, ? extends C> c2, Function<? super B, ? extends C> g2,
            StreamCodec<? super S, ? extends D> c3, Function<? super B, ? extends D> g3,
            Function3<? super A, ? super C, ? super D, ? extends B> combiner) {
        return of(
                (b, v) -> {
                    encodeImpl(c1, b, g1.apply(v));
                    encodeImpl(c2, b, g2.apply(v));
                    encodeImpl(c3, b, g3.apply(v));
                },
                b -> combiner.apply(decodeImpl(c1, b), decodeImpl(c2, b), decodeImpl(c3, b)));
    }

    /* ---- composite arity 4 ---- */
    @SuppressWarnings("unchecked")
    static <S, B, A, C, D, E> StreamCodec<S, B> composite(
            StreamCodec<? super S, ? extends A> c1, Function<? super B, ? extends A> g1,
            StreamCodec<? super S, ? extends C> c2, Function<? super B, ? extends C> g2,
            StreamCodec<? super S, ? extends D> c3, Function<? super B, ? extends D> g3,
            StreamCodec<? super S, ? extends E> c4, Function<? super B, ? extends E> g4,
            Function4<? super A, ? super C, ? super D, ? super E, ? extends B> combiner) {
        return of(
                (b, v) -> {
                    encodeImpl(c1, b, g1.apply(v));
                    encodeImpl(c2, b, g2.apply(v));
                    encodeImpl(c3, b, g3.apply(v));
                    encodeImpl(c4, b, g4.apply(v));
                },
                b -> combiner.apply(decodeImpl(c1, b), decodeImpl(c2, b), decodeImpl(c3, b), decodeImpl(c4, b)));
    }

    /* ---- composite arity 5 ---- */
    @SuppressWarnings("unchecked")
    static <S, B, A, C, D, E, F> StreamCodec<S, B> composite(
            StreamCodec<? super S, ? extends A> c1, Function<? super B, ? extends A> g1,
            StreamCodec<? super S, ? extends C> c2, Function<? super B, ? extends C> g2,
            StreamCodec<? super S, ? extends D> c3, Function<? super B, ? extends D> g3,
            StreamCodec<? super S, ? extends E> c4, Function<? super B, ? extends E> g4,
            StreamCodec<? super S, ? extends F> c5, Function<? super B, ? extends F> g5,
            Function5<? super A, ? super C, ? super D, ? super E, ? super F, ? extends B> combiner) {
        return of(
                (b, v) -> {
                    encodeImpl(c1, b, g1.apply(v));
                    encodeImpl(c2, b, g2.apply(v));
                    encodeImpl(c3, b, g3.apply(v));
                    encodeImpl(c4, b, g4.apply(v));
                    encodeImpl(c5, b, g5.apply(v));
                },
                b -> combiner.apply(decodeImpl(c1, b), decodeImpl(c2, b), decodeImpl(c3, b), decodeImpl(c4, b), decodeImpl(c5, b)));
    }

    /* ---- composite arity 6 ---- */
    @SuppressWarnings("unchecked")
    static <S, B, A, C, D, E, F, G> StreamCodec<S, B> composite(
            StreamCodec<? super S, ? extends A> c1, Function<? super B, ? extends A> g1,
            StreamCodec<? super S, ? extends C> c2, Function<? super B, ? extends C> g2,
            StreamCodec<? super S, ? extends D> c3, Function<? super B, ? extends D> g3,
            StreamCodec<? super S, ? extends E> c4, Function<? super B, ? extends E> g4,
            StreamCodec<? super S, ? extends F> c5, Function<? super B, ? extends F> g5,
            StreamCodec<? super S, ? extends G> c6, Function<? super B, ? extends G> g6,
            Function6<? super A, ? super C, ? super D, ? super E, ? super F, ? super G, ? extends B> combiner) {
        return of(
                (b, v) -> {
                    encodeImpl(c1, b, g1.apply(v));
                    encodeImpl(c2, b, g2.apply(v));
                    encodeImpl(c3, b, g3.apply(v));
                    encodeImpl(c4, b, g4.apply(v));
                    encodeImpl(c5, b, g5.apply(v));
                    encodeImpl(c6, b, g6.apply(v));
                },
                b -> combiner.apply(decodeImpl(c1, b), decodeImpl(c2, b), decodeImpl(c3, b), decodeImpl(c4, b), decodeImpl(c5, b), decodeImpl(c6, b)));
    }
}
