package dev.simulated_team.simulated.network;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.network.packets.*;
import dev.simulated_team.simulated.network.packets.contraption_diagram.DiagramDataPacket;
import dev.simulated_team.simulated.network.packets.contraption_diagram.DiagramOpenPacket;
import dev.simulated_team.simulated.network.packets.contraption_diagram.DiagramSaveConfigPacket;
import dev.simulated_team.simulated.network.packets.contraption_diagram.RequestDiagramDataPacket;
import dev.simulated_team.simulated.network.packets.handle.ClientboundPlayersHoldingHandlePacket;
import dev.simulated_team.simulated.network.packets.honey_glue.HoneyGlueChangeBoundsPacket;
import dev.simulated_team.simulated.network.packets.honey_glue.HoneyGlueSpawnPacket;
import dev.simulated_team.simulated.network.packets.honey_glue.HoneyGlueSyncBoundsPacket;
import dev.simulated_team.simulated.network.packets.linked_typewriter.*;
import dev.simulated_team.simulated.network.packets.lodestone_compass.UpdateClientLodestonePositionPacket;
import dev.simulated_team.simulated.network.packets.name_plate.NameplateChangeNamePacket;
import dev.simulated_team.simulated.network.packets.physics_assembler.PhysicsAssemblerFailedPacket;
import dev.simulated_team.simulated.network.packets.physics_assembler.PhysicsAssemblerFlickAndHoldLeverPacket;
import dev.simulated_team.simulated.network.packets.physics_staff.*;
import dev.simulated_team.simulated.network.packets.rope.ClientboundRopeDataPacket;
import dev.simulated_team.simulated.network.packets.rope.ClientboundRopeStoppedPacket;
import foundry.veil.api.network.VeilPacketManager;

public class SimPacketManager {

    public static final VeilPacketManager INSTANCE = VeilPacketManager.create(Simulated.MOD_ID, "0.1");

