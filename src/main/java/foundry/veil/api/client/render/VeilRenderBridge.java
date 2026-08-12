package foundry.veil.api.client.render;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.simulated_team.simulated.Simulated;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class VeilRenderBridge {

    private VeilRenderBridge() {}

    // 已通过 RegisterShadersEvent 注册的着色器实例，按 ResourceLocation 索引
    private static final Map<ResourceLocation, ShaderInstance> SHADERS = new HashMap<>();

    // [1.20.1 port] Veil 的 ShaderProgram 系统在 1.20.1 下是 no-op 空壳
    // （VeilRenderSystem.setShader 直接 return null），Veil 专有格式的 GLSL
    // （#include veil:fog、fog_distance、minecraft_sample_lightmap、block_brightness、
    // linear_fog、texelFetch 等）原版 ShaderInstance 完全无法编译。
    // 这里复用 Mojang 原版自带的等价着色器（资源包内必然可编译），仅把自定义 id
    // 映射到原版 shader 实例，渲染效果朴素但游戏可正常加载、不崩。
    private static final ResourceLocation VANILLA_TEX_COLOR = new ResourceLocation("minecraft", "position_tex_color");
    // [1.20.1 port] BLOCK 格式（6 元素：Position/Color/UV0/UV2/Normal/Padding）在原版 core 里
    // 没有同名着色器（position_tex_normal.json / block.json 在 1.20.1 均不存在）。
    // 方块渲染用的 rendertype_solid 正是声明 BLOCK 顶点格式的标准着色器，用它承载
    // BLOCK / SPRING 格式的自定义 id，加载期格式校验通过、不崩。
    private static final ResourceLocation VANILLA_BLOCK = new ResourceLocation("minecraft", "rendertype_solid");
    private static final ResourceLocation VANILLA_COLOR = new ResourceLocation("minecraft", "position_color");

    /**
     * 返回一个包裹“已注册 ShaderInstance”的着色器状态。
     * 真正的着色器在 RegisterShadersEvent 中按 json 加载（仿 Create 的 RenderTypes 做法）。
     * 若对应着色器未注册，这里返回 null 着色器会导致渲染异常，因此调用点务必只传入已注册的 id。
     */
    public static RenderStateShard.ShaderStateShard shaderState(final ResourceLocation id) {
        return new RenderStateShard.ShaderStateShard(() -> SHADERS.get(id));
    }

    @EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    private static class ShaderLoader {
        @SubscribeEvent
        public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
            ResourceProvider provider = event.getResourceProvider();
            // [1.20.1 port] 第二参数传 Mojang 原版自带着色器（资源包内必然可编译），
            // 仅把自定义 id 映射到原版 shader 实例；渲染效果朴素但游戏可加载。
            // [1.20.1 port 修正] 激光渲染类型不绑定纹理，绝不能映射到原版 position_tex_color
            // （它会采样上一批次残留的随机纹理 → 激光呈杂色断续碎片）。激光着色器逻辑简单
            // （顶点色 + 尾部渐隐 + 雾衰减，不采样纹理），已改写为 1.20.1 原版 core 格式：
            // assets/simulated/shaders/core/laser/laser.json/.vsh/.fsh，直接加载真自定义着色器。
            register(provider, event, Simulated.path("laser/laser"), DefaultVertexFormat.POSITION_TEX_COLOR, Simulated.path("laser/laser"));
            register(provider, event, Simulated.path("laser_pointer/lens"), DefaultVertexFormat.BLOCK, VANILLA_BLOCK);
            register(provider, event, Simulated.path("rope/rope"), DefaultVertexFormat.BLOCK, VANILLA_BLOCK);
            // [1.20.1 port 修正] 弹簧格式第 2 属性名为 Stress（非 Color），映射到原版 rendertype_solid 会属性错位
            // → 纹理坐标/顶点色混乱显示黑红色。着色器已改写为 1.20.1 core 格式
            // （assets/simulated/shaders/core/spring/），此处直接加载真自定义着色器（同激光做法）。
            register(provider, event, Simulated.path("spring/spring"), SPRING_FORMAT, Simulated.path("spring/spring"));
            register(provider, event, Aeronautics.path("levitite/levitite"), DefaultVertexFormat.BLOCK, VANILLA_BLOCK);
            register(provider, event, Simulated.path("redstone_accumulator/diode"), DefaultVertexFormat.BLOCK, VANILLA_BLOCK);
            // 物理法杖光束“特调辉光”：沿用原版 position_color 着色器（叠加混合 + ColorModulator 调制），
            // 顶点格式用 position_color（与 STAFF_OVERLAY 渲染类型一致）。Veil 自定义辉光在 1.20.1 下无法还原。
            register(provider, event, Simulated.path("staff_overlay/staff_overlay"), DefaultVertexFormat.POSITION_COLOR, VANILLA_COLOR);
        }

        private static void register(ResourceProvider provider, RegisterShadersEvent event, ResourceLocation id, VertexFormat format, ResourceLocation vanillaShader) throws IOException {
            event.registerShader(new ShaderInstance(provider, vanillaShader, format), shader -> SHADERS.put(id, shader));
        }
    }

    // 与 SimRenderTypes.SPRING_FORMAT 保持一致，否则弹簧自定义顶点属性对不上
    private static final VertexFormat SPRING_FORMAT = new VertexFormat(
            ImmutableMap.of(
                    "Position", DefaultVertexFormat.ELEMENT_POSITION,
                    "Stress", DefaultVertexFormat.ELEMENT_COLOR,
                    "UV0", DefaultVertexFormat.ELEMENT_UV0,
                    "UV2", DefaultVertexFormat.ELEMENT_UV2,
                    "Normal", DefaultVertexFormat.ELEMENT_NORMAL,
                    "Padding", DefaultVertexFormat.ELEMENT_PADDING));
}
