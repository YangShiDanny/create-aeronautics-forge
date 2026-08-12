package dev.ryanhcode.sable.mixin.sculk_vibrations;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EuclideanGameEventListenerRegistry.class)
public class EuclideanGameEventListenerRegistryMixin {

    // 1.20.1 中 Vec3i.distSqr(Vec3i) 返回 int 而非 double（签名变更），此处回调返回 double 无法匹配。
    // phase-1 先 require=0 优雅跳过（保留代码，phase-2 用 1.20.1 的 int 版重写）。
    @WrapOperation(method = "m_247048_", remap = false, require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;m_123331_(Lnet/minecraft/core/Vec3i;)D", remap = false))
    private static double replaceDistance(final BlockPos from, final Vec3i to, final Operation<Double> original, @Local(argsOnly = true) final ServerLevel level) {
        return Sable.HELPER.distanceSquaredWithSubLevels(level, JOMLConversion.atLowerCornerOf(from), JOMLConversion.atLowerCornerOf(to));
    }

}
