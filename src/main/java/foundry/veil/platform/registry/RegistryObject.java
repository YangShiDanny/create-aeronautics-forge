package foundry.veil.platform.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Backport shim of Veil's {@code foundry.veil.platform.registry.RegistryObject}.
 * Veil's multi-loader registry helper does not exist on Forge 1.20.1; this shim wraps a real
 * Forge {@link net.minecraftforge.registries.RegistryObject} (returned by
 * {@link net.minecraftforge.registries.DeferredRegister#register}) so the aeronautics/simulated
 * code that registers sounds, contraption types, custom registries, etc. compiles and the
 * entries are genuinely registered at runtime.
 */
public class RegistryObject<T> implements Supplier<T> {

    private final net.minecraftforge.registries.RegistryObject<T> delegate;

    public RegistryObject(final net.minecraftforge.registries.RegistryObject<T> delegate) {
        this.delegate = delegate;
    }

    /**
     * Backport of NeoForge 1.21's {@code RegistryObject.create(ResourceLocation, IForgeRegistry)}.
     * Mirrors the real Forge 1.20.1 {@link net.minecraftforge.registries.RegistryObject#create(ResourceLocation, IForgeRegistry)}.
     */
    public static <T> RegistryObject<T> create(final ResourceLocation id, final IForgeRegistry<T> registry) {
        return new RegistryObject<>(net.minecraftforge.registries.RegistryObject.create(id, registry));
    }

    /**
     * Backport of NeoForge 1.21's {@code RegistryObject.create(ResourceKey<T>)}.
     * Resolves the matching Forge registry from the key. Only the vanilla-style registries the
     * aeronautics/simulated/offroad code actually uses (ITEM, BLOCK, ENTITY_TYPE) are wired up;
     * everything delegates to the real Forge 1.20.1 {@code RegistryObject.create(ResourceLocation, IForgeRegistry)}.
     */
    public static <T> RegistryObject<T> create(final ResourceKey<T> key) {
        return new RegistryObject<>(net.minecraftforge.registries.RegistryObject.create(key.location(), resolveRegistry(key)));
    }

    /**
     * Backport of NeoForge 1.21's {@code RegistryObject.create(ResourceKey<T>, String)} used by
     * SimPonderScenes: the second argument is the namespace (mod id) that replaces the key's
     * namespace when looking the entry up.
     */
    public static <T> RegistryObject<T> create(final ResourceKey<T> key, final String namespace) {
        final ResourceLocation id = new ResourceLocation(namespace, key.location().getPath());
        return new RegistryObject<>(net.minecraftforge.registries.RegistryObject.create(id, resolveRegistry(key)));
    }

    @SuppressWarnings("unchecked")
    private static <T> IForgeRegistry<T> resolveRegistry(final ResourceKey<T> key) {
        final ResourceLocation regName = key.registry();
        if (regName.equals(Registries.ITEM.location())) {
            return (IForgeRegistry<T>) (Object) ForgeRegistries.ITEMS;
        }
        if (regName.equals(Registries.BLOCK.location())) {
            return (IForgeRegistry<T>) (Object) ForgeRegistries.BLOCKS;
        }
        if (regName.equals(Registries.ENTITY_TYPE.location())) {
            return (IForgeRegistry<T>) (Object) ForgeRegistries.ENTITY_TYPES;
        }
        throw new UnsupportedOperationException("RegistryObject.create 不支持的注册表: " + regName);
    }

    @Override
    public T get() {
        return this.delegate.get();
    }

    public boolean isPresent() {
        return this.delegate.isPresent();
    }

    public void ifPresent(final Consumer<? super T> action) {
        this.delegate.ifPresent(action);
    }

    public Stream<T> stream() {
        return this.delegate.stream();
    }

    public ResourceLocation getId() {
        return this.delegate.getId();
    }

    public ResourceKey<T> getKey() {
        return this.delegate.getKey();
    }

    public T orElse(final T other) {
        return this.isPresent() ? this.get() : other;
    }

    public T orElseGet(final Supplier<? extends T> other) {
        return this.isPresent() ? this.get() : other.get();
    }

    public <R> Optional<R> map(final Function<? super T, ? extends R> mapper) {
        return this.isPresent() ? Optional.ofNullable(mapper.apply(this.get())) : Optional.empty();
    }

    public Optional<T> filter(final Predicate<? super T> predicate) {
        return this.isPresent() && predicate.test(this.get()) ? Optional.of(this.get()) : Optional.empty();
    }

    /** Escape hatch for APIs that require the raw Forge RegistryObject. */
    public net.minecraftforge.registries.RegistryObject<T> getForgeRegistryObject() {
        return this.delegate;
    }
}
