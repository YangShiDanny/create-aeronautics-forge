package dev.simulated_team.simulated.mixin.torsion_spring;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.simulated_team.simulated.api.IDirectionalAnalogOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ComparatorBlock.class)
public class ComparatorBlockMixin {
    @WrapOperation(method = "m_7312_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;m_60674_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I", remap = false))
    private int simulated$potentiallyDirectionalAnalogueSignal(final BlockState instance, final Level level, final BlockPos pos, final Operation<Integer> original, @Local(name = "direction") final Direction direction) {
        if (instance.getBlock() instanceof final IDirectionalAnalogOutput directionalAnalogOutput) {
            return directionalAnalogOutput.getAnalogOutputSignalFrom(instance, level, pos, direction);
        }
        return original.call(instance, level, pos);
    }
}
