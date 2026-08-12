package dev.ryanhcode.offroad.index;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.data.AssetLookup;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import dev.ryanhcode.offroad.Offroad;
import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.content.items.tire.TireItem;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class OffroadItems {
    private static final SimulatedRegistrate REGISTRATE = Offroad.getRegistrate();

    static {
        var small_tire = REGISTRATE.item("small_tire", TireItem::new)
                .properties(x -> x)
                .recipe((c, p) -> ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, c.get(), 1)
                        .requires(AllBlocks.SHAFT)
                        .requires(Items.DRIED_KELP)
                        .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(AllBlocks.SHAFT.get()))
                        .save(p))
                .model(AssetLookup.itemModelWithPartials())
                .register();
        TireLike.register(small_tire, TireLike.SMALL_TIRE);

        var tire = REGISTRATE.item("tire", TireItem::new)
                .properties(x -> x)
                .recipe((c, p) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get(), 1)
                        .pattern(" K ")
                        .pattern("KSK")
                        .pattern(" K ")
                        .define('K', Items.DRIED_KELP.asItem())
                        .define('S', AllBlocks.SHAFT.get().asItem())
                        .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(AllBlocks.SHAFT.get()))
                        .save(p))
                .model(AssetLookup.itemModelWithPartials())
                .register();
        TireLike.register(tire, TireLike.TIRE);

        var large_tire = REGISTRATE.item("large_tire", TireItem::new)
                .properties(x -> x)
                .recipe((c, p) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get(), 1)
                        .pattern(" B ")
                        .pattern("BSB")
                        .pattern(" B ")
                        .define('B', AllBlocks.BELT.get().asItem())
                        .define('S', AllBlocks.SHAFT.get().asItem())
                        .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(AllBlocks.SHAFT.get()))
                        .save(p))
                .model(AssetLookup.itemModelWithPartials())
                .register();
        TireLike.register(large_tire, TireLike.LARGE_TIRE);

        var monstrous_tire = REGISTRATE.item("monstrous_tire", TireItem::new)
                .properties(x -> x)
                .recipe((c, p) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get(), 1)
                        .pattern(" K ")
                        .pattern("KSK")
                        .pattern(" K ")
                        .define('K', Blocks.DRIED_KELP_BLOCK.asItem())
                        .define('S', AllBlocks.SHAFT.get().asItem())
                        .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(AllBlocks.SHAFT.get()))
                        .save(p))
                .model(AssetLookup.itemModelWithPartials())
                .register();
        TireLike.register(monstrous_tire, TireLike.MONSTROUS_TIRE);
    }

    public static void init() {
    }
}
