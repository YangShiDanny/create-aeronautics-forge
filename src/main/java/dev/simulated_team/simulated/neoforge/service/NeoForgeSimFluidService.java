package dev.simulated_team.simulated.neoforge.service;

import dev.simulated_team.simulated.service.SimFluidService;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

public class NeoForgeSimFluidService implements SimFluidService {
    public long mbToLoaderUnits(final long mb) {
        return mb;
    }

    @Override
    public Fluid getFluidInItem(final ItemStack stack) {
        final LazyOptional<IFluidHandlerItem> handler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
        if (handler.isPresent()) {
            final FluidStack fluid = handler.resolve().get().getFluidInTank(0);
            if (!fluid.isEmpty()) {
                return fluid.getFluid();
            }
        }
        return null;
    }
}