    public static void init() {
        INSTANCE.registerServerbound(AssemblePacket.class, AssemblePacket.TYPE, AssemblePacket.CODEC, AssemblePacket::handle);
        INSTANCE.registerServerbound(BlockEntityObservedPacket.class, BlockEntityObservedPacket.TYPE, BlockEntityObservedPacket.CODEC, BlockEntityObservedPacket::handle);
        INSTANCE.registerServerbound(ConfigureAltitudeSensorPacket.class, ConfigureAltitudeSensorPacket.TYPE, ConfigureAltitudeSensorPacket.CODEC, ConfigureAltitudeSensorPacket::handle);
        INSTANCE.registerServerbound(ConfigureModulatingLinkedRecieverPacket.class, ConfigureModulatingLinkedRecieverPacket.TYPE, ConfigureModulatingLinkedRecieverPacket.CODEC, ConfigureModulatingLinkedRecieverPacket::handle);
        INSTANCE.registerServerbound(ThrottleLeverSignalPacket.class, ThrottleLeverSignalPacket.TYPE, ThrottleLeverSignalPacket.CODEC, ThrottleLeverSignalPacket::handle);

        INSTANCE.registerServerbound(NameplateChangeNamePacket.class, NameplateChangeNamePacket.TYPE, NameplateChangeNamePacket.CODEC, NameplateChangeNamePacket::handle);

        INSTANCE.registerServerbound(TypewriterKeyInteractionPacket.class, TypewriterKeyInteractionPacket.TYPE, TypewriterKeyInteractionPacket.CODEC, TypewriterKeyInteractionPacket::handle);
        INSTANCE.registerServerbound(TypewriterKeySavePacket.class, TypewriterKeySavePacket.TYPE, TypewriterKeySavePacket.CODEC, TypewriterKeySavePacket::handle);
        INSTANCE.registerServerbound(TypewriterDisconnectUser.class, TypewriterDisconnectUser.TYPE, TypewriterDisconnectUser.CODEC, TypewriterDisconnectUser::handle);
        INSTANCE.registerServerbound(TypewriterMenuModifySlots.class, TypewriterMenuModifySlots.TYPE, TypewriterMenuModifySlots.CODEC, TypewriterMenuModifySlots::handle);
        INSTANCE.registerServerbound(TypewriterSaveKeyToItemPacket.class, TypewriterSaveKeyToItemPacket.TYPE, TypewriterSaveKeyToItemPacket.CODEC, TypewriterSaveKeyToItemPacket::handle);

        INSTANCE.registerServerbound(SteeringWheelPacket.class, SteeringWheelPacket.TYPE, SteeringWheelPacket.CODEC, SteeringWheelPacket::handle);

        INSTANCE.registerServerbound(PlaceSpringPacket.class, PlaceSpringPacket.TYPE, PlaceSpringPacket.CODEC, PlaceSpringPacket::handle);
        INSTANCE.registerServerbound(PlaceMergingGluePacket.class, PlaceMergingGluePacket.TYPE, PlaceMergingGluePacket.CODEC, PlaceMergingGluePacket::handle);

        INSTANCE.registerServerbound(UpdatePlayerUsingHandlePacket.class, UpdatePlayerUsingHandlePacket.TYPE, UpdatePlayerUsingHandlePacket.CODEC, UpdatePlayerUsingHandlePacket::handle);

        INSTANCE.registerServerbound(HoneyGlueSpawnPacket.class, HoneyGlueSpawnPacket.TYPE, HoneyGlueSpawnPacket.CODEC, HoneyGlueSpawnPacket::handle);
        INSTANCE.registerServerbound(HoneyGlueChangeBoundsPacket.class, HoneyGlueChangeBoundsPacket.TYPE, HoneyGlueChangeBoundsPacket.CODEC, HoneyGlueChangeBoundsPacket::handle);
        INSTANCE.registerClientbound(HoneyGlueSyncBoundsPacket.class, HoneyGlueSyncBoundsPacket.TYPE, HoneyGlueSyncBoundsPacket.CODEC, HoneyGlueSyncBoundsPacket::handle);

        INSTANCE.registerServerbound(RopeRidingPacket.class, RopeRidingPacket.TYPE, RopeRidingPacket.CODEC, RopeRidingPacket::handle);
        INSTANCE.registerServerbound(RopeBreakPacket.class, RopeBreakPacket.TYPE, RopeBreakPacket.CODEC, RopeBreakPacket::handle);

        INSTANCE.registerServerbound(PhysicsStaffActionPacket.class, PhysicsStaffActionPacket.TYPE, PhysicsStaffActionPacket.CODEC, PhysicsStaffActionPacket::handle);
        INSTANCE.registerServerbound(PhysicsStaffDragPacket.class, PhysicsStaffDragPacket.TYPE, PhysicsStaffDragPacket.CODEC, PhysicsStaffDragPacket::handle);
        INSTANCE.registerClientbound(PhysicsStaffBeamPacket.class, PhysicsStaffBeamPacket.TYPE, PhysicsStaffBeamPacket.CODEC, PhysicsStaffBeamPacket::handle);
        INSTANCE.registerClientbound(PhysicsStaffLocksPacket.class, PhysicsStaffLocksPacket.TYPE, PhysicsStaffLocksPacket.CODEC, PhysicsStaffLocksPacket::handle);
        INSTANCE.registerClientbound(PhysicsStaffDragSessionsPacket.class, PhysicsStaffDragSessionsPacket.TYPE, PhysicsStaffDragSessionsPacket.CODEC, PhysicsStaffDragSessionsPacket::handle);

        INSTANCE.registerClientbound(PlungerLauncherShootPacket.class, PlungerLauncherShootPacket.TYPE, PlungerLauncherShootPacket.CODEC, PlungerLauncherShootPacket::handle);
        INSTANCE.registerClientbound(PhysicsAssemblerFailedPacket.class, PhysicsAssemblerFailedPacket.TYPE, PhysicsAssemblerFailedPacket.CODEC, PhysicsAssemblerFailedPacket::handle);
        INSTANCE.registerClientbound(PhysicsAssemblerFlickAndHoldLeverPacket.class, PhysicsAssemblerFlickAndHoldLeverPacket.TYPE, PhysicsAssemblerFlickAndHoldLeverPacket.CODEC, PhysicsAssemblerFlickAndHoldLeverPacket::handle);
        INSTANCE.registerClientbound(ClientboundPlayersHoldingHandlePacket.class, ClientboundPlayersHoldingHandlePacket.TYPE, ClientboundPlayersHoldingHandlePacket.CODEC, ClientboundPlayersHoldingHandlePacket::handle);
        INSTANCE.registerClientbound(ClientboundRopeDataPacket.class, ClientboundRopeDataPacket.TYPE, ClientboundRopeDataPacket.CODEC, ClientboundRopeDataPacket::handle);
        INSTANCE.registerClientbound(ClientboundRopeStoppedPacket.class, ClientboundRopeStoppedPacket.TYPE, ClientboundRopeStoppedPacket.CODEC, ClientboundRopeStoppedPacket::handle);

        // [1.20.1 port] EndSea packet disabled (end_sea excluded)
        // INSTANCE.registerClientbound(ClientboundEndSeaPacket.class, ClientboundEndSeaPacket.TYPE, ClientboundEndSeaPacket.CODEC, ClientboundEndSeaPacket::handle);

        INSTANCE.registerServerbound(RequestDiagramDataPacket.class, RequestDiagramDataPacket.TYPE, RequestDiagramDataPacket.CODEC, RequestDiagramDataPacket::handle);
        INSTANCE.registerClientbound(DiagramDataPacket.class, DiagramDataPacket.TYPE, DiagramDataPacket.CODEC, DiagramDataPacket::handle);
        INSTANCE.registerServerbound(DiagramSaveConfigPacket.class, DiagramSaveConfigPacket.TYPE, DiagramSaveConfigPacket.CODEC, DiagramSaveConfigPacket::handle);
        INSTANCE.registerClientbound(DiagramOpenPacket.class, DiagramOpenPacket.TYPE, DiagramOpenPacket.CODEC, DiagramOpenPacket::handle);

		INSTANCE.registerClientbound(UpdateClientLodestonePositionPacket.class, UpdateClientLodestonePositionPacket.TYPE, UpdateClientLodestonePositionPacket.STREAM_CODEC, UpdateClientLodestonePositionPacket::handle);
    }
}
