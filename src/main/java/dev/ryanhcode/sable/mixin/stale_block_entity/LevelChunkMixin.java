package dev.ryanhcode.sable.mixin.stale_block_entity;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [1.20.1 移植修复·残留方块实体]
 *
 * <p>原版 {@code LevelChunk#setBlockState} 开头有一处短路：
 * <pre>
 *     boolean flag = levelchunksection.hasOnlyAir();
 *     if (flag &amp;&amp; state.isAir()) {
 *         return null;   // 直接返回，不会调用 onRemove，也就不会清除方块实体
 *     }
 * </pre>
 *
 * <p>正常游戏中「整段只有空气的 section」里不可能存在方块实体，所以这个短路无害。
 * 但子层级装配 / 拆解会把整块结构一次性搬走：当某个 section 里的方块被逐个清空后，
 * 该 section 变成「只剩空气」，而此时仍可能有方块实体没被摘除（例如方块先被邻居更新
 * 连锁破坏、或被别的路径直接改成空气）。后续再对这些坐标写空气时就会命中短路，
 * 方块实体便永久残留在区块里。
 *
 * <p>残留的方块实体依旧会被主世界逐区块渲染循环按原坐标绘制，于是热气球升空后，
 * 拉杆、组装器数字、推进器等结构的「影子」会留在起飞点，看起来就是「部分结构向下偏移」。
 *
 * <p>这里在短路发生之前先把该坐标上的残留方块实体清掉。由于 {@link LevelChunk} 服务端和
 * 客户端共用，本修复对两端同时生效。
 */
@Mixin(LevelChunk.class)
public class LevelChunkMixin {

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void sable$purgeStaleBlockEntityBeforeAirShortCircuit(final BlockPos pos,
                                                                  final BlockState state,
                                                                  final boolean isMoving,
                                                                  final CallbackInfoReturnable<BlockState> cir) {
        if (!state.isAir()) {
            return;
        }

        final LevelChunk self = (LevelChunk) (Object) this;
        final int sectionIndex = self.getSectionIndex(pos.getY());

        if (sectionIndex < 0 || sectionIndex >= self.getSections().length) {
            return;
        }

        final LevelChunkSection section = self.getSection(sectionIndex);

        // 只有会触发原版短路（section 全空气 + 写入空气）的情况才需要介入。
        if (!section.hasOnlyAir()) {
            return;
        }

        final BlockEntity stale = self.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);

        if (stale == null) {
            return;
        }

        Sable.LOGGER.warn("[残留清理] 空气短路前清除残留方块实体 {} @ {}",
                BlockEntityType.getKey(stale.getType()), pos);

        self.removeBlockEntity(pos);
    }
}
