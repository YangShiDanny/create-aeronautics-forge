package dev.ryanhcode.sable.forge.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * BUG-28「物理化后方块隐形」现场诊断开关。
 *
 * <p>此前每验证一个假设就要重新构建一次模组，成本极高。这里把所有待验证的
 * 图形状态假设做成运行时可切换的模式，游戏里输入指令即可实时生效：
 *
 * <pre>
 *   /sabledbg          查看当前模式与全部模式说明
 *   /sabledbg 0        恢复原版行为（默认）
 *   /sabledbg 1        关闭背面剔除
 *   /sabledbg 2        关闭深度测试
 *   /sabledbg 3        关闭背面剔除 + 关闭深度测试
 *   /sabledbg 4        正面绕序改为顺时针（GL_CW）
 *   /sabledbg 5        只剔除正面（等价于「只画背面」）
 *   /sabledbg 6        关剔除 + 关深度 + 强制纯红（判定几何是否落到了屏幕上）
 *   /sabledbg 7        【关键】仅强制纯红，剔除与深度全部保持原样
 *   /sabledbg 8        关剔除 + 强制纯红（保留深度），与模式 7 成对照
 *   /sabledbg normal   开 / 关「方块实体法线矩阵同步」修复，用于当场判定
 *                      可动部件明暗异常是不是法线矩阵引起的
 *   /sabledbg rebuild  请求子关卡整段重烘焙
 * </pre>
 *
 * <p><b>注意模式 0~8 只覆盖 sable 自有的两条「方块网格」绘制路径</b>
 * （{@code VanillaSubLevelRenderDispatcher.renderAfterSections} 与
 * {@code VanillaChunkedSubLevelRenderData}）。齿轮、传动轴这类由
 * <b>方块实体渲染器</b>绘制的可动部件<b>不经过</b>这两条路径，
 * 所以它们在模式 7 / 8 下不会变红 —— 这本身就是一条有用的判据：
 * 「强制纯红对某个部件无效」⇒ 该部件走的是方块实体（或实体）渲染路径，
 * 应改用 {@code /sabledbg normal} 这类针对方块实体路径的开关来诊断。
 *
 * <p>各模式的判定意义：
 * <ul>
 *   <li><b>模式 1 有效</b>：面朝向被剔除 —— 绕序问题；</li>
 *   <li><b>模式 2 有效</b>：像素画上去了但深度测试没通过 —— 被别的东西遮挡，
 *       或深度值算错（矩阵 / 近远裁面问题）；</li>
 *   <li><b>模式 4 或 5 有效</b>：坐实网格烘焙阶段绕序翻转；</li>
 *   <li><b>模式 6 能看到红色块</b>：几何位置正确、光栅化正常，
 *       问题出在颜色 / 纹理 / 光照；</li>
 *   <li><b>模式 6 也毫无变化</b>：几何压根没进视口 ——
 *       模型视图 / 投影矩阵或 CHUNK_OFFSET 平移算错，顶点被裁剪掉了。</li>
 * </ul>
 *
 * <p><b>模式 7 / 8 是本轮新增的决定性判据。</b>此前的模式 6 把「关剔除」「关深度」
 * 「强制纯红」三件事捆在一起，一旦看到红色，根本分不清是哪一项起的作用，
 * 结论无法收敛。模式 7 只改颜色、其余一律不动，于是：
 * <ul>
 *   <li><b>模式 7 能看见红色方块（位置正确）</b> → 剔除、深度、矩阵、平移全部无罪，
 *       几何本来就画在了正确的像素上。问题只可能出在
 *       <b>颜色 / 纹理采样 / 光照</b>（例如整块被算成纯黑，肉眼当成了「隐形」）；</li>
 *   <li><b>模式 7 看不见、模式 8 才看得见</b> → 铁证：正面被背面剔除吃掉了；</li>
 *   <li><b>模式 7 与模式 8 都看不见</b> → 这些面压根没被烘焙进网格
 *       （邻接遮挡剔除在子关卡坐标系下判错，把外表面当成了内表面丢弃），
 *       此时任何图形状态开关都救不回来。</li>
 * </ul>
 *
 * <p>注意：这是纯客户端诊断设施，不参与任何游戏逻辑，定位完成后应整体删除。
 */
@OnlyIn(Dist.CLIENT)
public final class SableRenderDebug {

