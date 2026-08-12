package dev.eriksonn.aeronautics.content.ponder;

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import dev.simulated_team.simulated.index.SimPonderTags;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import dev.eriksonn.aeronautics.index.AeroTags;
import dev.eriksonn.aeronautics.service.AeroLevititeService;
import net.createmod.ponder.api.registration.MultiTagBuilder;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.DataProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.registries.DeferredRegister;

public class AeroPonderTags {

    public static final ResourceLocation
            LEVITITE_BREAKABLE = Aeronautics.path("levitite_breakable");

    public static void register(final PonderTagRegistrationHelper<ResourceLocation> helper) {
        final PonderTagRegistrationHelper<ItemLike> itemHelper = helper.withKeyFunction(
                item -> BuiltInRegistries.ITEM.getKey(item.asItem()));

        // Aero Tags

        // [1.20.1 移植·防御] 桶条目在 1.20.1 下可能未进注册表（registrate 1.3.3 的 standardFluid().bucket() 行为差异），
        // getBucket() 可能返回 null。兜底跳过，避免 Ponder 注册阶段把整局游戏崩掉。
        final Item levititeBucket = AeroLevititeService.INSTANCE.getBucket();

        if (levititeBucket != null) {
            helper.registerTag(LEVITITE_BREAKABLE)
                    .item(levititeBucket)
                    .title("Breaks When Crystallizing")
                    .description("Blocks that are broken when nearby Levitite Blend crystallizes into Levitite. Useful for making molds for casting")
                    .register();

            itemHelper.addToTag(LEVITITE_BREAKABLE).add(levititeBucket);
        }
        itemHelper.addToTag(LEVITITE_BREAKABLE)
                .add(Blocks.CLAY)
                .add(Blocks.MUD)
                .add(Blocks.PACKED_MUD)
                .add(Blocks.COARSE_DIRT);

        // Simulated Tags

        itemHelper.addToTag(SimPonderTags.PHYSICS_BEHAVIOR)
                .add(AeroBlocks.PROPELLER_BEARING.get().asItem())
                .add(AeroBlocks.GYROSCOPIC_PROPELLER_BEARING.get().asItem())
                .add(AeroBlocks.SMART_PROPELLER.get().asItem())
                .add(AeroBlocks.ANDESITE_PROPELLER.get().asItem())
                .add(AeroBlocks.WOODEN_PROPELLER.get().asItem())
                .add(AeroBlocks.WHITE_ENVELOPE_BLOCK.get().asItem())
                .add(AeroBlocks.HOT_AIR_BURNER.get().asItem())
                .add(AeroBlocks.STEAM_VENT.get().asItem())
                .add(AeroBlocks.LEVITITE.get().asItem())
                .add(AeroBlocks.PEARLESCENT_LEVITITE.get().asItem());

        itemHelper.addToTag(SimPonderTags.THRUST_PRODUCING_BLOCKS)
                .add(AeroBlocks.PROPELLER_BEARING.get().asItem())
                .add(AeroBlocks.GYROSCOPIC_PROPELLER_BEARING.get().asItem())
                .add(AeroBlocks.SMART_PROPELLER.get().asItem())
                .add(AeroBlocks.ANDESITE_PROPELLER.get().asItem())
                .add(AeroBlocks.WOODEN_PROPELLER.get().asItem());

        // Create Tags

        itemHelper.addToTag(AllCreatePonderTags.KINETIC_APPLIANCES)
                .add(AeroBlocks.PROPELLER_BEARING.get().asItem())
                .add(AeroBlocks.GYROSCOPIC_PROPELLER_BEARING.get().asItem())
                .add(AeroBlocks.SMART_PROPELLER.get().asItem())
                .add(AeroBlocks.ANDESITE_PROPELLER.get().asItem())
                .add(AeroBlocks.WOODEN_PROPELLER.get().asItem())
                .add(AeroBlocks.MOUNTED_POTATO_CANNON.get().asItem());

        itemHelper.addToTag(AllCreatePonderTags.ARM_TARGETS)
                .add(AeroBlocks.MOUNTED_POTATO_CANNON.get().asItem());

        //todo remove if this isn't actually implemented before release
        itemHelper.addToTag(AllCreatePonderTags.THRESHOLD_SWITCH_TARGETS)
                .add(AeroBlocks.HOT_AIR_BURNER.get().asItem())
                .add(AeroBlocks.STEAM_VENT.get().asItem());

        itemHelper.addToTag(AllCreatePonderTags.DISPLAY_SOURCES)
                .add(AeroBlocks.HOT_AIR_BURNER.get().asItem())
                .add(AeroBlocks.STEAM_VENT.get().asItem())
                .add(AeroBlocks.PROPELLER_BEARING.get().asItem())
                .add(AeroBlocks.GYROSCOPIC_PROPELLER_BEARING.get().asItem())
                .add(AeroBlocks.SMART_PROPELLER.get().asItem())
                .add(AeroBlocks.ANDESITE_PROPELLER.get().asItem())
                .add(AeroBlocks.WOODEN_PROPELLER.get().asItem());
    }
}
