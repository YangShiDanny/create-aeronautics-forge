package dev.simulated_team.simulated.neoforge.events;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterItemBindHandler;
import dev.simulated_team.simulated.events.SimulatedCommonClientEvents;
import dev.simulated_team.simulated.index.SimClickInteractions;
import dev.simulated_team.simulated.index.SimItems;
import dev.simulated_team.simulated.index.SimKeys;
import dev.simulated_team.simulated.neoforge.service.SimpleResourceManagerRegistryService;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;

@Mod.EventBusSubscriber(modid = Simulated.MOD_ID, value = Dist.CLIENT)
public class SimNeoForgeClientEvents {

	@SubscribeEvent
	public static void preClientTick(final TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.START) return;
		SimulatedCommonClientEvents.preClientTick(Minecraft.getInstance());
	}

	@SubscribeEvent
	public static void postClientTick(final TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		SimulatedCommonClientEvents.postClientTick(Minecraft.getInstance());
	}

	@SubscribeEvent
	public static void postRenderGui(final RenderGuiEvent.Post event) {
		SimulatedCommonClientEvents.renderOverlays(event.getGuiGraphics(), event.getPartialTick());
	}

	@SubscribeEvent
	public static void onRenderLevelStage(final RenderLevelStageEvent event) {
		SimulatedCommonClientEvents.onRenderLevelStage(event);
	}

	@SubscribeEvent
	public static void keyInput(final InputEvent.Key event) {
		SimulatedCommonClientEvents.onAfterKeyPress(event.getKey(), event.getScanCode(), event.getAction(), event.getModifiers());
	}

	@SubscribeEvent
	public static void postMouseButtonInput(final InputEvent.MouseButton.Post event) {
		SimulatedCommonClientEvents.onAfterMouseInput(event.getButton(), event.getModifiers(), event.getAction());
	}

	@SubscribeEvent
	public static void playerInteractRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
		if (event.getEntity().isLocalPlayer()) {
			final InteractionResult res = SimulatedCommonClientEvents.onRightClickBlock(event.getEntity(), event.getHand(), event.getPos(), event.getHitVec());

			if (res != null) {
				event.setCancellationResult(res);
				event.setCanceled(true);
				return;
			}
		}

		if (event.getItemStack().is(SimItems.HONEY_GLUE.get())) {
			event.setUseBlock(Event.Result.DENY);
			if (event.getLevel().isClientSide) {
				SimClickInteractions.HONEY_GLUE_MANAGER.selectPos(event.getPos(), event.getEntity(), event.getItemStack());
			}
			event.setCancellationResult(InteractionResult.SUCCESS);
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void itemTooltip(final ItemTooltipEvent event) {
		SimulatedCommonClientEvents.appendTooltip(event.getItemStack(), event.getFlags(), event.getEntity(), event.getToolTip());
	}

	@Mod.EventBusSubscriber(modid = Simulated.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
	public static class ModBusEvents {

		@SubscribeEvent
		public static void registerKeyMappings(final RegisterKeyMappingsEvent event) {
			SimKeys.registerTo(event::register);
		}

		@SubscribeEvent
		public static void registerGuiLayers(final RegisterGuiOverlaysEvent event) {
			event.registerAbove(new ResourceLocation("minecraft:hotbar"), "linked_typewriter_binding", LinkedTypewriterItemBindHandler.OVERLAY);
		}

		@SubscribeEvent
		public static void addReloadListener(final RegisterClientReloadListenersEvent event) {
			for (final PreparableReloadListener listener : SimpleResourceManagerRegistryService.LISTENERS) {
				event.registerReloadListener(listener);
			}
		}
	}
	@SubscribeEvent
	public static void keyInputMapping(final InputEvent.InteractionKeyMappingTriggered event) {
		if (event.isUseItem()) {
			if (SimulatedCommonClientEvents.useItemMappingTriggered()) {
				event.setCanceled(true);
				event.setSwingHand(false);
			}
		}
	}

}
