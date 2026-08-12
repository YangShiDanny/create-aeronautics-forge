package dev.eriksonn.aeronautics.index;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.api.levitite_blend_crystallization.CrystalPropagationContext;
import dev.eriksonn.aeronautics.content.blocks.hot_air.lifting_gas.LiftingGasType;
import foundry.veil.platform.registry.RegistrationProvider;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegistryBuilder;

public class AeroRegistries {
    public static class Keys {
        public static final ResourceKey<Registry<LiftingGasType>> LIFTING_GAS_TYPE = key("lifting_gas_type");
        public static final ResourceKey<Registry<CrystalPropagationContext>> LEVITITE_CRYSTAL_PROPAGATION_CONTEXT = key("levitite_crystal_propagation_context");

        private static <T> ResourceKey<Registry<T>> key(final String name) {
            return ResourceKey.createRegistryKey(Aeronautics.path(name));
        }
    }

    public static final RegistrationProvider<LiftingGasType> LIFTING_GAS_TYPE = registry(AeroRegistries.Keys.LIFTING_GAS_TYPE);
    public static final RegistrationProvider<CrystalPropagationContext> LEVITITE_CRYSTAL_PROPAGATION_CONTEXT = registry(Keys.LEVITITE_CRYSTAL_PROPAGATION_CONTEXT);

    private static boolean registriesCreated = false;

    // [1.20.1 port] NeoForge 1.21 created custom registries implicitly via
    // DeferredRegister.create(RegistryBuilder, modId); Forge 1.20.1 requires an explicit
    // NewRegistryEvent. Called from both common and client @Mod constructors (guarded).
    public static void createRegistries(final NewRegistryEvent event) {
        if (registriesCreated) {
            return;
        }
		// .hasTags() enables the vanilla Registry wrapper (RegistryBuilder.hasWrapper), so that
		// asVanillaRegistry()/getWrapper() in RegistrationProvider returns a non-null Registry<V>
		// for LevititeBlendTicker (asVanillaRegistry().get()/.getKey()).
		event.create(RegistryBuilder.<LiftingGasType>of(Keys.LIFTING_GAS_TYPE.location()).disableSync().disableSaving().hasTags());
		event.create(RegistryBuilder.<CrystalPropagationContext>of(Keys.LEVITITE_CRYSTAL_PROPAGATION_CONTEXT.location()).disableSync().disableSaving().hasTags());
        registriesCreated = true;
    }

    private static <T> RegistrationProvider<T> registry(final ResourceKey<Registry<T>> registryKey) {
        return RegistrationProvider.get(registryKey, Aeronautics.MOD_ID);
    }

    public static void init() {
        // no-op, for JIT
    }
}
