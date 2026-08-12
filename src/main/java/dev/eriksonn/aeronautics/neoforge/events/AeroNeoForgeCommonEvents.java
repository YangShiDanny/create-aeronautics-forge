package dev.eriksonn.aeronautics.neoforge.events;

import dev.simulated_team.simulated.service.SimPlatformService;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.data.AeroAdvancementTriggers;
import dev.eriksonn.aeronautics.events.AeronauticsCommonEvents;
import dev.eriksonn.aeronautics.index.*;
import dev.eriksonn.aeronautics.neoforge.data.recipe.AeroProcessingRecipeGen;
import dev.eriksonn.aeronautics.neoforge.index.AeroFluidsNeoForge;
import dev.eriksonn.aeronautics.neoforge.service.NeoForgeAeroConfigService;
import net.createmod.catnip.config.ConfigBase;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.RegisterEvent;

import java.util.concurrent.CompletableFuture;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.EventPriority;

@Mod.EventBusSubscriber(modid = Aeronautics.MOD_ID)
public class AeroNeoForgeCommonEvents {

	@SubscribeEvent
	public static void serverStop(ServerStoppedEvent event) {
		AeronauticsCommonEvents.onServerStopped(event.getServer());
	}

	@SubscribeEvent
	public static void postServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		final MinecraftServer server = event.getServer();
		for (final ServerLevel level : server.getAllLevels()) {
			AeronauticsCommonEvents.onServerTickEnd(level);
		}
	}

	@Mod.EventBusSubscriber(modid = Aeronautics.MOD_ID)
	public static class ModBusEvents {

		private static boolean triggerRegistered = false;

	@SubscribeEvent
	public static void registerEvent(RegisterEvent event) {
		AeroArmInteractionPoints.init();
	}

		// todo: move this somewhere more proper
		// JEI compat (jeiCompat) excluded on Forge 1.20.1 — phase 2

		@SubscribeEvent(priority = EventPriority.HIGH)
		public static void gatherDataHighPriority(GatherDataEvent event) {
			AeroTags.addGenerators();
		}

		@SubscribeEvent
		public static void gatherData(GatherDataEvent event) {
			final DataGenerator generator = event.getGenerator();
			final PackOutput output = generator.getPackOutput();
			final CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

			generator.addProvider(event.includeServer(), new AeroAdvancements(output, lookupProvider));
			generator.addProvider(event.includeServer(), AeroProcessingRecipeGen.registerAll(output, lookupProvider));
			generator.addProvider(event.includeServer(), AeroSoundEvents.REGISTRY.getProvider(output));
		}

	@SubscribeEvent
	public static void commonSetup(FMLCommonSetupEvent event) {
		AeroFluidsNeoForge.registerFluidInteractions();
		// [1.20.1 port] 原版在 RegisterEvent 期间调 AeroAdvancements.init() 会触发
		// SimAdvancements 的 <clinit> 立即 asStack() 取未注册条目（NeoForge 1.21 下注册表更早可用）。
		// 移到 FMLCommonSetupEvent（所有注册完成后）再触发，此时注册表已就位。
		if (!triggerRegistered) {
			triggerRegistered = true;
			AeroAdvancements.init();
			AeroAdvancementTriggers.register();
		}
	}

		@SubscribeEvent
		public static void loadConfig(final ModConfigEvent.Loading event) {
			for (final ConfigBase config : NeoForgeAeroConfigService.CONFIGS.values()) {
				if (config.specification == event.getConfig().getSpec()) {
					config.onLoad();
				}
			}
		}

		@SubscribeEvent
		public static void reloadConfig(final ModConfigEvent.Reloading event) {
			for (final ConfigBase config : NeoForgeAeroConfigService.CONFIGS.values()) {
				if (config.specification == event.getConfig().getSpec()) {
					config.onReload();
				}
			}
		}
	}
}
