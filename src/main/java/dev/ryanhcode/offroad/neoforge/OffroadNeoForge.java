package dev.ryanhcode.offroad.neoforge;
import net.minecraftforge.common.MinecraftForge;


import dev.ryanhcode.offroad.Offroad;
import dev.ryanhcode.offroad.data.OffroadAdvancementTriggers;
import dev.ryanhcode.offroad.data.OffroadTags;
import dev.ryanhcode.offroad.events.OffroadCommonEvents;
import dev.ryanhcode.offroad.index.OffroadAdvancements;
import dev.ryanhcode.offroad.neoforge.data.OffroadDatagen;
import dev.ryanhcode.offroad.neoforge.service.NeoForgeOffroadConfigService;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.event.TickEvent.LevelTickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

@Mod(Offroad.MOD_ID)
public class OffroadNeoForge {
    public OffroadNeoForge() {
        // [1.20.1 移植·修复] 合并 jar 多 @Mod 下 FMLJavaModLoadingContext 取到的总线
        // 并非本模组真正接收 RegisterEvent 的总线，导致 registrate 注册监听全部不触发。
        // 按模组 ID 取 Forge 专属事件总线（带安全回退）。
        IEventBus modBus;
        try {
            modBus = ((FMLModContainer) ModList.get().getModContainerById(Offroad.MOD_ID).orElseThrow()).getEventBus();
        } catch (final Exception e) {
            modBus = FMLJavaModLoadingContext.get().getModEventBus();
        }
        ModContainer modContainer = net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();
        this.modBusRegistry(modBus);
        this.listenCommonEvents(MinecraftForge.EVENT_BUS);

        Offroad.init();

        NeoForgeOffroadConfigService.register(modContainer);

        // [1.20.1 移植·修复] Forge 1.20.1 每个 modId 只能有一个 @Mod 类。
        // 客户端入口 OffroadNeoForgeClient 已去掉 @Mod，改由本类在客户端侧主动实例化。
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> new OffroadNeoForgeClient());
    }

    private void listenCommonEvents(final IEventBus eventBus) {

    }

    private void modBusRegistry(final IEventBus modBus) {
        modBus.register(NeoForgeOffroadConfigService.class);

        modBus.addListener(OffroadNeoForge::init);
        modBus.addListener(EventPriority.HIGHEST, OffroadDatagen::gatherDataHighPriority);
        modBus.addListener(EventPriority.LOWEST, OffroadDatagen::gatherData);
        modBus.addListener(OffroadDatagen::registerEvent);
        MinecraftForge.EVENT_BUS.addListener((final LevelTickEvent event) -> {
            if (event.phase == net.minecraftforge.event.TickEvent.Phase.END)
                OffroadCommonEvents.tickLevelEvent(event.level);
        });

        modBus.addListener((final GatherDataEvent event) -> {
        OffroadTags.addGenerators();
        });

        Offroad.getRegistrate().registerEventListeners(modBus);
    }

    private static boolean triggerRegistered = false;

    private static void init(final FMLCommonSetupEvent event) {
        // [1.20.1 port] 原版在 RegisterEvent 期间调 OffroadAdvancements.init() 会触发
        // 父类 SimAdvancements 的 <clinit> 立即 asStack() 取未注册条目（NeoForge 1.21 下注册表更早可用）。
        // 移到 FMLCommonSetupEvent（所有注册完成后）再触发，此时注册表已就位。
        if (!triggerRegistered) {
            triggerRegistered = true;
            OffroadAdvancements.init();
            OffroadAdvancementTriggers.register();
        }
    }
}
