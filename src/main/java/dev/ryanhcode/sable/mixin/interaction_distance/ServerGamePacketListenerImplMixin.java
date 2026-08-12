package dev.ryanhcode.sable.mixin.interaction_distance;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.ForgeMod;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.20.1 移植修复：右键使用物品(handleUseItemOn)的服务端距离检查支持子关卡。
 *
 * 背景：与破坏路径(ServerPlayerGameModeMixin)同款坑。Forge 1.20.1 在
 * ServerGamePacketListenerImpl.handleUseItemOn 里用 ServerPlayer.canReach /
 * canReachRaw 做距离检查；子关卡方块本地坐标偏移约 2048 万格，检查恒为
 * "too far"，服务端静默丢弃右键操作 —— 表现为右键物理化结构无任何反应
 * (结构图解放不上去、任何物品右键子关卡方块都没反应)。
 *
 * 本 Mixin 重定向这两个调用：目标方块在子关卡内且换算后仍在交互范围内则放行。
 * 注：canReach/canReachRaw 是 Forge 补丁新增方法(非原版混淆方法)，target 直接写
 * 官方名即可；handleUseItemOn 是原版方法走 refmap。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @Redirect(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;canReach(Lnet/minecraft/core/BlockPos;D)Z")
    )
    private boolean sable$useCanReachSubLevel(final ServerPlayer player, final BlockPos pos, final double slop) {
        final boolean orig = player.canReach(pos, slop);
        if (orig) {
            return true;
        }
        return sable$reachableInSubLevel(player, pos, slop);
    }

    @Redirect(
            method = "handleUseItemOn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;canReachRaw(Lnet/minecraft/core/BlockPos;D)Z")
    )
    private boolean sable$useCanReachRawSubLevel(final ServerPlayer player, final BlockPos pos, final double slop) {
        final boolean orig = player.canReachRaw(pos, slop);
        if (orig) {
            return true;
        }
        return sable$reachableInSubLevel(player, pos, slop);
    }

    /**
     * [1.20.1 移植修复] 实体交互(handleInteract)的服务端距离检查支持子关卡。
     *
     * 背景：图解(DiagramEntity)等物理化后位于 plot 坐标(约 2048 万格外)的实体，
     * Forge 1.20.1 在 ServerGamePacketListenerImpl.handleInteract 里唯一的距离门是
     * this.player.canReachRaw(entity, 3.0)，plot 内实体恒判"太远"，服务端静默丢弃
     * 交互包 —— 表现为右键图解无任何反应(interactAt 根本不会被调用)。
     * 方块交互(handleUseItemOn)已由上面两个重定向修复，本方法补齐实体这条链路。
     */
    @Redirect(
            method = "handleInteract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;canReachRaw(Lnet/minecraft/world/entity/Entity;D)Z")
    )
    private boolean sable$interactCanReachRawSubLevel(final ServerPlayer player, final Entity target, final double padding) {
        final boolean orig = player.canReachRaw(target, padding);
        if (orig) {
            return true;
        }
        // 与方块版 sable$reachableInSubLevel 同口径：用 (Level, Position) 重载取子层级，
        // 实体 position 位于 plot 局部坐标(约 2048 万格)与子层级方块同级。
        final SubLevel subLevel = Sable.HELPER.getContaining(target.level(), target.position());
        if (subLevel == null) {
            return false;
        }
        final double range = player.getAttributeValue(ForgeMod.ENTITY_REACH.get()) + padding;
        // [修复·物理化] 子层级实体 position/包围盒位于 plot 局部坐标(约 2048 万格)，与子层级
        // 方块一致(区块同在 plot 区域)。玩家眼睛是宿主主世界世界坐标，须投影进子层级局部空间
        // 再与实体的局部包围盒比(刚体变换下距离不变)。原"世界眼睛 vs 世界包围盒"写法错把
        // plot 包围盒当世界坐标→差 2048 万→够得着检查恒失败→左键攻击/右键交互物理化结构
        // 实体(结构图解等)无任何反应。
        Pose3dc pose = subLevel.logicalPose();
        if (player.level() instanceof final LevelPoseProviderExtension extension) {
            pose = extension.sable$getPose(subLevel);
        }
        final Vec3 localEye = JOMLConversion.toMojang(pose.transformPositionInverse(JOMLConversion.toJOML(player.getEyePosition())));
        final double dSq = target.getBoundingBox().inflate(target.getPickRadius()).distanceToSqr(localEye);
        return dSq < range * range;
    }

    private static boolean sable$reachableInSubLevel(final ServerPlayer player, final BlockPos pos, final double slop) {
        final SubLevel subLevel = Sable.HELPER.getContaining(player.level(), pos);
        if (subLevel == null) {
            return false;
        }
        final double range = player.getBlockReach() + slop;
        // [修复·物理化] 子层级方块 pos 是局部/plot 坐标(如 2048 万格)，玩家眼睛是宿主主世界
        // 世界坐标。物理化(非单位阵 logicalPose)时二者不在同一空间，原"世界眼睛 vs 局部 pos"
        // 距离恒为约 2048 万→够得着检查恒失败→右键 use(坐垫/放置方块)被服务端静默丢弃。
        // 正确做法：把玩家眼睛投影进子层级局部空间，再与局部 pos 比。刚体变换下距离保持不变，
        // 物理化与静态两种情形都正确(取 pose 口径与 clip() 一致)。
        Pose3dc pose = subLevel.logicalPose();
        if (player.level() instanceof final LevelPoseProviderExtension extension) {
            pose = extension.sable$getPose(subLevel);
        }
        final Vec3 localEye = JOMLConversion.toMojang(pose.transformPositionInverse(JOMLConversion.toJOML(player.getEyePosition())));
        final double dSq = new AABB(pos).distanceToSqr(localEye);
        return dSq < range * range;
    }
}
