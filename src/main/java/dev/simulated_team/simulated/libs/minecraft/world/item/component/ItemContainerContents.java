package dev.simulated_team.simulated.libs.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.stream.Stream;

/**
 * Backport shim for 1.20.5+ {@code ItemContainerContents} — holds a list of contained
 * item stacks (used by the linked typewriter controller item).
 */
public final class ItemContainerContents {

    public static final Codec<ItemContainerContents> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                            ItemStack.CODEC.listOf().fieldOf("items").forGetter(ItemContainerContents::getItems))
                    .apply(i, ItemContainerContents::new));

    private final List<ItemStack> items;

    public ItemContainerContents(final List<ItemStack> items) {
        this.items = items;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public Stream<ItemStack> stream() {
        return items.stream();
    }
}
