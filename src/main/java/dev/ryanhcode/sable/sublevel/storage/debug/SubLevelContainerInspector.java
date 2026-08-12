package dev.ryanhcode.sable.sublevel.storage.debug;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.network.chat.Component;

/**
 * Debug inspector for sub-level plot containers.
 *
 * <p>On Neoforge 1.21.1 this extended Veil's {@code SingleWindowInspector}
 * and drew an ImGui occupancy grid. The Veil editor + ImGui runtime are
 * unavailable on Forge 1.20.1, so this is a no-op placeholder that keeps the
 * public title constant. The inspector UI is a TODO for a later pass.
 */
public class SubLevelContainerInspector {
    public static final Component TITLE = Component.translatable("inspector.sable.sub_level_container.title");

    public Component getDisplayName() {
        return TITLE;
    }
}
