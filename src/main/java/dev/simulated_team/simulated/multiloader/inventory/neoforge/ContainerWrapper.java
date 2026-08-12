package dev.simulated_team.simulated.multiloader.inventory.neoforge;

import dev.simulated_team.simulated.multiloader.inventory.AbstractContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

public class ContainerWrapper<T extends AbstractContainer> implements IItemHandlerModifiable {

    private final  T container;

    public ContainerWrapper(final T container) {
        this.container = container;
    }

    @Override
    public  ItemStack insertItem(final int slot,  final ItemStack stack, final boolean simulate) {
        return this.container.insertSlot(stack, slot, simulate);
    }

    @Override
    public  ItemStack extractItem(final int slot, final int maxSize, final boolean simulate) {
        return this.container.extractSlot(slot, maxSize, simulate);
    }

    @Override
    public void setStackInSlot(final int i,  final ItemStack arg) {
        this.container.setItem(i, arg);
    }

    @Override
    public int getSlots() {
        return this.container.getContainerSize();
    }

    @Override
    public  ItemStack getStackInSlot(final int i) {
        return this.container.getItem(i);
    }

    @Override
    public int getSlotLimit(final int i) {
        return this.container.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(final int i,  final ItemStack arg) {
        return true;
    }
}