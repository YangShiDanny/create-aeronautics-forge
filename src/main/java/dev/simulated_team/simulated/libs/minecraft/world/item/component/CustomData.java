package dev.simulated_team.simulated.libs.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Backport shim for 1.20.5+ {@code CustomData} — the value type carried by the
 * {@code minecraft:block_entity_data} data component. Holds a raw {@link CompoundTag}.
 */
public final class CustomData {

    public static final Codec<CustomData> CODEC = new Codec<CustomData>() {
        @Override
        public <T> DataResult<Pair<CustomData, T>> decode(final DynamicOps<T> ops, final T input) {
            if (input instanceof CompoundTag ct) return DataResult.success(Pair.of(new CustomData(ct), input));
            return DataResult.success(Pair.of(new CustomData(new CompoundTag()), input));
        }

        @Override
        public <T> DataResult<T> encode(final CustomData cd, final DynamicOps<T> ops, final T prefix) {
            return DataResult.success((T) cd.getUnsafe());
        }
    };

    private final CompoundTag tag;

    public CustomData(final CompoundTag tag) {
        this.tag = tag;
    }

    public CompoundTag copyTag() {
        return tag.copy();
    }

    public CompoundTag getUnsafe() {
        return tag;
    }

    public static CustomData of(final CompoundTag tag) {
        return new CustomData(tag);
    }

    /** Convenience used by the linked typewriter packet to attach a block entity tag. */
    public static void set(final DataComponentType<?> type, final ItemStack stack, final Tag tag) {
        if (tag instanceof CompoundTag ct) {
            stack.addTagElement("BlockEntityTag", ct);
        }
    }
}
