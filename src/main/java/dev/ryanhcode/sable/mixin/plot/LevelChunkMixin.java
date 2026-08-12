package dev.ryanhcode.sable.mixin.plot;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableCommonEvents;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into setBlockState to notify plots & plot chunk holders of block changes.
 * <p>
 * 注意：纯客户端逻辑（如 BUG-32 破坏碎屑/声音补播）已拆分到 {@code ClientLevelChunkMixin}
 * （仅注册在 sable.mixins.json 的 client 数组）。本双端 Mixin 不引用任何客户端专属类，
 * 确保专用服务器（DEDICATED_SERVER，无 ClientLevel）也能正常 APPLY，不会崩溃。
 */
@Mixin(LevelChunk.class)
public class LevelChunkMixin {

    @Shadow
    @Final
    private Level level;

    @Unique
    private BlockPos sable$blockSet = null;

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void sable$preSetBlockState(final BlockPos pPos, final BlockState pState, final boolean pIsMoving,
                                        final CallbackInfoReturnable<BlockState> cir) {
        this.sable$blockSet = pPos;
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void sable$postSetBlockState(final BlockPos pPos, final BlockState pState, final boolean pIsMoving,
                                         final CallbackInfoReturnable<BlockState> cir) {
        if (this.sable$blockSet != null) {
            final SubLevel subLevel = Sable.HELPER.getContaining(this.level, this.sable$blockSet);

            if (subLevel != null) {
                subLevel.getPlot().onBlockChange(this.sable$blockSet, pState);
            }
        }
        this.sable$blockSet = null;
    }

    @WrapOperation(method = "m_6978_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;", at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;m_62986_(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState sable$setBlockState(final LevelChunkSection instance, int pX, int pY, int pZ, final BlockState newState, final Operation<BlockState> original) {
        final BlockState oldState = original.call(instance, pX, pY, pZ, newState);

        if (this.level instanceof final ServerLevel serverLevel && oldState != newState) {
            pX = this.sable$blockSet.getX();
            pY = this.sable$blockSet.getY();
            pZ = this.sable$blockSet.getZ();

            SableCommonEvents.handleBlockChange(serverLevel, (LevelChunk) (Object) this, pX, pY, pZ, oldState, newState);
        }

        return oldState;
    }


}
