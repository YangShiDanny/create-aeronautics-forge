package dev.ryanhcode.sable.mixin.sublevel_render.block_entity_render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;

import dev.ryanhcode.sable.mixinhelpers.sublevel_render.vanilla.VanillaSubLevelBlockEntityRenderer;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;

import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashSet;
import java.util.Set;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.SortedSet;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow

    private ClientLevel level;

    /** 已上报过的残留幽灵方块实体坐标，避免每帧刷屏。 */
    @Unique
    private static final Set<Long> sable$reportedGhostBEs = new HashSet<>();

    @Shadow
    @Final
    private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

    @Unique
    private VanillaSubLevelBlockEntityRenderer sable$subLevelBlockEntityRenderer;

    // [v6] 每帧在全局 BE 循环开始前（sable$preRenderBEs）捕获的干净基座姿态（world→camera，含相机旋转）。
    // 原版循环会在调用渲染前 pushPose + translate(样板坐标-相机)（百万量级 float），
    // 子层级 BE 渲染时用 set() 精确覆盖回该基座，全程避免大数 float 运算（undo 减法在 1e6 量级
    // 精度仅 ~0.06 格且随相机旋转逐帧变化 → v1 抽搐的真因）。
    // 注：原版对栈顶的污染只有 translate（不改 normal），故基座本身无需捕获 normal。
    // [BUG-28 第二阶段·勘误] 这里原先写的是「路径 1 的 mulPoseMatrix 同样不动 normal，两路径口径一致」，
    // 把一个真实缺陷当成了「一致」而放过：两路径确实一致，但一致地**错**——
    // 1.20.1 的 mulPoseMatrix 只乘 pose 不维护 normal，导致子层级方块实体的法线
    // 停在模型空间（路径 1 是单位阵，路径 2 只剩相机旋转、缺子层级旋转），
    // 方向性明暗整体算错。现两路径均在重建 pose 之后调用 syncNormalMatrix 依 pose 重算法线。
    // [v16.1 Mixin 铁律] Mixin **根本不会**把 mixin 类里实例字段的初始化器注入到目标类构造器
    // （无论 final 与否，实测两者都无效）。字段永远是 null，等到 preRenderBEs 里
    // this.sable$beBasePose.set(...) 就 NullPointerException 崩。
    // 正确写法：声明处不给初始化器，改为在 @Inject("<init>") 里显式赋值（见下方 init 方法）。
    // 之前崩溃 (sable$beBasePose is null) 及去掉 final 后仍崩，均验证了此点。
    @Unique
    private Matrix4f sable$beBasePose;

    // [v16] 每帧在 sable$preRenderBEs（P2 路径）捕获的活跃子层级列表。
    // P4 逐区块循环直接复用此列表做幽灵判定，避免「查错容器」和「时序为空」两个坑：
    //   - sable$getPlotContainer() 是地形渲染用的容器，飞行子层级不在里面（v15 证实返回0）
    //   - SubLevelContainer.getContainer(level) 虽然是 P2 的正确容器，但 P4 执行时可能尚未注册
    //   → 在 preRenderBEs（P2 前置）捕获时已确认有效（P2 正常渲染），P4 复用即保证一致
    // [v16.1] 同 sable$beBasePose：初始化器无效，改在 <init> 注入里赋值。
    @Unique
    private List<ClientSubLevel> sable$activeSubLevels;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(final Minecraft minecraft, final EntityRenderDispatcher entityRenderDispatcher, final BlockEntityRenderDispatcher blockEntityRenderDispatcher, final RenderBuffers renderBuffers, final CallbackInfo ci) {
        this.sable$subLevelBlockEntityRenderer = new VanillaSubLevelBlockEntityRenderer(blockEntityRenderDispatcher, renderBuffers, this.destructionProgress);
        // [v16.1] Mixin 不会注入实例字段初始化器，必须在此显式初始化，否则 preRenderBEs 里
        // this.sable$beBasePose.set(...) 与 P4 循环遍历 this.sable$activeSubLevels 都会 NPE。
        this.sable$beBasePose = new Matrix4f();
        this.sable$activeSubLevels = List.of();
    }

    // [1.20.1 物理化激光·修复 v2] Forge 1.20.1 给 renderLevel 的「全局方块实体」循环
    // 打了原版没有的视锥剔除补丁（字节码 47.4.21 偏移 1595：getRenderBoundingBox → Frustum.isVisible，
    // 不可见直接跳过整个渲染调用）。子层级方块实体（激光 shouldRenderOffScreen==true 归入全局集合）
    // 的方块坐标在百万格外的样板区，用样板坐标测视锥必然失败 → 下方 ordinal=1 的包裹注入根本不执行
    // → 物理化后激光完全不渲染。
    //
    // 修复策略（第三十轮修正）：把子层级 BE 的「局部坐标包围盒」用 logicalPose 变换到世界坐标，
    // 交给世界 frustum 判定（与 block_entity_visible mixin 的 frustum 判定同口径）。
    // 旧实现用 renderPose().position()（子层级原点）为中心的 256 半径盒：当玩家看向远离原点的发射器时，
    // 原点落在视锥后方 → 整盒判不可见 → 激光被剔除（症状「只有视野里没发射器才有光」）。
    // 用 logicalPose 变换真实盒则盒落在激光真实位置，任意视角都在视野内。
    // 注：用 logicalPose（每 tick 更新的逻辑位姿，无 partialTick 插值）而非 renderPose，避免逐帧抖动。
    // globalBlockEntities 数量极少（仅 shouldRenderOffScreen 的 BE），按真实盒测视锥不影响性能。
    // getRenderBoundingBox 是 Forge 补丁自带方法（字节码明文名），必须 remap=false。
    @WrapOperation(method = "m_109599_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;getRenderBoundingBox()Lnet/minecraft/world/phys/AABB;", ordinal = 1, remap = false))
    public AABB sable$remapGlobalBERenderBox(final BlockEntity blockEntity, final Operation<AABB> original) {
        final AABB box = original.call(blockEntity);
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(blockEntity);
        if (subLevel == null) {
            return box;
        }
        // 无限/超大包围盒本就不会被剔除，原样返回。
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)
                || box.getXsize() > 1.0e6 || box.getYsize() > 1.0e6 || box.getZsize() > 1.0e6) {
            return box;
        }
        // [BUG-30·第三十轮·根因修复] 把子层级方块实体的「局部坐标包围盒」用 logicalPose 变换到世界坐标，
        // 交给世界 frustum 判定（与 block_entity_visible mixin 的 frustum 判定同口径）。
        // 旧实现用 renderPose().position()（子层级原点）为中心的 256 半径盒：当玩家看向远离原点的发射器时，
        // 原点落在视锥后方 → 整盒判为不可见 → 激光被剔除，正是用户那句「只有视野里没发射器才有光」的根因。
        // 改为真实盒变换后，激光盒落在激光真实位置，任意视角都在视野内 → 不再被误剔。
        final BoundingBox3d bb = new BoundingBox3d(box);
        final AABB worldBox = bb.transform(subLevel.logicalPose(), bb).toMojang();
        final AABB result = worldBox.inflate(2.0, 2.0, 2.0);
        return result;
    }

    // [1.20.1 物理化激光·修复 v6] 子层级的全局方块实体（激光 shouldRenderOffScreen==true）**只**由本注入点
    // （Forge 全局循环 ordinal=1）渲染：VanillaSubLevelRenderDispatcher.renderBlockEntities 只遍历
    // getRenderableBlockEntities()，而激光被 vanilla 编译逻辑分到 getGlobalBlockEntities()，故路径 1 不碰它。
    // 绝不能在此跳过渲染（v4 跳过 → 激光完全消失）。
    //
    // v5 两处数学错误（导致激光被画到两百万格外 → 完全看不见）：
    //   ① 原版全局 BE 循环在调用本方法前已 pushPose + translate(样板坐标-相机)（百万量级），
    //      v5 未消除该污染就直接叠 transformation → 位置错到天边。
    //   ② renderSingleBE 相机参数传了 -rotationPoint（路径 1 传的是 -chunkOffset = +rotationPoint），
    //      内部 translate(pos - 参数) 变成 pos + rotationPoint，又叠一个百万偏移。
    // v1 做 undo 减法却抽搐的真因：float 矩阵在 1e6 量级精度仅 ~0.06 格且残差随相机旋转逐帧变化。
    //
    // v6 正确做法：不做任何大数加减 —— sable$preRenderBEs 在原版 translate 之前捕获干净基座矩阵，
    // 此处用 set() 整体覆盖被污染的栈顶（translate 不改 normal 矩阵，无需恢复 normal），
    // 再叠 transformation，与路径 1 完全同链：[基座 world→camera]∘[transformation]∘[translate(pos-rotationPoint)]。
    @WrapOperation(method = "m_109599_", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;m_112267_(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", ordinal = 1, remap = false))
    public <E extends BlockEntity> void sable$renderBlockEntities(final BlockEntityRenderDispatcher instance, final E blockEntity, final float pt, final PoseStack poseStack, final MultiBufferSource multiBufferSource, final Operation<Void> original) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(blockEntity);
        if (subLevel == null) {
            // [BUG-03 v30·P2 主世界分支] 全局循环里的主世界方块实体（含 shouldRenderOffScreen 的残留）。
            // 若该方块实体已从主世界真实数据中移除（装配清空后区块编译缓存里的过期引用），跳过渲染。
            if (sable$isMainWorldGhost(blockEntity)) {
                return;
            }
            original.call(instance, blockEntity, pt, poseStack, multiBufferSource);
            return;
        }

        // [下移诊断] 路径2 = 主世界全局循环里的子层级方块实体（激光等 shouldRenderOffScreen 的）。

        // renderData 可能在子层级加入容器与渲染数据构建之间短暂为 null，回退 vanilla 渲染避免崩溃。
        final SubLevelRenderData subLevelRenderData = subLevel.getRenderData();
        if (subLevelRenderData == null) {
            original.call(instance, blockEntity, pt, poseStack, multiBufferSource);
            return;
        }

        final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        final Vec3 cameraPosition = camera.getPosition();
        final Vector3dc rotationPoint = subLevel.renderPose().rotationPoint();
        final Matrix4f transformation = subLevelRenderData.getTransformation(cameraPosition.x, cameraPosition.y, cameraPosition.z);

        // 设置子层级相机位置供 BlockEntityRenderDispatcherMixin 做光照/可见性计算（与路径 1 同公式）。
        final BlockEntityRenderDispatcherExtension extension = (BlockEntityRenderDispatcherExtension) this.blockEntityRenderDispatcher;
        final Vector3f sableCameraPosition = new Vector3f();
        // [v10 回归上游] getTransformation 已恢复「减相机」，故逆变换须作用于原点（视图原点即相机位置），
        // 与上游 NeoForge 2.0.3 一字不差。若仍传真实相机坐标会多减一次相机，光照/可见性取样点错位。
        transformation.invert(new Matrix4f()).transformPosition(sableCameraPosition.zero());
        extension.sable$setCameraPosition(new Vec3(sableCameraPosition.x + rotationPoint.x(), sableCameraPosition.y + rotationPoint.y(), sableCameraPosition.z + rotationPoint.z()));

        // 栈顶当前 = 基座 ∘ translate(样板坐标-相机)（原版已 push，事后会 pop，可放心覆盖）。
        // 用干净基座整体覆盖（消除百万格污染），再叠子层级 transformation。
        poseStack.last().pose().set(this.sable$beBasePose).mul(transformation);
        // [BUG-28 第二阶段·可动部件方向性光照] 上一行只 set 了 pose，normal 还是原版栈顶那份
        // （只含相机旋转、缺子层级 transformation 的旋转）。方块实体渲染器（含 Create 的
        // SuperByteBuffer）用 normal 变换顶点法线来算方向性明暗，normal 与 pose 不同步
        // 就会让子层级里旋转过的部件明暗算错 —— 某些面被压黑，看着像「只显示一个面」。
        // 与路径 1 同口径，统一由 syncNormalMatrix 依 pose 重算。
        dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher.syncNormalMatrix(poseStack);
        // 相机参数与路径 1 严格同号（-chunkOffset = +rotationPoint）：
        // renderSingleBE 内部 translate(pos - rotationPoint) 得到小量级局部坐标。
        this.sable$subLevelBlockEntityRenderer.renderSingleBE(blockEntity, poseStack, pt, rotationPoint.x(), rotationPoint.y(), rotationPoint.z());

        extension.sable$setCameraPosition(null);
    }

    // [BUG-03 v33] 地面幽灵判定：给定主世界方块实体，若其坐标落在任一活跃子层级的
    // 「发射位(地面)主世界包围盒」(ClientSubLevel.sable$launchBounds) 内，说明它已被装配进子层级、
    // 应在高空飞行副本里渲染，主世界这份是残留幽灵，应跳过。
    // 之所以不用「实时 chunk 是否已移除」判定：实测升空后客户端实时 chunk 仍持有这些 BE
    // (inMainChunk=SAME)，过期引用假设不成立；正确判据是「坐标是否被活跃子层级认领」。
    @Unique
    private static boolean sable$isLaser(final BlockEntity be) {
        final String name = be.getClass().getName().toLowerCase();
        return name.contains("laser");
    }

    @Unique
    private boolean sable$isMainWorldGhost(final BlockEntity be) {        final double x = be.getBlockPos().getX() + 0.5;
        final double y = be.getBlockPos().getY() + 0.5;
        final double z = be.getBlockPos().getZ() + 0.5;
        // [BUG-03 v34] 改查静态发射位包围盒登记表（ClientSubLevel.SABLE_LAUNCH_BOUNDS），
        // 彻底绕开 SubLevelContainer.getContainer(this.level) 在 P4 路径返回空的诡异问题。
        return ClientSubLevel.sable$isAnyLaunchBoundsContains(x, y, z);
    }
    // 如物理组装器/拉杆）在 flywheel:off 下用 vanilla 渲染器泄漏 poseStack 的位置。
    // v6 只拦了 ordinal=1（全局/激光），没拦 ordinal=0，故主世界 BE 的泄漏仍导致
    // renderLevel 末尾 'Pose stack not empty' 崩溃。
    //
    // 修复策略（严格遵守 MEMORY 第十五条「不碰逐区块循环的子层级分支」）：
    //   · subLevel != null（子层级 BE）→ **完全走原版 original.call（与 v6 一字不差）**，
    //     对热气球渲染逻辑零改动，绝不在此重渲染（v2 失败的根因即在此处用隔离栈重渲染子层级）。
    //   · subLevel == null（主世界 BE）→ 用独立 PoseStack 副本隔离渲染：渲染器内部任何
    //     push/pop 泄漏只发生在 isolated 栈上，主 poseStack 完全不受影响 → 崩溃消除，
    //     且显示位置正确（isolated 以当前含 translate 偏移的矩阵初始化）。
    @WrapOperation(method = "m_109599_", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;m_112267_(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", ordinal = 0, remap = false))
    public <E extends BlockEntity> void sable$isolateMainWorldBEs(final BlockEntityRenderDispatcher instance, final E blockEntity, final float pt, final PoseStack poseStack, final MultiBufferSource multiBufferSource, final Operation<Void> original) {
        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(blockEntity);
        if (subLevel != null) {
            // [下移诊断·重点嫌疑] 路径3 = 主世界逐区块循环却遇到子层级方块实体。
            // 这条路径不会施加任何子层级变换，方块实体会被画在装配前的老位置 ——
            // 正是「热气球升空后拉杆 / 组装器数字 / 推进器留在地面」的最大嫌疑路径。
            // [v22 决策] 本次测试 P3 探针未触发，说明物理化结构幽灵不走 P3；且 P3 在逐区块循环
            // (ordinal=0) 早于 P2 前置设置 sable$beBasePose，此处若施加变换会用到上一帧陈旧基座，
            // 可能引发热气球 BE 与 P1 差一帧抖动。故回退为原版 original.call（与 v7 一致、对气球零改动），
            // 幽灵抑制交由已验证的 P2+P4 路径覆盖。若后续探针证实 P3 命中，再单独用「P3 起始捕获基座」方案修复。
            // 子层级 BE 完全走原版（与 v6 一致），对热气球渲染逻辑零改动
            original.call(instance, blockEntity, pt, poseStack, multiBufferSource);
            return;
        }

        // [下移诊断] 路径4 = 真正的主世界方块实体（正常情况）。

        // [BUG-03 v22·主世界重复方块实体抑制（静态注册表·彻底绕过容器）]
        // 架构同前（服务端原结构方块留在主世界，客户端 plot 子层级渲染飞行副本，
        // P4 逐区块循环画出主世界残留 → 表现为升空后拉杆/数字管下移）。
        //
        // v16-v21 全部失败的根本原因（v21 最终证实）：
        //   P4 的 @WrapOperation 注入上下文里，无论用容器查询(SubLevelContainer.getContainer)
        //   还是字段复用(this.sable$activeSubLevels)，都返回空列表——即使物理化 2.7 分钟后仍为 0。
        //   推测 @Inject(method="renderLevel") 与 @WrapOperation(method="m_109599_") 可能命中了
        //   不同方法实例（Forge SRG 映射差异），导致 preRenderBEs 对 P4 不可见。
        //   无论具体根因为何，7 个版本证明「在 P4 内部获取子层级引用」此路不通。
        //
        // ================= [BUG-03 v30 正式修复：过期方块实体过滤] =================
        // v22~v29 的「发射位包围盒 + 离位守卫」抑制机制经 2026-08-01 16:10 日志证实
        // 一次都没拦截成功（全日志 [P4-拦截] 计数为 0），方向错误，此处彻底弃用。
        //
        // 真凶（同一份日志实证）：
        //   · v29 诊断 [DIAG-v29·主世界残留] 显示主世界真实数据里这些方块实体**已经不存在**
        //     （只剩 20 格外一个无关拉杆），说明服务端 / 区块数据侧的清理是**正确**的；
        //   · 但同一时刻 [BEPROBE] P4-mainworld 仍渲染出高度计 (16.5,-58.5,1.5)、
        //     拉杆 (11.5,-58.5,2.5)、数字管、燃烧器、物理组装器全套，全停在地面原位。
        //   → 1.20.1 LevelRenderer 逐区块方块实体循环读的是**区块编译缓存**里的引用列表
        //     （CompiledChunk#getRenderableBlockEntities），装配清空 section 时若该区块
        //     没被重新编译，列表就一直持有已移除的旧对象并逐帧渲染。
        //
        // 这也精确解释了「以燃烧器为中心东侧出问题、西侧没事」：取决于所在**区块**
        // 有没有恰好被重编译（燃烧器 x=12 属区块 0，东侧高度计 x=16 属区块 1），与方向无关。
        //
        // 修复：渲染前回查区块实时方块实体表，已被移除 / 替换的直接跳过。零误杀、
        // 不依赖包围盒、不依赖升空守卫，未装配的地面热气球本体也完全不受影响。
        // [BUG-03 v33] 地面幽灵判定：坐标落在任一活跃子层级发射位(地面)主世界包围盒内 → 跳过。
        // 实测客户端实时 chunk 仍持有这些 BE(inMainChunk=SAME)，过期引用假设不成立，
        // 故改用「坐标是否被活跃子层级认领」判定（见 sable$isMainWorldGhost）。
        if (sable$isMainWorldGhost(blockEntity)) {
            return;
        }

        // 主世界 BE：独立 PoseStack 隔离，防止 flywheel:off 下 vanilla 渲染器泄漏污染主栈
        final PoseStack isolated = new PoseStack();
        isolated.last().pose().set(new Matrix4f(poseStack.last().pose()));
        isolated.last().normal().set(new Matrix3f(poseStack.last().normal()));
        original.call(instance, blockEntity, pt, isolated, multiBufferSource);
    }

    @Inject(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;globalBlockEntities:Ljava/util/Set;", shift = At.Shift.BEFORE, ordinal = 0))
    public void sable$preRenderBEs(final PoseStack poseStack, final float partialTick, final long finishTimeNano, final boolean bl, final Camera camera, final GameRenderer gameRenderer, final LightTexture lightTexture, final Matrix4f matrix4f, final CallbackInfo ci) {
        final List<ClientSubLevel> subLevels = SubLevelContainer.getContainer(this.level).getAllSubLevels();
        // [v16] 捕获活跃子层级列表供 P4 幽灵判定复用（P2 能正常渲染说明此列表有效）
        this.sable$activeSubLevels = subLevels;
        final Vec3 cameraPosition = camera.getPosition();
        // [BUG-03 真凶修复 v10·基座回归原版姿态栈] 基座必须且只能是「原版 renderLevel 姿态栈的栈顶」，
        // 即纯相机旋转，不含任何平移。
        //
        // 字节码实证（joined-1.20.1-srg，m_109599_ 偏移 1413-1458）：1.20.1 两处方块实体循环
        // 都用 aload_1（renderLevel 传入的 poseStack）pushPose + translate(方块坐标-相机) 后渲染，
        // 即原版方块实体的模型视图 = [poseStack 栈顶(相机旋转)] · translate(世界坐标-相机)。
        // 而上游 1.21.1 用的是 new PoseStack()（单位阵），因为 1.21.1 把相机旋转搬进了全局
        // RenderSystem modelView —— 这正是两个版本的分水岭，照抄上游必然丢旋转。
        //
        // 与地形黄金参照同源：impl/vanilla/LevelRendererMixin 里
        //   realModelView = new Matrix4f(poseStack.last().pose())
        // 地形能正确显示即证明该矩阵就是「纯相机旋转」。方块实体用同一个，两者才能对齐。
        //
        // v9 错误：用 camera.rotation() 重建。Camera.rotation() 是「局部->世界」旋转
        // （setRotation 里 forwards=(0,0,1) 经它旋转得到朝向），而模型视图要的是「世界->相机」，
        // 二者互为逆矩阵。方向取反后，相机一转方块实体就绕着乱跑，
        // 正是「物理化后方块实体跟着视角到处移动」的直接原因。
        // 又因旋转不改变向量模长，[BEVIEW] 探针的模长看起来始终正常，掩盖了该错误。
        final Matrix4f modelView = new Matrix4f(poseStack.last().pose());
        // [v36 BASEPOSE] 基座必须是纯相机旋转，平移分量恒为 0。
        // 非 0 即证明此前有渲染 pushPose 漏了 popPose，栈顶被污染 ——
        // 这会让方块实体路径整体偏移，而地形路径（更早取 modelView）不受影响。
        Sable.SABLE_BE_BASE_POSE = modelView;
        // [v6] 同步捕获干净基座供 sable$renderBlockEntities（路径2）覆盖被原版污染的栈顶。
        this.sable$beBasePose.set(modelView);
        try {
            SubLevelRenderDispatcher.get().renderBlockEntities(subLevels, this.sable$subLevelBlockEntityRenderer, cameraPosition.x, cameraPosition.y, cameraPosition.z, partialTick);
        } finally {
            Sable.SABLE_BE_BASE_POSE = null;
        }
    }
}