package dev.ryanhcode.sable.forge.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Embeddium / Sodium 兼容绘制处理器。
 * 当 Sodium 接管原版 {@code LevelRenderer.renderChunkLayer} 后，
 * sable 在原版方法上的注入点不再被触发。本处理器监听 Forge
 * {@link RenderLevelStageEvent}（地形渲染各阶段都会 dispatch），
 * 在对应阶段用原版着色器重新绘制子层级地形。
 */
@OnlyIn(Dist.CLIENT)
public final class SubLevelRenderStageHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 一次性诊断去重集合：同一条诊断信息全程只打印一次，避免刷屏。 */
    private static final Set<String> DIAGNOSED = new HashSet<>();

    private static final Map<RenderLevelStageEvent.Stage, RenderType> STAGE_TO_TYPE = new HashMap<>();

    static {
        STAGE_TO_TYPE.put(RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS, RenderType.solid());
        STAGE_TO_TYPE.put(RenderLevelStageEvent.Stage.AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS, RenderType.cutoutMipped());
        STAGE_TO_TYPE.put(RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS, RenderType.cutout());
        STAGE_TO_TYPE.put(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, RenderType.translucent());
        STAGE_TO_TYPE.put(RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS, RenderType.tripwire());
    }

    private SubLevelRenderStageHandler() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(final RenderLevelStageEvent event) {
        // [手机端优化·S1 阶段二] GL 上下文此时必定就绪，在这里做一次性的 GPU 档位判定
        // （原生 GPU / 翻译层）。方法内部有 glResolved 缓存，重复调用会立即返回，开销可忽略。
        dev.ryanhcode.sable.MobilePlatform.detectGlTier();

        final RenderLevelStageEvent.Stage stage = event.getStage();
        final RenderType renderType = STAGE_TO_TYPE.get(stage);

        // 诊断：确认 Embeddium 究竟派发了哪些渲染阶段（每个阶段只打印一次）。
        // 若某个地形阶段从未出现在日志里，说明该阶段的子层级方块根本没机会绘制。
        if (DIAGNOSED.add("stage:" + stage)) {
            LOGGER.info("[Sable诊断] 收到渲染阶段 {}，是否为子层级地形阶段 = {}", stage, renderType != null);
        }

        if (renderType == null) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        final ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        final Iterable<ClientSubLevel> sublevels = container.getAllSubLevels();
        final Iterator<ClientSubLevel> iterator = sublevels.iterator();
        if (!iterator.hasNext()) {
            return;
        }

        final PoseStack poseStack = event.getPoseStack();
        final Matrix4f projection = event.getProjectionMatrix();
        final Camera camera = event.getCamera();
        final Vec3 camPos = camera.getPosition();
        final float partialTick = event.getPartialTick();
        final Matrix4f modelView = new Matrix4f(poseStack.last().pose());

        renderType.setupRenderState();
        ShaderInstance shader = RenderSystem.getShader();

        // ==============================================================================
        // 跳过 Oculus / Iris 的阴影通道（shadow pass）。
        //
        // 启用光影包后，Oculus 会从太阳视角把整个世界重渲染一遍生成阴影贴图，
        // 这个通道同样会派发 RenderLevelStageEvent，着色器名形如 shadow_terrain_cutout。
        // 但事件携带的相机位置（event.getCamera()）始终是**玩家相机**，
        // 与阴影视角的矩阵并不匹配 —— 我们据此绘制会把子层级画到阴影贴图的错误位置，
        // 污染阴影结果，表现为「从某些朝向看方块整块发黑/看不见，换个方向又正常」。
        //
        // 子层级暂不参与阴影投射，直接跳过该通道（宁可没影子，也不能糊黑）。
        // ==============================================================================
        if (shader != null) {
            final String shaderName = shader.getName();
            if (shaderName != null && shaderName.contains("shadow")) {
                if (DIAGNOSED.add("shadowSkip:" + stage + ":" + shaderName)) {
                    LOGGER.info("[Sable诊断] 阶段 {} 命中光影阴影通道（着色器={}），已跳过绘制", stage, shaderName);
                }
                renderType.clearRenderState();
                return;
            }
        }

        // ==============================================================================
        // 【已废弃方案，留档警示】曾经在这里把着色器强制换回原版 rendertype_* ，
        // 理由是「Oculus 的 terrain_* 着色器要 Iris 扩展顶点属性，而子层级网格是原版
        // BLOCK 格式，切线缺失导致部分朝向的面算成全黑」。
        //
        // 实测结论：该推断**错误**，且换回原版着色器有害。
        //   * 不开光影时（着色器本来就是原版 rendertype_*，根本不触发换回），
        //     方向性隐形照旧 —— 证明根因与着色器无关；
        //   * 开光影时换回原版着色器，画出来的东西写不进 Iris 的 gbuffer 流程，
        //     结果比不换更糟（几乎全部不可见）。
        //
        // 因此这里不再做任何着色器替换，光影包给什么就用什么。
        // 千万不要再把这段逻辑加回来。
        // ==============================================================================

        if (shader != null) {
            // ==========================================================================
            // 完整复刻原版 LevelRenderer.renderChunkLayer 的着色器准备流程。
            //
            // Embeddium 用 @Overwrite 整体替换了原版 renderChunkLayer，
            // 下面这一整套 uniform 设置在它的实现里全部被跳过（它走 Sodium 自己的
            // 着色器体系）。如果我们只补矩阵，其余 uniform 会沿用上一批次的残留值：
            //   * FOG_START / FOG_END / FOG_COLOR 残留 —— 方块被雾整块吞掉；
            //   * COLOR_MODULATOR 残留 alpha=0 —— 方块彻底透明；
            //   * 光照方向残留 —— 某些朝向的面全黑；
            //   * Sampler 未绑定 —— 采样到错误纹理。
            // 这正是"部分结构隐形 / 只显示一面"的根因。
            //
            // 顺序铁律（照抄原版字节码）：所有 uniform 一律**只 set 不 upload**，
            // 最后由 apply() 统一 glUseProgram + 上传全部 uniform。
            // 在 apply() 之前调 upload() 时还没有活动着色器程序，
            // 会刷 GL_INVALID_OPERATION "No active program"。
            // ==========================================================================
            for (int slot = 0; slot < 12; slot++) {
                shader.setSampler("Sampler" + slot, RenderSystem.getShaderTexture(slot));
            }
            if (shader.MODEL_VIEW_MATRIX != null) {
                shader.MODEL_VIEW_MATRIX.set(modelView);
            }
            if (shader.PROJECTION_MATRIX != null) {
                shader.PROJECTION_MATRIX.set(projection);
            }
            if (shader.COLOR_MODULATOR != null) {
                shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
            }
            if (shader.GLINT_ALPHA != null) {
                shader.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
            }
            if (shader.FOG_START != null) {
                shader.FOG_START.set(RenderSystem.getShaderFogStart());
            }
            if (shader.FOG_END != null) {
                shader.FOG_END.set(RenderSystem.getShaderFogEnd());
            }
            if (shader.FOG_COLOR != null) {
                shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
            }
            if (shader.FOG_SHAPE != null) {
                shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
            }
            if (shader.TEXTURE_MATRIX != null) {
                shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
            }
            if (shader.GAME_TIME != null) {
                shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
            }
            RenderSystem.setupShaderLights(shader);

            // 统一绑定程序并上传全部 uniform。此后 upload() 才是合法的。
            shader.apply();

            // 诊断：每个阶段首次实际绘制时，打印子层级数量与关键着色器状态。
            // 雾区间若异常（例如 0~0 或极小值）就能直接印证"方块被雾吞掉"的推断。
            if (DIAGNOSED.add("draw:" + stage)) {
                int count = 0;
                for (final ClientSubLevel ignored : sublevels) {
                    count++;
                }
                final float[] modulator = RenderSystem.getShaderColor();
                LOGGER.info("[Sable诊断] 阶段 {} 首次绘制：子层级数量={}，着色器={}，雾区间={}~{}，颜色调制=[{}, {}, {}, {}]",
                        stage, count, shader.getName(),
                        RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(),
                        modulator[0], modulator[1], modulator[2], modulator[3]);
            }

            SubLevelRenderDispatcher.get().renderSectionLayer(sublevels, renderType, shader,
                    camPos.x, camPos.y, camPos.z,
                    modelView, projection, partialTick);
            SubLevelRenderDispatcher.get().renderAfterSections(sublevels,
                    camPos.x, camPos.y, camPos.z,
                    modelView, projection, partialTick);

            // 与原版 renderChunkLayer 收尾一致：绘制完解绑着色器程序。
            shader.clear();
        }
        renderType.clearRenderState();
    }
}
