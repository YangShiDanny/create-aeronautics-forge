package dev.simulated_team.simulated.content.navigation_targets;

import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentType;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget;
import dev.simulated_team.simulated.content.navigation_targets.lodestone_compass_compatability.LodestoneInformation;
import dev.simulated_team.simulated.content.navigation_targets.lodestone_compass_compatability.LodestoneTrackingMap;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CompassNavigationTarget implements NavigationTarget {

	@Override
	public Vec3 getTarget(final NavTableBlockEntity navBE, final ItemStack self) {
		final Level level = navBE.getLevel();

		// [1.20.1 port] 1.20.1 没有独立 lodestone_compass 物品：磁石指南针就是普通 compass 物品，
		// 经右键磁石方块绑定后，其 NBT 写入 LodestonePos 复合标签（含所绑磁石坐标）。据此判定磁石指南针。
		final CompoundTag tag = self.getTag();
		if (tag != null && tag.contains("LodestonePos", CompoundTag.TAG_COMPOUND)) {
			final CompoundTag lodestonePos = tag.getCompound("LodestonePos");
			final BlockPos pos = NbtUtils.readBlockPos(lodestonePos);
			return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		}

		// 子层级磁石追踪（模组扩展组件，1.20.1 当前未挂载到手持物，保留兼容）
		if (DataComponentType.has(self, SimDataComponents.LODESTONE_COMPASS_SUBLEVEL_TRACKER)) {
			final LodestoneTrackingMap map = LodestoneTrackingMap.getOrLoad(level);
			if (map != null) {
				final LodestoneInformation information = map.getInformation(DataComponentType.get(self, SimDataComponents.LODESTONE_COMPASS_SUBLEVEL_TRACKER));
				if (information != null) {
					return JOMLConversion.toMojang(information.projectedPos());
				}
			}
		}

		// 普通指南针指向出生点
		return level.getSharedSpawnPos().getCenter();
	}
}
