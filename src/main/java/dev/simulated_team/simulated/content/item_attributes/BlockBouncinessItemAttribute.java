package dev.simulated_team.simulated.content.item_attributes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;
import dev.ryanhcode.sable.mixinterface.block_properties.BlockStateExtension;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyTypes;
import dev.simulated_team.simulated.index.SimItemAttributeTypes;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import dev.simulated_team.simulated.libs.minecraft.network.codec.ByteBufCodecs;
import dev.simulated_team.simulated.libs.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BlockBouncinessItemAttribute implements ItemAttribute {
	private double bounciness;

	public BlockBouncinessItemAttribute(final double bounciness) {
		this.bounciness = bounciness;
	}

	public double bounciness() {
		return this.bounciness;
	}

	public static final MapCodec<BlockBouncinessItemAttribute> CODEC = Codec.DOUBLE
			.xmap(BlockBouncinessItemAttribute::new, BlockBouncinessItemAttribute::bounciness)
			.fieldOf("value");

	public static final StreamCodec<ByteBuf, BlockBouncinessItemAttribute> STREAM_CODEC = ByteBufCodecs.DOUBLE
			.map(BlockBouncinessItemAttribute::new, BlockBouncinessItemAttribute::bounciness);

	@Override
	public boolean appliesTo(final ItemStack stack, final Level world) {
		if(stack.getItem() instanceof final BlockItem item) {
			final BlockStateExtension extension = (BlockStateExtension) item.getBlock().defaultBlockState();
			return extension.sable$getProperty(PhysicsBlockPropertyTypes.RESTITUTION.get()) == this.bounciness();
		}
		return false;
	}

	@Override
	public ItemAttributeType getType() {
		return SimItemAttributeTypes.BLOCK_BOUNCINESS.get();
	}

	@Override
	public String getTranslationKey() {
		return "block_bounciness";
	}

	@Override
	public Object[] getTranslationParameters() {
		return new Object[]{ this.bounciness() };
	}

	@Override
	public void save(final CompoundTag nbt) {
		nbt.putDouble("value", this.bounciness);
	}

	@Override
	public void load(final CompoundTag nbt) {
		this.bounciness = nbt.getDouble("value");
	}

	public static class Type implements ItemAttributeType {

		@Override
		public  ItemAttribute createAttribute() {
			return new BlockBouncinessItemAttribute(1.0);
		}

		@Override
		public List<ItemAttribute> getAllAttributes(final ItemStack stack, final Level level) {
			if(stack.getItem() instanceof final BlockItem item) {
				final BlockStateExtension extension = (BlockStateExtension) item.getBlock().defaultBlockState();
				final double mass = extension.sable$getProperty(PhysicsBlockPropertyTypes.RESTITUTION.get());
				return List.of(new BlockBouncinessItemAttribute(mass));
			}
			return List.of();
		}

		public MapCodec<? extends ItemAttribute> codec() {
			return CODEC;
		}

		public StreamCodec<? super FriendlyByteBuf, ? extends ItemAttribute> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
