package dev.ryanhcode.sable.mixin.entity.server_entities_tick;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {

    @Shadow @Final private ServerLevel level;

    // 1.20.1 移植说明（已用 javap 反编译 SRG 版 ChunkMap 字节码核实）：
    //   inEntityTickingRange(J)Z 在 1.20.1 的 SRG 名是 m_183913_。
    //   ChunkMap.tick()（SRG: m_140421_）字节码第 143 行调用
    //     ChunkMap$DistanceManager.m_183913_(J)Z
    //   为 false 就跳过第 154 行的 ServerEntity.m_8533_()（sendChanges），
    //   也就是说它是「实体变化是否发给客户端」的唯一门控。
    //   注意：m_140847_(J)Z 是 hasPlayersNearby（附近有玩家，怪物生成用），
    //   只在 m_183879_ / m_183888_ 里被调用，跟实体同步毫无关系——
    //   之前误包装了它，导致处在 plot 坐标（约 2048 万格）的子层级实体
    //   永远通不过实体同步范围检查，客户端收不到位置与乘客同步包。
    //   owner 必须写内部类 ChunkMap$DistanceManager（字节码里就是它）。
    @WrapOperation(method = "*", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;m_183913_(J)Z", remap = false))
    private boolean sable$wrapEntityTickingRange(final ChunkMap.DistanceManager instance, final long l, final Operation<Boolean> original) {
        final ChunkPos chunkPos = new ChunkPos(l);
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        final PlotChunkHolder chunkHolder = container.getChunkHolder(chunkPos);

        if (chunkHolder != null) {
            return true;
        }

        return original.call(instance, l);
    }
}
