package dev.eriksonn.aeronautics.index;

import dev.eriksonn.aeronautics.content.components.Converter;
import dev.eriksonn.aeronautics.content.components.Levitating;
import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentType;

public class AeroDataComponents {
    public static final DataComponentType<Levitating> LEVITATING =
            DataComponentType.of(Levitating.CODEC, "levitating");

    public static final DataComponentType<Converter> CONVERTER =
            DataComponentType.of(Converter.CODEC, "converter");

    public static void init() {
    }
}
