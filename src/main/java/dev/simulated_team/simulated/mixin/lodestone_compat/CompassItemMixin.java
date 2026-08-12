package dev.simulated_team.simulated.mixin.lodestone_compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.simulated_team.simulated.content.navigation_targets.lodestone_compass_compatability.LodestoneTrackingMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(CompassItem.class)
public abstract class CompassItemMixin extends Item {
	public CompassItemMixin(final Properties properties) {
		super(properties);
	}

	// [1.20.1 port] DataComponentType get/set replaced with NBT; injection points retargeted to 1.20.1 methods.
	@Inject(method = "inventoryTick", at = @At("HEAD"))
	private void simulated$checkID(final ItemStack stack, final Level level, final Entity entity, final int itemSlot, final boolean isSelected, final CallbackInfo ci) {
		if (!level.isClientSide) {
			final CompoundTag tag = stack.getTag();
			if (tag != null && tag.hasUUID("lodestone_compass_tracker")) {
				final UUID trackerID = tag.getUUID("lodestone_compass_tracker");
				final LodestoneTrackingMap map = LodestoneTrackingMap.getOrLoad(level);
				if (map != null && entity instanceof final net.minecraft.server.level.ServerPlayer sp) {
					map.sendUpdateForPlayer(trackerID, sp);
				}
			}
		}
	}

	@Inject(method = "useOn", at = @At("RETURN"))
	public void simulated$setLodestoneData(final UseOnContext context, final CallbackInfoReturnable<InteractionResult> ci) {
		final ItemStack instance = context.getItemInHand();
		final CompoundTag tag = instance.getTag();
		if (tag != null && tag.contains("LodestonePosition", 10)) {
			final net.minecraft.core.BlockPos pos = NbtUtils.readBlockPos(tag.getCompound("LodestonePosition"));
			final LodestoneTrackingMap map = LodestoneTrackingMap.getOrLoad(context.getLevel());
			if (map != null) {
				final UUID uuid = map.addOrGetLodestoneTrackingPoint(pos);
				if (uuid != null) {
					instance.getOrCreateTag().putUUID("lodestone_compass_tracker", uuid);
				}
			}
		}
	}
}
