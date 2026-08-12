package dev.simulated_team.simulated.registrate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.builders.Builder;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import dev.simulated_team.simulated.client.BlockPropertiesTooltip;
import dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget;
import dev.simulated_team.simulated.index.SimRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SimulatedRegistrate extends CreateRegistrate {

    public static final Set<String> MODS = new HashSet<>();
    public static final List<Supplier<Item>> TAB_ITEMS = Collections.synchronizedList(new ArrayList<>());
    public static final Map<ResourceLocation, ResourceLocation> ITEM_TO_SECTION = new ConcurrentHashMap<>();

    private static final Map<ResourceLocation, Supplier<ItemLike>> NAVIGATION_TARGET_ITEMS = new ConcurrentHashMap<>();

    // [1.20.1 port] NeoForge 1.21 通过 ModifyDefaultComponentsEvent 把 TARGET 数据组件挂到导航目标物品上，
    // 该事件在 Forge 1.20.1 不存在，导致 ofStack 永远取不到 TARGET、导航台拒绝所有物品（BUG-19）。
    // 改为注册期建立 物品 -> 导航目标ID 的反向映射，运行时直接按手持物品反查实例。
    private static final Map<Item, ResourceLocation> NAVIGATION_TARGET_BY_ITEM = new ConcurrentHashMap<>();
    private static volatile boolean navigationIndexBuilt = false;

    private ResourceLocation currentSection;

    public SimulatedRegistrate(final ResourceLocation initialSection, final String modId) {
        super(modId);
        this.currentSection = initialSection;
        MODS.add(modId);
    }

    public SimulatedRegistrate inSection(final ResourceLocation section) {
        this.currentSection = section;
        return this;
    }

    public <T> Codec<T> byNameCodecExpanded(final ResourceKey<? extends Registry<T>> key) {
        return ResourceLocation.CODEC.flatXmap((resourceLoc) -> {
            T gatheredEntry = null;
            for (final RegistryEntry<T> entry : this.getAll(key)) {
                if (entry.getId().equals(resourceLoc)) {
                    gatheredEntry = entry.get();
                    break;
                }
            }

            if (gatheredEntry != null) {
                return DataResult.success(gatheredEntry);
            } else {
                return DataResult.error(() -> "Unknown registry element in " + key + ":" + resourceLoc);
            }
        }, (T) -> {
            ResourceLocation id = null;
            for (final RegistryEntry<T> entry : this.getAll(key)) {
                if (entry.is(T)) {
                    id = entry.getId();
                    break;
                }
            }

            if (id != null) {
                return DataResult.success(id);
            } else {
                return DataResult.error(() -> "Unknown registry element in " + key + ":" + T);
            }
        });
    }

    public static ResourceLocation sectionOf(final Item item) {
        return ITEM_TO_SECTION.get(BuiltInRegistries.ITEM.getKey(item));
    }

    @Override
    protected <R, T extends R>  RegistryEntry<T> accept(final String name, final ResourceKey<? extends Registry<R>> type, final Builder<R, T, ?, ?> builder, final NonNullSupplier<? extends T> creator, final NonNullFunction<RegistryObject<T>, ? extends RegistryEntry<T>> entryFactory) {
        final RegistryEntry<T> entry = super.accept(name, type, builder, creator, entryFactory);

        if (type.equals(Registries.ITEM)) {
            final RegistryEntry<? extends Item> itemEntry = (RegistryEntry<? extends Item>) entry;
            TAB_ITEMS.add(itemEntry::get);
            ITEM_TO_SECTION.put(entry.getId(), this.currentSection);
        }

        return entry;
    }

    public void addExtraItem(final ResourceLocation item) {
        TAB_ITEMS.add(() -> BuiltInRegistries.ITEM.get(item));
        ITEM_TO_SECTION.put(item, this.currentSection);
    }

    public <T extends NavigationTarget> RegistryEntry<T> navTarget(final String name, final NonNullSupplier<T> navTableItem, Supplier<ItemLike> itemSupplier) {
        RegistryEntry<T> entry = this.simple(this.self(), name, SimRegistries.Keys.NAVIGATION_TARGET, navTableItem);
        // [1.20.1 port] 只登记「物品供应器」，绝不在此处调用 itemSupplier.get()。
        // 本方法在 SimNavigationTargets 的静态初始化阶段（mod 构造期）执行，
        // 此时 Registrate 的方块/物品条目尚未注册完成，提前解析会抛
        // 「Registry entry not present: simulated:redstone_magnet」导致 mod 加载失败（BUG-20）。
        NAVIGATION_TARGET_ITEMS.put(entry.getId(), itemSupplier);
        return entry;
    }

    public <T extends NavigationTarget> RegistryEntry<T> navTarget(final String name, final NonNullSupplier<T> navTableItem, ItemLike item) {
        return navTarget(name, navTableItem, () -> item);
    }

    public <T extends BlockPropertiesTooltip.Entry> RegistryEntry<T>
            propertyTooltip(final String name, final NonNullSupplier<T> tooltipFunction) {
        return this.simple(this.self(), name, SimRegistries.Keys.PROPERTY_TOOLTIP, tooltipFunction);
    }

    /**
     * [1.20.1 port] 惰性建立「物品 -> 导航目标ID」索引。
     * 只能在游戏注册表冻结之后（即玩家真正交互时）执行，不可在 mod 构造期调用。
     */
    private static void buildNavigationIndex() {
        if (navigationIndexBuilt) {
            return;
        }
        synchronized (NAVIGATION_TARGET_BY_ITEM) {
            if (navigationIndexBuilt) {
                return;
            }
            for (final Map.Entry<ResourceLocation, Supplier<ItemLike>> entry : NAVIGATION_TARGET_ITEMS.entrySet()) {
                final ItemLike itemLike;
                try {
                    itemLike = entry.getValue().get();
                } catch (final Exception ignored) {
                    // 物品尚未注册完成：本次放弃建索引，保持未完成标记，下次交互再试
                    return;
                }
                if (itemLike == null) {
                    continue;
                }
                final Item item = itemLike.asItem();
                if (item == null || item == Items.AIR) {
                    continue;
                }
                NAVIGATION_TARGET_BY_ITEM.put(item, entry.getKey());
            }
            navigationIndexBuilt = true;
        }
    }

    /**
     * [1.20.1 port] 运行时按手持物品反查对应的导航目标实例。
     * 取代 NeoForge 1.21 的 ModifyDefaultComponentsEvent（Forge 1.20.1 无此事件）。
     */
    public static NavigationTarget getNavigationTarget(final ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        buildNavigationIndex();
        final ResourceLocation id = NAVIGATION_TARGET_BY_ITEM.get(stack.getItem());
        if (id == null) {
            return null;
        }
        return SimRegistries.NAVIGATION_TARGET.get(id);
    }
}
