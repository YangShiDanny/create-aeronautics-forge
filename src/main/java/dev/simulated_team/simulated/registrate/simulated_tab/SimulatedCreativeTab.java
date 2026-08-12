package dev.simulated_team.simulated.registrate.simulated_tab;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.simulated_team.simulated.client.sections.SimulatedSection;
import dev.simulated_team.simulated.index.SimResourceManagers;
import dev.simulated_team.simulated.mixin.accessor.CreativeModeInventoryScreenAccessor;
import dev.simulated_team.simulated.mixin_interface.SpriteContentsExtension;
import dev.simulated_team.simulated.mixin_interface.TickerExtension;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import foundry.veil.api.client.color.Color;
import foundry.veil.api.client.color.Colorc;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SimulatedCreativeTab {
	public static int CURRENT_ROW = 0;
	public static final Object2IntOpenHashMap<ResourceLocation> SECTION_Y_VALUES = new Object2IntOpenHashMap<>();

	public static void renderBanners(final CreativeModeInventoryScreen screen, final GuiGraphics graphics, int mouseX, int mouseY) {
		final PoseStack ps = graphics.pose();
		ps.pushPose();

		RenderSystem.enableDepthTest();
		RenderSystem.setShaderColor(1, 1, 1, 1);
		int left = ((CreativeModeInventoryScreenAccessor) screen).getLeftPos() + 8;
		int top = ((CreativeModeInventoryScreenAccessor) screen).getTopPos() + 17;
		ps.translate(left, top, 0);

		final List<SimulatedSection> sections = SimResourceManagers.SIMULATED_SECTION.sortedEntries();

		for (final SimulatedSection section : sections) {
			ResourceLocation id = SimResourceManagers.SIMULATED_SECTION.getId(section);
			int yValue = SECTION_Y_VALUES.getInt(id);
			final int sectionRow = (yValue - CURRENT_ROW);
			if(sectionRow < 0 || sectionRow > 4) continue;

			Font font = Minecraft.getInstance().font;
			int x = 0;
			int y = sectionRow * 18;
			int w = 162;
			int h = 18;

			ResourceLocation bannerTexture = section.sprite();

			if(section.animateOnHover()) {
				boolean isHovering =
						mouseX >= left + x &&
						mouseX <= left + x + w &&
						mouseY >= top + y &&
						mouseY <= top + y + h;
				setPlaying(bannerTexture, isHovering);
			}

			// 【1.20.1 移植·修复】1.21 的 GUI 精灵图集机制在 1.20.1 不存在，
			// 直接 blit(精灵名) 会把 "simulated:banner" 当独立纹理文件加载 → 失败 → 紫黑棋盘
			//（日志实证：Failed to load texture: simulated:banner）。
			// 横幅已通过 assets/minecraft/atlases/blocks.json 缝进方块图集，这里改用图集精灵绘制。
			final TextureAtlas bannerAtlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
			final TextureAtlasSprite bannerSprite = bannerAtlas.getSprite(bannerTexture);
			graphics.blit(x, y, 0, w, h, bannerSprite);

			Component text = section.title().text();
			int textWidth = font.width(text);

			Colorc background = section.title().background();
			graphics.fill(x + 2, y + 2, x + textWidth + 8, y + h - 2, background.argb());

			Colorc light = section.title().color();
			Colorc dark = section.title().secondaryColor()
					.orElseGet(() -> {
						final int argb = light.argb();
						final float f = 0.8f;
						final int a = (argb >> 24) & 0xFF;
						final int r = (int) (((argb >> 16) & 0xFF) * f);
						final int g = (int) (((argb >> 8) & 0xFF) * f);
						final int b = (int) ((argb & 0xFF) * f);
						return new Color((a << 24) | (r << 16) | (g << 8) | b);
					});
			drawAuraText(graphics, text, dark.argb(), light.argb(), x + 5, y + 5);
		}
		ps.popPose();
		RenderSystem.disableDepthTest();
	}

	public static void drawAuraText(GuiGraphics graphics, Component text, int color1, int color2, int x, int y) {
		Font font = Minecraft.getInstance().font;
		Window window = Minecraft.getInstance().getWindow();
		float scale = (float) window.getGuiScale();

		graphics.drawString(font, text, x, y, color1, true);

		PoseStack ps = graphics.pose();
		ps.pushPose();
		ps.translate(0, 0, 1);
		Matrix4f pose = ps.last().pose();
		Vector3f position = pose.transformPosition(new Vector3f(x, y, 0));
		Vector3f corner = pose.transformPosition(new Vector3f(x + font.width(text), y + font.lineHeight / 1.8f, 0));

		position.mul(scale);
		corner.mul(scale);
		int height = (int) (corner.y - position.y);
		int width = (int) (corner.x - position.x);
		RenderSystem.enableScissor(
                (int) position.x,
				window.getHeight() - (int) position.y - height,
				width,
				height
		);

		graphics.drawString(font, text, x, y, color2, false);

		RenderSystem.disableScissor();

		ps.popPose();

	}

	public static void processItems(final Consumer<ItemStack> displayItems, final Consumer<ItemStack> searchItems) {
		final Map<SimulatedSection, List<ItemStack>> sectionMap = new HashMap<>();

		for (final Supplier<Item> entry : SimulatedRegistrate.TAB_ITEMS) {
			final Item item = entry.get();
			final ItemStack stack = item.getDefaultInstance();

			final ResourceLocation sectionId = SimulatedRegistrate.sectionOf(item);
			if(sectionId == null)
				continue;

			final SimulatedSection section = SimResourceManagers.SIMULATED_SECTION.get(sectionId);
			sectionMap.computeIfAbsent(section, (s) -> new LinkedList<>()).add(stack);
		}

		for (int i = 0; i < 9; i++) {
			displayItems.accept(ItemStack.EMPTY);
		}

		int y = 0;
		final List<SimulatedSection> sectionKeys = sectionMap.keySet().stream().sorted().toList();
		for (final SimulatedSection key : sectionKeys) {

			int itemCount = 0;
			final List<ItemStack> sectionItems = sectionMap.get(key);
			for (ItemStack item : sectionItems) {
				item = CreativeTabItemTransforms.applyTransform(item);

				if(CreativeTabItemTransforms.VisibilityType.SEARCH_ONLY.has(item.getItem())) {
					searchItems.accept(item);
				} else if(!CreativeTabItemTransforms.VisibilityType.INVISIBLE.has(item.getItem())) {
					displayItems.accept(item);
					searchItems.accept(item);
					itemCount++;
				}
			}

			ResourceLocation id = SimResourceManagers.SIMULATED_SECTION.getId(key);
			SECTION_Y_VALUES.put(id, y);
			final int rowCount = (int) Math.ceil(itemCount / 9.0f);
			y += rowCount + 1;

			if(key != null && key.equals(sectionKeys.get(sectionKeys.size()-1))) {
				break;
			}

			int padding = 9 - itemCount % 9;
			if(padding < 9) {
				padding += 9;
			}
			for (int i = 0; i < padding; i++) {
				displayItems.accept(ItemStack.EMPTY);
			}
		}
	}

	public static void setPlaying(ResourceLocation resourceLocation, boolean playing) {
		final TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
		final TextureAtlasSprite sprite = atlas.getSprite(resourceLocation);
		SpriteContents.Ticker ticker = ((SpriteContentsExtension) sprite.contents()).simulated$getTicker();
		if(ticker instanceof TickerExtension extension) {
			extension.simulated$setPlaying(playing);
		}
	}

}
