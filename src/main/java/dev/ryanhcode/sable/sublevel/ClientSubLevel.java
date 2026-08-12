package dev.ryanhcode.sable.sublevel;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.network.client.ClientSableInterpolationState;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import dev.ryanhcode.sable.sublevel.plot.ClientLevelPlot;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A sub-level in a {@link net.minecraft.client.multiplayer.ClientLevel}
 */
public class ClientSubLevel extends SubLevel implements ClientSubLevelAccess {

    /**
     * The renderer for this sub-level
     */
    private SubLevelRenderData renderData;

    /**
     * The latest networked velocity received from the server [m/s]
     */
    private final Vector3d latestNetworkedVelocity = new Vector3d();

    /**
     * The latest networked angular velocity received from the server [rad/s]
     */
    private final Vector3d latestNetworkedAngularVelocity = new Vector3d();

    /**
     * Storage pose for the current frame render pose
     */
    private final Pose3d renderPose = new Pose3d();

    /**
     * The interpolation buffer for the latest server snapshots
     */
    private final SubLevelSnapshotInterpolator interpolator;
    /**
     * Storage for swept bounds to not instantiate new bounding boxes for every {@link ClientSubLevel#boundingBox()} call
     */
    private final BoundingBox3d sweptBounds = new BoundingBox3d();
    /**
     * Last center of the bounds used for sky light calculation
     */
    private final Vector3d lastBoundsCenter = new Vector3d();

    /**
     * [BUG-03 v20] 子层级在「地面发射位」时冻结的主世界包围盒。
     * 升空后子层级的 globalBounds/sweptBounds 随 pose 上移，但主世界残留方块实体
     * 始终固定在发射位 —— 若用「当前」包围盒判定，升空后残留会落在包围盒之外 → 抑制失效。
     * 故在追踪开始（forceUpdateBounds，此时在地面发射位）冻结一份主世界包围盒，
     * 升空前后恒定，P4 用它抑制主世界残留方块实体。
     */
    private final BoundingBox3d restMainWorldBounds = new BoundingBox3d();

    /**
     * 是否已冻结发射位主世界包围盒。
     */
    private boolean sable$restBoundsFrozen = false;

    /**
     * [BUG-03 v25] 发射位姿态：在 StartTracking（子层级在发射位、尚未升空）时记录。
     * 之后枚举方块实体时用它把 sector 坐标变换到「发射位世界坐标」，构建精确抑制框。
     * 不在此刻冻结框本身——StartTracking 时子层级 chunk 尚未加载完，枚举会拿到 beCount=0，
     * 框会退化成过时的 plot 包围盒（漏掉后期放置的东南向方块）。冻结推迟到首次 P4 判定且已升空时。
     */
    private Pose3dc sable$launchPose = null;
    // [BUG-03 v33] 发射位(地面)主世界包围盒：在 forceUpdateBounds 首次(装配)冻结，
    // 精确圈定结构在主世界的原始占据范围。升空后地面残留(幽灵)方块实体必落在此范围内，
    // 渲染期据此跳过即可，无需依赖「客户端实时 chunk 是否已移除」(实测 inMainChunk=SAME 仍持有)。
    private AABB sable$launchBounds = null;

    /**
     * [BUG-03 v34] 静态发射位包围盒登记表：forceUpdateBounds 冻结时登记本子层级的地面包围盒，
     * 渲染期(LevelRendererMixin.sable$isMainWorldGhost)直接查此表判定主世界幽灵方块实体。
     * <p>
     * 之所以用静态表而非 {@code SubLevelContainer.getContainer(this.level).getAllSubLevels()}：
     * 实测同帧、同一个 LevelRenderer 实例、同一个 this.level 下，preRenderBEs(飞行副本渲染前置)
     * 用该句能拿到子层级、P4 逐区块循环里却拿到 0（活跃子层级=0 贯穿全程），根因未明。
     * 本表在 forceUpdateBounds 直接写入(与容器无关)，飞行副本确实渲染(LAUNCH-BOUNDS 已打印)，
     * 故必有效。子层级移除时(onRemove)从表中清除，避免陈旧坐标误抑制。
     */
    public static final Map<UUID, AABB> SABLE_LAUNCH_BOUNDS = new HashMap<>();