    /** 关闭诊断，完全走原版渲染状态。 */
    public static final int MODE_OFF = 0;
    /** 关闭背面剔除。 */
    public static final int MODE_NO_CULL = 1;
    /** 关闭深度测试。 */
    public static final int MODE_NO_DEPTH = 2;
    /** 关闭背面剔除 + 关闭深度测试。 */
    public static final int MODE_NO_CULL_NO_DEPTH = 3;
    /** 正面绕序翻转为顺时针。 */
    public static final int MODE_FRONT_FACE_CW = 4;
    /** 剔除正面（只保留背面）。 */
    public static final int MODE_CULL_FRONT = 5;
    /** 关剔除 + 关深度 + 强制纯红。 */
    public static final int MODE_FORCE_RED = 6;
    /** 仅强制纯红，剔除与深度保持原样 —— 区分「被剔除」与「颜色不对」的关键判据。 */
    public static final int MODE_RED_ONLY = 7;
    /** 关剔除 + 强制纯红（保留深度），作为模式 7 的对照组。 */
    public static final int MODE_RED_NO_CULL = 8;

    private static final int MODE_MAX = 8;

    /**
     * {@link #apply(ShaderInstance)} 当次实际采用的模式快照。
     *
     * <p><b>这是修一个真实缺陷：</b>原先 {@code restore()} 会重新读一次
     * {@link #mode}。而 {@code mode} 由指令线程随时写入，一旦玩家恰好在
     * apply 与 restore 之间敲了 {@code /sabledbg}，restore 读到的就是**新模式**，
     * 于是旧模式改过的状态永远不会被还原。
     *
     * <p>后果尤其严重的是 {@code glFrontFace} 与 {@code glCullFace}：
     * 原版 Minecraft 全程<b>从不设置</b>这两项（默认 CCW + BACK 用一辈子），
     * 所以没有任何后续渲染批次会把它们纠正回来 —— 泄漏即永久污染，
     * 整个游戏画面都会变成「只画背面」，而玩家还以为那是被测模式的效果。
     * 实测玩家从模式 4 切到模式 5 时就踩中了这个坑，导致模式 4/5 的观察结果
     * 相互污染、无法采信。
     *
     * <p>apply / restore 在渲染线程上严格成对且不嵌套，单个字段足够。
     */
    private static int appliedMode = MODE_OFF;

    /**
     * 模式刚被切换的标志。指令线程置位，渲染线程在下一次 apply 时消费，
     * 借机把 GL 绕序 / 剔除面硬复位一次，抹掉历史泄漏。
     */
    private static volatile boolean resetRequested = false;

    /**
     * 当前诊断模式。渲染线程读、指令线程写，故用 volatile 保证可见性。
     * 默认 0：不改变任何原版状态，避免诊断代码本身引入画面异常
     * （此前写死的「关剔除」会让方块内壁参与绘制，与外壁共面造成
     *  深度冲突，表现为黑色与原贴图交替频闪的「重影」）。
     */
    public static volatile int mode = MODE_OFF;

    /**
     * 子关卡方块实体「法线矩阵同步」修复的总开关，默认开启。
     *
     * <p>用途是让玩家在游戏里实时开关这项修复、当场肉眼对比，
     * 而不必为了验证一个假设重新构建一次模组。
     *
     * <p>背景：1.20.1 的 {@code PoseStack.mulPoseMatrix(Matrix4f)} 只乘 pose、
     * <b>完全不维护 normal 矩阵</b>（字节码实证）。子关卡方块实体的姿态栈恰好由
     * {@code setIdentity() → mulPoseMatrix(相机基座) → mulPoseMatrix(子关卡变换)}
     * 搭成，于是 normal 恒为单位阵，顶点法线停留在模型空间，
     * 而 {@code rendertype_entity_*} 着色器的 {@code Light0_Direction /
     * Light1_Direction} 是<b>视图空间</b>常量 —— 两者点乘出来的方向性明暗必然错。
     * 表现就是齿轮、传动轴这类由方块实体渲染器绘制的可动部件，
     * 某些面被压到最暗（约 40% 亮度），看着像「只剩一个面」，
     * 且明暗不跟随结构旋转变化，转到特定角度反而「碰巧正常」。
     *
     * <p>判定方法：
     * <ul>
     *   <li>{@code /sabledbg normal} 切到<b>关</b>后现象复现、切到<b>开</b>后消失
     *       → 坐实就是法线矩阵问题，修复有效；</li>
     *   <li>开与关<b>毫无差别</b> → 法线不是主因，问题另有出处
     *       （需转向几何 / 剔除 / 烘焙方向继续排查）。</li>
     * </ul>
     */
    public static volatile boolean normalFixEnabled = true;

