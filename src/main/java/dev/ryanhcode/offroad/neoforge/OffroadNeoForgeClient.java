package dev.ryanhcode.offroad.neoforge;

import dev.ryanhcode.offroad.Offroad;
import dev.ryanhcode.offroad.OffroadClient;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory;

// [1.20.1 移植·修复] 去掉 @Mod：Forge 1.20.1 每个 modId 只能有一个 @Mod 类，
// 本客户端入口改由 OffroadNeoForge 在客户端侧通过 DistExecutor 主动实例化。
@OnlyIn(Dist.CLIENT)
public class OffroadNeoForgeClient {
	public OffroadNeoForgeClient() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		ModContainer container = net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();
		this.listenClientEvents(modBus);
		container.registerExtensionPoint(ConfigScreenFactory.class, () -> new ConfigScreenFactory((screen) -> new BaseConfigScreen(screen, Offroad.MOD_ID)));

		OffroadClient.init();
	}

	private void listenClientEvents(final IEventBus modBus) {

	}
}
