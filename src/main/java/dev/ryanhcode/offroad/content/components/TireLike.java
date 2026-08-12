package dev.ryanhcode.offroad.content.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import dev.ryanhcode.offroad.Offroad;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dev.ryanhcode.offroad.index.OffroadDataComponents;
import dev.simulated_team.simulated.libs.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.tterrag.registrate.util.entry.RegistryEntry;

public record TireLike(float radius, Vec3 rotation, Vec3 offset, Optional<ResourceLocation> model) {
    public static final Codec<TireLike> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    Codec.FLOAT.optionalFieldOf("radius", 1.0f).forGetter(TireLike::radius),
                    Vec3.CODEC.optionalFieldOf("rotation", new Vec3(90, 0, 0)).forGetter(TireLike::rotation),
                    Vec3.CODEC.optionalFieldOf("offset", new Vec3(0, 0, 0)).forGetter(TireLike::offset),
                    ResourceLocation.CODEC.optionalFieldOf("model").forGetter(TireLike::model)
            ).apply(i, TireLike::new));

    public TireLike(float radius, Vec3 rotation, Vec3 offset,  ResourceLocation model) {
        this(radius, rotation, offset, Optional.ofNullable(model));
    }

    public TireLike(final float radius) {
        this(radius, new Vec3(90, 0, 0), new Vec3(0, 0, 0), Optional.empty());
    }

    public TireLike(final float radius, final ResourceLocation model) {
        this(radius, new Vec3(90, 0, 0), new Vec3(0, 0, 0), Optional.of(model));
    }

    /*
    public static final TireLike SMALL_TIRE = new TireLike(9.0f / 16.0f, Offroad.path("item/small_tire/block"));
    public static final TireLike TIRE = new TireLike(15.5f / 16.0f, Offroad.path("item/tire/block"));
    public static final TireLike LARGE_TIRE = new TireLike(1.0f + 4.0f / 16.0f, Offroad.path("item/large_tire/block"));
    public static final TireLike MONSTROUS_TIRE = new TireLike(1.0f + 14.0f / 16.0f, Offroad.path("item/monstrous_tire/block"));
    */
    public static final TireLike SMALL_TIRE = new TireLike(12.0f / 16.0f, Offroad.path("item/small_tire/block"));
    public static final TireLike TIRE = new TireLike(15.5f / 16.0f, Offroad.path("item/tire/block"));
    public static final TireLike LARGE_TIRE = new TireLike(1.0f + 4.0f / 16.0f, Offroad.path("item/large_tire/block"));
    public static final TireLike MONSTROUS_TIRE = new TireLike(2.0f, Offroad.path("item/monstrous_tire/block"));
    public static final TireLike CRUSHING_WHEEL = new TireLike(1.0f);
    public static final TireLike WATER_WHEEL = new TireLike(1.0f);
    public static final TireLike FLYWHEEL = new TireLike(1.0f + 6.0f / 16.0f);
    public static final TireLike LARGE_WATER_WHEEL = new TireLike(2.0f + 7.0f / 16.0f);
    public static final TireLike ROCKCUTTING_WHEEL = new TireLike(0.8f, new Vec3(90, 0, 0), Vec3.ZERO, Offroad.path("block/rockcutting_wheel/wheel"));
    public static final TireLike MECHANICAL_ROLLER = new TireLike(0.7f, Vec3.ZERO, new Vec3(0, -0.5f, 0), Create.asResource("block/mechanical_roller/wheel"));

    // [1.20.1 port] NeoForge 1.21 attached the TIRE component via Item.Properties().component(),
    // which does not exist on Forge 1.20.1's Item.Properties. As a faithful fallback we keep a
    // static item->TireLike registry; fromStack() resolves the component first (for stacks that
    // already carry it) and falls back to the registry so tires are always recognised.
    //
    // [崩溃修复] 原先 register(...) 接收已 .get() 出的 Item，而 OffroadItems 在静态初始化块里
    // 调 .get() —— 那时 Forge 注册表尚未填充（注册事件在模组构造之后才触发），
    // 会抛 "Registry entry not present: offroad:small_tire" (NullPointerException) 导致整个 offroad 模组加载失败。
    // 现改为接收 RegistryEntry<Item>（本质是 Supplier<Item>），仅在运行时（注册表已就绪）才 .get() 解析，
    // 静态初始化阶段只存引用、绝不触发注册查询。
    private static final Map<RegistryEntry<? extends Item>, TireLike> BY_ENTRY = new HashMap<>();

    public static void register(final RegistryEntry<? extends Item> item, final TireLike tire) {
        BY_ENTRY.put(item, tire);
    }

    @Nullable
    public static TireLike fromStack(final ItemStack stack) {
        final TireLike component = DataComponentType.get(stack, OffroadDataComponents.TIRE);
        if (component != null) return component;
        final Item item = stack.getItem();
        for (final var entry : BY_ENTRY.entrySet()) {
            if (entry.getKey().get() == item) return entry.getValue();
        }
        return null;
    }
}
