package dev.simulated_team.simulated.neoforge;
import net.minecraftforge.common.MinecraftForge;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.SimulatedClient;
import dev.simulated_team.simulated.index.SimRegistries;
import dev.simulated_team.simulated.neoforge.events.SimNeoForgeClientEvents;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory;

// [1.20.1 移植·修复] 去掉 @Mod：Forge 1.20.1 每个 modId 只能有一个 @Mod 类，
// 本客户端入口改由 SimulatedNeoForge 在客户端侧通过 DistExecutor 主动实例化。
@OnlyIn(Dist.CLIENT)
public class SimulatedNeoForgeClient {

	public SimulatedNeoForgeClient() {
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		ModContainer container = net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();
		// [1.20.1 移植·修复] 自定义注册表（SimRegistries::createRegistries）已由公共类 SimulatedNeoForge 注册，
		// 客户端不能重复注册，否则 NewRegistryEvent 建两次会崩。
		container.registerExtensionPoint(ConfigScreenFactory.class, () -> new ConfigScreenFactory((screen) -> new BaseConfigScreen(screen, Simulated.MOD_ID)));

		MinecraftForge.EVENT_BUS.register(SimNeoForgeClientEvents.class);
		modEventBus.register(SimNeoForgeClientEvents.ModBusEvents.class);
		SimulatedClient.PLUNGER_LAUNCHER_RENDER_HANDLER.registerListeners(MinecraftForge.EVENT_BUS);

		SimulatedClient.init();
	}

}