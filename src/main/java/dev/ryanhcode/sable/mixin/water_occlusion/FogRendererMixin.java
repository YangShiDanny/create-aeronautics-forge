package dev.ryanhcode.sable.mixin.water_occlusion;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.mixinterface.water_occlusion.CameraWaterOcclusionExtension;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * For now, we're okay with water fog in the camera.
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @WrapOperation(method = "*", remap = false, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;m_167685_()Lnet/minecraft/world/level/material/FogType;", remap = false))
    private static FogType sable$getFluidinCamera(final Camera instance, final Operation<FogType> original) {
        final CameraWaterOcclusionExtension camera = (CameraWaterOcclusionExtension) Minecraft.getInstance().gameRenderer.getMainCamera();
        camera.sable$setIgnoreOcclusion(true);
        final FogType type = original.call(instance);
        camera.sable$setIgnoreOcclusion(false);
        return type;
    }

}
