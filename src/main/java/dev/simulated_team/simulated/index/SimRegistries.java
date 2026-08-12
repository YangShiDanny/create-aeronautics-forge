package dev.simulated_team.simulated.index;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.client.BlockPropertiesTooltip;
import dev.simulated_team.simulated.index.SimDataComponents;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget;
import foundry.veil.platform.registry.RegistrationProvider;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegistryBuilder;

import java.lang.reflect.Method;

public class SimRegistries {
	public static class Keys {
		public static final ResourceKey<Registry<NavigationTarget>> NAVIGATION_TARGET = key("navigation_target");
		public static final ResourceKey<Registry<BlockPropertiesTooltip.Entry>> PROPERTY_TOOLTIP = key("property_tooltip");

		private static <T> ResourceKey<Registry<T>> key(final String name) {
			return ResourceKey.createRegistryKey(Simulated.path(name));
		}
	}

	// [1.20.1 port] Assigned in createRegistries(NewRegistryEvent). Must NOT be cached via
	// asVanillaRegistry() (which returns null before a server is up at class-load time).
	public static Registry<NavigationTarget> NAVIGATION_TARGET;
	public static Registry<BlockPropertiesTooltip.Entry> PROPERTY_TOOLTIP;

	private static boolean registriesCreated = false;

	// [1.20.1 port] NeoForge 1.21 created custom registries implicitly via
	// DeferredRegister.create(RegistryBuilder, modId); Forge 1.20.1 requires an explicit
	// NewRegistryEvent. Called from both common and client @Mod constructors (guarded).
	// NOTE: event.create(...) returns a Supplier that only resolves inside event.fill()
	// (which runs during postNewRegistryEvent, BEFORE applyObjectHolders). We must use the
	// onFill Consumer overload so the real registry is captured into our fields. The onFill
	// lambda receives the IForgeRegistry (a ForgeRegistry), but our fields are typed as the
	// vanilla Registry<T> that callers (SimDataComponents.byNameCodec / BlockPropertiesTooltip
	// .entrySet) expect. The vanilla Registry is the wrapper obtained via getWrapper().
	// .hasTags() enables that wrapper (RegistryBuilder.hasWrapper, otherwise getWrapper()==null).
	public static void createRegistries(final NewRegistryEvent event) {
		if (registriesCreated) {
			return;
		}
		// [1.20.1 port] Forge 1.20.1 的 RegistryBuilder 构造器是无参的，名字用 .setName() 设置
		// （此处用静态工厂 RegistryBuilder.of(ResourceLocation)）。.hasTags() 会启用 wrapper
		// （RegistryBuilder.hasWrapper=true），使 getWrapper() 返回真正的 vanilla Registry<V>
		// （NamespacedWrapper，继承 MappedRegistry）。拿到 wrapper 后，
		// NAVIGATION_TARGET.byNameCodec() 与 PROPERTY_TOOLTIP.entrySet() 这些 vanilla
		// Registry API 才可用。getWrapper() 在 ForgeRegistry 里是包私有，用反射取。
		event.create(RegistryBuilder.<NavigationTarget>of(Keys.NAVIGATION_TARGET.location()).disableSync().disableSaving().hasTags(),
				reg -> {
					NAVIGATION_TARGET = getWrapper(reg);
					// [1.20.1 port] onFill 在此处拿到已填充的 NAVIGATION_TARGET wrapper，
					// 紧接着惰性创建依赖它的数据组件 TARGET（见 SimDataComponents）。必须在同一个
					// onFill 内、NAVIGATION_TARGET 赋值后立即调用，避免 NewRegistryEvent 派发循环里
					// 时机的竞态（fill 在 postNewRegistryEvent 才跑，监听器里此刻仍 null）。
					SimDataComponents.register();
				});
		event.create(RegistryBuilder.<BlockPropertiesTooltip.Entry>of(Keys.PROPERTY_TOOLTIP.location()).disableSync().disableSaving().hasTags(),
				reg -> PROPERTY_TOOLTIP = getWrapper(reg));
		registriesCreated = true;
	}

	// [1.20.1 port] ForgeRegistry.getWrapper() 是包私有方法，无法从本包直接调用。
	// 用反射取真正的 vanilla Registry<V> wrapper（NamespacedWrapper）。
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

	public static void register() {}
}
