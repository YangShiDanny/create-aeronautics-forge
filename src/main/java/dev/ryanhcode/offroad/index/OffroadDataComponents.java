package dev.ryanhcode.offroad.index;

import dev.ryanhcode.offroad.content.components.TireLike;
import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentType;

public class OffroadDataComponents {
    public static final DataComponentType<TireLike> TIRE =
            DataComponentType.of(TireLike.CODEC, "tire");

    public static void init() {
        // no-op
    }
}
