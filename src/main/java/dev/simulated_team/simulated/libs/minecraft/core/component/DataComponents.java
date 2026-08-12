package dev.simulated_team.simulated.libs.minecraft.core.component;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import dev.simulated_team.simulated.libs.minecraft.world.item.component.CustomData;
import dev.simulated_team.simulated.libs.minecraft.world.item.component.LodestoneTracker;

import org.jetbrains.annotations.Nullable;

/**
 * Backport shim for the vanilla 1.20.5+ {@code DataComponents} constants that this port still
 * references. Only the fields actually used by the code base are declared.
 */
public final class DataComponents {

    private DataComponents() {}

    /** Item custom hover name (backed by {@link ItemStack}'s vanilla name field). */
    public static final DataComponentType<Component> CUSTOM_NAME = DataComponentType.of(new DataComponentType.ComponentAccessor<Component>() {
        @Override
        public boolean has(final ItemStack stack) {
            return stack.hasCustomHoverName();
        }

        @Override
        
        public Component get(final ItemStack stack) {
            return stack.getHoverName();
        }

        @Override
        public void set(final ItemStack stack, final Component value) {
            stack.setHoverName(value);
        }

        @Override
        public void remove(final ItemStack stack) {
            stack.resetHoverName();
        }
    });

    /** Block entity tag carried by an item (backed by the vanilla {@code BlockEntityTag} sub-tag). */
    public static final DataComponentType<CustomData> BLOCK_ENTITY_DATA = DataComponentType.of(new DataComponentType.ComponentAccessor<CustomData>() {
        @Override
        public boolean has(final ItemStack stack) {
            final CompoundTag tag = stack.getTag();
            return tag != null && tag.contains("BlockEntityTag");
        }

        @Override
        
        public CustomData get(final ItemStack stack) {
            final CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains("BlockEntityTag")) return null;
            return new CustomData(tag.getCompound("BlockEntityTag"));
        }

        @Override
        public void set(final ItemStack stack, final CustomData value) {
            stack.addTagElement("BlockEntityTag", value.getUnsafe());
        }

        @Override
        public void remove(final ItemStack stack) {
            final CompoundTag tag = stack.getTag();
            if (tag != null) tag.remove("BlockEntityTag");
        }
    });

    /** Lodestone tracker (NBT-backed, used by the recovery/lodestone compass). */
    public static final DataComponentType<LodestoneTracker> LODESTONE_TRACKER =
            DataComponentType.of(LodestoneTracker.CODEC, "lodestone_tracker");

    /** Max damage override (NBT-backed, checked by the honey glue renderer). */
    public static final DataComponentType<Integer> MAX_DAMAGE = DataComponentType.of(Codec.INT, "max_damage");
}
