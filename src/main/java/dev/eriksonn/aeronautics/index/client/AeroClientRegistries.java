package dev.eriksonn.aeronautics.index.client;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.api.CustomSituationalMusic;
import foundry.veil.platform.registry.RegistrationProvider;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegistryBuilder;

public class AeroClientRegistries {
	public static class Keys {
		public static final ResourceKey<Registry<CustomSituationalMusic>> CUSTOM_SITUATIONAL_MUSIC = key("custom_situational_music");

		private static <T> ResourceKey<Registry<T>> key(final String name) {
			return ResourceKey.createRegistryKey(Aeronautics.path(name));
		}
	}

	public static RegistrationProvider<CustomSituationalMusic> CUSTOM_SITUATIONAL_MUSIC = registry(Keys.CUSTOM_SITUATIONAL_MUSIC);

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
		// for CustomSituationalMusic (asVanillaRegistry().entrySet()).
		event.create(RegistryBuilder.<CustomSituationalMusic>of(Keys.CUSTOM_SITUATIONAL_MUSIC.location()).disableSync().disableSaving().hasTags());
		registriesCreated = true;
	}

	private static <T> RegistrationProvider<T> registry(final ResourceKey<Registry<T>> registryKey) {
		return RegistrationProvider.get(registryKey, Aeronautics.MOD_ID);
	}

	public static void init() {

	}

}
