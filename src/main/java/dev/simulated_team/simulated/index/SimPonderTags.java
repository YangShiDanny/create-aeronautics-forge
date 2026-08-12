package dev.simulated_team.simulated.index;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import dev.simulated_team.simulated.Simulated;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

public class SimPonderTags {

    public static final ResourceLocation
            NAVIGATION_ITEMS = Simulated.path("navigation_items"),
            PHYSICS_BEHAVIOR = Simulated.path("physics_behavior"),
            THRUST_PRODUCING_BLOCKS = Simulated.path("thrust_blocks"),
            PHYSICS_SENSORS = Simulated.path("physics_sensors");

    public static void register(final PonderTagRegistrationHelper<ResourceLocation> helper) {
        final PonderTagRegistrationHelper<ItemLike> itemHelper = helper.withKeyFunction(
                item -> BuiltInRegistries.ITEM.getKey(item.asItem()));

        // Sim Tags

        helper.registerTag(NAVIGATION_ITEMS)
                .addToIndex()
                .item(SimBlocks.NAVIGATION_TABLE.get().asItem())
                .title("Navigation Items")
                .description("Components which offer a destination to a Navigation Table")
                .register();

        itemHelper.addToTag(NAVIGATION_ITEMS)
                .add(SimBlocks.NAVIGATION_TABLE.get().asItem()) // why create... why
                .add(Items.COMPASS)
                .add(Blocks.LODESTONE)
                .add(Items.RECOVERY_COMPASS)
                .add(Items.FILLED_MAP)
                .add(SimBlocks.REDSTONE_MAGNET.get().asItem());

        helper.registerTag(PHYSICS_BEHAVIOR)
                .addToIndex()
                .item(SimBlocks.PHYSICS_ASSEMBLER.get().asItem())
                .title("Physics Behavior")
                .description("Components which have unique physics behavior or interactions")
                .register();

        itemHelper.addToTag(PHYSICS_BEHAVIOR)
                .add(SimBlocks.PHYSICS_ASSEMBLER.get().asItem()) // why create... why
                .add(SimBlocks.SWIVEL_BEARING.get().asItem())
                .add(AllBlocks.STICKER.get().asItem())
                .add(AllBlocks.WEIGHTED_EJECTOR.get().asItem())
                .add(SimBlocks.DOCKING_CONNECTOR.get().asItem())
                .add(SimBlocks.REDSTONE_MAGNET.get().asItem())
                .add(AllBlocks.SAIL.get().asItem())
                .add(SimBlocks.WHITE_SYMMETRIC_SAIL.get().asItem())
                .add(AllItems.BELT_CONNECTOR.get().asItem())
                .add(SimItems.SPRING.get().asItem())
                .add(SimItems.ROPE_COUPLING.get().asItem());

        helper.registerTag(THRUST_PRODUCING_BLOCKS)
                .addToIndex()
                .item(AllBlocks.ENCASED_FAN.get().asItem())
                .title("Thrust Producing Blocks")
                .description("Components which produce thrust on Simulated Contraptions")
                .register();

        itemHelper.addToTag(THRUST_PRODUCING_BLOCKS)
                .add(AllBlocks.ENCASED_FAN.get().asItem())
                .add(AllBlocks.NOZZLE.get().asItem());

        helper.registerTag(PHYSICS_SENSORS)
                .addToIndex()
                .item(SimBlocks.OPTICAL_SENSOR.get().asItem())
                .title("Physics Sensor Blocks")
                .description("Components which provide dynamic information about the world around them")
                .register();

        itemHelper.addToTag(PHYSICS_SENSORS)
                .add(SimBlocks.ALTITUDE_SENSOR.get().asItem())
                .add(SimBlocks.VELOCITY_SENSOR.get().asItem())
                .add(SimBlocks.GIMBAL_SENSOR.get().asItem())
                .add(SimBlocks.OPTICAL_SENSOR.get().asItem())
                .add(SimBlocks.NAVIGATION_TABLE.get().asItem())
                .add(SimBlocks.LASER_SENSOR.get().asItem());

        // Create Tags

        itemHelper.addToTag(AllCreatePonderTags.KINETIC_RELAYS)
                .add(SimBlocks.DIRECTIONAL_GEARSHIFT.get().asItem())
                .add(SimBlocks.TORSION_SPRING.get().asItem())
                .add(SimBlocks.ANALOG_TRANSMISSION.get().asItem());

        itemHelper.addToTag(AllCreatePonderTags.KINETIC_SOURCES)
                .add(SimBlocks.STEERING_WHEEL.get().asItem())
                .add(SimBlocks.RED_PORTABLE_ENGINE.asItem());

        itemHelper.addToTag(AllCreatePonderTags.KINETIC_APPLIANCES)
                .add(SimBlocks.SWIVEL_BEARING.get().asItem())
                .add(SimBlocks.ROPE_WINCH.get().asItem());

        itemHelper.addToTag(AllCreatePonderTags.FLUIDS)
                .add(SimBlocks.DOCKING_CONNECTOR.get().asItem());

        itemHelper.addToTag(AllCreatePonderTags.LOGISTICS)
                .add(SimBlocks.AUGER_SHAFT.get().asItem())
                .add(SimBlocks.AUGER_COG.get().asItem())
                .add(SimBlocks.DOCKING_CONNECTOR.get().asItem());

        itemHelper.addToTag(AllCreatePonderTags.REDSTONE)
                .add(SimBlocks.THROTTLE_LEVER.get().asItem())
                .add(SimBlocks.LINKED_TYPEWRITER.get().asItem())
                .add(SimBlocks.DIRECTIONAL_LINKED_RECEIVER.get().asItem())
                .add(SimBlocks.MODULATING_LINKED_RECEIVER.get().asItem())
                .add(SimBlocks.REDSTONE_ACCUMULATOR.get().asItem())
                .add(SimBlocks.REDSTONE_INDUCTOR.get().asItem());

        itemHelper.addToTag(AllCreatePonderTags.MOVEMENT_ANCHOR)
                .add(SimBlocks.PHYSICS_ASSEMBLER.get().asItem())
                .add(SimBlocks.SWIVEL_BEARING.get().asItem());

        itemHelper.addToTag(AllCreatePonderTags.SAILS)
                .add(SimBlocks.WHITE_SYMMETRIC_SAIL.get().asItem());

        itemHelper.addToTag(AllCreatePonderTags.ARM_TARGETS)
                .add(SimBlocks.RED_PORTABLE_ENGINE.asItem())
                .add(SimBlocks.NAVIGATION_TABLE.get().asItem());

        itemHelper.addToTag(AllCreatePonderTags.DISPLAY_SOURCES)
                .add(SimBlocks.AUGER_SHAFT.get().asItem())
                .add(SimBlocks.AUGER_COG.get().asItem())
                .add(SimBlocks.RED_PORTABLE_ENGINE.asItem())
                .add(SimBlocks.ALTITUDE_SENSOR.get().asItem())
                .add(SimBlocks.VELOCITY_SENSOR.get().asItem())
                .add(SimBlocks.GIMBAL_SENSOR.get().asItem())
                .add(SimBlocks.OPTICAL_SENSOR.get().asItem())
                .add(SimBlocks.NAVIGATION_TABLE.get().asItem())
                .add(SimBlocks.DOCKING_CONNECTOR.get().asItem())
                .add(SimBlocks.LINKED_TYPEWRITER.get().asItem());

        itemHelper.addToTag(AllCreatePonderTags.DISPLAY_TARGETS)
                .add(SimBlocks.NAMEPLATES.get(DyeColor.WHITE).get().asItem());

        itemHelper.addToTag(AllCreatePonderTags.THRESHOLD_SWITCH_TARGETS)
                .add(SimBlocks.ROPE_WINCH.get().asItem());
    }
}
