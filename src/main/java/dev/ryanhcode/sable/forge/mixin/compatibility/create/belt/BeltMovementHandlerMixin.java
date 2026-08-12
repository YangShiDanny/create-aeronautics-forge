package dev.ryanhcode.sable.forge.mixin.compatibility.create.belt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltMovementHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeltMovementHandler.class)
public class BeltMovementHandlerMixin {

    @WrapOperation(method = "transportEntity", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20186_()D", ordinal = 0, remap = false),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20186_()D", ordinal = 1, remap = false),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20186_()D", ordinal = 2, remap = false)
    })
    private static double sable$getLocalEntityY(final Entity instance, final Operation<Double> original, @Local(argsOnly = true) final BeltBlockEntity be) {
        final SubLevel subLevel = Sable.HELPER.getContaining(be);

        if (subLevel != null) {
            return subLevel.logicalPose().transformPositionInverse(instance.position()).y;
        }

        return original.call(instance);
    }

    @ModifyVariable(remap = false, method = "transportEntity", at = @At("STORE"), ordinal = 0)
    private static double sable$diffCenter(final double originalValue,
                                           @Local(argsOnly = true) final BeltBlockEntity be,
                                           @Local(argsOnly = true) final Entity entity,
                                           @Local(ordinal = 0) final BlockPos pos,
                                           @Local final Direction.Axis axis) {

        final SubLevel subLevel = Sable.HELPER.getContaining(be);

        if (subLevel == null) {
            return originalValue;
        }

        final Vec3 entityPos = subLevel.logicalPose().transformPositionInverse(entity.position());

        return axis == Direction.Axis.Z ? (pos.getX() + 0.5 - entityPos.x()) : (pos.getZ() + 0.5 - entityPos.z());
    }

    @Inject(remap = false, method = "transportEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_274421_()F"))
    private static void sable$maxUpStep(final BeltBlockEntity beltBE, final Entity entityIn, final BeltMovementHandler.TransportedEntityInfo info, final CallbackInfo ci, @Local(ordinal = 0) final LocalRef<Vec3> movement) {
        final SubLevel subLevel = Sable.HELPER.getContaining(beltBE);

        if (subLevel != null) {
            movement.set(subLevel.logicalPose().transformNormal(movement.get()));
        }
    }

}
