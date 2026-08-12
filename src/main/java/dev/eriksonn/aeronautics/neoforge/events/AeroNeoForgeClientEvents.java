package dev.eriksonn.aeronautics.neoforge.events;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.events.AeronauticsClientEvents;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import dev.eriksonn.aeronautics.index.client.AeroRenderTypes;
import dev.eriksonn.aeronautics.neoforge.content.fluids.AeroFluidType;
import dev.eriksonn.aeronautics.neoforge.index.AeroFluidsNeoForge;
import foundry.veil.forge.event.ForgeVeilRegisterBlockLayersEvent;
import foundry.veil.forge.event.ForgeVeilRegisterFixedBuffersEvent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

@Mod.EventBusSubscriber(modid = Aeronautics.MOD_ID, value = Dist.CLIENT)
public class AeroNeoForgeClientEvents {

    @SubscribeEvent
    public static void preClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        AeronauticsClientEvents.clientLevelTick(false);
    }

    @SubscribeEvent
    public static void postClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        AeronauticsClientEvents.clientLevelTick(true);
    }

    /**
     * [手机端优化·S1 阶段二] 手机端 GPU 档位判定的**主入口**。
     *
     * <p>阶段一（是否安卓）在启动期即可完成；阶段二需要 GL 上下文才能读
     * {@code GL_RENDERER}/{@code GL_VERSION} 区分「原生移动 GPU」与「gl4es/VirGL 翻译层」，
     * 因此挂在世界渲染事件上——此时 GL 上下文必定就绪且处于渲染线程。
     *
     * <p>本类是 {@code @Mod.EventBusSubscriber} 无条件注册的，不像
     * {@code SubLevelRenderStageHandler} 那样只在装了 Embeddium 时才注册，
     * 所以放在这里才能保证任何环境都能判定出档位。
     *
     * <p>{@code detectGlTier()} 内部有 {@code glResolved} 缓存，判定完成后每次调用
     * 只是一次布尔判断即返回，逐帧多次派发的开销可以忽略。
     */
    @SubscribeEvent
    public static void detectMobileGlTier(final RenderLevelStageEvent event) {
        dev.ryanhcode.sable.MobilePlatform.detectGlTier();
    }


    @Mod.EventBusSubscriber(modid = Aeronautics.MOD_ID, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void clientSetup(final FMLClientSetupEvent event) {
            // [1.20.1 移植] Forge 1.20.1 的 ItemBlockRenderTypes.getChunkRenderType 只认
            // 单参 setRenderLayer(block, RenderType) 注册的 TYPE_BY_BLOCK 映射，完全不查三参
            // setRenderLayer(block, ChunkRenderTypeSet) 注册的 BLOCK_RENDER_TYPES，且 ChunkRenderTypeSet
            // 的底层层数组是 private static final（不可扩展）。原 NeoForge 1.21 的
            // ChunkRenderTypeSet.of(...) + 反射改 final 字段方案在 1.20.1 不可行（且会导致
            // Mixin @Accessor 注入 final 字段崩溃）。
            // 故直接用单参把 levitite / pearlescent_levitite 方块注册为原生半透明层（translucent）：
            // 保证方块可见、不崩、半透明正确。levitite 自定义 RenderType 的专属 shader 辉光
            // 受 1.20.1 引擎限制可能弱化——若 Veil 通过全局 hook 按 Block 身份注入则可保留，进游戏验证。

            // 防御式：方块未注册时跳过渲染层设置，避免硬崩，保证游戏能启动。
            if (AeroBlocks.LEVITITE.isPresent())
                ItemBlockRenderTypes.setRenderLayer(AeroBlocks.LEVITITE.get(), RenderType.translucent());
            if (AeroBlocks.PEARLESCENT_LEVITITE.isPresent())
                ItemBlockRenderTypes.setRenderLayer(AeroBlocks.PEARLESCENT_LEVITITE.get(), RenderType.translucent());
        }

        @SubscribeEvent
        public static void registerRegisterStageEvent(final RenderLevelStageEvent.RegisterStageEvent event) {
            event.register(Aeronautics.path("levitite"), AeroRenderTypes.levitite());
            event.register(Aeronautics.path("levitite_ghosts"), AeroRenderTypes.levititeGhosts());
        }
    }
}
