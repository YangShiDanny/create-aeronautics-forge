package dev.eriksonn.aeronautics.neoforge.data.recipe;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.index.AeroItems;
import dev.eriksonn.aeronautics.neoforge.index.AeroFluidsNeoForge;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.data.recipe.MixingRecipeGen;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.tags.FluidTags;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class AeroMixingRecipes extends MixingRecipeGen {
	public AeroMixingRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, Aeronautics.MOD_ID);
	}

	@Override
	protected void buildRecipes(@NotNull Consumer<FinishedRecipe> consumer) {
		create("levitite_blend", b -> b
				.require(AeroItems.ENDSTONE_POWDER)
				.require(AeroItems.ENDSTONE_POWDER)
				.require(AeroItems.ENDSTONE_POWDER)
				.require(AeroItems.ENDSTONE_POWDER)
				.require(AllItems.ZINC_NUGGET)
				.require(AllItems.ZINC_NUGGET)
				.require(FluidTags.WATER, 500)
				.output(AeroFluidsNeoForge.LEVITITE_BLEND.get(), 500)
				.requiresHeat(HeatCondition.HEATED)
		).build(consumer);
	}
}