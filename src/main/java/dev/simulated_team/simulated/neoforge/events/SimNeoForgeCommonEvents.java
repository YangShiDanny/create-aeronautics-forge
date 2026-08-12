package dev.simulated_team.simulated.neoforge.events;


import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.command.SimCommand;
import dev.simulated_team.simulated.data.advancements.SimAdvancementTriggers;
import dev.simulated_team.simulated.data.advancements.SimAdvancements;
import dev.simulated_team.simulated.data.neoforge.SimProcessingRecipeGen;
import dev.simulated_team.simulated.events.SimulatedCommonClientEvents;
import dev.simulated_team.simulated.events.SimulatedCommonEvents;
import dev.simulated_team.simulated.index.SimArmInteractions;
import dev.simulated_team.simulated.index.SimSoundEvents;
import dev.simulated_team.simulated.index.SimTags;
import dev.simulated_team.simulated.index.neoforge.NeoForgeSimStats;
import dev.simulated_team.simulated.multiloader.energy.SingleBattery;
import dev.simulated_team.simulated.multiloader.energy.SingleBatteryWrapper;
import dev.simulated_team.simulated.multiloader.inventory.AbstractContainer;
import dev.simulated_team.simulated.multiloader.inventory.neoforge.ContainerWrapper;
import dev.simulated_team.simulated.multiloader.tanks.SingleTank;
import dev.simulated_team.simulated.multiloader.tanks.neoforge.SingleTankWrapper;
import dev.simulated_team.simulated.neoforge.service.NeoForgeSimConfigService;
import dev.simulated_team.simulated.neoforge.service.NeoForgeSimInventoryService;
import dev.simulated_team.simulated.util.hold_interaction.HoldInteractionManager;
import net.createmod.catnip.config.ConfigBase;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.RegisterEvent;

import java.util.concurrent.CompletableFuture;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.EventPriority;

@Mod.EventBusSubscriber(modid = Simulated.MOD_ID)
public class SimNeoForgeCommonEvents {

	@SubscribeEvent
	public static void loadChunk(final ChunkEvent.Load event) {
		SimulatedCommonEvents.onChunkLoad(event.getLevel(), event.getChunk(), event.isNewChunk());
	}

	@SubscribeEvent
	public static void playerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
		final Player player = event.getEntity();
		SimulatedCommonEvents.onPlayerLoggedIn(player);
	}

	@SubscribeEvent
	public static void registerCommands(final RegisterCommandsEvent event) {
		SimCommand.register(event.getDispatcher(), event.getBuildContext());
	}

	@SubscribeEvent
	public static void serverStopped(final ServerStoppedEvent event) {
		SimulatedCommonEvents.onServerStopped(event.getServer());
	}

	@SubscribeEvent
	public static void postServerTick(final TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		final MinecraftServer server = event.getServer();
		for (final ServerLevel level : server.getAllLevels()) {
			SimulatedCommonEvents.onServerTickEnd(level);
		}
	}

	@SubscribeEvent
	public static void syncDataPack(final OnDatapackSyncEvent event) {
		// [1.20.1 port] EndSeaPhysicsData.syncDataPacket disabled (end_sea excluded)
	}

	@SubscribeEvent
	public static void addReloadListeners(final AddReloadListenerEvent event) {
		// [1.20.1 port] EndSeaPhysicsData.ReloadListener disabled (end_sea excluded)
	}

