package dev.simulated_team.simulated.index;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.simibubi.create.foundation.render.RenderTypes;
import dev.simulated_team.simulated.Simulated;
import foundry.veil.api.client.render.VeilRenderBridge;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Function;

public final class SimRenderTypes extends RenderStateShard {

    private static final RenderType STAFF_OVERLAY = RenderType.create(
            Simulated.MOD_ID + ":staff_overlay/staff_overlay",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLE_STRIP,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    // 物理法杖光束“特调辉光”：用自定义 staff_overlay 着色器（叠加混合 + 提亮微冷调），
                    // 配合 ADDITIVE_TRANSPARENCY 让重叠处更亮，形成发光核心。
                    .setShaderState(VeilRenderBridge.shaderState(Simulated.path("staff_overlay/staff_overlay")))
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(CULL)
                    .createCompositeState(false)
    );
    // [BUG-30·第二十轮] 用户明确否决穿墙，撤掉 NO_DEPTH_TEST，回到与源版 1.21.1 完全一致的深度测试（默认 LEQUAL）。绝不穿墙。
    //
    // [BUG-30·第二十三轮·主修复] 补上 COLOR_WRITE（只写颜色、不写深度）。
    //   真因链：RenderType.CompositeState 的写掩码默认是 COLOR_DEPTH_WRITE ——「颜色和深度一起写」。
    //   源版 1.21.1 的激光是「十字薄鳍」，四片鳍互不遮挡、且完全没有端面，写不写深度都看不出区别；
    //   而端口第十七轮为了消除用户反馈的「缝隙」，把几何换成了实心方柱，于是凭空多出两个端面。
    //   其中【远端面的 alpha 恒等于 0】（着色器 endTaper 在远端把 alpha 淡到 0），
    //   但 GL 的深度写入【与 alpha 完全无关】——它照样把自己的深度值刷进深度缓冲。
    //   结果：从正对发射口 / 侧面看过去时，这张「完全透明却抢先占住深度」的面
    //   把它后面本该出颜色的近端面与侧壁一并顶掉了 LEQUAL 测试 → 光束整体消失；
    //   只有从背光方向看（透明端面落在光束背后）才侥幸躲开 → 正好就是用户描述的
    //  「正对着、侧对着看不见，只有背着能看见」。这是端口独有的问题，源版几何里不存在。
    //
    //   COLOR_WRITE 与用户明确否决的 NO_DEPTH_TEST 是两回事，务必分清：
    //     · NO_DEPTH_TEST = 不做深度【测试】→ 墙也挡不住 → 穿墙（已否决，绝不使用）；
    //     · COLOR_WRITE   = 照常做深度测试，只是自己不【写】深度 → 墙照旧挡住激光，
    //                       但激光不再遮挡任何东西（包括它自己的另外几个面）。
    //   同一文件里的法杖光束 STAFF_OVERLAY 用的就是 COLOR_WRITE，是半透明光束的标准写法。
    // [BUG-30·第二十五轮·主修复] 正式激光改用【原版 POSITION_COLOR 着色器】，彻底弃用自定义着色器。
    //
    // 为什么弃用 laser/laser 自定义着色器（实证，非推测）：
    //   源版那份 GLSL 是给 Veil 的延迟渲染管线写的，端口虽已改写成 1.20.1 core 格式
    //   （assets/simulated/shaders/core/laser/laser.*），但「Veil 在本端口是空壳、没有延迟管线」，
    //   它编译虽能过（本轮 latest.log 里 simulated:laser/laser 零 WARN、加载成功），
    //   渲染却不可靠 —— 实测激光在所有视角都不可见。
    //   同模组 spring 也走自定义着色器却未见异常，说明「自定义」本身并非不可渲染，
    //   更可能是 laser 这份 GLSL 仍隐含依赖了 Veil 专有语义；与其逐个排查它依赖了哪些 hook，
    //   不如直接用最标准的原版着色器，一劳永逸绕开整类问题。
    //   注：第二十三 / 二十四轮的雾 uniform 假设已被用户实测否决（改后「依旧」），故不再纠缠着色器内部。
    //
    // 原版 position_color 恰好是激光的理想着色器：无雾、无多余 uniform，
    // 且片元里自带 `if (color.a == 0.0) discard;`，正好用来实现远端渐隐（远端顶点 alpha 给 0 即可）。
    // 源版靠 UV 传 lengthData 到片元做逐像素渐隐，这一步完全可以搬到 CPU 侧写进顶点 alpha，
    // 顶点色本来就是线性插值，肉眼效果一致。
    //
    // [BUG-30·第二十八轮·主修复] LASER 改回 COLOR_DEPTH_WRITE（写颜色 + 写深度）。
    //   第二十三轮为消除「端面自遮挡」加的 COLOR_WRITE（不写深度）带来致命副作用：激光只测深度、自己不写深度，
    //   一旦被发射器方块（不透明、先画、已写深度）在深度上剔除，近距离整条消失 —— 这正是用户实测
    //   「视角里有指示器就看不到光、没指示器就看到，且近距离看不到 / 远距离能看到」的教科书式深度遮挡。
    //   改回写深度（与 vanilla translucent 一致）后排序稳健；实心方柱写深度的自遮挡只丢背面壁，正面观感无碍。
    //   另见 AbstractLaserRenderer.transformPose：激光起点外移到方块表面外约 0.17 格，避开与方块表面的 z-fighting。
    //   深度测试照旧开启（绝不穿墙）+ NO_CULL 不变。
    private static final RenderType LASER = RenderType.create(
            Simulated.MOD_ID + ":laser",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    // [BUG-30·第三十六+轮·回退 ADDITIVE] 上一轮用 ADDITIVE 解决「玻璃渣」，但 ADDITIVE 颜色相加，
                    //   视线穿过方箱近+远两面恒叠加 ⇒ 整体过亮，且近端面亮度不随距离衰减 ⇒「亮度都一致、不渐隐」。
                    //   源版那种「越远越暗直至消失」只能靠半透明（TRANSLUCENT）混合 + 远端 alpha→0 实现：
                    //   半透明下远端面 alpha=0 时会被 position_color.fsh 的 discard 整片吞掉，自然消失。
                    //   故改回 TRANSLUCENT；保留 COLOR_WRITE（只写颜色、不写深度）：
                    //     ① 深度【测试】仍开着 ⇒ 墙照挡激光、绝不穿墙；
                    //     ② 不写深度 ⇒ 方箱 6 个面之间不互写深度、无 z-fighting、无「只有背着能看见」。
                    //   几何已是正方形实心箱体（任何角度闭合、绝无中缝），半透明下即「方形半透明光棒、远端渐隐」，
                    //   观感等同源版减去 bloom 的方形激光，且绝不依赖 Veil、不冲突用户装的性能/着色器模组。
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );
    // [BUG-30·光影适配] 启用光影包（Oculus）后，原版的 sortOnUpload 半透明排序
    // 会与光影自身的半透明处理冲突，导致实心箱体的 12 个三角出现缝隙或深度穿插。
    // 光影适配版禁用原版排序（sortOnUpload=false），让光影包自己排序；几何也换成
    // 空心方柱（只有 4 个侧面、无顶底端面），大幅减少参与排序的面数，避免端面 alpha=0
    // 在光影着色器下产生异常。
    private static final RenderType LASER_SHADERS = RenderType.create(
            Simulated.MOD_ID + ":laser_shaders",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            true,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );
    // [BUG-30·诊断专用] 与 LASER 完全一致，只额外关掉深度测试。
    // 只有 /laserdbg 3 或 /laserdbg 6 会用到它；它会穿墙，仅用于当场判定
    //「光束到底是被遮挡吃掉了，还是压根没画上去」，定位完成后连同诊断设施一起删除。
    private static final RenderType LASER_NO_DEPTH = RenderType.create(
            Simulated.MOD_ID + ":laser_dbg_nodepth",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );
    // [BUG-30·第二十五轮·反向对照] 老配置：POSITION_TEX_COLOR + Veil 自定义着色器。
    // 现在正式路径已经不走它了，把它降级为 /laserdbg 7 的【反向】对照：
    //   · 正常游玩（模式 0）看得见，切到模式 7 就看不见 ⇒ 实锤问题出在自定义着色器 / 渲染桥，本轮修复成立；
    //   · 两者都看不见 ⇒ 着色器无罪，问题在几何 / 矩阵 / 方块实体调用链，下一轮直奔那里。
    private static final RenderType LASER_VEIL_SHADER = RenderType.create(
            Simulated.MOD_ID + ":laser_dbg_veil",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(VeilRenderBridge.shaderState(Simulated.path("laser/laser")))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );
    private static final RenderType LENS = RenderType.create(
            Simulated.MOD_ID + ":laser_pointer_lens",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            256,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setLightmapState(LIGHTMAP)
                    .setShaderState(RENDERTYPE_CUTOUT_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED)
                    .setShaderState(VeilRenderBridge.shaderState(Simulated.path("laser_pointer/lens")))
                    .createCompositeState(true));

