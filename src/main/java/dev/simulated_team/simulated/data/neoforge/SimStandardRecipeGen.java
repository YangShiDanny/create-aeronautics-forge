package dev.simulated_team.simulated.data.neoforge;

import com.simibubi.create.api.data.recipe.BaseRecipeProvider;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.index.neoforge.SimNeoForgeRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.concurrent.CompletableFuture;

public class SimStandardRecipeGen extends BaseRecipeProvider {

    GeneratedRecipe PORTABLE_ENGINE_DYEING = this.createSpecial(SimNeoForgeRecipeTypes.PORTABLE_ENGINE_DYEING.getSerializer(), "crafting", "portable_engine_dyeing");

    public SimStandardRecipeGen(final PackOutput output, final CompletableFuture<HolderLookup.Provider> registries) {
        super(output, Simulated.MOD_ID);
    }

    @SuppressWarnings("unchecked")
    private GeneratedRecipe createSpecial(final RecipeSerializer<?> serializer, final String recipeType, final String path) {
        final ResourceLocation location = Simulated.path(recipeType + "/" + path);

        return this.register(consumer -> {
            final SpecialRecipeBuilder b = SpecialRecipeBuilder.special((RecipeSerializer<? extends CraftingRecipe>) serializer);
            b.save(consumer, location.toString());
        });
    }
}