@SubscribeEvent
	public static void useItemOnBlock(final PlayerInteractEvent.RightClickBlock event) {
		// [1.20.1 port] NeoForge 1.21 的 UsePhase 在 Forge 1.20.1 不存在；RightClickBlock 单次触发直接处理
		if (event.getLevel().isClientSide()) {
			if (event.getEntity() != null) {
				if (SimulatedCommonClientEvents.useItemOnBlockEvent(event.getLevel(), event.getEntity(), event.getItemStack(), event.getHand())) {
					event.setCanceled(true);
					event.setCancellationResult(InteractionResult.CONSUME);
				}
			}

			useItemOnBlockClient(event);
		}
	}

	@SubscribeEvent
	public static void rightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
		final InteractionResult result = SimulatedCommonEvents.rightClickBlock(event.getLevel(), event.getPos(), event.getEntity(), event.getItemStack());
		if (result != null) {
			event.setCancellationResult(result);
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onLivingEntityUseItem(final PlayerInteractEvent.RightClickItem event) {
		final LivingEntity entity = event.getEntity();
		if (entity instanceof final Player player && player.isLocalPlayer()) {
			SimulatedCommonClientEvents.useItemOnAirEvent(entity.level(), player, event.getItemStack(), event.getHand());
		}
	}


	private static void useItemOnBlockClient(final PlayerInteractEvent.RightClickBlock event) {
		// [1.20.1 port] PlayerInteractEvent 无 getPlayer()，用 getEntity()
		if (event.getEntity().isLocalPlayer() && HoldInteractionManager.isActive()) {
			event.setCanceled(true);
		}
	}

	@Mod.EventBusSubscriber(modid = Simulated.MOD_ID)
	public static class ModBusEvents {

		private static boolean triggerRegistered = false;

		@SubscribeEvent
		public static void register(final RegisterEvent event) {
			// [1.20.1 port] SimArmInteractions.init() 安全（仅注册交互点，不取未注册条目）。
			// SimAdvancements.register() / SimAdvancementTriggers.register() 会触发父类
			// SimAdvancements 的 <clinit> 立即 asStack() 取未注册条目 NPE，已移到 commonSetup。
			SimArmInteractions.init();
		}

		@SubscribeEvent
		public static void commonSetup(final FMLCommonSetupEvent event) {
			// [1.20.1 port] 所有 RegisterEvent 完成后再触发成就注册，避免 <clinit> 取未注册条目。
			if (!triggerRegistered) {
				triggerRegistered = true;
				SimAdvancements.register();
				SimAdvancementTriggers.register();
			}
		}

		@SubscribeEvent(priority = EventPriority.HIGHEST)
		public static void gatherDataHighPriority(final GatherDataEvent event) {
			SimTags.addGenerators();
		}

		@SubscribeEvent
		public static void gatherData(final GatherDataEvent event) {
			final DataGenerator generator = event.getGenerator();

			final PackOutput output = generator.getPackOutput();
			final CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

			if (event.includeClient()) {
				// [1.20.1 port] Forge 1.20.1 的 GatherDataEvent 无 addProvider，改走 generator
				generator.addProvider(true, SimSoundEvents.REGISTRY.getProvider(output));
			}

			generator.addProvider(event.includeServer(), new SimAdvancements(output, lookupProvider));
			generator.addProvider(event.includeServer(), SimProcessingRecipeGen.registerAll(output, lookupProvider));
		}

		@SubscribeEvent
	public static void registerCapabilities(final RegisterCapabilitiesEvent event) {
		// [1.20.1 移植] NeoForge 1.21 的 RegisterCapabilitiesEvent.registerBlockEntity(Capability, BlockEntityType, provider)
		// 在 Forge 1.20.1 不存在（1.20.1 仅有 register(Class)，且 ITEM_HANDLER/FLUID_HANDLER/ENERGY 为内建能力，无需注册类型）。
		// 能力（物品栏/流体罐/能量）的附加需由各 BlockEntity 覆写 getCapability 或后续统一 Mixin 处理；本方法暂留空，不影响编译与加载。
	}

	@SubscribeEvent
	public static void loadConfig(final ModConfigEvent.Loading event) {
			for (final ConfigBase config : NeoForgeSimConfigService.CONFIGS.values()) {
				if (config.specification == event.getConfig().getSpec()) {
					config.onLoad();
				}
			}

		}

		@SubscribeEvent
		public static void reloadConfig(final ModConfigEvent.Reloading event) {
			for (final ConfigBase config : NeoForgeSimConfigService.CONFIGS.values()) {
				if (config.specification == event.getConfig().getSpec()) {
					config.onReload();
				}
			}

		}

		@SubscribeEvent
		public static void postRegister(final FMLLoadCompleteEvent event) {
			NeoForgeSimStats.bootstrap();
		}
	}

}
