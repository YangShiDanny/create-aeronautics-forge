package dev.simulated_team.simulated.libs.minecraft.core.component;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Backport shim for the 1.20.5+ Data Component system.
 *
 * <p>In 1.20.1 there is no {@code DataComponentType} API on {@link ItemStack}. We emulate it
 * by serialising each component to a {@link CompoundTag} sub-entry keyed by a stable string
 * (the component's registration name). Components may alternatively be backed by a vanilla
 * {@link ItemStack} field (e.g. custom name, block entity tag) via a {@link ComponentAccessor}.
 *
 * <p>Call sites that originally read {@code stack.get(type)} now call the static helpers on this
 * class, e.g. {@code DataComponentType.get(stack, type)}.
 */
public final class DataComponentType<T> {

    private static final Set<DataComponentType<?>> ALL = new HashSet<>();

    
    private final Codec<T> codec;
    private final String nbtKey;
    
    private final ComponentAccessor<T> accessor;

    private DataComponentType( final Codec<T> codec, final String nbtKey,
                              final ComponentAccessor<T> accessor) {
        this.codec = codec;
        this.nbtKey = nbtKey;
        this.accessor = accessor;
    }

    public Codec<T> codec() {
        return codec;
    }

    public String nbtKey() {
        return nbtKey;
    }

    // ---- factories ----

    /** NBT-backed component (serialised via its codec under {@code nbtKey}). */
    public static <T> DataComponentType<T> of(final Codec<T> codec, final String nbtKey) {
        final DataComponentType<T> type = new DataComponentType<>(codec, nbtKey, null);
        ALL.add(type);
        return type;
    }

    /** Accessor-backed component (delegates to a vanilla ItemStack field). */
    public static <T> DataComponentType<T> of(final ComponentAccessor<T> accessor) {
        final DataComponentType<T> type = new DataComponentType<>(null, "_accessor", accessor);
        ALL.add(type);
        return type;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    // ---- static compat API (replaces ItemStack.get/set/has/remove) ----

    public static <T> boolean has(final ItemStack stack, final DataComponentType<T> type) {
        if (type.accessor != null) return type.accessor.has(stack);
        final CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(type.nbtKey);
    }

    
    public static <T> T get(final ItemStack stack, final DataComponentType<T> type) {
        if (type.accessor != null) return type.accessor.get(stack);
        final CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(type.nbtKey)) return null;
        if (type.codec == null) return null;
        return type.codec.parse(NbtOps.INSTANCE, tag.get(type.nbtKey)).result().orElse(null);
    }

    public static <T> T getOrDefault(final ItemStack stack, final DataComponentType<T> type, final T def) {
        final T value = get(stack, type);
        return value != null ? value : def;
    }

    public static <T> void set(final ItemStack stack, final DataComponentType<T> type, final T value) {
        if (type.accessor != null) {
            type.accessor.set(stack, value);
            return;
        }
        if (type.codec == null) return;
        final Tag encoded = type.codec.encodeStart(NbtOps.INSTANCE, value).result().orElse(null);
        if (encoded != null) {
            stack.getOrCreateTag().put(type.nbtKey, encoded);
        }
    }

    public static <T> void remove(final ItemStack stack, final DataComponentType<T> type) {
        if (type.accessor != null) {
            type.accessor.remove(stack);
            return;
        }
        final CompoundTag tag = stack.getTag();
        if (tag != null) tag.remove(type.nbtKey);
    }

    /** Builds a {@link DataComponentPatch} capturing every component currently present on the stack. */
    public static DataComponentPatch getComponentsPatch(final ItemStack stack) {
        final DataComponentPatch.Builder builder = DataComponentPatch.builder();
        for (final DataComponentType<?> type : ALL) {
            if (has(stack, type)) {
                @SuppressWarnings("unchecked")
                final DataComponentType<Object> t = (DataComponentType<Object>) type;
                builder.set(t, get(stack, t));
            }
        }
        return builder.build();
    }

    public static void applyComponents(final ItemStack stack, final DataComponentPatch patch) {
        patch.applyTo(stack);
    }

    /** Returns a {@link DataComponentMap} view over the given stack (used for {@code stack.getComponents()}). */
    public static DataComponentMap componentsOf(final ItemStack stack) {
        return new DataComponentMap.ItemStackComponents(stack);
    }

    // ---- builder ----

    public static final class Builder<T> {
        
        private Codec<T> codec;
        private String nbtKey = "_unnamed";

        public Builder<T> persistent(final Codec<T> codec) {
            this.codec = codec;
            return this;
        }

        public Builder<T> networkSynchronized(final Object streamCodec) {
            return this;
        }

        public Builder<T> withNbtKey(final String key) {
            this.nbtKey = key;
            return this;
        }

        public DataComponentType<T> build() {
            final DataComponentType<T> type = new DataComponentType<>(codec, nbtKey, null);
            ALL.add(type);
            return type;
        }
    }

    /** Bridges a component to a vanilla ItemStack field. */
    public interface ComponentAccessor<T> {
        boolean has(ItemStack stack);

        
        T get(ItemStack stack);

        void set(ItemStack stack, T value);

        void remove(ItemStack stack);
    }
}
