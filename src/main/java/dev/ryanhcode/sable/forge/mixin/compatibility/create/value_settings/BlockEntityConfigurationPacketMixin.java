package dev.ryanhcode.sable.forge.mixin.compatibility.create.value_settings;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * [1.20.1 移植修复] 让机械动力「数值设置包」的服务端距离检查支持子关卡。
 *
 * 背景：
 * 燃烧室输出值、便携发动机转速这类可调数值的方块，客户端长按右键弹出刻度盘后，
 * 松手时会发送 ValueSettingsPacket（继承自机械动力的 BlockEntityConfigurationPacket）。
 * 服务端在 BlockEntityConfigurationPacket.handle 的 lambda 里做距离校验：
 *
 *     if (!pos.closerThan(player.blockPosition(), maxRange())) return;
 *
 * 这里走的是 BlockPos.closerThan(Vec3i, double)（纯整数坐标直算），
 * 既不是 Entity.distanceToSqr（已被 interaction_distance.EntityMixin 覆盖），
 * 也不是 ServerPlayer.canReach（已被 interaction_distance 的两个 Mixin 覆盖）。
 * 子关卡方块的本地坐标偏移约 2048 万格，该检查恒为 false，服务端直接 return，
 * 静默丢弃整个配置包 —— 表现为：刻度盘能弹出、能选值，但松手后数值纹丝不动，
 * 且日志里没有任何报错。
 *
 * 本 Mixin 重定向这一次 closerThan 调用：原判为真则照常放行；原判为假时，
 * 若目标方块位于某子关卡内，就把玩家眼睛位置逆变换回该子关卡的本地坐标系再比距离，
 * 仍在范围内则放行。口径与 interaction_distance.ServerPlayerGameModeMixin 完全一致。
 *
 * 一次修复覆盖机械动力全部配置包（数值设置、序列齿轮箱、显示链接、阈值开关、车站等），
 * 因为它们都继承同一个 BlockEntityConfigurationPacket 基类。
 *
 * 注：机械动力自身的类不参与混淆，故 @Mixin 与注入点均 remap = false，
 * 被拦截的原版方法 BlockPos.closerThan 需按 SRG 名 m_123314_ 书写
 * （已用 javap 反编译 create-1.20.1-6.0.8.jar 核对字节码确认）。
 * 该配置文件 injectors.defaultRequire = 0（注入失败静默），故显式 require = 1，
 * 一旦注入点失配会立刻报错而不是悄悄失效。
 */
@Mixin(value = BlockEntityConfigurationPacket.class, remap = false)
public class BlockEntityConfigurationPacketMixin {

    @WrapOperation(
            method = "lambda$handle$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;m_123314_(Lnet/minecraft/core/Vec3i;D)Z",
                    remap = false
            ),
            require = 1
    )
    private boolean sable$closerThanWithSubLevel(final BlockPos blockPos,
                                                 final Vec3i playerBlockPos,
                                                 final double maxRange,
                                                 final Operation<Boolean> original,
                                                 final NetworkEvent.Context context) {
        if (original.call(blockPos, playerBlockPos, maxRange)) {
            return true;
        }

        final ServerPlayer player = context.getSender();
        if (player == null) {
            return false;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(player.level(), blockPos);
        if (subLevel == null) {
            return false;
        }

        final Vec3 eyeInSubLevel = subLevel.logicalPose().transformPositionInverse(player.getEyePosition());
        return new AABB(blockPos).distanceToSqr(eyeInSubLevel) < maxRange * maxRange;
    }
}
