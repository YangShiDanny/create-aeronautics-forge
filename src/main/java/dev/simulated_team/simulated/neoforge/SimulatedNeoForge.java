package dev.simulated_team.simulated.neoforge;
import net.minecraftforge.common.MinecraftForge;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.index.SimRegistries;
import dev.simulated_team.simulated.index.neoforge.NeoForgeSimStats;
import dev.simulated_team.simulated.index.neoforge.SimNeoForgeRecipeTypes;
import dev.simulated_team.simulated.index.neoforge.SimParticleTypesImpl;
import dev.simulated_team.simulated.neoforge.events.SimNeoForgeCommonEvents;
import dev.simulated_team.simulated.neoforge.service.NeoForgeSimConfigService;
import dev.simulated_team.simulated.neoforge.service.NeoForgeSimEntityDataSerialization;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.NewRegistryEvent;

@Mod(Simulated.MOD_ID)
public final class SimulatedNeoForge {
    public static final CreativeModeTab TAB = CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + Simulated.MOD_ID + ".group"))
            .icon(() -> new ItemStack(SimBlocks.PHYSICS_ASSEMBLER.get()))
            .build();

    public SimulatedNeoForge() {
        // [1.20.1 移植·修复] 合并 jar 多 @Mod 下 FMLJavaModLoadingContext 取到的总线
        // 并非本模组真正接收 RegisterEvent 的总线，导致 registrate 注册监听全部不触发。
        // 按模组 ID 取 Forge 专属事件总线（带安全回退）。
        IEventBus modBus;
        try {
            modBus = ((FMLModContainer) ModList.get().getModContainerById(Simulated.MOD_ID).orElseThrow()).getEventBus();
        } catch (final Exception e) {
            modBus = FMLJavaModLoadingContext.get().getModEventBus();
        }
        ModContainer modContainer = net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();
        // [1.20.1 port] Create custom registries (NeoForge 1.21 did this implicitly).
        // TARGET 数据组件依赖的 NAVIGATION_TARGET 注册表在此事件的 onFill 内被赋值，
        // 并紧接着由 SimRegistries.createRegistries 触发 SimDataComponents.register() 惰性创建。
        modBus.addListener(SimRegistries::createRegistries);
        // deferred register tab
        final DeferredRegister<CreativeModeTab> tabRegister = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Simulated.MOD_ID);
        tabRegister.register("main_tab", () -> TAB);
        tabRegister.register(modBus);

        MinecraftForge.EVENT_BUS.register(SimNeoForgeCommonEvents.class);
        modBus.register(SimNeoForgeCommonEvents.ModBusEvents.class);

        SimParticleTypesImpl.register(modBus);
        SimNeoForgeRecipeTypes.register(modBus);

        NeoForgeSimEntityDataSerialization.register(modBus);
        Simulated.getRegistrate().registerEventListeners(modBus);

        NeoForgeSimStats.register(modBus);

        // [1.20.1 port] ComputerCraft peripheral service disabled (computercraft API not available)
        // if (ModList.get().isLoaded("computercraft")) {
        //     modBus.register(NeoForgeSimPeripheralService.class);
        // }

        Simulated.init();
        NeoForgeSimConfigService.register(ModLoadingContext.get(), modContainer);

        // [1.20.1 移植·修复] Forge 1.20.1 每个 modId 只能有一个 @Mod 类。
        // 客户端入口 SimulatedNeoForgeClient 已去掉 @Mod，改由本类在客户端侧主动实例化。
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> new SimulatedNeoForgeClient());
    }
}
