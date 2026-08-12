package dev.simulated_team.simulated.content.navigation_targets;

import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentType;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.core.GlobalPos;
import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

public class RecoveryCompassNavigationTarget implements NavigationTarget {
    @Override
    public  Vec3 getTarget(final NavTableBlockEntity navBE, final ItemStack self) {
        final UUID lastPlayer = DataComponentType.get(self, SimDataComponents.COMPASS_PLACER_UUID);
        if (lastPlayer != null) {
            GlobalPos lastDeathLocation;

            final Player player = navBE.getLevel().getPlayerByUUID(lastPlayer);
            if (player != null) {
                Optional<GlobalPos> lastDeathLocationOptional = player.getLastDeathLocation();
                if (lastDeathLocationOptional.isEmpty()) {
                    DataComponentType.remove(self, SimDataComponents.LAST_PLAYER_DEATH_LOCATION);
                    return null;
                }

                lastDeathLocation = lastDeathLocationOptional.get();
                DataComponentType.set(self, SimDataComponents.LAST_PLAYER_DEATH_LOCATION, lastDeathLocation);
            } else {
                lastDeathLocation = DataComponentType.get(self, SimDataComponents.LAST_PLAYER_DEATH_LOCATION);
            }

            if (lastDeathLocation == null) {
                return null;
            }

            final ResourceKey<Level> dimension = navBE.getLevel().dimension();
            if (!lastDeathLocation.dimension().equals(dimension)) {
                return null;
            }

            return lastDeathLocation.pos().getCenter();
        }

        return null;
    }

    @Override
    public void onInsert(final ItemStack itemStack, final NavTableBlockEntity be,  final Player player) {
        if (player != null) {
            DataComponentMap.Builder builder = DataComponentMap.builder().set(SimDataComponents.COMPASS_PLACER_UUID, player.getUUID());
            player.getLastDeathLocation().ifPresent(globalPos -> builder.set(SimDataComponents.LAST_PLAYER_DEATH_LOCATION, globalPos));
            DataComponentType.applyComponents(itemStack, builder.build());
        }
    }

    @Override
    public void onExtract(final ItemStack itemStack, final NavTableBlockEntity be,  final Player player) {
        DataComponentType.remove(itemStack, SimDataComponents.COMPASS_PLACER_UUID);
        DataComponentType.remove(itemStack, SimDataComponents.LAST_PLAYER_DEATH_LOCATION);
    }
}
