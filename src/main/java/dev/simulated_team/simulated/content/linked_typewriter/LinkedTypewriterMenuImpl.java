package dev.simulated_team.simulated.content.linked_typewriter;

import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterMenuCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

//TODO:figure out where items are rendered and change it
public class LinkedTypewriterMenuImpl extends LinkedTypewriterMenuCommon {

    public LinkedTypewriterMenuImpl(final MenuType<?> type, final int id, final Inventory inv, final FriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public LinkedTypewriterMenuImpl(final MenuType<?> type, final int id, final Inventory inv, final LinkedTypewriterBlockEntity be) {
        super(type, id, inv, be);
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        return new ItemStackHandler(2);
    }

    @Override
    protected void addSlots() {
        this.addPlayerSlots(6 + (16 * 2), 11 + (16 * 3));

        for (int i = 0; i < 2; i++) {
            this.addSlot(new GhostSlotHandler(this.ghostInventory, i, 105 + (i * 18), 1));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        // 如果父类持有blockEntity，沿用方块实体的距离校验逻辑
        if (this.blockEntity != null) {
            return AbstractContainerMenu.stillValid(player, this.blockEntity.getBlockPos(), this.blockEntity.getBlockState().getBlock());
        }
        return true;
    }

    private class GhostSlotHandler extends SlotItemHandler {

        public GhostSlotHandler(final IItemHandler itemHandler, final int index, final int xPosition, final int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        public boolean isFake() {
            return true;
        }

        @Override
        public boolean isActive() {
            return LinkedTypewriterMenuImpl.this.slotsActive;
        }
    }
}