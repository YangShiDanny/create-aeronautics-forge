package dev.simulated_team.simulated.libs.minecraft.core.component;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.ItemStack;

/**
 * Backport shim for 1.20.5+ {@code DataComponentMap}.
 *
 * <p>{@link #builder()} delegates to {@link DataComponentPatch.Builder} (whose {@code build()}
 * returns a {@link DataComponentPatch}), and {@link #of(ItemStack)} wraps a stack so the
 * {@code get/has/getOrDefault} call sites from {@code stack.getComponents()} keep working.
 */
public interface DataComponentMap {

    Codec<DataComponentMap> CODEC = Codec.unit(DataComponentMap::empty);

    static DataComponentMap empty() {
        return new EmptyMap();
    }

    static Builder builder() {
        return DataComponentPatch.builder();
    }

    static DataComponentMap of(final ItemStack stack) {
        return new ItemStackComponents(stack);
    }

    boolean has(DataComponentType<?> type);

    <T> T get(DataComponentType<T> type);

    <T> T getOrDefault(DataComponentType<T> type, T def);

    interface Builder {
        <T> Builder set(DataComponentType<? super T> type, T value);

        DataComponentPatch build();
    }

    final class ItemStackComponents implements DataComponentMap {
        private final ItemStack stack;

        ItemStackComponents(final ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public boolean has(final DataComponentType<?> type) {
            return DataComponentType.has(stack, type);
        }

        @Override
        public <T> T get(final DataComponentType<T> type) {
            return DataComponentType.get(stack, type);
        }

        @Override
        public <T> T getOrDefault(final DataComponentType<T> type, final T def) {
            return DataComponentType.getOrDefault(stack, type, def);
        }
    }

    final class EmptyMap implements DataComponentMap {
        @Override
        public boolean has(final DataComponentType<?> type) {
            return false;
        }

        @Override
        public <T> T get(final DataComponentType<T> type) {
            return null;
        }

        @Override
        public <T> T getOrDefault(final DataComponentType<T> type, final T def) {
            return def;
        }
    }
}
