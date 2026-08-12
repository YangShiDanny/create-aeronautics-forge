package dev.ryanhcode.sable.mixin.entity.tamed_teleport;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// In NeoForge 1.21.1 the "teleport pet to owner" logic lived on TamableAnimal
// (maybeTeleportTo -> canTeleportTo). In 1.20.1 it lives on FollowOwnerGoal instead:
//   maybeTeleportTo = m_25303_(III)Z, which calls this.canTeleportTo(BlockPos) = m_25307_.
// So we retarget the mixin to FollowOwnerGoal and obtain the animal from its field
// f_25283_ (tamable). Behaviour is identical to the NeoForge version.
@Mixin(FollowOwnerGoal.class)
public class TamableAnimalMixin {

	@Unique
	private static final BoundingBox3d sable$BOX = new BoundingBox3d();

	@Shadow(remap = false)
	@Final
	private TamableAnimal f_25283_;

	@WrapOperation(method = "m_25303_", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/FollowOwnerGoal;m_25307_(Lnet/minecraft/core/BlockPos;)Z", remap = false))
	private boolean sable$blockPosition(final FollowOwnerGoal instance, final BlockPos blockPos, final Operation<Boolean> original) {
		final TamableAnimal animal = this.f_25283_;

		final SubLevel subLevel = Sable.HELPER.getTrackingSubLevel(animal.getOwner());
		if(subLevel != null) {
			final BlockPos pos = BlockPos.containing(subLevel.logicalPose().transformPositionInverse(blockPos.getCenter()));
			if (original.call(instance, pos)) {
				final double dot = subLevel.logicalPose().transformNormal(new Vector3d(0, 1, 0)).dot(OrientedBoundingBox3d.UP);

				if (dot > 0.85) {
					return true;
				}
			}
		}

        sable$BOX.set(animal.getBoundingBox().move(blockPos.subtract(animal.blockPosition())));
        final Iterable<SubLevel> subLevels = Sable.HELPER.getAllIntersecting(animal.level(), sable$BOX);
        for (final SubLevel subLevel1 : subLevels) {
            final Vector3d center = sable$BOX.center();
            final BlockPos pos = BlockPos.containing(subLevel1.logicalPose().transformPositionInverse(new Vec3(center.x(), center.y(), center.z())));
            if (!animal.level().getBlockState(pos).isAir()) {
                return false;
            }
        }

		return original.call(instance, blockPos);
	}

}
