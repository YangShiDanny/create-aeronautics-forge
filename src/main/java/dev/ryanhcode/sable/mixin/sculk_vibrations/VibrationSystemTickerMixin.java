package dev.ryanhcode.sable.mixin.sculk_vibrations;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationInfo;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(VibrationSystem.Ticker.class)
public interface VibrationSystemTickerMixin {

    // 1.20.1: VibrationInfo.getPos() is f_243906_ (1.21 used m_122646_)
    @WrapOperation(method = "m_280174_", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationInfo;f_243906_()Lnet/minecraft/world/phys/Vec3;", remap = false))
    public default Vec3 sable$useGlobalPos(final VibrationInfo instance, final Operation<Vec3> original, @Local(argsOnly = true) final ServerLevel level) {
        return Sable.HELPER.projectOutOfSubLevel(level, original.call(instance));
    }

    // 1.20.1: the 3 methods in VibrationSystem$Ticker that call
    // VibrationSystem$User.m_280010_() (getPositionSource) are the SRG methods
    // m_280404_, m_280174_, m_280257_ (the 1.21 Mojang names
    // receiveVibration / lambda$... / method_51408 / tryReloadVibrationParticle
    // do not all resolve via refmap here).
    @WrapOperation(method = {"m_280404_", "m_280174_", "m_280257_"}, expect = 3, require = 3, remap = false,
            at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;m_280010_()Lnet/minecraft/world/level/gameevent/PositionSource;"))
    public default PositionSource sable$useGlobalDestPos(final VibrationSystem.User instance, final Operation<PositionSource> original, @Local(argsOnly = true) final ServerLevel level) {
        final PositionSource origSource = original.call(instance);
        final Optional<Vec3> optPos = origSource.getPosition(level);
        if (optPos.isPresent()) {
            return new BlockPositionSource(BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(level, optPos.get())));
        }
        return origSource;
    }
}
