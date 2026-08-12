package dev.eriksonn.aeronautics.neoforge;
import net.minecraftforge.common.MinecraftForge;

import com.tterrag.registrate.util.OneTimeEventReceiver;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.AeronauticsClient;
import dev.eriksonn.aeronautics.events.AeronauticsClientEvents;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import dev.eriksonn.aeronautics.index.AeroRegistries;
import dev.eriksonn.aeronautics.index.client.AeroClientRegistries;
import dev.eriksonn.aeronautics.index.client.AeroRenderTypes;
import dev.eriksonn.aeronautics.neoforge.events.AeroNeoForgeClientEvents;
import dev.ryanhcode.sable.forge.compat.EmbeddiumCompat;
import dev.ryanhcode.sable.forge.debug.SableRenderDebug;
import dev.ryanhcode.sable.forge.event.SubLevelRenderStageHandler;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.NewRegistryEvent;

import java.util.Set;
import java.util.stream.Collectors;
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory;

// [1.20.1 移植·修复] 去掉 @Mod：Forge 1.20.1 每个 modId 只能有一个 @Mod 类，
// 本客户端入口改由 AeronauticsNeoForge 在客户端侧通过 DistExecutor 主动实例化。
@OnlyIn(Dist.CLIENT)
public class AeronauticsNeoForgeClient {
	public AeronauticsNeoForgeClient() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		ModContainer container = net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();
		// [1.20.1 移植·修复] 自定义注册表（AeroRegistries/AeroClientRegistries::createRegistries）
		// 已由公共类 AeronauticsNeoForge 注册，客户端不能重复注册，否则 NewRegistryEvent 建两次会崩。
		MinecraftForge.EVENT_BUS.register(AeroNeoForgeClientEvents.class);
		// Embeddium / Sodium 接管原版地形渲染后，子层级改由 RenderLevelStageEvent 绘制。
		if (EmbeddiumCompat.isLoaded()) {
			MinecraftForge.EVENT_BUS.register(SubLevelRenderStageHandler.class);
		}
		// BUG-28 现场诊断指令 /sabledbg，与是否安装 Embeddium 无关，必须无条件注册。
		MinecraftForge.EVENT_BUS.register(SableRenderDebug.class);
		modBus.register(AeroNeoForgeClientEvents.ModBusEvents.class);
		container.registerExtensionPoint(ConfigScreenFactory.class, () -> new ConfigScreenFactory((screen) -> new BaseConfigScreen(screen, Aeronautics.MOD_ID)));

		AeronauticsClient.init();
	}
}
