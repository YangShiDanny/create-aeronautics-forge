package dev.eriksonn.aeronautics.neoforge.content.fluids;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tterrag.registrate.builders.FluidBuilder;
import dev.eriksonn.aeronautics.Aeronautics;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;
import org.joml.Vector3f;

import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class AeroFluidType extends FluidType {
	private Vector3f fogColor;
	private Supplier<Float> fogDistance;
	private final ResourceLocation stillTexture;
	private final ResourceLocation flowingTexture;

	public AeroFluidType(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture) {
		super(properties);
		this.stillTexture = stillTexture;
		this.flowingTexture = flowingTexture;
	}

	// [1.20.1 移植修正·v3] 崩溃根因：Forge 1.20.1 的 Registrate 1.3.3 调用 FluidTypeFactory 时
	// 传入的 still/flowing 贴图 ResourceLocation 为 null，原兜底用 ResourceLocation.tryBuild(...)，
	// 而 tryBuild 在路径被判无效时会返回 null -> getStillTexture() 返回 null ->
	// ForgeHooksClient.getFluidSprites 用 null 作键调 TextureAtlas.getSprite(null) -> 空指针崩溃
	// （本次崩溃：Ponder 渲染 levitite_blend 流体时 Tesselating liquid in world）。
	// 改用两参构造器 new ResourceLocation(namespace, path)：它不做任何校验、永不返回 null，
	// 贴图 PNG 实际位于 assets/aeronautics/textures/fluid/levitite_blend_still.png，可被正常缝合。
	public static FluidBuilder.FluidTypeFactory create(String fluidName, int fogColor, Supplier<Float> fogDistance, Factory factory) {
		return (p, s, f) -> {
			ResourceLocation still = s != null ? s : new ResourceLocation(Aeronautics.MOD_ID, "fluid/" + fluidName + "_still");
			ResourceLocation flowing = f != null ? f : new ResourceLocation(Aeronautics.MOD_ID, "fluid/" + fluidName + "_flowing");
			AeroFluidType fluidType = factory.create(p, still, flowing);
			fluidType.fogColor = new Color(fogColor, false).asVectorF();
			fluidType.fogDistance = fogDistance;
			return fluidType;
		};
	}

	public interface Factory {
		AeroFluidType create(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture);
	}

	// [1.20.1 移植关键修正 v2] Forge 1.20.1 明确禁止 FluidType 自身 implements IClientFluidTypeExtensions：
	// FluidType.initClient 会检测传给 consumer 的对象——若它本身就是 FluidType 实例，直接抛
	//   IllegalStateException: Don't extend IFluidTypeRenderProperties in your fluid type, use an anonymous class instead.
	// （上一版写成 consumer.accept(this) 正好踩雷，导致注册流体时崩溃。）
	// 正确做法：传入一个“匿名的”IClientFluidTypeExtensions，把渲染回调委托回本 FluidType 的字段/方法。
	@Override
	public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
		consumer.accept(new IClientFluidTypeExtensions() {
			@Override
			public ResourceLocation getStillTexture() {
				return AeroFluidType.this.stillTexture;
			}

			@Override
			public ResourceLocation getFlowingTexture() {
				return AeroFluidType.this.flowingTexture;
			}

			@Override
			public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
				Vector3f customFogColor = AeroFluidType.this.getCustomFogColor();
				return customFogColor == null ? fluidFogColor : customFogColor;
			}

			@Override
			public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick, float nearDistance, float farDistance, FogShape shape) {
				IClientFluidTypeExtensions.super.modifyFogRender(camera, mode, renderDistance, partialTick, nearDistance, farDistance, shape);
				float modifier = AeroFluidType.this.getFogDistanceModifier();
				float baseWaterFog = 96.0f;
				if(modifier != 1.0f) {
					RenderSystem.setShaderFogShape(FogShape.CYLINDER);
					RenderSystem.setShaderFogStart(-8);
					RenderSystem.setShaderFogEnd(baseWaterFog * modifier);
				}
			}
		});
	}

	public Vector3f getCustomFogColor() {
		return this.fogColor;
	}

	public float getFogDistanceModifier() {
		return this.fogDistance.get();
	}
}
