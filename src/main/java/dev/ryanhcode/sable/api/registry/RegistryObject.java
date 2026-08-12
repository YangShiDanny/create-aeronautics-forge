package dev.ryanhcode.sable.api.registry;

/**
 * Minimal stand-in for the NeoForge / Veil {@code RegistryObject} used by the original mod.
 * The original mod relied on Veil's datapack-registry helper, which does not exist on
 * Forge 1.20.1. This holder keeps the same {@code get()} API so callers are unchanged.
 *
 * @param <T> the held value type
 */
public class RegistryObject<T> {
    private final T value;

    public RegistryObject(final T value) {
        this.value = value;
    }

    public T get() {
        return this.value;
    }
}
