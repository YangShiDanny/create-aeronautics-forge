package dev.eriksonn.aeronautics.neoforge;
import net.minecraftforge.common.MinecraftForge;


import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.index.AeroArmorMaterials;
import dev.eriksonn.aeronautics.index.AeroRegistries;
import dev.eriksonn.aeronautics.index.client.AeroClientRegistries;
import dev.eriksonn.aeronautics.neoforge.events.AeroNeoForgeCommonEvents;
import dev.eriksonn.aeronautics.neoforge.index.AeroFluidsNeoForge;
import dev.eriksonn.aeronautics.neoforge.index.AeroParticleTypesNeoForge;
import dev.eriksonn.aeronautics.neoforge.service.NeoForgeAeroConfigService;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;

@Mod(Aeronautics.MOD_ID)
public class AeronauticsNeoForge {
    public AeronauticsNeoForge() {
        IEventBus fmlBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModContainer modContainer = net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();
        // [1.20.1 移植·修复] 合并 jar 多 @Mod 下 FMLJavaModLoadingContext 取到的 fmlBus
        // 并非本模组真正接收 RegisterEvent 的总线，导致 registrate 注册监听全部不触发
        // （方块/物品/流体全 0、自定义注册表不创建）。按模组 ID 取 Forge 专属总线。
        IEventBus modBus;
        try {
            modBus = ((FMLModContainer) ModList.get().getModContainerById(Aeronautics.MOD_ID).orElseThrow()).getEventBus();
        } catch (final Exception e) {
            modBus = fmlBus;
            Aeronautics.LOGGER.warn("按 ID 取总线失败，回退 FML 总线: {}", e.toString());
        }
        // [1.20.1 port] Create custom registries (NeoForge 1.21 did this implicitly).
        modBus.addListener(AeroRegistries::createRegistries);
        modBus.addListener(AeroClientRegistries::createRegistries);
        MinecraftForge.EVENT_BUS.register(AeroNeoForgeCommonEvents.class);
        modBus.register(AeroNeoForgeCommonEvents.ModBusEvents.class);

        AeroParticleTypesNeoForge.registerEventListeners(modBus);
        Aeronautics.getRegistrate().registerEventListeners(modBus);

        Aeronautics.init();
        AeroFluidsNeoForge.init();

        NeoForgeAeroConfigService.register(modContainer);

        // [1.20.1 移植·修复] Forge 1.20.1 每个 modId 只能有一个 @Mod 类
        // （NeoForge 1.21 允许同 modId 按 dist 拆分 common/client 两个 @Mod）。
        // 客户端专属入口 AeronauticsNeoForgeClient 已去掉 @Mod，改由本类在客户端侧主动实例化，
        // 避免两个 @Mod 争抢唯一实例导致本类（承载注册逻辑）不被构造、方块/物品/流体全 0。
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> new AeronauticsNeoForgeClient());
    }

}
