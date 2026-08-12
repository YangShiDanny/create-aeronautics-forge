package dev.ryanhcode.sable.util;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

/**
 * [1.20.1 兼容] 配置注册。
 *
 * <p>历史教训（2026-07-27 第四轮，javap 铁证）：
 * <ul>
 *   <li>47.3.1 的 FMLJavaModLoadingContext 只有 getModEventBus()/get() 两个方法，
 *       既没有 registerConfig，也不继承 ModLoadingContext——此前两轮反射都在它身上找方法，必然落空。
 *       （47.3.x 后期版本它才继承 ModLoadingContext，本机 47.3.27 的 javap 结果曾造成误判。）</li>
 *   <li>registerConfig 的真正归属自始至终是 ModLoadingContext，且 47.3.1 与 47.4.20 的签名逐字节一致：
 *       registerConfig(ModConfig.Type, IConfigSpec)。IConfigSpec 在 47.3.1 的 fmlcore 中存在。</li>
 * </ul>
 *
 * <p>因此无需任何反射：直接编译期调用，47.4.20 编译出的方法描述符在 47.3.1 上原样存在，
 * 两个运行环境都不会 NoSuchMethodError。
 */
public final class ForgeConfigRegistrar {
    private ForgeConfigRegistrar() {}

    public static void register(final ModConfig.Type type, final Object spec) {
        ModLoadingContext.get().registerConfig(type, (IConfigSpec<?>) spec);
    }
}
