package dev.simulated_team.simulated.compat.explorerscompass;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget;
import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class ExplorersCompassNavigationTarget implements NavigationTarget {
	@Override
	public  Vec3 getTarget(final NavTableBlockEntity navBE, final ItemStack self) {
		final Integer x = DataComponentType.get(self, ExplorersCompass.FOUND_X_COMPONENT);
		final Integer z = DataComponentType.get(self, ExplorersCompass.FOUND_Z_COMPONENT);
		if (x != null && z != null) {
			final Vec3 pos = navBE.getProjectedSelfPos();
			return new Vec3(x, pos.y(), z);
		}

		return null;
	}
}
