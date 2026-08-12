package dev.ryanhcode.sable.forge.mixin.compatibility.create.funnels;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes a Funnel check in Create to take into account sub-levels
 */
@Mixin(FunnelBlock.class)
public class FunnelBlockMixin {

    @Redirect(remap = false, method = "m_7892_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/createmod/catnip/math/VecHelper;getCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sable$projectFunnelPos(final Vec3i pos, @Local(argsOnly = true) final Level level) {
        return JOMLConversion.toMojang(Sable.HELPER.projectOutOfSubLevel(level, JOMLConversion.atCenterOf(pos)));
    }

}
