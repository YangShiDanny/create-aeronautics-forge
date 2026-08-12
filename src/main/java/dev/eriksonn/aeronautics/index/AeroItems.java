package dev.eriksonn.aeronautics.index;

import com.simibubi.create.AllItems;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullUnaryOperator;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.content.components.Levitating;
import dev.eriksonn.aeronautics.content.items.AviatorsGogglesItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.RecordItem;

public class AeroItems {
	private static final SimulatedRegistrate REGISTRATE = Aeronautics.getRegistrate();

	public static final ItemEntry<AviatorsGogglesItem> AVIATORS_GOGGLES = REGISTRATE
					.item("aviators_goggles", AviatorsGogglesItem::new)
					.lang("Aviator's Goggles")
					.recipe((c, p) -> ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, c.get(), 1)
							.requires(AeroTags.ItemTags.LEATHERS)
							.requires(AllItems.GOGGLES)
							.unlockedBy("has_ingredient", RegistrateRecipeProvider.has(AllItems.GOGGLES))
							.save(p))
					.tag(AeroTags.ItemTags.ARMORS)
					.tag(AeroTags.ItemTags.HEAD_ARMOR)
					.tag(ItemTags.FREEZE_IMMUNE_WEARABLES)
					.register();

	// [1.20.1 移植修复] 源版 1.21.1 用 Item + jukeboxPlayable 数据组件，但 1.20.1 的唱片机只认 RecordItem，
	// 故改用 Forge 提供的 RecordItem(Supplier<SoundEvent>) 构造；声音惰性取自身 SoundEvent，避免注册期解引用。
	// 参数取自源版 data/aeronautics/jukebox_song/cloud_skipper.json：比较器输出 12、时长 225 秒。
	// [关键坑·时长单位] 原版构造 RecordItem(int,SoundEvent,...) 的第 4 参是「秒」（内部 ×20 存为刻），
	// 但 Forge 追加的 Supplier 版构造 RecordItem(int,Supplier,...) 第 4 参是「刻」且不 ×20（字节码实测）。
	// 我们用的是 Supplier 版，故必须自行换算：225 秒 × 20 = 4500 刻。
	// 若误填 225，则 lengthInTicks=225 刻≈11.25 秒，JukeboxBlockEntity 会在约 12 秒后 levelEvent(1011) 掐断声音（歌没放完就停）。
	// ogg 实测时长 225.1 秒，填 4500 刻使停止判定落在 226 秒，恰在歌自然放完之后。
	public static ItemEntry<RecordItem> MUSIC_DISC_CLOUD_SKIPPER =
			REGISTRATE.item("music_disc_cloud_skipper",
							properties -> new RecordItem(12, () -> AeroSoundEvents.MUSIC_DISC_CLOUD_SKIPPER.event(), properties, 4500))
					.properties(p -> p
							.stacksTo(1)
							.rarity(Rarity.RARE)
					)
					.tag(AeroTags.ItemTags.MUSIC_DISCS)
					// [1.20.1 移植修复·关键] 源版只打 c:music_discs（NeoForge 通用标签命名空间），
					// 但 1.20.1 的 JukeboxBlockEntity.setItem 首行硬编码判定 #minecraft:music_discs，
					// 不在该标签内就直接 return——不存物品、不改方块状态、不播放，
					// 而 RecordItem.useOn 无论如何都会扣掉 1 个，表现为「唱片凭空消失、唱片机毫无反应」。
					// 故必须补打原版标签（同时手写 data/minecraft/tags/items/music_discs.json 保证不依赖数据生成）。
					.tag(ItemTags.MUSIC_DISCS)
					.lang("Music Disc")
					.register();

	public static ItemEntry<Item> ENDSTONE_POWDER = ingredient("end_stone_powder", p -> p
			);

	private static ItemEntry<Item> ingredient(final String name, NonNullUnaryOperator<Item.Properties> poperator) {
		return REGISTRATE.item(name, Item::new)
				.properties(poperator)
				.register();
	}

	public static void init() {}
}