    /**
     * 强制重烘焙请求。指令线程置位，子关卡在下一次 compileSections 里消费。
     *
     * <p>存在的意义：烘焙期的面朝向统计只在「有渲染段变脏」时才会产出一条日志，
     * 进世界后往往只打一次。要复现或换个角度再看一次，就得退出重进，成本极高。
     * 有了这条指令，游戏里敲一下就能重新烘焙并立即拿到新统计。
     */
    private static volatile boolean rebuildRequested = false;

    /** 请求下一次 compileSections 时整段重烘焙。 */
    public static void requestRebuild() {
        rebuildRequested = true;
    }

    /**
     * 消费一次重烘焙请求。
     *
     * @return 本次是否需要重烘焙；返回 true 后标志自动清除，不会重复触发
     */
    public static boolean consumeRebuildRequest() {
        if (!rebuildRequested) {
            return false;
        }
        rebuildRequested = false;
        return true;
    }

    private SableRenderDebug() {
    }

    /**
     * 在子关卡绘制循环开始前修改图形状态。
     *
     * @param shader 当前绑定的着色器，可为 {@code null}
     */
    /** 该模式是否需要关闭背面剔除。 */
    private static boolean isNoCull(final int value) {
        return value == MODE_NO_CULL || value == MODE_NO_CULL_NO_DEPTH
                || value == MODE_FORCE_RED || value == MODE_RED_NO_CULL;
    }

    /** 该模式是否需要关闭深度测试。 */
    private static boolean isNoDepth(final int value) {
        return value == MODE_NO_DEPTH || value == MODE_NO_CULL_NO_DEPTH || value == MODE_FORCE_RED;
    }

    /** 该模式是否需要把颜色强制刷成纯红。 */
    private static boolean isForceRed(final int value) {
        return value == MODE_FORCE_RED || value == MODE_RED_ONLY || value == MODE_RED_NO_CULL;
    }

    public static void apply(final ShaderInstance shader) {
        // 玩家刚切过模式的话，先把绕序 / 剔除面硬复位成原版默认，
        // 清掉历史版本可能已经泄漏出去的脏状态（GL_CW 或 CULL_FRONT 残留）。
        // 只在切换后的第一帧做一次，正常游玩不产生任何额外 GL 调用。
        if (resetRequested) {
            resetRequested = false;
            GL11.glFrontFace(GL11.GL_CCW);
            GL11.glCullFace(GL11.GL_BACK);
        }

        // 立刻把模式定格下来。此后本次绘制的所有判断（包括 restore）一律以快照为准，
        // 绝不再读 volatile 的 mode —— 详见 appliedMode 的注释。
        final int current = mode;
        appliedMode = current;
        if (current == MODE_OFF) {
            return;
        }

        if (isNoCull(current)) {
            RenderSystem.disableCull();
        }
        if (isNoDepth(current)) {
            RenderSystem.disableDepthTest();
        }
        if (current == MODE_FRONT_FACE_CW) {
            GL11.glFrontFace(GL11.GL_CW);
        }
        if (current == MODE_CULL_FRONT) {
            GL11.glCullFace(GL11.GL_FRONT);
        }
        if (isForceRed(current) && shader != null && shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(1.0f, 0.0f, 0.0f, 1.0f);
            shader.COLOR_MODULATOR.upload();
        }
    }

    /**
     * 回读当前真实生效的 OpenGL 图形状态，用于确认诊断模式「究竟有没有落到 GL 上」。
     *
     * <p>此前吃过大亏：模式 4 把绕序改成 GL_CW，但没有任何一条日志能证明它真的生效，
     * 结论只能靠肉眼猜。之后凡是切换状态的诊断，都必须在 apply 之后回读一次。
     *
     * @return 中文格式化的状态串，可直接拼进日志
     */
    public static String snapshotGlState() {
        final int frontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        final int cullMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        final boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        return "绕序=" + frontFace + (frontFace == GL11.GL_CCW ? "(CCW逆时针)" : "(CW顺时针)")
                + "，剔除开启=" + cullEnabled
                + "，剔除面=" + cullMode + (cullMode == GL11.GL_BACK ? "(BACK背面)" : "(FRONT正面)")
                + "，深度测试=" + depthEnabled + "，深度函数=" + depthFunc
                + "，深度写入=" + depthMask;
    }

