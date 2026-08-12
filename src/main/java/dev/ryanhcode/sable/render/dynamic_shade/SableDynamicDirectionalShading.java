package dev.ryanhcode.sable.render.dynamic_shade;

import net.minecraft.core.Direction;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * 动态方向着色（Forge 1.20.1 移植版）。
 *
 * <p>原版（NeoForge 1.21.1）靠 Veil 的 GLSL 着色器预处理，在区块顶点着色器里
 * 用「世界法线」实时算方向光照，刚体一翻转法线跟着转、亮度就对了。但 Veil 是
 * NeoForge 专属库，Forge 1.20.1 没有，移植时整段被砍，回退成原版「局部烘焙」着色——
 * 区块在子关卡局部坐标里烘焙，方向性亮度（顶亮底暗）写死，刚体旋转后不跟着转，
 * 于是「原底面翻到上面仍是暗」。</p>
 *
 * <p>本移植版改用「构建期烘焙」策略（无需 Veil / 不依赖 Worker 线程 mixin）：
 * <ol>
 *   <li>子关卡区块在渲染线程上同步构建时，通过 {@link #beginSubLevelBuild(Quaterniondc)} /
 *       {@link #endSubLevelBuild()} 标记「当前线程正在构建子关卡 + 其世界朝向」；</li>
 *   <li>{@link dev.ryanhcode.sable.mixin.dynamic_directional_shading.ModelBlockRendererMixin}
 *       与 {@code AmbientOcclusionFaceMixin} 在 {@code getShade} 处把面方向按该朝向旋转到世界方向，
 *       烘焙出正确的世界方向性亮度；</li>
 *   <li>刚体朝向变化时由 {@code VanillaChunkedSubLevelRenderData} 触发整段重烘焙。</li>
 * </ol>
 * 注意：异步（远处）区块在 Worker 线程构建，本方案不会打标——近处（玩家盯着看）的
 * 同步构建已覆盖绝大多数使用场景。
 */
public class SableDynamicDirectionalShading {

    private static boolean isEnabled = true;

    /**
     * 当前线程的子关卡构建上下文。用线程本地变量而非 ModelBlockRenderer.CACHE，
     * 以避免在非 mixin 类里访问包私有 CACHE 字段导致的编译/可达性问题。
     */
    private static final ThreadLocal<BuildContext> BUILD_CONTEXT = ThreadLocal.withInitial(BuildContext::new);

    public static boolean isEnabled() {
        return isEnabled;
    }

    public static void setIsEnabled(final boolean isEnabled) {
        SableDynamicDirectionalShading.isEnabled = isEnabled;
    }

    /**
     * 「任意线程正在构建子关卡」的粗粒度快速门槛。
     *
     * <p>[BUG-28 诊断] 剔除统计挂在 {@code Block.shouldRenderFace} 上，而该方法在主世界
     * 区块烘焙时每帧被调用几十万次。若每次都做 {@link ThreadLocal#get()} 会带来可测量的
     * 开销。这里先用一个普通静态 volatile 布尔挡住 99.99% 的调用 —— 只有子关卡确实在
     * 烘焙的那极短窗口内，才会继续走精确的 ThreadLocal 判断。
     */
    private static volatile boolean anySubLevelBuilding = false;

    /**
     * @return 是否有任意线程正在构建子关卡（仅作快速门槛，精确判断仍用 {@link #isBuildingSubLevel()}）
     */
    public static boolean isAnySubLevelBuilding() {
        return anySubLevelBuilding;
    }

    /**
     * @return 当前线程是否正在构建子关卡区块（用于开启世界朝向着色）
     */
    public static boolean isBuildingSubLevel() {
        return BUILD_CONTEXT.get().onSubLevel;
    }

    /**
     * @return 当前正在构建的子关卡的世界朝向（四元数）；非构建期为 null
     */
    public static Quaterniondc subLevelOrientation() {
        return BUILD_CONTEXT.get().orientation;
    }

    /**
     * 标记当前线程开始构建一个子关卡区块，并带上它的世界朝向。
     */
    public static void beginSubLevelBuild(final Quaterniondc orientation) {
        final BuildContext ctx = BUILD_CONTEXT.get();
        ctx.onSubLevel = true;
        ctx.orientation = orientation;
        anySubLevelBuilding = true;
    }

    /**
     * 标记当前线程的子关卡区块构建结束。
     */
    public static void endSubLevelBuild() {
        final BuildContext ctx = BUILD_CONTEXT.get();
        ctx.onSubLevel = false;
        ctx.orientation = null;
        anySubLevelBuilding = false;
    }

    /**
     * 把一个局部面方向按子关卡朝向旋转到世界方向，并吸附到最近的主轴 Cardinal 方向。
     * 用于烘焙期把方向性亮度算到世界坐标系（翻转后底面自动拿到世界「朝下」暗度）。
     *
     * @param dir        局部坐标系下的面方向
     * @param orientation 子关卡当前世界朝向（四元数）
     * @return 旋转后吸附到 Cardinal 的世界方向
     */
    public static Direction rotate(final Direction dir, final Quaterniondc orientation) {
        final Vector3d v = new Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        orientation.transform(v);

        final double ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z);
        if (ax >= ay && ax >= az) {
            return v.x > 0.0 ? Direction.EAST : Direction.WEST;
        }
        if (ay >= az) {
            return v.y > 0.0 ? Direction.UP : Direction.DOWN;
        }
        return v.z > 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ------------------------------------------------------------------
    // [BUG-28 诊断] 烘焙期几何统计
    //
    // 现场实测已把「图形状态」这条线彻底排除：GL 绕序 CCW、剔除面 BACK、
    // 深度 LEQUAL、矩阵行列式 +1（无镜像）、着色器 uniform 正常，
    // 且 /sabledbg 4（反绕序）会让本来可见的面消失 —— 说明可见部分的绕序完全正确。
    //
    // 于是「看不见」只剩最后一种可能：那些面【压根没被烘焙进网格】。
    // 最大嫌疑是邻接遮挡剔除（Block.shouldRenderFace）在子关卡里判错，
    // 把外表面当成了「被邻居挡住的内表面」丢弃。
    //
    // 这里按【面朝向】分桶统计烘焙期真正提交的四边形数量。判定方法：
    //   · 六个方向数量大体均衡  -> 几何完整，问题不在烘焙，回头查绘制；
    //   · 某几个方向为 0 或极少 -> 坐实遮挡剔除判错，且直接指出是哪几个方向，
    //     再对照子关卡朝向即可反推邻居查询的偏移错在哪一轴。
    //
    // 定位完成后本段与相关调用点一并删除。
    // ------------------------------------------------------------------

    /** 按 {@link Direction#ordinal()} 分桶的四边形计数，另加一个「无方向面」桶。 */
    private static final int[] QUAD_COUNT_BY_FACE = new int[Direction.values().length];

    /** 模型里方向为 null 的四边形（十字草之类）单独计数，避免污染六个主方向。 */
    private static int quadCountNoFace;

    /**
     * 邻接遮挡剔除的判定结果统计。
     *
     * <p>{@code FACE_TEST_KEPT} 是 {@code Block.shouldRenderFace} 返回 true（面保留）的次数，
     * {@code FACE_TEST_CULLED} 是返回 false（面被当作「被邻居挡住」丢弃）的次数。
     *
     * <p>与四边形统计配合使用，可以把「看不见」精确切成三种情形：
     * <ul>
     *   <li>某方向 <b>被剔除数远大于保留数</b> → 邻接遮挡剔除判错，面根本没进网格；</li>
     *   <li>剔除数正常但该方向 <b>四边形数为 0</b> → 面过了剔除却没写进网格，问题在模型/网格层；</li>
     *   <li>两者都正常 → 几何完整，问题在绘制层（Embeddium / Oculus 顶点格式、着色器等）。</li>
     * </ul>
     */
    private static final int[] FACE_TEST_KEPT = new int[Direction.values().length];

    private static final int[] FACE_TEST_CULLED = new int[Direction.values().length];

    /**
     * 面被剔除时「挡住它的那个邻居方块」的名字分布（取前几名输出）。
     *
     * <p>这是判断偏移是否算错的直接证据：物理化后的结构悬空放在 plot 里，
     * 外表面的邻居本应大多是空气。若这里出现大量石头、基岩之类的实心方块，
     * 就说明邻居查询读到了根本不该读的位置。
     */
    private static final java.util.HashMap<String, Integer> CULL_BLOCKER_NAMES = new java.util.HashMap<>();

    /** 清零统计，在一轮烘焙开始前调用。 */
    public static void resetQuadStats() {
        java.util.Arrays.fill(QUAD_COUNT_BY_FACE, 0);
        quadCountNoFace = 0;
        java.util.Arrays.fill(FACE_TEST_KEPT, 0);
        java.util.Arrays.fill(FACE_TEST_CULLED, 0);
        CULL_BLOCKER_NAMES.clear();
    }

    /**
     * 记录一次邻接遮挡剔除判定。
     *
     * @param face        被测试的面朝向
     * @param kept        {@code true} 表示面保留，{@code false} 表示被当作内表面丢弃
     * @param blockerName 面被丢弃时挡住它的邻居方块名；{@code kept} 为 true 时可传 {@code null}
     */
    public static void countFaceTest(final Direction face, final boolean kept, final String blockerName) {
        if (face == null) {
            return;
        }
        if (kept) {
            FACE_TEST_KEPT[face.ordinal()]++;
            return;
        }
        FACE_TEST_CULLED[face.ordinal()]++;
        if (blockerName != null) {
            CULL_BLOCKER_NAMES.merge(blockerName, 1, Integer::sum);
        }
    }

    /**
     * @return 中文格式化的遮挡剔除统计；若一次判定都没发生，返回明确提示
     *         （那意味着注入没生效或烘焙压根没走原版剔除路径，同样是重要信息）
     */
    public static String describeCullStats() {
        int totalKept = 0;
        int totalCulled = 0;
        for (int i = 0; i < FACE_TEST_KEPT.length; i++) {
            totalKept += FACE_TEST_KEPT[i];
            totalCulled += FACE_TEST_CULLED[i];
        }
        if (totalKept + totalCulled == 0) {
            return "本轮没有发生任何邻接遮挡剔除判定（注入未生效，或烘焙未走原版 Block.shouldRenderFace 路径）";
        }

        final StringBuilder builder = new StringBuilder("遮挡剔除 保留合计=").append(totalKept)
                .append(" 丢弃合计=").append(totalCulled).append(" · 分朝向(保留/丢弃)：");
        for (final Direction direction : Direction.values()) {
            builder.append(describeFace(direction))
                    .append('=')
                    .append(FACE_TEST_KEPT[direction.ordinal()])
                    .append('/')
                    .append(FACE_TEST_CULLED[direction.ordinal()])
                    .append(' ');
        }

        if (!CULL_BLOCKER_NAMES.isEmpty()) {
            builder.append("· 挡住面的邻居方块TOP：");
            CULL_BLOCKER_NAMES.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .forEach(entry -> builder.append(entry.getKey()).append('x').append(entry.getValue()).append(' '));
        }
        return builder.toString();
    }

    /** 记录一个烘焙期提交的四边形，{@code face} 允许为 null。 */
    public static void countQuad(final Direction face) {
        if (face == null) {
            quadCountNoFace++;
        } else {
            QUAD_COUNT_BY_FACE[face.ordinal()]++;
        }
    }

    /**
     * @return 中文格式化的统计结果，可直接拼进日志；无任何四边形时返回明确提示
     */
    public static String describeQuadStats() {
        int total = quadCountNoFace;
        for (final int count : QUAD_COUNT_BY_FACE) {
            total += count;
        }
        if (total == 0) {
            return "本轮烘焙没有提交任何四边形（几何完全为空）";
        }

        final StringBuilder builder = new StringBuilder("四边形合计=").append(total).append(" · 分朝向：");
        for (final Direction direction : Direction.values()) {
            builder.append(describeFace(direction))
                    .append('=')
                    .append(QUAD_COUNT_BY_FACE[direction.ordinal()])
                    .append(' ');
        }
        builder.append("无朝向=").append(quadCountNoFace);
        return builder.toString();
    }

    /** 面朝向的中文名，便于直接对照肉眼观察到的「哪一面不见了」。 */
    private static String describeFace(final Direction direction) {
        switch (direction) {
            case DOWN:
                return "下";
            case UP:
                return "上";
            case NORTH:
                return "北";
            case SOUTH:
                return "南";
            case WEST:
                return "西";
            case EAST:
                return "东";
            default:
                return direction.getName();
        }
    }

    private static final class BuildContext {
        private boolean onSubLevel;
        private Quaterniondc orientation;
    }
}
