package dev.ryanhcode.sable.forge.mixin.compatibility.create.inventory_manipulation;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChuteBlockEntity.class)
public abstract class ChuteBlockEntityMixin extends SmartBlockEntity {

	public ChuteBlockEntityMixin(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
		super(type, pos, state);
	}

	@WrapMethod(method = "grabCapability")
	public LazyOptional<IItemHandler> sable$grabCap(final Direction side, final Operation<LazyOptional<IItemHandler>> original) {
		final LazyOptional<IItemHandler> handler = original.call(side);
		if (handler.isPresent()) {
			return handler;
		}

		// anything past this, we don't really need a cache... It has the potential to constantly move as it's not local
		final Level level = this.getLevel();
		assert level != null;

		final BlockPos checkPos = this.worldPosition.relative(side);
		final Direction opposite = side.getOpposite();
		final Vector3d mut = new Vector3d(opposite.getStepX(), opposite.getStepY(), opposite.getStepZ());

        final ActiveSableCompanion helper = Sable.HELPER;
        final SubLevel parentSublevel = helper.getContaining(level, checkPos);
		if (parentSublevel != null) {
			parentSublevel.logicalPose().transformNormalInverse(mut);
		}

		final Vector3d includSublevelDir = new Vector3d(mut);
		final IItemHandler result = helper.runIncludingSubLevels(
				level,
				checkPos.getCenter(),
				false,
				parentSublevel,
				(sublevel, pos) -> {
					includSublevelDir.set(mut);
					if (sublevel != null) {
						sublevel.logicalPose().transformNormal(includSublevelDir);
					}

					final BlockEntity targetBe = level.getBlockEntity(pos);
					return targetBe != null ? targetBe.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.getNearest(includSublevelDir.x, includSublevelDir.y, includSublevelDir.z)).orElse(null) : null;
				}
		);
		return result != null ? LazyOptional.of(() -> result) : LazyOptional.empty();
	}
}