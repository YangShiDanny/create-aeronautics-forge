package dev.ryanhcode.sable.mixin.plot;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [BUG-32 修复] 仅客户端：物理化飞行结构上方块被移除（block→air）时，统一在「子层级 chunk 方块置空」
 * 这一层补播破坏碎屑+声音。无论破坏经由单方块包 / 批量区段包 / 还是附属模组被 Create 当结构部件直接
 * 移除（此类方块不走 handleBlockUpdate 单包路径、原 BUG-29 补播永远漏掉），最终都会落到 LevelChunk
 * .setBlockState 把方块置空，这里用真实的「旧方块状态」补播，故机械动力附属模组方块也能覆盖。
 * <p>
 * 本 Mixin 仅注册在 sable.mixins.json 的 client 数组，专用服务器（DEDICATED_SERVER）不会加载，
 * 从而规避 ClientLevel 在专用服务端缺失导致的 Mixin APPLY 崩溃（ClassMetadataNotFoundException）。
 * 客户端 / 集成服务器（含手机端安卓）正常加载，BUG-32 行为不变。
 */
@Mixin(LevelChunk.class)
public class ClientLevelChunkMixin {

    @Shadow
    private Level level;

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void sable$clientBlockBreakEffects(final BlockPos pPos, final BlockState pState, final boolean pIsMoving,
                                               final CallbackInfoReturnable<BlockState> cir) {
        if (!(this.level instanceof final ClientLevel clientLevel)) {
            return;
        }

        final BlockState oldState = cir.getReturnValue();
        // oldState 为 setBlockState 返回的「变更前状态」。本地预测可能已把它写成空气，
        // 此时改从 ClientSubLevel 维护的「最近非空气状态」取真实被破坏方块。
        if (pState.isAir()) {
            final SubLevelContainer container = SubLevelContainer.getContainer(clientLevel);
            if (container != null) {
                final LevelPlot plot = container.getPlot(pPos.getX() >> 4, pPos.getZ() >> 4);
                if (plot != null && plot.getSubLevel() instanceof final ClientSubLevel csl) {
                    BlockState broken = (oldState != null && !oldState.isAir()) ? oldState
                            : csl.sable$getLastNonAirState(pPos);
                    if (broken != null && !broken.isAir() && csl.sable$isPhysicsActive()) {
                        final Vec3 world = csl.logicalPose().transformPosition(
                                new Vec3(pPos.getX() + 0.5, pPos.getY() + 0.5, pPos.getZ() + 0.5));
                        clientLevel.addDestroyBlockEffect(BlockPos.containing(world), broken);
                        clientLevel.playLocalSound(world.x, world.y, world.z,
                                broken.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F, false);
                        // 播放后清空兜底状态，避免同一坐标残留旧方块导致重复/误播。
                        csl.sable$setLastNonAirState(pPos, null);
                    }
                }
            }
        }
    }
}
