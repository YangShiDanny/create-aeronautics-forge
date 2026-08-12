package dev.ryanhcode.sable.api.physics.force;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.registry.RegistryObject;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All default force groups.
 *
 * <p>Port note: the original used Veil's {@code RegistrationProvider} / {@code RegistryObject}
 * (a datapack-registry helper absent on Forge 1.20.1). Replaced with a self-contained static
 * map so the public API ({@code GRAVITY.get()}, {@code count()}, ...) is unchanged.</p>
 */
public class ForceGroups {
    public static final ResourceKey<Registry<ForceGroup>> REGISTRY_KEY = ResourceKey.createRegistryKey(Sable.sablePath("force_groups"));
    public static final Map<ResourceLocation, ForceGroup> REGISTRY = new LinkedHashMap<>();

    public static final RegistryObject<ForceGroup> GRAVITY = register(Sable.sablePath("gravity"), new ForceGroup(Component.translatable("force_group.sable.gravity"), null, 0x216e55, false));
    public static final RegistryObject<ForceGroup> DRAG = register(Sable.sablePath("drag"), new ForceGroup(Component.translatable("force_group.sable.drag"), null, 0x834f31, false));
    public static final RegistryObject<ForceGroup> LEVITATION = register(Sable.sablePath("levitation"), new ForceGroup(Component.translatable("force_group.sable.levitation"), null, 0x734480, true));
    public static final RegistryObject<ForceGroup> BALLOON_LIFT = register(Sable.sablePath("balloon_lift"), new ForceGroup(Component.translatable("force_group.sable.balloon_lift"), null, 0xd2643e, true));
    public static final RegistryObject<ForceGroup> PROPULSION = register(Sable.sablePath("propulsion"), new ForceGroup(Component.translatable("force_group.sable.propulsion"), null, 0x5a7c9f, true));
    public static final RegistryObject<ForceGroup> LIFT = register(Sable.sablePath("lift"), new ForceGroup(Component.translatable("force_group.sable.lift"), null, 0x8cb6c6, true));
    public static final RegistryObject<ForceGroup> MAGNETIC_FORCE = register(Sable.sablePath("magnetic_force"), new ForceGroup(Component.translatable("force_group.sable.magnetic_force"), null, 0xe05343, false));

    private static RegistryObject<ForceGroup> register(final ResourceLocation id, final ForceGroup value) {
        REGISTRY.put(id, value);
        return new RegistryObject<>(value);
    }

    public static void register() {
        // no-op: registries are populated statically above
    }

    /**
     * The count of registered force groups.
     */
    public static int count() {
        return REGISTRY.size();
    }

    public static ResourceLocation getKey(final ForceGroup group) {
        for (final Map.Entry<ResourceLocation, ForceGroup> e : REGISTRY.entrySet()) {
            if (e.getValue() == group) {
                return e.getKey();
            }
        }
        return null;
    }
}
