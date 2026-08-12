package dev.simulated_team.simulated.libs.minecraft.core.component;

import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Backport shim for 1.20.5+ {@code DataComponentPatch}.
 *
 * <p>Holds a map of {@link DataComponentType} to either a present value ({@link Optional#of})
 * or a removal marker ({@link Optional#empty}). {@link #applyTo(ItemStack)} replays the
 * entries onto a stack via the {@link DataComponentType} static helpers.
 */
public final class DataComponentPatch {

    private final Map<DataComponentType<?>, Optional<?>> entries = new HashMap<>();

    private DataComponentPatch() {}

    public static Builder builder() {
        return new Builder();
    }

    public static DataComponentPatch empty() {
        return new DataComponentPatch();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Set<Map.Entry<DataComponentType<?>, Optional<?>>> entrySet() {
        return entries.entrySet();
    }

    void applyTo(final ItemStack stack) {
        for (final Map.Entry<DataComponentType<?>, Optional<?>> entry : entries.entrySet()) {
            @SuppressWarnings("unchecked")
            final DataComponentType<Object> type = (DataComponentType<Object>) entry.getKey();
            final Optional<?> value = entry.getValue();
            if (value.isPresent()) {
                DataComponentType.set(stack, type, value.get());
            } else {
                DataComponentType.remove(stack, type);
            }
        }
    }

    public static final class Builder implements DataComponentMap.Builder {

        private final DataComponentPatch patch = new DataComponentPatch();

        @Override
        @SuppressWarnings("unchecked")
        public <T> Builder set(final DataComponentType<? super T> type, final T value) {
            patch.entries.put((DataComponentType<?>) type, Optional.of(value));
            return this;
        }

        public <T> Builder remove(final DataComponentType<?> type) {
            patch.entries.put(type, Optional.empty());
            return this;
        }

        @Override
        public DataComponentPatch build() {
            return patch;
        }
    }
}
