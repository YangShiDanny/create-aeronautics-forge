package dev.simulated_team.simulated.libs.minecraft.network.codec;

import com.mojang.serialization.Codec;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Backport shim of the vanilla 1.21 {@code dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs}.
 * All members are real, functional {@link StreamCodec}s over {@link ByteBuf}
 * (casting to {@link FriendlyByteBuf} only where the vanilla buffer lacks the helper) so the
 * merged aeronautics packets actually (de)serialize at runtime on Forge 1.20.1.
 *
 * <p>This is the CANONICAL type: the 32 source files which
 * {@code import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs} resolve to it.
 */
public final class ByteBufCodecs {

    private ByteBufCodecs() {
    }

    public static final StreamCodec<ByteBuf, Integer> INT =
            StreamCodec.of((b, i) -> b.writeInt(i), b -> b.readInt());

    public static final StreamCodec<ByteBuf, Boolean> BOOL =
            StreamCodec.of((b, v) -> b.writeBoolean(v), b -> b.readBoolean());

    public static final StreamCodec<ByteBuf, Double> DOUBLE =
            StreamCodec.of((b, v) -> b.writeDouble(v), b -> b.readDouble());

    public static final StreamCodec<ByteBuf, Float> FLOAT =
            StreamCodec.of((b, v) -> b.writeFloat(v), b -> b.readFloat());

    public static final StreamCodec<ByteBuf, String> STRING_UTF8 =
            StreamCodec.of((b, v) -> ((FriendlyByteBuf) b).writeUtf(v),
                    b -> ((FriendlyByteBuf) b).readUtf());

    /** NeoForge used org.joml.Quaternionf; Forge 1.20.1 uses org.joml.Quaternionf (getters x()/y()/z()/w()). */
    public static final StreamCodec<ByteBuf, Quaternionf> QUATERNIONF =
            StreamCodec.of((b, q) -> {
                final FriendlyByteBuf fb = (FriendlyByteBuf) b;
                fb.writeFloat(q.x());
                fb.writeFloat(q.y());
                fb.writeFloat(q.z());
                fb.writeFloat(q.w());
            }, b -> {
                final FriendlyByteBuf fb = (FriendlyByteBuf) b;
                return new Quaternionf(fb.readFloat(), fb.readFloat(), fb.readFloat(), fb.readFloat());
            });

    /** Backport of NeoForge's {@code ItemStack.OPTIONAL_STREAM_CODEC}: an empty stack encodes absence. */
    public static final StreamCodec<ByteBuf, ItemStack> OPTIONAL_ITEM =
            StreamCodec.of((b, v) -> ((FriendlyByteBuf) b).writeItem(v),
                    b -> ((FriendlyByteBuf) b).readItem());

    public static <V> Function<StreamCodec<ByteBuf, V>, StreamCodec<ByteBuf, List<V>>> list() {
        return base -> StreamCodec.of((b, l) -> {
            ((FriendlyByteBuf) b).writeVarInt(l.size());
            for (final V v : l)
                base.encode(b, v);
        }, b -> {
            final int n = ((FriendlyByteBuf) b).readVarInt();
            final List<V> r = new ArrayList<>(n);
            for (int i = 0; i < n; i++)
                r.add(base.decode(b));
            return r;
        });
    }

    /** NeoForge 1.21 fixed-size list codec: ByteBufCodecs.<X>list(int). */
    public static <V> Function<StreamCodec<ByteBuf, V>, StreamCodec<ByteBuf, List<V>>> list(final int size) {
        return base -> StreamCodec.of((b, l) -> {
            for (final V v : l)
                base.encode(b, v);
        }, b -> {
            final List<V> r = new ArrayList<>(size);
            for (int i = 0; i < size; i++)
                r.add(base.decode(b));
            return r;
        });
    }

    public static <K, V> StreamCodec<ByteBuf, Map<K, V>> map(
            final Supplier<Map<K, V>> factory,
            final StreamCodec<ByteBuf, K> keyCodec,
            final StreamCodec<ByteBuf, V> valueCodec) {
        return StreamCodec.of((b, m) -> {
            ((FriendlyByteBuf) b).writeVarInt(m.size());
            for (final Map.Entry<K, V> e : m.entrySet()) {
                keyCodec.encode(b, e.getKey());
                valueCodec.encode(b, e.getValue());
            }
        }, b -> {
            final int n = ((FriendlyByteBuf) b).readVarInt();
            final Map<K, V> r = factory.get();
            for (int i = 0; i < n; i++)
                r.put(keyCodec.decode(b), valueCodec.decode(b));
            return r;
        });
    }

    public static <E, C extends Collection<E>> StreamCodec<ByteBuf, C> collection(
            final Supplier<C> factory,
            final StreamCodec<ByteBuf, E> elementCodec) {
        return StreamCodec.of((b, c) -> {
            ((FriendlyByteBuf) b).writeVarInt(c.size());
            for (final E e : c)
                elementCodec.encode(b, e);
        }, b -> {
            final int n = ((FriendlyByteBuf) b).readVarInt();
            final C r = factory.get();
            for (int i = 0; i < n; i++)
                r.add(elementCodec.decode(b));
            return r;
        });
    }

    public static <V> StreamCodec<ByteBuf, Optional<V>> optional(
            final StreamCodec<ByteBuf, V> elementCodec) {
        return StreamCodec.of((b, opt) -> {
            b.writeBoolean(opt.isPresent());
            opt.ifPresent(v -> elementCodec.encode(b, v));
        }, b -> b.readBoolean() ? Optional.of(elementCodec.decode(b)) : Optional.empty());
    }

    /**
     * Backport of NeoForge 1.21's {@code ByteBufCodecs.fromCodec(Codec<T>)}.
     * Serializes the value through the supplied Mojang {@link Codec} using an NBT compound,
     * which is the faithful 1.20.1 equivalent of the 1.21 registry-friendly codec path.
     */
    public static <T> StreamCodec<ByteBuf, T> fromCodec(final Codec<T> codec) {
        return StreamCodec.of((b, v) -> {
            final CompoundTag tag = (CompoundTag) codec.encodeStart(NbtOps.INSTANCE, v)
                    .result().orElseGet(CompoundTag::new);
            ((FriendlyByteBuf) b).writeNbt(tag);
        }, b -> {
            final CompoundTag tag = ((FriendlyByteBuf) b).readNbt();
            return codec.decode(NbtOps.INSTANCE, tag == null ? new CompoundTag() : tag)
                    .result().map(Pair::getFirst).orElse(null);
        });
    }
}
