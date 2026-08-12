package dev.simulated_team.simulated.mixin.ponder;

import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.function.BiConsumer;

@Mixin(PonderLevel.class)
public class PonderLevelRestoreMixin {

    private static final Logger SIMULATED_LOG = LoggerFactory.getLogger(PonderLevelRestoreMixin.class);

    // [1.20.1 子层级修复] Ponder 1.0.91 的 restore() 在恢复方块实体时，
    // 若场景中某个方块无法构造方块实体（BlockEntity.loadStatic 返回 null，
    // 例如依赖特殊 Level 环境的自定义方块在 Ponder 虚拟世界里构造失败），
    // lambda$restore$3 会对 null 调用 getBlockPos() 抛出 NullPointerException 并崩溃整个游戏
    // （鼠标悬停带教学的物品即触发）。
    // 此处重定向 restore() 内的 originalBlockEntities.forEach(...) 调用，
    // 用带 try-catch 的安全 consumer 包裹每一条记录：单个方块实体加载失败仅跳过该条目，
    // 其余方块正常恢复，从而让 Ponder 教学界面可正常打开而不崩溃。
    @Redirect(
        method = "restore",
        remap = false,
        at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V")
    )
    private void simulated$safeRestoreForEach(
            final Map<BlockPos, CompoundTag> map,
            final BiConsumer<BlockPos, CompoundTag> consumer) {
        map.forEach((pos, tag) -> {
            try {
                consumer.accept(pos, tag);
            } catch (final NullPointerException | IllegalArgumentException e) {
                SIMULATED_LOG.warn("[aeronautics] 跳过 Ponder 场景里无法加载的方块实体 ({}, {}, {}): {}",
                        pos.getX(), pos.getY(), pos.getZ(), e.getMessage());
            }
        });
    }
}
