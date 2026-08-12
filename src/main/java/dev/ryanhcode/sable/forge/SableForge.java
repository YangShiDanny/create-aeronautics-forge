package dev.ryanhcode.sable.forge;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableCommonEvents;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.command.SableCommand;
import dev.ryanhcode.sable.command.argument.SubLevelSelectorModifiers;
import dev.ryanhcode.sable.forge.platform.SableChunkDataProvider;
import dev.ryanhcode.sable.index.SableAttributes;
import dev.ryanhcode.sable.network.tcp.SableTCPPackets;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.CrashReportCallables;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import dev.ryanhcode.sable.util.ForgeConfigRegistrar;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.registries.DeferredRegister;

@Mod(Sable.MOD_ID)
public final class SableForge {

    public SableForge() {
        try {
            // [1.20.1 移植·修复] 合并 jar 多 @Mod 下 FMLJavaModLoadingContext 取到的总线
            // 并非本模组真正接收 RegisterEvent 的总线，导致 DeferredRegister / Sable.init 的
            // 注册监听全部不触发（方块/物品/流体全 0）。按模组 ID 取 Forge 专属事件总线。
            IEventBus modBus;
            try {
                modBus = ((FMLModContainer) ModList.get().getModContainerById(Sable.MOD_ID).orElseThrow()).getEventBus();
            } catch (final Exception e) {
                modBus = FMLJavaModLoadingContext.get().getModEventBus();
                Sable.LOGGER.warn("sable 按 ID 取总线失败，回退 FML 总线: {}", e.toString());
            }
            final IEventBus neoBus = MinecraftForge.EVENT_BUS;

            // 先把所有事件监听（含命令注册）挂上，确保即使后续步骤抛异常，
            // /sable 命令与区块能力也已在事件总线上，不会被构造器中断连带吞掉。
            neoBus.addListener(this::registerCommand);
            neoBus.addListener(this::registerReloadListeners);
            modBus.addListener(this::serverSetup);
            neoBus.addListener(this::syncDataPack);
            neoBus.addListener(this::onTagsUpdated);
            // AttachCapabilitiesEvent<LevelChunk> 是泛型事件(继承 GenericEvent<LevelChunk>)，
            // Forge 1.20.1 的 eventbus 6.2.33 禁止用 addListener 注册泛型事件，
            // 必须用 addGenericListener 并显式给出泛型类型 LevelChunk.class。
            MinecraftForge.EVENT_BUS.addGenericListener(LevelChunk.class, this::attachChunkCapabilities);

            SubLevelSelectorModifiers.registerModifiers();

            // 属性注册：NeoForge 把属性移入原版 BuiltInRegistries.ATTRIBUTE，
            // Forge 1.20.1 属性仍在 ForgeRegistries.ATTRIBUTES。
            final DeferredRegister<Attribute> attributes = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, Sable.MOD_ID);
            SableAttributes.PUNCH_STRENGTH = attributes.register(SableAttributes.PUNCH_STRENGTH_NAME, () -> SableAttributes.PUNCH_STRENGTH_ATTRIBUTE);
            SableAttributes.PUNCH_COOLDOWN = attributes.register(SableAttributes.PUNCH_COOLDOWN_NAME, () -> SableAttributes.PUNCH_COOLDOWN_ATTRIBUTE);
            attributes.register(modBus);

            ForgeConfigRegistrar.register(ModConfig.Type.COMMON, SableConfig.SPEC);
            // 客户端配置：在 @Mod 构造器里注册 CLIENT 类型，客户端(含单人集成服务端)
            // 会加载它，使 SableClientConfig 的字段被真实填充；否则 .get() 解引用 null 崩。
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            ForgeConfigRegistrar.register(ModConfig.Type.CLIENT, SableClientConfig.SPEC);
        }

            CrashReportCallables.registerCrashCallable("Sable", Sable::getCrashHeader);

            // Sable.init() 跨加载器移植后任一子步骤（网络/标签/力组注册）可能抛异常。
            // 包住以便把根因打到日志（"Sable init failed!"），同时不阻断上面已注册的监听。
            try {
                Sable.init();
            } catch (final Throwable t) {
                Sable.LOGGER.error("Sable init failed!", t);
            }
        } catch (final Throwable t) {
            // 构造器任一步（含上面 try 之外的步骤）抛错都会被这里捕获并打印完整栈。
            Sable.LOGGER.error("SableForge construction failed!", t);
        }
    }

    private void attachChunkCapabilities(final AttachCapabilitiesEvent<LevelChunk> event) {
        final LevelChunk chunk = event.getObject();
        event.addCapability(new ResourceLocation(Sable.MOD_ID, "chunk_data"), new SableChunkDataProvider());
    }

    public void registerReloadListeners(final AddReloadListenerEvent event) {
        event.addListener(PhysicsBlockPropertiesDefinitionLoader.INSTANCE);
        event.addListener(DimensionPhysicsData.ReloadListener.INSTANCE);
        event.addListener(FloatingBlockMaterialDataHandler.ReloadListener.INSTANCE);
    }

    private void serverSetup(final FMLCommonSetupEvent event) {
        SableAttributes.register();
    }

    private void registerCommand(final RegisterCommandsEvent event) {
        SableCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private void syncDataPack(final OnDatapackSyncEvent event) {
        SableCommonEvents.syncDataPacket(packet -> event.getPlayers().forEach(player -> SableTCPPackets.sendToPlayer(player, packet)));
    }

    private void onTagsUpdated(final TagsUpdatedEvent event) {
        // [1.20.1 移植修正] 物理方块属性依赖方块标签选择器，必须等标签就绪后再应用，
        // 否则 getTag() 全为空、属性写不进、物理系统失效、装配子关卡时物理计算拿到空属性而崩溃。
        // TagsUpdatedEvent 在标签真正加载完成后触发；shouldUpdateStaticData() 保证单人集成服务端
        // 只在服务端数据加载那次应用（避免客户端/服务端双写）。
        if (event.shouldUpdateStaticData()) {
            PhysicsBlockPropertiesDefinitionLoader.INSTANCE.applyAll();
        }
    }
}
