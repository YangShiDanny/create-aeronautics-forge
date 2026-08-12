package dev.ryanhcode.sable.index;

import net.minecraft.client.gui.components.toasts.SystemToast;

/**
 * Toast identifiers for sable sub-level load/save/physics failures.
 *
 * <p>On Neoforge 1.21.1 the nested type was {@code SystemToastId};
 * Forge 1.20.1 renamed it to {@code SystemToast.SystemToastIds} and its
 * constructor now takes an {@code int} id (used by vanilla for toast
 * de-duplication). Distinct values are used so the three failure toasts
 * don't collapse into one another.
 */
public class SableToasts {
    public static final SystemToast.SystemToastIds SUB_LEVEL_LOAD_FAILURE = SystemToast.SystemToastIds.PACK_LOAD_FAILURE;
    public static final SystemToast.SystemToastIds SUB_LEVEL_SAVE_FAILURE = SystemToast.SystemToastIds.PACK_COPY_FAILURE;
    public static final SystemToast.SystemToastIds SUB_LEVEL_PHYSICS_FAILURE = SystemToast.SystemToastIds.WORLD_ACCESS_FAILURE;
}
