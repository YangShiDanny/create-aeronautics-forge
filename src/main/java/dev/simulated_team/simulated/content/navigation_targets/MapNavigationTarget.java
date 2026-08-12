package dev.simulated_team.simulated.content.navigation_targets;

import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class MapNavigationTarget implements NavigationTarget {
	@Override
	public  Vec3 getTarget(final NavTableBlockEntity navBE, final ItemStack self) {
		final Level level = navBE.getLevel();
		final Vec3 pos = navBE.getProjectedSelfPos();
		return getNearestDecorationPos(level, pos, self);
	}

	private static Vec3 getNearestDecorationPos(final Level level, final Vec3 pos, final ItemStack stack) {
		// [1.20.1 port] DataComponents / MapId removed (data-component system unavailable in Forge 1.20.1).
		// Map-decoration-based navigation targeting is disabled; the nav table still works via other navigation targets.
		return null;
	}
}
