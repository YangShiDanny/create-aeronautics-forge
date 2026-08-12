package dev.simulated_team.simulated.neoforge.service;

import dev.simulated_team.simulated.service.SimItemService;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

public class NeoForgeSimItemService implements SimItemService {

    public int getBurnTime(final ItemStack stack) {
        return stack.getBurnTime(RecipeType.SMELTING);
    }

    @Override
    public int getSuperheatedBurnTime(final ItemStack stack) {
        // [1.20.1 port] AllDataMaps / BlazeBurnerFuel 是 Create 7.x 才有；退回普通燃烧时间。
        return getBurnTime(stack);
    }
}