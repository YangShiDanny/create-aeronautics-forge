package dev.eriksonn.aeronautics.neoforge.data.recipe;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.data.recipe.MechanicalCraftingRecipeGen;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class AeroMechanicalCraftingRecipes extends MechanicalCraftingRecipeGen {
	public AeroMechanicalCraftingRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, Aeronautics.MOD_ID);
	}

	@Override
	protected void buildRecipes(@NotNull Consumer<FinishedRecipe> consumer) {
		this.create(AeroBlocks.MOUNTED_POTATO_CANNON::get)
				.returns(1)
				.recipe(b -> b
						.patternLine("SR  ")
						.patternLine("KCPP")
						.patternLine("SR  ")
						.key('S', AllItems.COPPER_SHEET)
						.key('R', Items.REDSTONE)
						.key('K', Blocks.DRIED_KELP_BLOCK)
						.key('C', AllBlocks.COGWHEEL)
						.key('P', AllBlocks.FLUID_PIPE)
				).build(consumer);
	}
}