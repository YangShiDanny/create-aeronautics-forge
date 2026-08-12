package dev.ryanhcode.sable.mixin.entity.entity_ai;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EatBlockGoal.class)
public class EatBlockGoalMixin {

	@WrapOperation(method = {"m_8036_", "m_8041_"}, remap = false, at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraft/world/entity/Mob;m_20183_()Lnet/minecraft/core/BlockPos;"))
	private BlockPos sable$blockPosition(Mob instance, Operation<BlockPos> original) {
		BlockPos pos = original.call(instance);
		SubLevel subLevel = Sable.HELPER.getTrackingSubLevel(instance);
		if(subLevel != null) {
			Vec3 transformed = subLevel.logicalPose().transformPositionInverse(instance.position());
			pos = BlockPos.containing(transformed);
		}

		return pos;
	}

}
