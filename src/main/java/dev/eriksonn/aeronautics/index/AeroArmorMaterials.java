package dev.eriksonn.aeronautics.index;

import dev.eriksonn.aeronautics.Aeronautics;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public class AeroArmorMaterials {
	// [1.20.1 port] Forge 1.20.1 has no ArmorMaterial registry (added in NeoForge 1.21),
	// so we expose the material as a plain object and pass it straight to the ArmorItem constructor.
	public static final ArmorMaterial AVIATORS_GOGGLES = new ArmorMaterial() {
		private final Object2ObjectOpenHashMap<ArmorItem.Type, Integer> durability = new Object2ObjectOpenHashMap<>();

		{
			durability.put(ArmorItem.Type.HELMET, 1);
		}

		@Override
		public int getDurabilityForType(ArmorItem.Type type) {
			return durability.getOrDefault(type, 0);
		}

		@Override
		public int getDefenseForType(ArmorItem.Type type) {
			return 1;
		}

		@Override
		public int getEnchantmentValue() {
			return 15;
		}

		@Override
		public SoundEvent getEquipSound() {
			return SoundEvents.ARMOR_EQUIP_LEATHER;
		}

		@Override
		public Ingredient getRepairIngredient() {
			return Ingredient.of(Items.LEATHER);
		}

		@Override
		public String getName() {
			return Aeronautics.MOD_ID + ":aviators_goggles";
		}

		@Override
		public float getToughness() {
			return 0.0f;
		}

		@Override
		public float getKnockbackResistance() {
		return 0.0f;
	}
	};

	public static void init() {}
}
