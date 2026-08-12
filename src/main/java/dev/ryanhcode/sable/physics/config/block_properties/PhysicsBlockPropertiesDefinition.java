package dev.ryanhcode.sable.physics.config.block_properties;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The definition of the physics block properties for a block
 */
public record PhysicsBlockPropertiesDefinition(ExtraCodecs.TagOrElementLocation selector,
                                               int priority,
                                               Map<ResourceLocation, Object> properties,
                                               Optional<Map<BlockStateConditionSet, Map<ResourceLocation, Object>>> overrides) {

    /**
     * 1.20.1 (DFU 6.0.8) has no Codec.dispatchedMap; this is a faithful manual reimplementation:
     * a map codec where each value is encoded/decoded with the codec selected by its key.
     */
    private static <K, V> Codec<Map<K, V>> dispatchMap(final Codec<K> keyCodec,
                                                        final Function<K, Codec<V>> codecGetter) {
        final MapCodec<Map<K, V>> mapCodec = new MapCodec<Map<K, V>>() {
            @Override
            public <T> DataResult<Map<K, V>> decode(final DynamicOps<T> ops, final MapLike<T> input) {
                final Map<K, V> map = new HashMap<>();
                final List<String> errors = new ArrayList<>();
                input.entries().forEach(e -> {
                    final DataResult<K> kr = keyCodec.parse(ops, e.getFirst());
                    kr.error().ifPresent(err -> errors.add(err.message()));
                    kr.result().ifPresent(key -> {
                        final DataResult<V> vr = codecGetter.apply(key).parse(ops, e.getSecond());
                        vr.result().ifPresent(v -> map.put(key, v));
                        vr.error().ifPresent(err -> errors.add(err.message()));
                    });
                });
                if (!errors.isEmpty()) {
                    final StringBuilder sb = new StringBuilder();
                    for (final String err : errors) {
                        sb.append(err).append("; ");
                    }
                    return DataResult.error(sb::toString);
                }
                return DataResult.success(map);
            }

            @Override
            public <T> RecordBuilder<T> encode(final Map<K, V> input, final DynamicOps<T> ops, final RecordBuilder<T> prefix) {
                for (final Map.Entry<K, V> e : input.entrySet()) {
                    final DataResult<T> k = keyCodec.encodeStart(ops, e.getKey());
                    final DataResult<T> v = codecGetter.apply(e.getKey()).encodeStart(ops, e.getValue());
                    k.result().ifPresent(kk -> v.result().ifPresent(vv -> prefix.add(kk, vv)));
                }
                return prefix;
            }

            @Override
            public <T> java.util.stream.Stream<T> keys(final DynamicOps<T> ops) {
                return java.util.stream.Stream.empty();
            }
        };
        return mapCodec.codec();
    }

    public static final Codec<Map<ResourceLocation, Object>> PROPERTIES_CODEC =
            dispatchMap(ResourceLocation.CODEC, PhysicsBlockPropertyTypes::getPropertyCodec);

    public static final Codec<PhysicsBlockPropertiesDefinition> CODEC =
            RecordCodecBuilder.create(i -> i.group(
                    ExtraCodecs.TAG_OR_ELEMENT_ID.fieldOf("selector").forGetter(PhysicsBlockPropertiesDefinition::selector),
                    Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("priority", 1000).forGetter(PhysicsBlockPropertiesDefinition::priority),
                    PROPERTIES_CODEC.fieldOf("properties").forGetter(PhysicsBlockPropertiesDefinition::properties),
                    dispatchMap(BlockStateConditionSet.CODEC, (ignored) -> PROPERTIES_CODEC)
                            .optionalFieldOf("overrides").forGetter(PhysicsBlockPropertiesDefinition::overrides)
            ).apply(i, PhysicsBlockPropertiesDefinition::new));

    public static void encode(final FriendlyByteBuf buf, final PhysicsBlockPropertiesDefinition msg) {
        final CompoundTag tag = (CompoundTag) PhysicsBlockPropertiesDefinition.CODEC.encodeStart(NbtOps.INSTANCE, msg).result().orElseThrow();
        buf.writeNbt(tag);
    }

    public static PhysicsBlockPropertiesDefinition decode(final FriendlyByteBuf buf) {
        final CompoundTag tag = buf.readNbt();
        return PhysicsBlockPropertiesDefinition.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.selector);
    }

    @Override
    public String toString() {
        return "PhysicsBlockPropertiesDefinition{selector=%s, properties=%s}".formatted(this.selector, this.properties);
    }
}
