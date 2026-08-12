package dev.ryanhcode.sable.mixin.interaction_distance;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.20.1 移植修复：让服务端方块破坏的“够得着”(reach) 距离检查支持子关卡。
 *
 * 背景：源版本(1.21) 破坏距离检查走 Player.canInteractWithBlock，由被禁用的
 * interaction_distance.PlayerMixin 拦截放行子关卡方块。1.20.1 没有该 API，
 * 破坏距离检查改由 Forge 的 ServerPlayer.canReach / canReachRaw 完成，而这两个
 * 方法内部使用 Vec3.distanceToSqr(Vec3)；interaction_distance.EntityMixin 覆盖的
 * 是 Entity.distanceToSqr，拦不到它。导致子关卡方块(偏移约 2048 万格)破坏时距离
 * 检查恒为 "too far"，服务端拒绝破坏 —— 表现为左键有破坏音效与碎屑但方块删不掉。
 *
 * 本 Mixin 重定向 handleBlockBreakAction 里的 canReach / canReachRaw 两个调用：
 * 当目标方块位于某子关卡内、且用子关卡相对坐标换算后玩家仍在交互范围内时，放行。
 */
@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {

    @Redirect(
            method = "handleBlockBreakAction",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;canReach(Lnet/minecraft/core/BlockPos;D)Z")
    )
    private boolean sable$canReachSubLevel(final ServerPlayer player, final BlockPos pos, final double slop) {
        if (player.canReach(pos, slop)) {
            return true;
        }
        return sable$reachableInSubLevel(player, pos, slop);
    }

    @Redirect(
            method = "handleBlockBreakAction",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;canReachRaw(Lnet/minecraft/core/BlockPos;D)Z")
    )
    private boolean sable$canReachRawSubLevel(final ServerPlayer player, final BlockPos pos, final double slop) {
        if (player.canReachRaw(pos, slop)) {
            return true;
        }
        return sable$reachableInSubLevel(player, pos, slop);
    }

    private static boolean sable$reachableInSubLevel(final ServerPlayer player, final BlockPos pos, final double slop) {
        final SubLevel subLevel = Sable.HELPER.getContaining(player.level(), pos);
        if (subLevel == null) {
            return false;
        }
        final double range = player.getBlockReach() + slop;
        // [修复·物理化] 与 ServerGamePacketListenerImplMixin 同口径：把玩家眼睛投影进子层级
        // 局部空间，再与局部 pos 比。物理化(非单位阵 logicalPose)时"世界眼睛 vs 局部 pos"恒为
        // 约 2048 万→左键破坏被服务端静默拒绝；刚体变换下距离不变，两种情形都正确。
        Pose3dc pose = subLevel.logicalPose();
        if (player.level() instanceof final LevelPoseProviderExtension extension) {
            pose = extension.sable$getPose(subLevel);
        }
        final Vec3 localEye = JOMLConversion.toMojang(pose.transformPositionInverse(JOMLConversion.toJOML(player.getEyePosition())));
        return new AABB(pos).distanceToSqr(localEye) < range * range;
    }
}