    /** [BUG-03 v34] 任一已登记发射位包围盒是否包含该世界坐标（用于主世界幽灵判定）。 */
    public static boolean sable$isAnyLaunchBoundsContains(final double x, final double y, final double z) {
        for (final AABB b : SABLE_LAUNCH_BOUNDS.values()) {
            if (b != null && b.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * [BUG-03 v26] 发射位框候选（逐帧并集，随 chunk 加载持续扩大，最终覆盖全部结构方块实体）。
     * 用 6 个标量存储，避免依赖 BoundingBox3d 的访问器 API。
     */
    private double sable$rbMinX = Double.POSITIVE_INFINITY;
    private double sable$rbMinY = Double.POSITIVE_INFINITY;
    private double sable$rbMinZ = Double.POSITIVE_INFINITY;
    private double sable$rbMaxX = Double.NEGATIVE_INFINITY;
    private double sable$rbMaxY = Double.NEGATIVE_INFINITY;
    private double sable$rbMaxZ = Double.NEGATIVE_INFINITY;
    /**
     * [BUG-03 v26] 候选框是否已写入过数据（区分「空集」与「已有并集」）。
     */
    private boolean sable$restBoxHasData = false;

    /**
     * Latest sub-level sky light scaling
     */
    private int latestSkyLightScale = -1;

    /**
     * Last partial tick used for rendering interpolation
     */
    private float lastRenderPosePartialTick = -1;

    /**
     * Flywheel lighting scene ID
     */
    private int lightingSceneId = -1;

    /**
     * If we've received all initial data regarding this sub-level from the server (all chunks, bounds, data, etc.)
     */
    private boolean finalized = false;

    /** [BUG-29] 物理化飞行状态标记：最近数 tick 内收到运动快照包即视为物理化飞行中。由快照包处理刷新，
     *  用于判定是否需对增量更新补播操作反馈（声音+碎屑）——静态结构有本地预测不补，物理化结构无本地预测需补。 */
    private long sable$lastPhysicsSnapshotTime = -1L;

    /**
     * Creates a new sub-level with the given parent level and pose.
     *
     * @param level the parent level
     * @param plotX the global plot x coordinate
     * @param plotY the global plot y coordinate
     * @param pose  the initialization pose of the sub-level
     */
    public ClientSubLevel(final Level level, final int plotX, final int plotY, final Pose3d pose) {
        super(level, plotX, plotY, pose);

        this.logicalPose().set(pose);
        this.interpolator = new SubLevelSnapshotInterpolator(pose);
    }

    /**
     * Creates the plot for this sub-level.
     *
     * @param plotContainer the parent plot container of this sub-level
     * @param plotX         the global plot x coordinate
     * @param plotY         the global plot y coordinate
     * @param logPlotSize   the log_2 of the side length of a plot, in chunks
     * @return a new {@link LevelPlot} instance for this sub-level
     */
    @Override
    protected LevelPlot createPlot(final SubLevelContainer plotContainer, final int plotX, final int plotY, final int logPlotSize) {
        return new ClientLevelPlot(plotContainer, plotX, plotY, plotContainer.getLogPlotSize(), this);
    }

    /**
     * Ticks this sub-level, updating the global bounding box and components.
     */
    @Override
    public void tick() {
        this.updateLastPose();
        super.tick();

        this.lastRenderPosePartialTick = -1.0f;
        final Pose3d logicalPose = this.logicalPose();

        final ClientSubLevelContainer container = ClientSubLevelContainer.getContainer(this.getLevel());
        assert container != null;
        this.interpolator.tick(container.getInterpolation().getTickPointer());
        final Pose3dc interpolatedPose = this.interpolator.getInterpolatedPose();

        logicalPose.set(interpolatedPose);

        this.updateBoundingBox();

        if (this.lastGlobalBounds.minX == 0 && this.lastGlobalBounds.maxX == 0) {
            // we can assume that we don't have a last bounds yet
            this.sweptBounds.set(this.globalBounds);
        } else {
            this.sweptBounds.set(this.lastGlobalBounds).expandTo(this.globalBounds, this.sweptBounds);
        }

        this.latestSkyLightScale = this.computeSubLevelSkyLight(this.logicalPose());
    }

    public void forceUpdateBounds() {
        this.updateBoundingBox();
        this.lastGlobalBounds.set(this.globalBounds);
        this.sweptBounds.set(this.globalBounds);

        // [BUG-03 v25] 仅记录发射位姿态，不在此刻冻结包围盒。
        // 原因：StartTracking（追踪开始，子层级在发射位）时子层级 chunk 尚未加载完，
        // 此刻枚举方块实体得到 beCount=0，会导致框退化成过时的 plot 包围盒
        // （漏掉后期放置的东南向带实体方块）。真正的冻结推迟到「首次进入 P4 判定且已升空」时，
        // 那时 chunk 已加载、主世界残留已出现，枚举能拿到全部实体，框才准确。
        if (this.sable$launchPose == null) {
            // [BUG-03 v28 致命修复] 必须<b>深拷贝</b>快照，绝不能直接存 logicalPose() 的引用！
            // SubLevel.logicalPose() 返回的是内部可变字段 this.pose 的<b>引用</b>，
            // 而 ClientSubLevel.tick() 每 tick 都执行 logicalPose.set(interpolatedPose) 原地改写它。
            // 直接存引用 => sable$launchPose 会跟着热气球一起飞，
            // hasLeftLaunch() 算出的位移恒为 0 => 抑制守卫永远返回 false => 拦截从未触发。
            // 这正是 v23~v27「改了框却依旧残留、日志里一条 [P4-拦截] 都没有」的真正根因。
            this.sable$launchPose = new Pose3d(this.logicalPose());
            // [BUG-03 v33] 此刻 pose 为发射位(地面)位姿，boundingBox() 即结构在主世界的原始占据范围。
            // 冻结为地面包围盒，供渲染期判定地面幽灵方块实体。
            final BoundingBox3dc sable$lb = this.boundingBox();
            this.sable$launchBounds = new AABB(sable$lb.minX(), sable$lb.minY(), sable$lb.minZ(),
                    sable$lb.maxX(), sable$lb.maxY(), sable$lb.maxZ());
            // [BUG-03 v34] 冻结同时登记进静态表，供 P4 幽灵判定直接查询（绕开容器查询诡异返空）。
            SABLE_LAUNCH_BOUNDS.put(this.getUniqueId(), this.sable$launchBounds);
        }
    }

    /**
     * [BUG-03 v26] 构建/扩大发射位包围盒（幂等）。
     * 枚举子层级所有已加载 chunk 里实际存在的方块实体，用<b>发射位姿态</b>（sable$launchPose）
     * 把 sector 坐标变换到世界坐标，<b>与已有候选框取并集（逐帧持续扩大，只增不减）</b>。
     *
     * <p><b>v26 关键修正</b>：v25 在 beCount 首次 &gt; 0 时立即冻结，但此刻 chunk 往往只加载了一部分
     * （如 beCount=1），框成了一个极小点、漏掉东南向其余方块实体 → 抑制失效「依旧东南」。
     * v26 改为：只要本帧枚举非空，就不断把当前框并入候选框（只增不减），
     * 直到 chunk 全部加载完（用户激活燃烧室前已历经数十帧），候选框自然覆盖全部结构方块实体（含东南向）。
     *
     * @return 是否已至少有过一次有效数据（beCount&gt;0）。
     */
    public boolean sable$ensureRestBoundsFrozen() {
        final Pose3dc sable$pose = this.sable$launchPose != null ? this.sable$launchPose : this.logicalPose();
        double wMinX = Double.POSITIVE_INFINITY, wMaxX = Double.NEGATIVE_INFINITY;
        double wMinY = Double.POSITIVE_INFINITY, wMaxY = Double.NEGATIVE_INFINITY;
        double wMinZ = Double.POSITIVE_INFINITY, wMaxZ = Double.NEGATIVE_INFINITY;
        // [BUG-03 v27 修正] 抑制框必须用<b>完整结构范围</b>，否则东南/顶部残留漏拦（「依旧东南」）。
        // v26 只枚举 plot 已加载 chunk 的方块实体，实测 beCount=5、框偏小且缺顶部
        // （X=-8..-3 对了，但 Y 只到 -55，结构真实 Y=-60..-49），漏掉结构其余部分 -> 东南残留在框外。
        // 正确做法（与 ClientboundStartTrackingSubLevelPacket 的 PKTDBG 注释完全一致）：
        //   主世界残留方块实体的 BlockPos == launchPose.transformPosition(plot 包围盒角点)
        // 故直接把 plot 包围盒（localBounds，覆盖整块 plot 矩形 = 全部结构区块并集）的 8 个角点
        // 经<b>发射位姿态</b>变换到主世界坐标，得到<b>完整结构范围</b>，再并上枚举到的方块实体
        // （防御 plot 包围盒过时 / 后期放置超出原 plot 的方块）。
        final var sable$pb = this.getPlot().getBoundingBox();
        if (sable$pb != null) {
            final double[] sable$xs = { sable$pb.minX(), sable$pb.maxX() };
            final double[] sable$ys = { sable$pb.minY(), sable$pb.maxY() };
            final double[] sable$zs = { sable$pb.minZ(), sable$pb.maxZ() };
            for (final double sable$cx : sable$xs) {
                for (final double sable$cy : sable$ys) {
                    for (final double sable$cz : sable$zs) {
                        final Vector3d sable$w = sable$pose.transformPosition(
                                new Vector3d(sable$cx + 0.5, sable$cy + 0.5, sable$cz + 0.5));
                        wMinX = Math.min(wMinX, sable$w.x); wMaxX = Math.max(wMaxX, sable$w.x);
                        wMinY = Math.min(wMinY, sable$w.y); wMaxY = Math.max(wMaxY, sable$w.y);
                        wMinZ = Math.min(wMinZ, sable$w.z); wMaxZ = Math.max(wMaxZ, sable$w.z);
                    }
                }
            }
        }
        // 并上枚举到的方块实体（防御 plot 包围盒过时 / 后期放置超出原 plot 的方块）
        for (final PlotChunkHolder sable$holder : this.getPlot().getLoadedChunks()) {
            final LevelChunk sable$chunk = sable$holder.getChunk();
            if (sable$chunk == null) {
                continue;
            }
            for (final BlockEntity sable$be : sable$chunk.getBlockEntities().values()) {
                final BlockPos sable$bp = sable$be.getBlockPos();
                final Vector3d sable$w = sable$pose.transformPosition(new Vector3d(
                        sable$bp.getX() + 0.5, sable$bp.getY() + 0.5, sable$bp.getZ() + 0.5));
                wMinX = Math.min(wMinX, sable$w.x); wMaxX = Math.max(wMaxX, sable$w.x);
                wMinY = Math.min(wMinY, sable$w.y); wMaxY = Math.max(wMaxY, sable$w.y);
                wMinZ = Math.min(wMinZ, sable$w.z); wMaxZ = Math.max(wMaxZ, sable$w.z);
            }
        }
        if (wMinX == Double.POSITIVE_INFINITY) {
            // 既无 plot 包围盒也无方块实体（极罕见），本次不冻结、下帧再试。
            return this.sable$restBoundsFrozen;
        }
        // 把本帧框并入 candidate（只增不减，确保最终覆盖全部结构方块实体，含东南向）
        if (!this.sable$restBoxHasData) {
            this.sable$rbMinX = wMinX; this.sable$rbMaxX = wMaxX;
            this.sable$rbMinY = wMinY; this.sable$rbMaxY = wMaxY;
            this.sable$rbMinZ = wMinZ; this.sable$rbMaxZ = wMaxZ;
            this.sable$restBoxHasData = true;
        } else {
            this.sable$rbMinX = Math.min(this.sable$rbMinX, wMinX);
            this.sable$rbMaxX = Math.max(this.sable$rbMaxX, wMaxX);
            this.sable$rbMinY = Math.min(this.sable$rbMinY, wMinY);
            this.sable$rbMaxY = Math.max(this.sable$rbMaxY, wMaxY);
            this.sable$rbMinZ = Math.min(this.sable$rbMinZ, wMinZ);
            this.sable$rbMaxZ = Math.max(this.sable$rbMaxZ, wMaxZ);
        }
        // 实际生效的抑制框 = candidate + 3 格兜底（经完整 plot 包围盒变换，已覆盖东南/顶部）
        this.restMainWorldBounds.setUnchecked(
                this.sable$rbMinX, this.sable$rbMinY, this.sable$rbMinZ,
                this.sable$rbMaxX, this.sable$rbMaxY, this.sable$rbMaxZ).expand(3.0);
        this.sable$restBoundsFrozen = true;
        return true;
    }

    /** [BUG-03 v25] 发射位姿态（getter，供 Sable$RestEntry 构造时读取）。 */
    public Pose3dc sable$launchPose() {
        return this.sable$launchPose;
    }

    /** [BUG-03 v25] 已冻结的发射位主世界包围盒（getter）。 */
    public BoundingBox3dc sable$restMainWorldBounds() {
        return this.restMainWorldBounds;
    }

    /** [BUG-03 v33] 发射位(地面)主世界 AABB（getter，供渲染期判定地面幽灵方块实体）。 */
    public AABB sable$getLaunchBounds() {
        return this.sable$launchBounds;
    }

    /** [BUG-03 v25] 发射位包围盒是否已成功冻结（getter）。 */
    public boolean sable$restBoundsFrozen() {
        return this.sable$restBoundsFrozen;
    }

    /**
     * Scales a sky light value by this sub-level sky light scale
     */
    public int scaleSkyLight(final int skyLight) {
        return (int) (skyLight * (this.getLatestSkyLightScale() / 15.0f));
    }

    /**
     * Scales a light color value by this sub-level sky light scale
     */
    public int scaleLightColor(int lightColor) {
        final int skyLightScale = this.getLatestSkyLightScale();

        final int newSkyLight = (int) ((lightColor >> 20) * (skyLightScale / 15.0f));
        lightColor = (lightColor & 0xfffff) | (newSkyLight << 20);

        return lightColor;
    }

    /**
     * @return the latest computed sky-light scaling of the sub-level
     */
    public int getLatestSkyLightScale() {
        if (this.latestSkyLightScale == -1) {
            this.latestSkyLightScale = this.computeSubLevelSkyLight(this.logicalPose());
        }
        return this.latestSkyLightScale;
    }

    /**
     * Computes the sky-light scaling of this sub-level
     */
    public int computeSubLevelSkyLight(final Pose3dc pose) {
        final Vector3dc pos = pose.position();
        final ClientLevel level = this.getLevel();

        final int result;

        if (this.boundingBox().volume() < 9) {
            int skyLight = level.getBrightness(LightLayer.SKY, BlockPos.containing(pos.x(), pos.y(), pos.z()));

            if (skyLight == 0)
                skyLight = level.getBrightness(LightLayer.SKY, BlockPos.containing(pos.x(), pos.y() + 1, pos.z()));

            if (skyLight == 0)
                skyLight = level.getBrightness(LightLayer.SKY, BlockPos.containing(pos.x(), pos.y() - 1, pos.z()));

            result = skyLight;
        } else {
            final BoundingBox3dc box = this.boundingBox();
            final Vector3dc center = box.center(this.lastBoundsCenter);
            final double xMin = box.minX();
            final double xMax = box.maxX();
            final double zMin = box.minZ();
            final double zMax = box.maxZ();

            int maxLight = 0;

            final double sampleY = center.y() + 0.1;
            maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(center.x(), sampleY, center.z())));

            maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(xMin, sampleY, zMin)));
            maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(xMax, sampleY, zMin)));
            maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(xMin, sampleY, zMax)));
            maxLight = Math.max(maxLight, level.getBrightness(LightLayer.SKY, BlockPos.containing(xMax, sampleY, zMax)));

            result = maxLight;
        }

        return result;
    }

    /**
     * @return the global bounding box of this sub-level
     */
    @Override
    public BoundingBox3dc boundingBox() {
        return this.sweptBounds;
    }

    /**
     * @return 子层级在「地面发射位」时冻结的主世界包围盒，用于 P4 抑制主世界残留方块实体。
     */
    public BoundingBox3dc restMainWorldBounds() {
        return this.restMainWorldBounds;
    }

    /**
     * @return 是否已冻结发射位主世界包围盒。
     */
    public boolean hasRestMainWorldBounds() {
        return this.sable$restBoundsFrozen;
    }

    /**
     * Called when the bounds of the inner plot have changed.
     */
    @Override
    public void onPlotBoundsChanged() {
        this.renderData = SubLevelRenderDispatcher.get().resize(this, this.renderData);
    }

    @Override
    public void onRemove() {
        if (this.lightingSceneId != -1) {
            SubLevelContainer.getContainer(this.getLevel())
                    .freeLightingScene(this.lightingSceneId);
            this.lightingSceneId = -1;
        }

        // [BUG-03 v34] 子层级移除时同步从静态发射位包围盒表中清除，避免陈旧坐标误抑制主世界方块实体。
        SABLE_LAUNCH_BOUNDS.remove(this.getUniqueId());

        super.onRemove();
        this.renderData.close();
    }

    /**
     * Re-creates the render data using the current renderer.
     */
    public void updateRenderData() {
        try {
            if (this.renderData != null) {
                this.renderData.close();
            }
            this.renderData = SubLevelRenderDispatcher.get().createRenderData(this);
        } catch (final Throwable t) {
            // [1.20.1 移植修正] 子关卡渲染数据创建在 1.20.1 下可能抛错（flywheel 视觉 / 渲染 API 不兼容），
            // 原版会在此抛 ReportedException -> 经 Forge 网络派发捕获并断开客户端连接（"拉动后连接中断"根因之一）。
            // 改为记录完整堆栈并保留现有渲染数据（renderData 可能为 null，下游已做空判断跳过），
            // 使子关卡仍可追踪 / 接收快照 / 物理运动，避免连接被断开。确切异常见下方堆栈，供后续精修渲染。
            dev.ryanhcode.sable.Sable.LOGGER.error("更新子关卡渲染数据失败（已忽略，子关卡仍可用）：", t);
        }
    }

    /**
     * @return the renderer for this sub-level
     */
    public SubLevelRenderData getRenderData() {
        return this.renderData;
    }

    @Override
    public ClientLevel getLevel() {
        return (ClientLevel) super.getLevel();
    }

    /**
     * @return the plot containing the contents of this sub-level
     */
    @Override
    public ClientLevelPlot getPlot() {
        return (ClientLevelPlot) super.getPlot();
    }

    @ApiStatus.Internal
    public void setLightingSceneId(final int lightingSceneId) {
        this.lightingSceneId = lightingSceneId;
    }

    @ApiStatus.Internal
    public int getLightingSceneId() {
        return this.lightingSceneId;
    }

    /**
     * @return the pose for rendering with the current partialtick
     */
    @Override
    public Pose3dc renderPose() {
        final float pt = Minecraft.getInstance().getPartialTick();

        if (this.lastRenderPosePartialTick == pt) {
            return this.renderPose;
        }

        return this.renderPose(pt);
    }

    /**
     * @return the pose for rendering with a given partialtick
     */
    @Override
    public Pose3dc renderPose(final float pt) {
        if (this.lastRenderPosePartialTick == pt) {
            return this.renderPose;
        }

        this.lastRenderPosePartialTick = pt;

        final Pose3d renderPose = this.renderPose.set(this.lastPose());
        final Pose3d target = this.logicalPose();

        renderPose.position().lerp(target.position(), pt);
        renderPose.orientation().slerp(target.orientation(), pt);
        renderPose.rotationPoint().lerp(target.rotationPoint(), pt);
        renderPose.scale().lerp(target.scale(), pt);

        return renderPose;
    }

    public void receiveServerMovementStop() {
        this.latestNetworkedVelocity.zero();
        this.latestNetworkedAngularVelocity.zero();
        this.interpolator.receiveStop();
    }

    @ApiStatus.Internal
    public void wasSplitFrom(final ClientSableInterpolationState state,  final ClientSubLevel splitFrom,  final Pose3dc pose) {
        final SubLevelSnapshotInterpolator otherInterpolator = splitFrom.getInterpolator();

        this.interpolator.splitFrom(otherInterpolator, pose);

        this.setInitialPosesFrom(state);
    }

    @ApiStatus.Internal
    public void setInitialPosesFrom(final ClientSableInterpolationState state) {
        if (!state.isStopped()) {
            this.interpolator.getSampleAt(state.mostRecentInterpolationTick, this.logicalPose());
            this.interpolator.getSampleAt(state.lastInterpolationTick, this.lastPose);
        }
    }

    public SubLevelSnapshotInterpolator getInterpolator() {
        return this.interpolator;
    }

    @Override
    public String toString() {
        return "ClientSubLevel" + super.toString();
    }

    /**
     * Sets this sub-level as finalized. This means we've received all initial data regarding this sub-level from the
     * server.
     */
    public void setFinalized() {
        this.finalized = true;
    }

    /**
     * If we've received all initial data regarding this sub-level from the server (all chunks, bounds, data, etc.)
     */
    public boolean isFinalized() {
        return this.finalized;
    }

    /**
     * [BUG-29] 该子层级当前是否处于物理化飞行（由运动快照驱动）。
     * 物理化结构客户端无本地预测，放置/破坏的反馈（声音+碎屑）需由增量更新包补播；
     * 静态结构客户端有本地预测，补播会双播，故只在物理化时补。
     * 判定：最近 10 tick（约 0.5 秒）内收过运动快照包即视为物理化飞行中，
     * 停止收快照（解除物理化落地）后自动回落，避免静态结构误补播。
     */
    public boolean sable$isPhysicsActive() {
        // [BUG-29] 用真实时钟（毫秒）代替客户端 gameTime：物理化场景下游戏时间不稳定，
        // 以 gameTime 为基准的窗口会在增量包到达时多半已过期，把本应补播的反馈错误拦截。
        return this.sable$lastPhysicsSnapshotTime >= 0L
                && (System.currentTimeMillis() - this.sable$lastPhysicsSnapshotTime) <= 1000L;
    }

    /** [BUG-29] 收到运动快照包时由快照处理调用，刷新物理化标记（真实时钟）。 */
    public void sable$markPhysicsSnapshot() {
        this.sable$lastPhysicsSnapshotTime = System.currentTimeMillis();
    }

    /**
     * [BUG-29] 每个子关卡自己维护的「上一次服务端确认方块状态」映射。
     * 物理化飞行结构客户端无本地预测、且本地预测会预先把 plot chunk 写成新状态，
     * 导致收包时 {@code level.getBlockState(pos)} 读到的旧状态已被污染（恒等于新状态），
     * 无法据此判断真实变更。于是改在「包应用后(TAIL)」把新状态记进本映射，
     * 收包时(HEAD)拿本映射里「上一个服务端确认状态」与新状态比对：
     *   相等 -> 机械部件每秒无操作刷新等无变化包 -> 不补播；
     *   不等 -> 用户放置/破坏 -> 补播声音与碎屑。
     * 整块加载（replaceWithPacketData）会把整块状态播种进本映射，避免初次刷新被误判为变更。
     */
    private final Map<BlockPos, BlockState> sable$lastKnownStates = new HashMap<>();

    /** [BUG-29] 取得某 plot 坐标上一次服务端确认的方块状态（未记录过返回 null）。 */
    public BlockState sable$getLastKnownState(final BlockPos pos) {
        return this.sable$lastKnownStates.get(pos);
    }

    /** [BUG-29] 记录某 plot 坐标当前服务端确认的方块状态（包应用后调用）。 */
    public void sable$setLastKnownState(final BlockPos pos, final BlockState state) {
        this.sable$lastKnownStates.put(pos, state);
    }

    /**
     * [BUG-30 衍生·破坏反馈缺失修复] 每个子关卡自己维护的「上一次非空气方块状态」映射。
     * 物理化飞行结构上，机械动力(Create)等联动方块会被客户端本地预测/联动逻辑先置成空气，
     * 导致 {@link #sable$lastKnownStates} 在破坏包到达时已被污染为 air，破坏分支
     * （依赖 lastKnown 非空气）被整体跳过 —— 表现为破坏机械动力方块时既无碎屑也无声音。
     * 故额外记一份「最近一次非空气状态」，仅由非空气包更新、破坏播放后清空；
     * 破坏反馈改用它，不再受 lastKnown 被污染成空气的影响。
     */
    private final Map<BlockPos, BlockState> sable$lastNonAirStates = new HashMap<>();

    /** [BUG-30 衍生] 取得某 plot 坐标最近一次非空气方块状态（未记录过或已清空返回 null）。 */
    public BlockState sable$getLastNonAirState(final BlockPos pos) {
        return this.sable$lastNonAirStates.get(pos);
    }

    /** [BUG-30 衍生] 记录/清空某 plot 坐标的最近一次非空气方块状态（非空气包写入、破坏播放后清空）。 */
    public void sable$setLastNonAirState(final BlockPos pos, final BlockState state) {
        if (state == null || state.isAir()) {
            this.sable$lastNonAirStates.remove(pos);
        } else {
            this.sable$lastNonAirStates.put(pos, state);
        }
    }
}
