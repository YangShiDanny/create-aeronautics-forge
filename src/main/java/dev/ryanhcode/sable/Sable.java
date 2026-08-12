package dev.ryanhcode.sable;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelTicketLoadingSystem;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.network.tcp.SableTCPPackets;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyTypes;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointObserver;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Sable {

    public static final String MOD_NAME = "Sable";
    public static final String MOD_ID = "sable";

    public static final String ISSUE_TRACKER_URL = "https://github.com/ryanhcode/sable/issues";

    public static final Logger LOGGER = LogUtils.getLogger();
    // 1.20.1 移植修复：renderLevel 姿态栈的相机旋转基座（子关卡方块实体渲染用，非调试）
    public static org.joml.Matrix4f SABLE_BE_BASE_POSE = null;
    public static final ActiveSableCompanion HELPER = (ActiveSableCompanion) SableCompanion.INSTANCE;

    /**
     *     /**
     * [BUG-03 v23] 已追踪子层级的「地面发射位」注册表。
     * 每条 = 发射位主世界包围盒 + 子层级引用 + 注册时发射位世界坐标中心。
     * 在 ClientSubLevel.forceUpdateBounds()（追踪开始，子层级在发射位）冻结并注册。
     * P2/P4 路径直接读此静态列表——彻底绕过「容器查询在 Mixin 上下文返回空」的 v16-v21 死胡同。
     *
     * <p><b>v23 关键守卫</b>：只有当子层级当前位置离开发射位超过阈值时才返回命中。
     * 没有这个守卫，追踪一开始（子层级还在地面）就拦截 -> 热气球本体 BE 全部消失（误杀）。
     */
    public static final java.util.List<Sable$RestEntry> SABLE_REST_ENTRIES =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** [BUG-03 v25] 发射位注册条目：仅持有子层级引用 + 发射位时的姿态（launchPose）。
     *  发射位包围盒<b>不在此刻冻结</b>——StartTracking 时子层级 chunk 尚未加载完，
     *  枚举方块实体会得到 beCount=0，导致框退化成过时的 plot 包围盒（漏掉后期放置的东南向方块）。
     *  改为：首次进入 P4 判定且子层级已升空时，由 ClientSubLevel.sable$ensureRestBoundsFrozen()
     *  用 launchPose 枚举当前所有方块实体（含后期放置的）构建精确框，幂等冻结。 */
    public static final class Sable$RestEntry {
        /** 对应的子层级客户端实例 */
        public final dev.ryanhcode.sable.sublevel.ClientSubLevel subLevel;
        /** 子层级在发射位（StartTracking 时）的姿态；用于把方块实体 sector 坐标变换到发射位世界坐标 */
        public final dev.ryanhcode.sable.companion.math.Pose3dc launchPose;

        public Sable$RestEntry(final dev.ryanhcode.sable.sublevel.ClientSubLevel subLevel) {
            this.subLevel = subLevel;
            // 发射位姿态在 StartTracking 时已记录到 subLevel.sable$launchPose()
            this.launchPose = subLevel.sable$launchPose();
        }

        /** 子层级是否已真正离开发射位（Y 偏移 > 5 格 或 总距离 > 10 格） */
        public boolean hasLeftLaunch() {
            if (this.launchPose == null) {
                // 防御：发射位姿态尚未记录（理论不会发生，注册前已 forceUpdateBounds）
                return false;
            }
            final org.joml.Vector3dc currentPos = this.subLevel.renderPose().position();
            final org.joml.Vector3dc launchPos = this.launchPose.position();
            final double dx = currentPos.x() - launchPos.x();
            final double dy = currentPos.y() - launchPos.y();
            final double dz = currentPos.z() - launchPos.z();
            // v23~v27 因 launchPose 存的是 logicalPose() 的<b>可变引用</b>（每 tick 被原地改写），
            // 位移恒为 0、守卫恒 false、抑制从未触发 —— 这是「依旧残留」的真根因；
            // v28 起改为深拷贝快照，守卫会随升空正常翻转为 true。
            return Math.abs(dy) > 5.0 || (dx * dx + dy * dy + dz * dz) > 100.0;
        }
    }

    /**
     * [BUG-03 v23] 判定给定世界坐标方块位置是否落在某个**已离开发射位**的子层级
     * 「地面发射位主世界包围盒」内。
     * 双重守卫：
     * 1. 静态表为空 -> false（不误伤原版 BE）
     * 2. 子层级还在发射位没飞走 -> false（不误杀还没升空的热气球本体）
     */
    public static boolean sable$inRestMainWorldBounds(final net.minecraft.core.BlockPos pos) {
        if (SABLE_REST_ENTRIES.isEmpty()) {
            return false;
        }
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();
        for (final Sable$RestEntry entry : SABLE_REST_ENTRIES) {
            // [BUG-03 v25] 幂等冻结：首次进入此处（此时 chunk 已加载、主世界残留已出现）
            // 用发射位姿态枚举子层级全部方块实体，构建精确覆盖（含后期放置的）发射位框。
            // 若 chunk 尚未就绪（beCount=0）则本次不冻结、下帧再试，绝不用过时 plot 框兜底。
            entry.subLevel.sable$ensureRestBoundsFrozen();
            // 守卫1：发射位框尚未成功冻结（chunk 仍没就绪）则不拦截，避免用未就绪的默认空框误杀
            if (!entry.subLevel.sable$restBoundsFrozen()) {
                continue;
            }
            // 守卫2：子层级必须已离开发射位，否则不拦截（防止误杀地面热气球本体）
            if (!entry.hasLeftLaunch()) {
                continue;
            }
            final dev.ryanhcode.sable.companion.math.BoundingBox3dc b = entry.subLevel.sable$restMainWorldBounds();
            if (x >= b.minX() && x <= b.maxX()
                    && y >= b.minY() && y <= b.maxY()
                    && z >= b.minZ() && z <= b.maxZ()) {
                return true;
            }
        }
        return false;
    }

    /**
     * [BUG-03 v30 正式修复] 判定一个「主世界方块实体」是否已经是<b>过期幽灵</b>。
     *
     * <p><b>根因（2026-08-01 16:10 日志实证）</b>：装配（物理化）把原结构方块从主世界
     * 清空之后，主世界真实区块数据里这些方块实体<b>已经不存在</b>
     * （v29 诊断 {@code [DIAG-v29·主世界残留]} 只剩 20 格外一个无关拉杆），
     * 但同一时刻 {@code [BEPROBE] P4-mainworld} 仍渲染出高度计 (16.5,-58.5,1.5)、
     * 拉杆 (11.5,-58.5,2.5)、数字管、燃烧器、物理组装器全套 —— 全停在地面原位。
     *
     * <p>原因：1.20.1 的 {@code LevelRenderer} 逐区块方块实体循环遍历的是
     * <b>区块编译缓存</b>（{@code CompiledChunk#getRenderableBlockEntities}）中的引用列表，
     * 而不是区块实时数据。装配清空 section 时若没触发该区块重新编译，
     * 这份列表就会一直持有<b>已被移除</b>的旧方块实体对象，并逐帧把它们画在原地。
     *
     * <p>这精确解释了「以燃烧器为中心、东侧出问题西侧没事」：是否残留取决于所在
     * <b>区块</b>有没有恰好被重编译，而不是方向 —— 燃烧器 x=12 属区块 0，
     * 东侧高度计 / 数字管 x=16 属区块 1，分属不同区块。也解释了本次「高度计貌似好了」的偶然性。
     *
     * <p><b>判定方式</b>：先看 {@code isRemoved()}，再回查所在区块的实时方块实体表 ——
     * 若该坐标上的方块实体已不是它自己（被移除或被替换），即判定为过期幽灵、跳过渲染。
     * 活着的正常主世界方块实体一定能在表里找到自己，因此<b>零误杀</b>；
     * 也不再依赖包围盒与离位守卫（那套机制经日志证实一次都没拦截成功）。
     *
     * @param be 待判定的方块实体
     * @return true = 已过期，应跳过渲染
     */
    public static boolean sable$isStaleMainWorldBE(final Level mainLevel, final net.minecraft.world.level.block.entity.BlockEntity be) {
        if (be == null) {
            return true;
        }
        if (be.isRemoved()) {
            return true;
        }
        final net.minecraft.core.BlockPos pos = be.getBlockPos();
        final net.minecraft.world.level.chunk.LevelChunk chunk =
                mainLevel.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false);
        if (chunk == null) {
            // 区块未加载时无法判定，保守放行（此时本来也不会被渲染）
            return false;
        }
        return chunk.getBlockEntities().get(pos) != be;
    }

    @ApiStatus.Internal
    public static void init() {
        SableTCPPackets.init();
        SableTags.register();
        PhysicsBlockPropertyTypes.register();
        ForceGroups.register();

        LOGGER.info("{} loaded!", MOD_NAME);
    }
    /**
     * @param path the path to the resource
     * @return a {@link ResourceLocation} with a {@link Sable#MOD_ID} namespace
     */
    public static ResourceLocation sablePath(final String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    /**
     * Initializes & sets up sub-level containers to contain physics systems and tracking systems by default.
     *
     * @param level     the level to initialize the container for
     * @param container the sub-level container to initialize
     */
    @ApiStatus.Internal
    public static void defaultSubLevelContainerInitializer(final Level level, final SubLevelContainer container) {
        if (container instanceof final ServerSubLevelContainer serverContainer) {
            final ServerLevel serverLevel = serverContainer.getLevel();

            // Give the container a physics system
            final SubLevelPhysicsSystem physicsSystem = new SubLevelPhysicsSystem(serverLevel);
            physicsSystem.initialize();
            serverContainer.takePhysicsSystem(physicsSystem);

            // Give it a tracking system to notify clients
            final SubLevelTrackingSystem trackingSystem = new SubLevelTrackingSystem(serverLevel);
            serverContainer.takeTrackingSystem(trackingSystem);

            serverContainer.addObserver(physicsSystem);
            serverContainer.addObserver(trackingSystem);
            serverContainer.addObserver(new SubLevelTrackingPointObserver(serverLevel));
            serverContainer.addObserver(new SubLevelTicketLoadingSystem(serverContainer));

            // [1.20.1 移植修正] 物理方块属性的应用已移至 PhysicsBlockPropertiesDefinitionLoader.apply()
            // （资源重载监听器，在方块标签注册完成后执行）。此处不再提前调用 applyAll()，
            // 否则会因标签尚未就绪而刷出大量 "Unknown tag" 报错，且物理属性实际未被写入。
        }
    }

    private static final List<String> WITTIER_COMMENTS = List.of(
            "Hi. I'm Sable and I dislike float casts",
            "*plays dead*",
            "It wasn't me (it probably was)",
            "Lets see if this is repro or cosmic radiation",
            "What did you do",
            "ooprs",
            "dude... thats so mossed up...",
            "What is this thing",
            "I am capable of so much more than being a crash log. There has to be more to this world.",
            "tfw no sable gf",
            "someone please advice devs that pancakes are serve"
    );

    private static String getWittierComment() {
        try {
            if (LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY && Util.getMillis() % 2 == 0) {
                return "It's sable sunday";
            }
            return WITTIER_COMMENTS.get((int) (Util.getMillis() % WITTIER_COMMENTS.size()));
        } catch (final Throwable t) {
            return "Wittier comment unavailable :(";
        }
    }

    public static String getCrashHeader() {
        return "\n// " + getWittierComment() +
                "\nPlease make sure this issue is not caused by Sable before reporting it to other mod authors." +
                "\nIf you cannot reproduce it without Sable, file a report on the issue tracker" +
                "\n" + ISSUE_TRACKER_URL +
                "\n";
    }
}
