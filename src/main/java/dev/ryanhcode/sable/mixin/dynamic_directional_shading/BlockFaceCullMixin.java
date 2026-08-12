package dev.ryanhcode.sable.mixin.dynamic_directional_shading;

import dev.ryanhcode.sable.render.dynamic_shade.SableDynamicDirectionalShading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [BUG-28 诊断] 统计子关卡烘焙期的邻接遮挡剔除判定结果。
 *
 * <p><b>为什么要有这个统计。</b>现场实测已经把「图形状态」这条线全部排除：GL 绕序 CCW、
 * 剔除面 BACK、深度 LEQUAL、矩阵行列式为正（无镜像）、着色器 uniform 正常。
 * 而唯一可信的一组观察是「关掉背面剔除后方块又能看见了，并伴随黑色频闪」——
 * 这恰恰说明看见的是<b>远侧面的背面</b>：近侧面压根不在网格里，视线直接穿进方块内部，
 * 远侧面正面朝外、从内看是背面因而被剔除，于是表现为「隐形」；关掉剔除后它被画出来，
 * 内外表面共面就产生了黑色频闪。
 *
 * <p>顺着这条线，唯一还没被证伪的假设就是：<b>那些面在烘焙期就被遮挡剔除丢弃了</b>。
 * 本 Mixin 直接把 {@link Block#shouldRenderFace} 的判定结果按朝向记下来，
 * 并在面被丢弃时记录「挡住它的邻居方块是什么」，从而一次性区分：
 * <ul>
 *   <li>邻居确实是实心方块 → 剔除本身没错，问题在结构数据（plot 里混进了不该有的方块）；</li>
 *   <li>邻居是本不该出现在那儿的东西 → 邻居查询坐标偏移算错，直接反推错在哪一轴。</li>
 * </ul>
 *
 * <p><b>为什么挂在 {@link Block} 而不是 {@code ModelBlockRenderer} 内部。</b>
 * 客户端装了 Embeddium + Rubidium Extra + Oculus，它们对区块渲染管线改动很大。
 * 挂在静态工具方法上不依赖任何调用方的内部结构，既不会和它们抢注入点，
 * 也不会因为对方改了循环写法而失配。
 *
 * <p><b>性能。</b>该方法在主世界区块烘焙时每帧被调用几十万次，因此第一道判断用的是
 * 普通静态 volatile 布尔；只有子关卡真的在烘焙的那极短窗口才会继续往下走。
 *
 * <p>注意：这是纯诊断设施，定位完成后与 {@code /sabledbg} 一并删除。
 */
@Mixin(Block.class)
public class BlockFaceCullMixin {

    // 官方名映射下用 SRG 名 + remap=false（与工程内既有注入一致，加载期按运行时混淆名解析）。
    // m_152444_ = Block.shouldRenderFace(BlockState, BlockGetter, BlockPos, Direction, BlockPos)Z
    //（已用 build/createMcpToSrg/output.tsrg 核对）。
    // require = 0：诊断代码绝不允许因为注入失配就让游戏起不来；万一没注入上，
    // 日志里的「本轮没有发生任何邻接遮挡剔除判定」会明确提示，不会造成误判。
    @Inject(method = "m_152444_", at = @At("RETURN"), remap = false, require = 0)
    private static void sable$countFaceCull(final BlockState state,
                                            final BlockGetter getter,
                                            final BlockPos pos,
                                            final Direction direction,
                                            final BlockPos neighborPos,
                                            final CallbackInfoReturnable<Boolean> cir) {
        if (!SableDynamicDirectionalShading.isAnySubLevelBuilding()
                || !SableDynamicDirectionalShading.isBuildingSubLevel()) {
            return;
        }

        final boolean kept = Boolean.TRUE.equals(cir.getReturnValue());
        String blockerName = null;
        if (!kept) {
            // 只在面被丢弃时才去查邻居，避免给保留路径增加无谓开销。
            try {
                blockerName = BuiltInRegistries.BLOCK
                        .getKey(getter.getBlockState(neighborPos).getBlock())
                        .toString();
            } catch (final Throwable ignored) {
                // 邻居可能落在 region 边界外。这种情况本身也是线索，但绝不能让诊断代码把烘焙搞崩。
                blockerName = "<读取失败>";
            }
        }
        SableDynamicDirectionalShading.countFaceTest(direction, kept, blockerName);
    }
}
