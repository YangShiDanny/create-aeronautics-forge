package dev.simulated_team.simulated.data.neoforge;

import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlock;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.index.neoforge.SimNeoForgeRecipeTypes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.Tags;

public class PortableEngineDyeingRecipe extends CustomRecipe {

    // [1.20.1 移植] NeoForge 1.21 的 CustomRecipe 构造器为 (CraftingBookCategory)，
    // 但 Forge 1.20.1 的 CustomRecipe 构造器为 (ResourceLocation, CraftingBookCategory)。
    // 单参构造器在 1.20.1 无对应调用点（序列化器统一走双参），故删除。
    public PortableEngineDyeingRecipe(final ResourceLocation id, final CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(final CraftingContainer input, final Level level) {
        int engines = 0;
        int dyes = 0;

        for (int i = 0; i < input.getContainerSize(); ++i) {
            final ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                if (Block.byItem(stack.getItem()) instanceof PortableEngineBlock) {
                    ++engines;
                } else {
                    if (!stack.is(Tags.Items.DYES))
                        return false;
                    ++dyes;
                }

                if (dyes > 1 || engines > 1) {
                    return false;
                }
            }
        }

        return engines == 1 && dyes == 1;
    }

    @Override
    public ItemStack assemble(final CraftingContainer input, final RegistryAccess registries) {
        ItemStack engine = ItemStack.EMPTY;
        DyeColor color = DyeColor.RED;

        for (int i = 0; i < input.getContainerSize(); ++i) {
            final ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                if (Block.byItem(stack.getItem()) instanceof PortableEngineBlock) {
                    engine = stack;
                } else {
                    final DyeColor color1 = DyeColor.getColor(stack);
                    if (color1 != null) {
                        color = color1;
                    }
                }
            }
        }

        final ItemStack dyedEngine = SimBlocks.PORTABLE_ENGINES.get(color)
                .asStack();
        if (engine.getTag() != null) {
            dyedEngine.setTag(engine.getTag().copy());
        }

        return dyedEngine;
    }

    @Override
    public boolean canCraftInDimensions(final int width, final int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SimNeoForgeRecipeTypes.PORTABLE_ENGINE_DYEING.getSerializer();
    }

}
