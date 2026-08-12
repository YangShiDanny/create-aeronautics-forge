package dev.simulated_team.simulated.libs.minecraft.world.item.component;

import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentType;

/**
 * Backport shim for 1.20.5+ {@code AllDataComponents} — only the field actually referenced
 * by this port is declared.
 */
public final class AllDataComponents {

    public static final DataComponentType<ItemContainerContents> LINKED_CONTROLLER_ITEMS =
            DataComponentType.of(ItemContainerContents.CODEC, "linked_controller_items");
}
