package dev.simulated_team.simulated.libs.catnip.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Backport shim of Create's {@code dev.simulated_team.simulated.libs.catnip.registry.RegisteredObjectsHelper}.
 * Create 6.0.8 for Forge 1.20.1 does NOT ship the {@code catnip} package, so the merged
 * aeronautics build (which calls {@code RegisteredObjectsHelper.getKeyOrThrow}) needs this.
 *
 * <p>On Forge 1.20.1 the registry key is obtained from {@link ForgeRegistries}; this throws if the
 * object is somehow not registered, matching catnip's contract.
 */
public final class RegisteredObjectsHelper {

    private RegisteredObjectsHelper() {
    }

    public static ResourceLocation getKeyOrThrow(final Block block) {
        final ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        if (key == null) {
            throw new IllegalStateException("Unregistered block: " + block);
        }
        return key;
    }

    public static ResourceLocation getKeyOrThrow(final Item item) {
        final ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        if (key == null) {
            throw new IllegalStateException("Unregistered item: " + item);
        }
        return key;
    }
}