    /**
     * 绘制循环结束后还原图形状态，必须与 {@link #apply(ShaderInstance)} 成对调用，
     * 且务必放在 finally 块里，否则一旦绘制抛异常，被改坏的状态会泄漏给后续所有渲染批次。
     *
     * @param shader 当前绑定的着色器，可为 {@code null}
     */
    public static void restore(final ShaderInstance shader) {
        // 一律使用 apply 当次的快照，而不是重新读 mode。
        final int current = appliedMode;
        appliedMode = MODE_OFF;
        if (current == MODE_OFF) {
            return;
        }

        if (isNoCull(current)) {
            RenderSystem.enableCull();
        }
        if (isNoDepth(current)) {
            RenderSystem.enableDepthTest();
        }
        if (current == MODE_FRONT_FACE_CW) {
            // 原版全程使用逆时针为正面，还原回去。
            GL11.glFrontFace(GL11.GL_CCW);
        }
        if (current == MODE_CULL_FRONT) {
            GL11.glCullFace(GL11.GL_BACK);
        }
        if (isForceRed(current) && shader != null && shader.COLOR_MODULATOR != null) {
            final float[] color = RenderSystem.getShaderColor();
            shader.COLOR_MODULATOR.set(color[0], color[1], color[2], color[3]);
            shader.COLOR_MODULATOR.upload();
        }
    }

    /**
     * @return 指定模式的中文说明
     */
    public static String describe(final int value) {
        switch (value) {
            case MODE_OFF:
                return "关闭诊断（原版行为）";
            case MODE_NO_CULL:
                return "关闭背面剔除";
            case MODE_NO_DEPTH:
                return "关闭深度测试";
            case MODE_NO_CULL_NO_DEPTH:
                return "关闭背面剔除 + 关闭深度测试";
            case MODE_FRONT_FACE_CW:
                return "正面绕序改为顺时针 GL_CW";
            case MODE_CULL_FRONT:
                return "剔除正面（只画背面）";
            case MODE_FORCE_RED:
                return "关剔除 + 关深度 + 强制纯红";
            case MODE_RED_ONLY:
                return "【关键】仅强制纯红，剔除与深度保持原样";
            case MODE_RED_NO_CULL:
                return "关剔除 + 强制纯红（保留深度），模式7的对照组";
            default:
                return "未知模式";
        }
    }

    /**
     * 注册客户端指令 {@code /sabledbg}。客户端指令不需要 OP 权限，
     * 单人存档与联机都能直接使用。
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(final RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sabledbg")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            "[Sable诊断] 当前渲染模式 = " + mode + " · " + describe(mode)), false);
                    for (int i = 0; i <= MODE_MAX; i++) {
                        final int index = i;
                        context.getSource().sendSuccess(() -> Component.literal(
                                "  " + index + " = " + describe(index)), false);
                    }
                    return 1;
                })
                .then(Commands.literal("normal")
                        .executes(context -> {
                            normalFixEnabled = !normalFixEnabled;
                            final boolean now = normalFixEnabled;
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "[Sable诊断] 方块实体法线矩阵同步修复 = " + (now ? "开启" : "关闭")
                                            + "（开=按姿态矩阵重算法线，关=沿用错误的单位阵旧行为）"
                                            + "；请对着有齿轮/传动轴的可动部件反复切换对比明暗"), false);
                            return 1;
                        }))
                .then(Commands.literal("rebuild")
                        .executes(context -> {
                            requestRebuild();
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "[Sable诊断] 已请求子关卡整段重烘焙，稍候在游戏内观察网格是否刷新"), false);
                            return 1;
                        }))
                .then(Commands.argument("模式", IntegerArgumentType.integer(MODE_OFF, MODE_MAX))
                        .executes(context -> {
                            final int value = IntegerArgumentType.getInteger(context, "模式");
                            // 先请求硬复位再改模式，确保上一模式残留的 GL 状态一定被清掉。
                            resetRequested = true;
                            mode = value;
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "[Sable诊断] 渲染模式已切换为 " + value + " · " + describe(value)), false);
                            return 1;
                        })));
    }
}
