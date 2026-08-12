package dev.ryanhcode.offroad.neoforge.data;

import dev.ryanhcode.offroad.Offroad;
import dev.ryanhcode.offroad.index.OffroadAdvancements;
import dev.ryanhcode.offroad.index.OffroadSoundEvents;
import dev.ryanhcode.offroad.index.OffroadTags;
import dev.ryanhcode.offroad.neoforge.index.OffroadSoundEventsNeoForge;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.registries.RegisterEvent;

import java.util.concurrent.CompletableFuture;

public class OffroadDatagen {

    public static void gatherDataHighPriority(final GatherDataEvent event) {
        OffroadTags.addGenerators();
    }

    public static void gatherData(final GatherDataEvent event) {
        final DataGenerator generator = event.getGenerator();
        final PackOutput output = generator.getPackOutput();
        final CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.addProvider(event.includeServer(), new OffroadAdvancements(output, lookupProvider));
        generator.addProvider(event.includeServer(), OffroadSoundEvents.REGISTRY.getProvider(output));
    }

    public static void registerEvent(final RegisterEvent event) {
        // [1.20.1 port] 仅保留 SOUND_EVENT 批次注册。原 OffroadAdvancements.init() /
        // OffroadAdvancementTriggers.register() 会触发父类 SimAdvancements 的 <clinit>
        // 立即 asStack() 取未注册条目 NPE，已移到 OffroadNeoForge.init(FMLCommonSetupEvent) 执行。
        event.register(Registries.SOUND_EVENT, OffroadSoundEventsNeoForge::register);
    }
}
