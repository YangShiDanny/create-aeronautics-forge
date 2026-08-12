package dev.eriksonn.aeronautics.neoforge.service;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.infrastructure.config.CStress;
import dev.eriksonn.aeronautics.config.client.AeroClient;
import dev.eriksonn.aeronautics.config.server.AeroServer;
import dev.eriksonn.aeronautics.config.AeroConfig;
import dev.eriksonn.aeronautics.config.server.AeroStress;
import net.createmod.catnip.config.ConfigBase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import dev.ryanhcode.sable.util.ForgeConfigRegistrar;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public class NeoForgeAeroConfigService implements AeroConfig {

	public static final Map<ModConfig.Type, ConfigBase> CONFIGS = new EnumMap<>(ModConfig.Type.class);

	private static AeroServer server;
	private static AeroClient client;

	@Override
	public AeroServer getServerConfig() {
		return server;
	}

	@Override
	public AeroClient getClientConfig() {
		return client;
	}

	private static <T extends ConfigBase> T register(final Supplier<T> factory, final ModConfig.Type side) {
		final Pair<T, ForgeConfigSpec> specPair = (new ForgeConfigSpec.Builder()).configure((builder) -> {
			final T config = factory.get();
			config.registerAll(builder);
			return config;
		});

		final T config = specPair.getLeft();
		config.specification = specPair.getRight();
		CONFIGS.put(side, config);
		return config;
	}

	public static void register(final ModContainer container) {
		server = register(AeroServer::new, ModConfig.Type.SERVER);
		client = register(AeroClient::new, ModConfig.Type.CLIENT);

		for (final Map.Entry<ModConfig.Type, ConfigBase> typeConfigBaseEntry : CONFIGS.entrySet()) {
			if (typeConfigBaseEntry.getKey() != net.minecraftforge.fml.config.ModConfig.Type.CLIENT || net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
                ForgeConfigRegistrar.register(typeConfigBaseEntry.getKey(), typeConfigBaseEntry.getValue().specification);
            }
		}

		CStress stress = server.kinetics.stressValues;
		BlockStressValues.IMPACTS.registerProvider(stress::getImpact);
		BlockStressValues.CAPACITIES.registerProvider(stress::getCapacity);
	}
}
