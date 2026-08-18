package dev.eriksonn.aeronautics.neoforge.data.recipe;

import com.simibubi.create.api.data.recipe.WashingRecipeGen;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import dev.eriksonn.aeronautics.index.AeroTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class AeroWashingRecipes extends WashingRecipeGen {
    public AeroWashingRecipes(final PackOutput output, final CompletableFuture<HolderLookup.Provider> registries) {
        super(output, Aeronautics.MOD_ID);
    }

    @Override
    protected void buildRecipes(@NotNull Consumer<FinishedRecipe> consumer) {
        this.create("envelope_washing", b -> b
                .require(AeroTags.ItemTags.SHAFTLESS_ENVELOPE)
                .output(AeroBlocks.WHITE_ENVELOPE_BLOCK.get().asItem())
        ).build(consumer);
    }
}