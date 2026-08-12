package dev.ryanhcode.offroad.index;

import com.tterrag.registrate.util.entry.ItemProviderEntry;
import dev.ryanhcode.offroad.Offroad;
import dev.ryanhcode.offroad.content.ponder.scenes.BoreheadBearingScenes;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

public class OffroadPonderScenes {

    public static void register(final PonderSceneRegistrationHelper<ResourceLocation> registry) {
        final PonderSceneRegistrationHelper<ItemProviderEntry<?>> helper = registry.withKeyFunction(ItemProviderEntry::getId);

        helper.forComponents(OffroadBlocks.BOREHEAD_BEARING_BLOCK, OffroadBlocks.ROCK_CUTTER_BLOCK)
                .addStoryBoard("borehead_bearing/intro", BoreheadBearingScenes::boreheadIntro)
                .addStoryBoard("borehead_bearing/excavating", BoreheadBearingScenes::boreheadExcavating)
                .addStoryBoard("borehead_bearing/efficiency", BoreheadBearingScenes::boreheadEfficiency);
    }

    private static ItemProviderEntry<Item> offroadItemProvider(final String id) {
        return new ItemProviderEntry<>(
                Offroad.getRegistrate(),
                RegistryObject.create(Offroad.path(id), net.minecraftforge.registries.ForgeRegistries.ITEMS)
        );
    }
}