    private static final VertexFormat SPRING_FORMAT = new VertexFormat(
            ImmutableMap.of(
                    "Position", DefaultVertexFormat.ELEMENT_POSITION,
                    "Stress", DefaultVertexFormat.ELEMENT_COLOR,
                    "UV0", DefaultVertexFormat.ELEMENT_UV0,
                    "UV2", DefaultVertexFormat.ELEMENT_UV2,
                    "Normal", DefaultVertexFormat.ELEMENT_NORMAL,
                    "Padding", DefaultVertexFormat.ELEMENT_PADDING));

    private static final RenderType LOCK = RenderType.create(
            Simulated.MOD_ID + ":lock",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            256,
            true,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_TEX_LIGHTMAP_SHADER)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setTextureState(new RenderStateShard.TextureStateShard(Simulated.path("textures/gui/lock.png"), false, false))
                    .createCompositeState(true));

    private static final RenderType ROPE = RenderType.create(
            Simulated.MOD_ID + ":rope",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            256,
            true,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(VeilRenderBridge.shaderState(Simulated.path("rope/rope")))
                    .setTextureState(new RenderStateShard.TextureStateShard(Simulated.path("textures/block/rope_particle.png"), false, false))
                    .setLightmapState(LIGHTMAP)
                    .setCullState(CULL)
                    .createCompositeState(false));

    private static final Function<ResourceLocation, RenderType> SPRING = Util.memoize((ResourceLocation texture) -> {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(VeilRenderBridge.shaderState(Simulated.path("spring/spring")))
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .createCompositeState(true);
        return RenderType.create("spring", SPRING_FORMAT, VertexFormat.Mode.QUADS, 256, true, false, state);
    });

    private SimRenderTypes() {
        super(null, null, null);
    }

    public static RenderType staffOverlay() {
        return STAFF_OVERLAY;
    }

    public static RenderType laser() {
        return laser(false);
    }

    /**
     * 获取当前应使用的激光渲染类型。
     *
     * @param shadersActive 是否已启用光影包；光影下半透明排序由光影自己处理，使用专用的
     *                      {@link #LASER_SHADERS} 以减少缝隙。
     */
    public static RenderType laser(final boolean shadersActive) {
        return shadersActive ? LASER_SHADERS : LASER;
    }

    /** [BUG-30 诊断专用] 关掉深度测试的激光渲染类型，仅供 /laserdbg 3、/laserdbg 6 使用。会穿墙。 */
    public static RenderType laserNoDepth() {
        return LASER_NO_DEPTH;
    }

    /** [BUG-30 反向对照专用] 用 Veil 自定义着色器绘制的老配置激光，仅供 /laserdbg 7 使用。 */
    public static RenderType laserVeilShader() {
        return LASER_VEIL_SHADER;
    }

    public static RenderType lens() {
        return LENS;
    }

    public static RenderType lock() {
        return LOCK;
    }

    public static RenderType rope() {
        return ROPE;
    }

    public static RenderType itemGlowingSolid(boolean shadersActive) {
        return shadersActive ? Sheets.solidBlockSheet() : RenderTypes.itemGlowingSolid();
    }

    public static RenderType itemGlowingTranslucent(boolean shadersActive) {
        return shadersActive ? Sheets.translucentCullBlockSheet() : RenderTypes.itemGlowingTranslucent();
    }

    public static RenderType spring(final ResourceLocation texture) {
        return SPRING.apply(texture);
    }
}
