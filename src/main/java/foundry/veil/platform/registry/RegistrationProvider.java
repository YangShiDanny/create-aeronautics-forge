package foundry.veil.platform.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Backport shim of Veil's {@code foundry.veil.platform.registry.RegistrationProvider}.
 * Veil's multi-loader registry abstraction does not exist on Forge 1.20.1; this shim backs it
 * with a real {@link DeferredRegister} (auto-registered to the current mod's event bus, exactly
 * how a normal Forge mod declares its {@code DeferredRegister} as a static final and registers it
 * in the mod constructor). The merged aeronautics build's registry-holder classes load during
 * mod construction, so {@link FMLJavaModLoadingContext#get()} is valid at that time.
 */
public interface RegistrationProvider<T> {

    static <T> RegistrationProvider<T> get(final ResourceKey<? extends Registry<T>> key, final String modId) {
        final DeferredRegister<T> dr = DeferredRegister.create(key, modId);
        dr.register(FMLJavaModLoadingContext.get().getModEventBus());
        return new Impl<>(dr, (ResourceKey<Registry<T>>) key);
    }

    static <T> RegistrationProvider<T> get(final Registry<T> registry, final String modId) {
        return get(registry.key(), modId);
    }

    <T1 extends T> RegistryObject<T1> register(final String name, final Supplier<T1> supplier);

    Collection<RegistryObject<T>> getEntries();

    /**
     * Backport of Veil's {@code RegistrationProvider#asVanillaRegistry()}.
     * [1.20.1 port] The original Veil shim resolved the registry from the running
     * {@code MinecraftServer}, which is null on the client (or before a world loads) and
     * would NPE every caller (CustomSituationalMusic, LevititeBlendTicker). On Forge
     * 1.20.1 the custom registries we create via {@code NewRegistryEvent} live in
     * {@code RegistryManager.ACTIVE}, which is always queryable without a server.
     * The {@code ForgeRegistry} itself is NOT a vanilla {@code Registry}; the vanilla
     * view is the wrapper produced by {@link ForgeRegistry#getWrapper()}, which is
     * non-null only because the {@code RegistryBuilder} was created with {@code hasTags()}
     * (RegistryBuilder.hasWrapper) in the matching *Registries.createRegistries() method.
     */
    default Registry<T> asVanillaRegistry() {
        final IForgeRegistry<T> reg = RegistryManager.ACTIVE.getRegistry(this.getKey());
        return reg != null ? getWrapper(reg) : null;
    }

    // [1.20.1 port] ForgeRegistry.getWrapper() 是包私有方法，用反射取真正的 vanilla Registry<V>。
    @SuppressWarnings("unchecked")
    static <T> Registry<T> getWrapper(final IForgeRegistry<T> reg) {
        if (reg == null) {
            return null;
        }
        try {
            final Method m = reg.getClass().getDeclaredMethod("getWrapper");
            m.setAccessible(true);
            return (Registry<T>) m.invoke(reg);
        } catch (final ReflectiveOperationException e) {
            return null;
        }
    }

    ResourceKey<Registry<T>> getKey();

    final class Impl<T> implements RegistrationProvider<T> {
        private final DeferredRegister<T> dr;
        private final ResourceKey<Registry<T>> key;

        Impl(final DeferredRegister<T> dr, final ResourceKey<Registry<T>> key) {
            this.dr = dr;
            this.key = key;
        }

        @Override
        public ResourceKey<Registry<T>> getKey() {
            return this.key;
        }

        @Override
        public <T1 extends T> RegistryObject<T1> register(final String name, final Supplier<T1> supplier) {
            return new RegistryObject<>(this.dr.register(name, supplier));
        }

        @Override
        public Collection<RegistryObject<T>> getEntries() {
            return this.dr.getEntries().stream()
                    .map(RegistryObject::new)
                    .collect(Collectors.toList());
        }
    }
}
